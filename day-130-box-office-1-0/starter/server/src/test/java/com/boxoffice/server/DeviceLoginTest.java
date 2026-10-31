package com.boxoffice.server;

import static com.boxoffice.server.ServerTestSupport.ADMIN_KEY;
import static com.boxoffice.server.ServerTestSupport.OTIENO;
import static com.boxoffice.server.ServerTestSupport.register;
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

/**
 * The whole server starts once for these tests, but no real port is opened:
 * MockMvcTester hands requests straight to the controllers.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DeviceLoginTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient db;

    @BeforeEach
    void emptyTheServer() {
        ServerTestSupport.emptyTheServer(db);
    }

    @Test
    void theOrganiserRegistersAGateWithTheAdminKey() {
        assertThat(register(mvc, ADMIN_KEY, "A"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.gate").isEqualTo("A");
    }

    @Test
    void nobodyElseCanRegisterAGate() {
        assertThat(register(mvc, "guess", "A"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.error").isEqualTo("Wrong admin key");
    }

    @Test
    void aGateIdIsShort() {
        assertThat(register(mvc, ADMIN_KEY, "main-gate")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theServerKeepsOnlyAHashOfTheToken() {
        String token = tokenFor(mvc, "A");

        String kept = db.sql("select token_hash from devices where gate = 'A'").query(String.class).single();

        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
        assertThat(kept).isNotEqualTo(token).isEqualTo(Tokens.hash(token)).hasSize(64);
    }

    @Test
    void theSameTokenAlwaysHashesTheSame() {
        assertThat(Tokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void syncNeedsAToken() {
        assertThat(sendSale(mvc, null, "key-1", OTIENO))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.error").isEqualTo("Unknown device. Register this laptop first");
    }

    @Test
    void aMadeUpTokenIsRefused() {
        tokenFor(mvc, "A");

        assertThat(sendSale(mvc, "made-up", "key-1", OTIENO)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registeringAgainStopsTheOldToken() {
        String lost = tokenFor(mvc, "A");
        String fresh = tokenFor(mvc, "A");

        assertThat(sendSale(mvc, lost, "key-1", OTIENO)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(sendSale(mvc, fresh, "key-1", OTIENO)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void healthNeedsNoToken() {
        assertThat(mvc.get().uri("/health")).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("ok");
    }
}
