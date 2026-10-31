package com.boxoffice.core.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import com.boxoffice.core.net.NetworkException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutboxSenderTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    private TicketStore store;
    private Gate gate;

    /** A pretend server: writes down what it gets, and can be told to be down. */
    private final List<String> received = new ArrayList<>();
    private final List<String> keysReceived = new ArrayList<>();
    private boolean wifiDown;
    private int failOnPush = -1;

    private final SyncClient server = entry -> {
        if (wifiDown || received.size() == failOnPush) {
            throw new NetworkException("Gave up after 5 attempts: Can't reach the server", 5, null);
        }
        received.add(SalePayload.fromJson(entry.payload()).tickets().getFirst().code());
        keysReceived.add(entry.key());
    };

    @BeforeEach
    void openTheGate() {
        store = TicketStore.open(folder.resolve("gate.db"));
        SampleEvent.ensureIn(store);
        gate = new Gate(store, SampleEvent.ID, NINE_PM);
    }

    @AfterEach
    void closeTheStore() {
        store.close();
    }

    private SyncReport sync() {
        return new OutboxSender(store, server, NINE_PM).sendWaiting();
    }

    @Test
    void sendsEveryWaitingSaleOldestFirst() {
        gate.sell("REG", "Otieno");
        gate.sell("VIP", "Wanjiku");
        gate.sell("REG", "Achieng");

        SyncReport report = sync();

        assertThat(received).containsExactly("REG-0001", "VIP-0001", "REG-0002");
        assertThat(report).isEqualTo(new SyncReport(3, 0, ""));
        assertThat(store.countWaitingToSync()).isZero();
    }

    @Test
    void neverSendsTheSameSaleTwice() {
        gate.sell("REG", "Otieno");
        sync();

        SyncReport second = sync();

        assertThat(second.sent()).isZero();
        assertThat(received).containsExactly("REG-0001");
    }

    @Test
    void keepsSellingWhileTheWifiIsDown() {
        wifiDown = true;
        gate.sell("REG", "Otieno");
        gate.sell("REG", "Achieng");

        SyncReport report = sync();

        assertThat(report).isEqualTo(new SyncReport(0, 2, "Gave up after 5 attempts: Can't reach the server"));
        assertThat(gate.remaining("REG")).isEqualTo(498);
    }

    @Test
    void stopsAtTheFirstFailureSoNoSaleOvertakesAnother() {
        failOnPush = 1;
        gate.sell("REG", "Otieno");
        gate.sell("REG", "Achieng");
        gate.sell("REG", "Kamau");

        SyncReport report = sync();

        assertThat(received).containsExactly("REG-0001");
        assertThat(report.sent()).isEqualTo(1);
        assertThat(report.waiting()).isEqualTo(2);
    }

    @Test
    void remembersWhyASaleFailed() {
        wifiDown = true;
        gate.sell("REG", "Otieno");
        sync();
        sync();

        OutboxEntry entry = store.waitingToSync(10).getFirst();
        assertThat(entry.attempts()).isEqualTo(2);
        assertThat(store.lastSyncError(entry.id())).isEqualTo("Gave up after 5 attempts: Can't reach the server");
    }

    @Test
    void catchesUpWhenTheWifiComesBackWithTheSameKeys() {
        wifiDown = true;
        gate.sell("REG", "Otieno");
        String key = store.waitingToSync(10).getFirst().key();
        sync();

        wifiDown = false;
        SyncReport report = sync();

        assertThat(report.allSent()).isTrue();
        assertThat(keysReceived).containsExactly(key);
    }

    @Test
    void describesWhatItDid() {
        assertThat(new SyncReport(1, 0, "").describe()).isEqualTo("Sent 1 sale. Nothing waiting.");
        assertThat(new SyncReport(0, 2, "Can't reach the server").describe())
                .isEqualTo("Sent 0 sales. 2 still waiting: Can't reach the server");
    }
}
