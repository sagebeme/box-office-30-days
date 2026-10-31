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
    private String oversold = "";

    private final SyncClient server = entry -> {
        if (wifiDown || received.size() == failOnPush) {
            throw new NetworkException("Gave up after 5 attempts: Can't reach the server", 5, null);
        }
        String code = SalePayload.fromJson(entry.payload()).tickets().getFirst().code();
        if (code.equals(oversold)) {
            throw new SaleConflict("Only 0 VIP places were left");
        }
        received.add(code);
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

    // Day 120: the server refused a sale because another gate got the last place first.

    @Test
    void anOversoldSaleDoesNotHoldUpTheRest() {
        oversold = "VIP-0001";
        gate.sell("VIP", "Wanjiku");
        gate.sell("REG", "Otieno");

        SyncReport report = sync();

        assertThat(report).isEqualTo(new SyncReport(1, 0, "", 1));
        assertThat(received).containsExactly("REG-0001");
        assertThat(report.describe())
                .isEqualTo("Sent 1 sale. Nothing waiting. 1 sale was oversold: run conflicts to see who.");
    }

    @Test
    void anOversoldSaleIsListedForStaff() {
        oversold = "VIP-0001";
        gate.sell("VIP", "Wanjiku", "0712 345 678");

        sync();

        assertThat(store.conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.codes()).containsExactly("VIP-0001");
            assertThat(conflict.describe())
                    .isEqualTo("VIP-0001 (Wanjiku, 254712345678): Only 0 VIP places were left");
        });
    }

    @Test
    void anOversoldSaleIsNotSentAgain() {
        oversold = "VIP-0001";
        gate.sell("VIP", "Wanjiku");
        sync();

        assertThat(sync()).isEqualTo(new SyncReport(0, 0, ""));
        assertThat(store.countWaitingToSync()).isZero();
    }

    @Test
    void theTicketStaysOnTheGate() {
        // The buyer may already be inside: the gate keeps the ticket, and staff decide.
        oversold = "VIP-0001";
        gate.sell("VIP", "Wanjiku");
        sync();

        assertThat(gate.checkIn("VIP-0001")).isInstanceOf(com.boxoffice.core.CheckInOutcome.Admitted.class);
    }
}
