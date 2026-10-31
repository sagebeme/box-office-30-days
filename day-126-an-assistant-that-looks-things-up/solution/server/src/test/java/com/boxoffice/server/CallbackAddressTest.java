package com.boxoffice.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** One proxy in front of the server, and Safaricom's callbacks only from 196.201.214.200. */
@SpringBootTest(properties = {
        "boxoffice.mpesa.callback-ips=196.201.214.200, 196.201.214.206",
        "boxoffice.trusted-proxies=1"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CallbackAddressTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient db;

    @Autowired
    Orders orders;

    @Autowired
    Payments payments;

    private Order order;

    @BeforeEach
    void anOrderWaitingForPayment() {
        ServerTestSupport.emptyTheServer(db);
        order = ((Orders.Placed.Done) orders.place("kilele-2026",
                new OrderForm("REG", 1, "Achieng", "0700 111 222"))).order();
        payments.requestPayment(order.reference());
    }

    private void callbackForwardedFor(String forwardedFor) {
        String body = PracticePayments.callback(payments.latestCheckout(order.reference()).orElseThrow(), 0, order);
        assertThat(mvc.post().uri("/mpesa/callback/" + MpesaCallbackTest.SECRET)
                .with(r -> {
                    r.setRemoteAddr("10.0.0.5");
                    return r;
                })
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo(MpesaCallbackTest.ACCEPTED);
    }

    private Order.Status status() {
        return orders.find(order.reference()).orElseThrow().status();
    }

    @Test
    void aCallbackFromSafaricomCounts() {
        callbackForwardedFor("196.201.214.200");

        assertThat(status()).isEqualTo(Order.Status.PAID);
    }

    @Test
    void theRightSecretFromAnyoneElseDoesNot() {
        callbackForwardedFor("41.90.2.2");

        assertThat(status()).isEqualTo(Order.Status.AWAITING_PAYMENT);
    }

    @Test
    void pretendingToBeSafaricomInTheHeaderDoesNot() {
        // The caller wrote Safaricom's address first; our proxy added their real one after it.
        callbackForwardedFor("196.201.214.200, 41.90.2.2");

        assertThat(status()).isEqualTo(Order.Status.AWAITING_PAYMENT);
    }
}
