package com.boxoffice.server;

import static com.boxoffice.server.ServerTestSupport.sendSale;
import static com.boxoffice.server.ServerTestSupport.tokenFor;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Headers on every page, and what the server refuses to take in. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SecurityPassTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient db;

    private String gateA;

    @BeforeEach
    void emptyServer() {
        ServerTestSupport.emptyTheServer(db);
        gateA = tokenFor(mvc, "A");
    }

    private static String saleWith(String code, String buyer, String phone, long priceCents, String soldAt) {
        return """
                {"eventId":"kilele-2026","tickets":[{"code":"%s","type":"REG","buyer":"%s","phone":"%s",\
                "priceCents":%d,"soldAt":"%s"}]}""".formatted(code, buyer, phone, priceCents, soldAt);
    }

    @Test
    void everyPageSaysWhatTheBrowserMustRefuse() {
        assertThat(mvc.get().uri("/events/kilele-2026"))
                .hasHeader("Content-Security-Policy", SecurityHeaders.POLICY)
                .hasHeader("X-Content-Type-Options", "nosniff")
                .hasHeader("X-Frame-Options", "DENY")
                .hasHeader("Referrer-Policy", "no-referrer")
                .bodyText().contains("\"includeIndicatorStyles\":false");
    }

    @Test
    void theApiGetsTheHeadersToo() {
        assertThat(mvc.get().uri("/health")).hasHeader("X-Content-Type-Options", "nosniff");
    }

    @Test
    void anOrderPageIsNeverCached() {
        String page = mvc.post().uri("/events/kilele-2026/orders")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("type", "REG").param("quantity", "1").param("buyer", "Otieno").param("phone", "0712 345 678")
                .exchange().getResponse().getRedirectedUrl();

        assertThat(mvc.get().uri(page)).hasHeader("Cache-Control", "no-store");
    }

    @Test
    void aNameWithANewlineInItIsRefused() {
        assertThat(mvc.post().uri("/events/kilele-2026/orders").header("HX-Request", "true")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("type", "REG").param("quantity", "1").param("buyer", "Otieno\nPAID IN FULL")
                .param("phone", "0712 345 678"))
                .bodyText().contains("Use letters, spaces and punctuation only");
    }

    @Test
    void aGiantNameFromAGateIs400Not500() {
        assertThat(sendSale(mvc, gateA, "a-1", saleWith("A-REG-0001", "x".repeat(300), "", 150000,
                "2026-11-14T18:00:00Z")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.error").isEqualTo("A buyer's name is 1 to 200 characters");
    }

    @Test
    void aStrangeTicketCodeIsRefusedWithoutEchoingItAllBack() {
        assertThat(sendSale(mvc, gateA, "a-1", saleWith("A-" + "R".repeat(500), "Otieno", "", 150000,
                "2026-11-14T18:00:00Z")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.error").isEqualTo("A ticket code looks like A-REG-0001, not A-RRRRRRRRRRRRRRRRRR...");
    }

    @Test
    void aSillyPriceIsRefused() {
        assertThat(sendSale(mvc, gateA, "a-1", saleWith("A-REG-0001", "Otieno", "", -5, "2026-11-14T18:00:00Z")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.error").isEqualTo("A ticket costs between KES 0 and KES 100,000");
    }

    @Test
    void aTimeThatIsntATimeIsRefused() {
        assertThat(sendSale(mvc, gateA, "a-1", saleWith("A-REG-0001", "Otieno", "", 150000, "last night")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.error").isEqualTo("soldAt is a time like 2026-11-14T18:00:00Z");
    }

    @Test
    void aBadPhoneIsRefused() {
        assertThat(sendSale(mvc, gateA, "a-1", saleWith("A-REG-0001", "Otieno", "0712", 150000,
                "2026-11-14T18:00:00Z")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.error").isEqualTo("A phone number is empty or like 254712345678");
    }

    @Test
    void aSaleOfAThousandTicketsIsRefused() {
        assertThat(sendSale(mvc, gateA, "a-1", ServerTestSupport.saleOf("A", "REG", 1, 1_000)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.error").isEqualTo("A sale has at most 50 tickets");
    }
}
