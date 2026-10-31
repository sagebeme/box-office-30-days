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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Small limits, so a test can reach them: 2 orders and 2 admin tries a minute, 2 prompts per phone. */
@SpringBootTest(properties = {
        "boxoffice.limits.orders-per-minute=2",
        "boxoffice.limits.admin-per-minute=2",
        "boxoffice.limits.prompts-per-phone=2"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RateLimitTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient db;

    @Autowired
    Payments payments;

    @Autowired
    Orders orders;

    @BeforeEach
    void emptyServer() {
        ServerTestSupport.emptyTheServer(db);
    }

    private MvcTestResult orderFrom(String ip, String forwardedFor, String phone) {
        MockMvcTester.MockMvcRequestBuilder request = mvc.post().uri("/events/kilele-2026/orders")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })
                .header("HX-Request", "true")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("type", "REG").param("quantity", "1").param("buyer", "Fan").param("phone", phone);
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return request.exchange();
    }

    @Test
    void aThirdOrderInAMinuteFromOneAddressMustWait() {
        orderFrom("41.90.2.2", null, "0700 000 001");
        orderFrom("41.90.2.2", null, "0700 000 002");

        assertThat(orderFrom("41.90.2.2", null, "0700 000 003"))
                .hasStatus(429)
                .hasHeader("Retry-After", "30")
                .hasHeader("HX-Retarget", "#notice")
                .bodyText().isEqualTo("Too many orders from here. Wait 30 seconds and try again.");
        assertThat(db.sql("select count(*) from orders").query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void anotherAddressCanStillOrder() {
        orderFrom("41.90.2.3", null, "0700 000 001");
        orderFrom("41.90.2.3", null, "0700 000 002");

        assertThat(orderFrom("41.90.2.4", null, "0700 000 003")).hasStatusOk();
    }

    @Test
    void makingUpAForwardedForHeaderDoesntHelp() {
        orderFrom("41.90.2.5", null, "0700 000 001");
        orderFrom("41.90.2.5", null, "0700 000 002");

        assertThat(orderFrom("41.90.2.5", "196.201.214.9", "0700 000 003")).hasStatus(429);
    }

    @Test
    void lookingAtThePageIsNotLimited() {
        for (int i = 0; i < 5; i++) {
            assertThat(mvc.get().uri("/events/kilele-2026").with(r -> {
                r.setRemoteAddr("41.90.2.6");
                return r;
            })).hasStatusOk();
        }
    }

    @Test
    void guessingTheAdminKeyIsSlowedDownEvenWithTheRightOne() {
        ServerTestSupport.register(mvc, "guess-1", "A");
        ServerTestSupport.register(mvc, "guess-2", "A");

        assertThat(ServerTestSupport.register(mvc, ServerTestSupport.ADMIN_KEY, "A"))
                .hasStatus(429)
                .bodyJson().extractingPath("$.error").isEqualTo("Too many tries. Wait 30 seconds");
    }

    @Test
    void aPhoneGetsAFewPromptsThenNoMore() {
        Order order = ((Orders.Placed.Done) orders.place("kilele-2026",
                new OrderForm("REG", 1, "Kamau", "0711 222 333"))).order();
        assertThat(payments.requestPayment(order.reference())).isEmpty();
        assertThat(payments.requestPayment(order.reference())).isEmpty();

        assertThat(payments.requestPayment(order.reference()))
                .contains("Too many M-Pesa prompts to this number. Try again in 5 minutes");
    }
}
