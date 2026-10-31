package com.boxoffice.core.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boxoffice.core.Gate;
import com.boxoffice.core.Money;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.StoreException;
import com.boxoffice.core.Ticket;
import com.boxoffice.core.TicketStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutboxTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    private TicketStore store;
    private Gate gate;

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

    @Test
    void everySaleWaitsInTheOutboxForTheServer() {
        gate.sell("VIP", "Wanjiku", "0712 345 678");

        assertThat(store.waitingToSync(10)).singleElement().satisfies(entry -> {
            SalePayload sale = SalePayload.fromJson(entry.payload());
            assertThat(sale.eventId()).isEqualTo(SampleEvent.ID);
            assertThat(sale.tickets()).containsExactly(new SalePayload.TicketPayload(
                    "VIP-0001", "VIP", "Wanjiku", "254712345678", 500_000, "2026-11-14T18:00:00Z"));
            assertThat(entry.attempts()).isZero();
        });
    }

    @Test
    void aGroupSaleIsOneEntry() {
        gate.sellGroup("REG", "Kamau family", "", 3);

        assertThat(store.waitingToSync(10)).singleElement()
                .extracting(entry -> SalePayload.fromJson(entry.payload()).tickets().size())
                .isEqualTo(3);
    }

    @Test
    void everySaleGetsItsOwnKey() {
        gate.sell("REG", "Otieno");
        gate.sell("REG", "Achieng");

        assertThat(store.waitingToSync(10)).extracting(OutboxEntry::key).doesNotHaveDuplicates().hasSize(2);
    }

    @Test
    void oldestFirst() {
        gate.sell("REG", "Otieno");
        gate.sell("VIP", "Wanjiku");

        assertThat(store.waitingToSync(10))
                .extracting(entry -> SalePayload.fromJson(entry.payload()).tickets().getFirst().code())
                .containsExactly("REG-0001", "VIP-0001");
    }

    @Test
    void aSaleThatFailsLeavesNoNote() {
        gate.sell("REG", "Otieno");
        Ticket taken = new Ticket("REG-0001", "REG", "Kamau", Money.ofKes(1_500), NINE_PM.instant());

        assertThatThrownBy(() -> store.recordSale(SampleEvent.ID, taken)).isInstanceOf(StoreException.class);

        assertThat(store.countWaitingToSync()).isEqualTo(1);
    }

    @Test
    void theOutboxSurvivesARestart() {
        gate.sell("REG", "Otieno");
        store.close();

        store = TicketStore.open(folder.resolve("gate.db"));

        assertThat(store.countWaitingToSync()).isEqualTo(1);
    }

    @Test
    void aSyncedSaleStopsWaiting() {
        gate.sell("REG", "Otieno");
        OutboxEntry entry = store.waitingToSync(10).getFirst();

        store.markSynced(entry.id(), NINE_PM.instant());

        assertThat(store.waitingToSync(10)).isEmpty();
    }
}
