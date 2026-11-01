package com.boxoffice.server;

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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The public ticket site, as a fan's browser (with and without htmx) sees it. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TicketSiteTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient db;

    @BeforeEach
    void emptyServer() {
        ServerTestSupport.emptyTheServer(db);
    }

    private MvcTestResult order(boolean htmx, String type, String quantity, String buyer, String phone) {
        MockMvcTester.MockMvcRequestBuilder request = mvc.post().uri("/events/kilele-2026/orders")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("type", type)
                .param("quantity", quantity)
                .param("buyer", buyer)
                .param("phone", phone);
        if (htmx) {
            request.header("HX-Request", "true");
        }
        return request.exchange();
    }

    @Test
    void theEventPageShowsEachTicketWithItsPriceAndPlacesLeft() {
        assertThat(mvc.get().uri("/events/kilele-2026"))
                .hasStatusOk()
                .bodyText()
                .contains("Kilele Nights", "Ngong Racecourse", "Sat 14 Nov, 18:00")
                .contains("KES 1,500", "500 places left", "KES 5,000", "50 places left")
                .contains("/webjars/htmx.org/2.0.4/dist/htmx.min.js");
    }

    @Test
    void htmxIsServedByOurOwnServer() {
        assertThat(mvc.get().uri("/webjars/htmx.org/2.0.4/dist/htmx.min.js")).hasStatusOk();
    }

    @Test
    void theHomePageGoesStraightToTheEvent() {
        assertThat(mvc.get().uri("/")).hasStatus3xxRedirection().hasRedirectedUrl("/events/kilele-2026");
    }

    @Test
    void anOrderFromHtmxGetsJustTheSummaryBack() {
        assertThat(order(true, "REG", "2", "Otieno", "0712 345 678"))
                .hasStatusOk()
                .bodyText()
                .contains("Your tickets are held", "KES 3,000", "0712 ••• 678")
                .doesNotContain("<html");

        assertThat(db.sql("select phone || ' ' || status from orders").query(String.class).list())
                .containsExactly("254712345678 AWAITING_PAYMENT");
    }

    @Test
    void anOrderWithoutHtmxRedirectsToItsOwnPage() {
        MvcTestResult posted = order(false, "VIP", "1", "Wanjiku", "+254 722 000 111");

        assertThat(posted).hasStatus3xxRedirection();
        String page = posted.getResponse().getRedirectedUrl();
        assertThat(page).matches("/orders/[2-9A-Z]{4}-[2-9A-Z]{4}");
        assertThat(mvc.get().uri(page)).hasStatusOk().bodyText().contains("Wanjiku", "KES 5,000");
    }

    @Test
    void aBadFormComesBackWithWhatToFixAndWhatWasTyped() {
        assertThat(order(true, "REG", "9", "  ", "12345"))
                .hasStatusOk()
                .bodyText()
                .contains("Type the name for the tickets")
                .contains("Type a Kenyan mobile number, like 0712 345 678")
                .contains("Choose 1 to 6 tickets")
                .contains("id=\"order\"", "value=\"12345\"")
                .doesNotContain("<html");

        assertThat(db.sql("select count(*) from orders").query(Integer.class).single()).isZero();
    }

    @Test
    void withoutHtmxABadFormIsTheWholePageAgainWith422() {
        assertThat(order(false, "REG", "1", "Otieno", "not a phone"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyText()
                .contains("<html", "Type a Kenyan mobile number");
    }

    @Test
    void placesHeldByAnOrderShowAsTaken() {
        order(true, "VIP", "2", "Achieng", "0700 111 222");

        assertThat(mvc.get().uri("/events/kilele-2026/places").header("HX-Request", "true"))
                .hasStatusOk()
                .bodyText()
                .contains("48 places left", "500 places left", "hx-trigger=\"every 15s\"")
                .doesNotContain("<html");
    }

    @Test
    void aSoldOutTypeSaysSoAndCantBeOrdered() {
        db.sql("insert into orders (reference, event_id, type_code, quantity, buyer, phone, amount_cents, status,"
                + " created_at) values ('TOUT-0001', 'kilele-2026', 'VIP', 50, 'Tout', '254700000000', 0,"
                + " 'AWAITING_PAYMENT', current_timestamp)").update();

        assertThat(mvc.get().uri("/events/kilele-2026")).bodyText().contains("Sold out");
        assertThat(order(true, "VIP", "1", "Kamau", "0712 345 678")).bodyText().contains("Sold out, sorry");
    }

    @Test
    void whatAFanTypesIsShownAsTextNeverRunAsCode() {
        assertThat(order(true, "REG", "1", "<script>alert(1)</script>", "0712 345 678"))
                .bodyText()
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .doesNotContain("<script>alert");
    }

    @Test
    void anUnknownOrderIsNotFound() {
        assertThat(mvc.get().uri("/orders/NOPE-NOPE"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyText().contains("No order NOPE-NOPE");
    }

    @Test
    void anUnknownEventIsNotFound() {
        assertThat(mvc.get().uri("/events/no-such-night")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
