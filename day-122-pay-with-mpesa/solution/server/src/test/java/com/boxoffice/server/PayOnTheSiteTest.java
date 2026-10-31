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

/** A fan orders on the site and pays, with the practice M-Pesa. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PayOnTheSiteTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient db;

    private String reference;

    @BeforeEach
    void otienoOrdersTwoRegular() {
        ServerTestSupport.emptyTheServer(db);
        String page = mvc.post().uri("/events/kilele-2026/orders")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("type", "REG").param("quantity", "2").param("buyer", "Otieno").param("phone", "0712 345 678")
                .exchange().getResponse().getRedirectedUrl();
        reference = page.substring("/orders/".length());
    }

    @Test
    void theOrderPageSaysCheckYourPhoneAndKeepsAsking() {
        assertThat(mvc.get().uri("/orders/" + reference))
                .hasStatusOk()
                .bodyText()
                .contains("Check your phone", "0712 ••• 678", "KES 3,000")
                .contains("hx-get=\"/orders/" + reference + "/status?while=AWAITING_PAYMENT\"")
                .contains("hx-trigger=\"every 3s\"")
                .contains("Pretend to pay");
    }

    @Test
    void whileNothingHasChangedTheAnswerTellsHtmxNotToSwap() {
        assertThat(mvc.get().uri("/orders/" + reference + "/status?while=AWAITING_PAYMENT").header("HX-Request", "true"))
                .hasStatusOk()
                .hasHeader("HX-Reswap", "none");
    }

    @Test
    void oncePaidThePageShowsTheTicketsAndStopsAsking() {
        assertThat(mvc.post().uri("/practice/orders/" + reference + "/pay").header("HX-Request", "true"))
                .hasStatusOk()
                .bodyText()
                .contains("Paid", "PRACTICE02", "W-REG-0001", "W-REG-0002");

        assertThat(mvc.get().uri("/orders/" + reference + "/status?while=AWAITING_PAYMENT").header("HX-Request", "true"))
                .hasStatusOk()
                .doesNotContainHeader("HX-Reswap")
                .bodyText()
                .contains("W-REG-0002")
                .doesNotContain("hx-trigger", "<html");
    }

    @Test
    void aCancelledPaymentSaysSoAndFreesThePlaces() {
        assertThat(mvc.post().uri("/practice/orders/" + reference + "/pay?result=1032"))
                .bodyText()
                .contains("Not paid", "You cancelled the M-Pesa payment", "Order again");

        assertThat(mvc.get().uri("/events/kilele-2026/places")).bodyText().contains("500 places left");
    }

    @Test
    void aFanCanAskForThePromptAgain() {
        assertThat(mvc.post().uri("/orders/" + reference + "/pay").header("HX-Request", "true"))
                .hasStatusOk()
                .bodyText()
                .contains("Check your phone");

        assertThat(db.sql("select count(*) from stk_requests").query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void theGateIdWIsKeptForTheSite() {
        assertThat(ServerTestSupport.register(mvc, ServerTestSupport.ADMIN_KEY, "W"))
                .hasStatus4xxClientError()
                .bodyJson().extractingPath("$.error").isEqualTo("W is the ticket site's. Pick another gate id");
    }
}
