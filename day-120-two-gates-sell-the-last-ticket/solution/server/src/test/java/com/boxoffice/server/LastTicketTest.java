package com.boxoffice.server;

import static com.boxoffice.server.ServerTestSupport.saleOf;
import static com.boxoffice.server.ServerTestSupport.sendSale;
import static com.boxoffice.server.ServerTestSupport.tokenFor;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Two gates were offline all evening. Now they both come back with sales. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class LastTicketTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient db;

    @Autowired
    Sales sales;

    private String gateA;
    private String gateB;

    @BeforeEach
    void twoGatesSold49VipBetweenThem() {
        ServerTestSupport.emptyTheServer(db);
        gateA = tokenFor(mvc, "A");
        gateB = tokenFor(mvc, "B");
        sendSale(mvc, gateA, "a-early", saleOf("A", "VIP", 1, 30));
        sendSale(mvc, gateB, "b-early", saleOf("B", "VIP", 1, 19));
    }

    @Test
    void theLastVipGoesToTheSaleThatReachesTheServerFirst() {
        assertThat(sendSale(mvc, gateA, "a-last", saleOf("A", "VIP", 31, 1))).hasStatus(HttpStatus.CREATED);

        assertThat(sendSale(mvc, gateB, "b-last", saleOf("B", "VIP", 20, 1)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.error").isEqualTo("Only 0 VIP places were left");
        assertThat(sales.ticketCount("kilele-2026", "VIP")).isEqualTo(50);
    }

    @Test
    void aGroupThatDoesNotFitIsRefusedWhole() {
        assertThat(sendSale(mvc, gateB, "b-family", saleOf("B", "VIP", 20, 3)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.error").isEqualTo("Only 1 VIP place was left");
        assertThat(sales.ticketCount("kilele-2026", "VIP")).isEqualTo(49);
    }

    @Test
    void aRepeatGetsTheSameAnswerAsTheFirstTime() {
        sendSale(mvc, gateA, "a-last", saleOf("A", "VIP", 31, 1));
        sendSale(mvc, gateB, "b-last", saleOf("B", "VIP", 20, 1));

        // B's gate didn't hear the answer and sends it again.
        assertThat(sendSale(mvc, gateB, "b-last", saleOf("B", "VIP", 20, 1)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.result").isEqualTo("oversold");
        assertThat(sendSale(mvc, gateA, "a-last", saleOf("A", "VIP", 31, 1)))
                .hasStatusOk()
                .bodyJson().extractingPath("$.result").isEqualTo("duplicate");
    }

    @Test
    void otherTypesStillSell() {
        sendSale(mvc, gateA, "a-last", saleOf("A", "VIP", 31, 1));

        assertThat(sendSale(mvc, gateB, "b-reg", saleOf("B", "REG", 1, 1))).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void anUnknownTicketTypeIsRefused() {
        assertThat(sendSale(mvc, gateA, "a-vvip", saleOf("A", "VVIP", 1, 1)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.error").isEqualTo("No ticket type VVIP at kilele-2026");
    }
}
