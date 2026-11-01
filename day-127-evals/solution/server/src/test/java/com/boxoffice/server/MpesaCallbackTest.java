package com.boxoffice.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.server.mpesa.StubDaraja;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The callback address, as Safaricom (and anyone else on the internet) sees it. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MpesaCallbackTest {

    static final String SECRET = "test-callback-secret-that-is-long-enough";
    static final String ACCEPTED = "{\"ResultCode\":0,\"ResultDesc\":\"Accepted\"}";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient db;

    @Autowired
    Orders orders;

    @Autowired
    Payments payments;

    @Autowired
    StubDaraja daraja;

    private Order order;

    @BeforeEach
    void anOrderWaitingForPayment() {
        ServerTestSupport.emptyTheServer(db);
        order = ((Orders.Placed.Done) orders.place("kilele-2026",
                new OrderForm("VIP", 1, "Wanjiku", "0722 000 111"))).order();
        payments.requestPayment(order.reference());
    }

    private MvcTestResult post(String secret, String body) {
        return mvc.post().uri("/mpesa/callback/" + secret)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private String paidCallback() {
        return PracticePayments.callback(payments.latestCheckout(order.reference()).orElseThrow(), 0, order);
    }

    private Order.Status status() {
        return orders.find(order.reference()).orElseThrow().status();
    }

    @Test
    void theStkPushAskedForTheOrdersAmountOnTheFansPhone() {
        var request = daraja.requests().getLast();

        assertThat(request.phone()).isEqualTo("254722000111");
        assertThat(request.shillings()).isEqualTo(5_000);
        assertThat(request.accountReference()).isEqualTo(order.reference().replace("-", ""));
    }

    @Test
    void aGoodCallbackSettlesTheOrder() {
        assertThat(post(SECRET, paidCallback())).hasStatusOk().bodyJson().isStrictlyEqualTo(ACCEPTED);

        assertThat(status()).isEqualTo(Order.Status.PAID);
    }

    @Test
    void aWrongSecretGetsTheSameAnswerAndChangesNothing() {
        assertThat(post("guessed-secret", paidCallback())).hasStatusOk().bodyJson().isStrictlyEqualTo(ACCEPTED);

        assertThat(status()).isEqualTo(Order.Status.AWAITING_PAYMENT);
    }

    @Test
    void rubbishGetsTheSameAnswerToo() {
        assertThat(post(SECRET, "<xml>hello</xml>")).hasStatusOk().bodyJson().isStrictlyEqualTo(ACCEPTED);
        assertThat(post(SECRET, "")).hasStatusOk().bodyJson().isStrictlyEqualTo(ACCEPTED);
    }

    @Test
    void aCallbackSentAsPlainTextStillCounts() {
        assertThat(mvc.post().uri("/mpesa/callback/" + SECRET).contentType(MediaType.TEXT_PLAIN)
                .content(paidCallback()).exchange()).hasStatusOk();

        assertThat(status()).isEqualTo(Order.Status.PAID);
    }
}
