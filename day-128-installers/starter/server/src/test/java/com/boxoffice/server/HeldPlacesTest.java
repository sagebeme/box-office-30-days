package com.boxoffice.server;

import static com.boxoffice.server.ServerTestSupport.saleOf;
import static com.boxoffice.server.ServerTestSupport.sendSale;
import static com.boxoffice.server.ServerTestSupport.tokenFor;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The site and the gates share one set of places. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class HeldPlacesTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient db;

    @Autowired
    Orders orders;

    @BeforeEach
    void emptyServer() {
        ServerTestSupport.emptyTheServer(db);
    }

    @Test
    void aPlaceAFanIsPayingForIsTakenForTheGatesToo() {
        String gateA = tokenFor(mvc, "A");
        sendSale(mvc, gateA, "a-49", saleOf("A", "VIP", 1, 49));

        assertThat(orders.place("kilele-2026", new OrderForm("VIP", 1, "Achieng", "0700 111 222")))
                .isInstanceOf(Orders.Placed.Done.class);

        assertThat(sendSale(mvc, gateA, "a-50", saleOf("A", "VIP", 50, 1)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.error").isEqualTo("Only 0 VIP places were left");
    }

    @Test
    void anOrderBiggerThanWhatIsLeftIsRefused() {
        String gateA = tokenFor(mvc, "A");
        sendSale(mvc, gateA, "a-47", saleOf("A", "VIP", 1, 47));

        Orders.Placed placed = orders.place("kilele-2026", new OrderForm("VIP", 4, "Achieng", "0700 111 222"));

        assertThat(placed).isEqualTo(new Orders.Placed.Refused(java.util.Map.of("quantity", "Only 3 places are left")));
    }

    @Test
    void anUnknownTicketTypeIsRefused() {
        assertThat(orders.place("kilele-2026", new OrderForm("VVIP", 1, "Achieng", "0700 111 222")))
                .isEqualTo(new Orders.Placed.Refused(java.util.Map.of("type", "Choose a ticket type")));
    }

    @Test
    void tenFansRushingForFiftyVipPlacesNeverGetMoreThanFifty() throws Exception {
        List<Callable<Orders.Placed>> fans = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            fans.add(() -> orders.place("kilele-2026", new OrderForm("VIP", 6, "Fan", "0700 111 222")));
        }
        int done = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (Future<Orders.Placed> result : pool.invokeAll(fans)) {
                if (result.get() instanceof Orders.Placed.Done) {
                    done++;
                }
            }
        }

        // 8 orders of 6 is 48. A ninth would need 54 places.
        assertThat(done).isEqualTo(8);
        assertThat(db.sql("select sum(quantity) from orders").query(Integer.class).single()).isEqualTo(48);
    }

    @Test
    void orderReferencesAreRandomAndEasyToReadAloud() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String reference = References.next();
            assertThat(reference).matches("[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}");
            seen.add(reference);
        }
        assertThat(seen).hasSize(1_000);
    }
}
