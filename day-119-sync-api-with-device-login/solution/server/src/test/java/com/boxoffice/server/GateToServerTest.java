package com.boxoffice.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import com.boxoffice.core.net.ResilientHttp;
import com.boxoffice.core.net.RetryPolicy;
import com.boxoffice.core.sync.HttpSyncClient;
import com.boxoffice.core.sync.OutboxSender;
import com.boxoffice.core.sync.SyncReport;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * The real thing: the server on a real port, and a real gate (core's own
 * classes, with an SQLite file) syncing to it over HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GateToServerTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient db;

    @Autowired
    Devices devices;

    @Autowired
    Sales sales;

    @TempDir
    Path folder;

    @BeforeEach
    void emptyTheServer() {
        ServerTestSupport.emptyTheServer(db);
    }

    private static ResilientHttp http() {
        return new ResilientHttp(HttpClient.newHttpClient(),
                new RetryPolicy(2, Duration.ofMillis(100), Duration.ofSeconds(1)),
                Duration.ofSeconds(5), wait -> { }, () -> 1.0, retry -> { });
    }

    @Test
    void aGateSyncsItsSalesToTheServer() {
        String token = devices.register("A");
        try (TicketStore store = TicketStore.open(folder.resolve("gate.db"))) {
            SampleEvent.ensureIn(store);
            store.setGateId("A");
            Gate gate = new Gate(store, SampleEvent.ID, NINE_PM);
            gate.sell("VIP", "Wanjiku");
            gate.sellGroup("REG", "Kamau family", "", 3);

            HttpSyncClient client = new HttpSyncClient(URI.create("http://127.0.0.1:" + port), http(), token);
            SyncReport report = new OutboxSender(store, client, NINE_PM).sendWaiting();

            assertThat(report).isEqualTo(new SyncReport(2, 0, ""));
        }
        assertThat(sales.ticketCount(SampleEvent.ID)).isEqualTo(4);
    }

    @Test
    void aGateWithNoTokenIsTurnedAway() {
        try (TicketStore store = TicketStore.open(folder.resolve("gate.db"))) {
            SampleEvent.ensureIn(store);
            store.setGateId("A");
            new Gate(store, SampleEvent.ID, NINE_PM).sell("VIP", "Wanjiku");

            HttpSyncClient client = new HttpSyncClient(URI.create("http://127.0.0.1:" + port), http(), "");
            SyncReport report = new OutboxSender(store, client, NINE_PM).sendWaiting();

            assertThat(report.problem()).isEqualTo("The server doesn't know this laptop. Run register first");
            assertThat(report.waiting()).isEqualTo(1);
        }
    }
}
