package com.boxoffice.server;

import static com.boxoffice.server.ServerTestSupport.OTIENO;
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

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SyncApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient db;

    @Autowired
    Sales sales;

    private String gateA;

    @BeforeEach
    void registerGateA() {
        ServerTestSupport.emptyTheServer(db);
        gateA = tokenFor(mvc, "A");
    }

    @Test
    void savesTheSaleAndItsTickets() {
        assertThat(sendSale(mvc, gateA, "key-1", OTIENO))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.result").isEqualTo("accepted");

        assertThat(sales.ticketCount("kilele-2026")).isEqualTo(1);
        assertThat(db.sql("select buyer from tickets where code = 'A-REG-0001'").query(String.class).single())
                .isEqualTo("Otieno");
    }

    @Test
    void aSaleSentTwiceIsSavedOnce() {
        sendSale(mvc, gateA, "key-1", OTIENO);

        assertThat(sendSale(mvc, gateA, "key-1", OTIENO))
                .hasStatusOk()
                .bodyJson().extractingPath("$.result").isEqualTo("duplicate");
        assertThat(sales.ticketCount("kilele-2026")).isEqualTo(1);
    }

    @Test
    void everySaleNeedsAnIdempotencyKey() {
        assertThat(sendSale(mvc, gateA, null, OTIENO))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.error").isEqualTo("Send an Idempotency-Key header with every sale");
    }

    @Test
    void aGateCanOnlySendItsOwnTickets() {
        String gateB = tokenFor(mvc, "B");

        assertThat(sendSale(mvc, gateB, "key-1", OTIENO))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.error")
                .isEqualTo("Gate B can't send ticket A-REG-0001: its codes start with B-");
        assertThat(sales.ticketCount("kilele-2026")).isZero();
    }

    @Test
    void somethingThatIsNotASaleIsRefused() {
        assertThat(sendSale(mvc, gateA, "key-1", "not json")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(sendSale(mvc, gateA, "key-2", "{\"eventId\":\"kilele-2026\",\"tickets\":[]}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
