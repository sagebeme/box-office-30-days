package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroupSaleTest {

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
    void sellsSeveralTicketsAsOneSale() {
        SaleOutcome outcome = gate.sellGroup("REG", "Kamau family", "", 4);

        assertThat(outcome).isInstanceOfSatisfying(SaleOutcome.SoldGroup.class, sold -> {
            assertThat(sold.tickets()).extracting(Ticket::code)
                    .containsExactly("REG-0001", "REG-0002", "REG-0003", "REG-0004");
            assertThat(sold.total()).isEqualTo(Money.ofKes(6_000));
        });
        assertThat(store.ticketsInSales(SampleEvent.ID)).isEqualTo(4);
        assertThat(store.ticketsPromisedBySales(SampleEvent.ID)).isEqualTo(4);
    }

    @Test
    void carriesOnNumberingAfterSingleSales() {
        gate.sell("REG", "Otieno");

        SaleOutcome outcome = gate.sellGroup("REG", "Kamau family", "", 2);

        assertThat(((SaleOutcome.SoldGroup) outcome).tickets()).extracting(Ticket::code)
                .containsExactly("REG-0002", "REG-0003");
    }

    @Test
    void describesTheGroupSale() {
        assertThat(gate.sellGroup("REG", "Kamau family", "", 4).describe())
                .isEqualTo("Sold 4 tickets to Kamau family for KES 6,000: REG-0001 to REG-0004");
    }

    @Test
    void refusesMoreTicketsThanAreLeft() {
        gate.sellGroup("VIP", "Wanjiku", "", 48);

        assertThat(gate.sellGroup("VIP", "Kamau family", "", 4))
                .isEqualTo(new SaleOutcome.Rejected("Only 2 VIP left"));
        assertThat(gate.remaining("VIP")).isEqualTo(2);
    }

    @Test
    void refusesAnEmptySale() {
        assertThat(gate.sellGroup("REG", "Kamau family", "", 0))
                .isEqualTo(new SaleOutcome.Rejected("A sale needs at least one ticket"));
    }

    @Test
    void aSaleThatFailsHalfwaySavesNothing() {
        // A ticket code that's already taken makes the third insert fail.
        gate.sell("REG", "Otieno");
        Instant now = NINE_PM.instant();
        List<Ticket> clash = List.of(
                new Ticket("REG-0002", "REG", "Kamau", Money.ofKes(1_500), now),
                new Ticket("REG-0003", "REG", "Kamau", Money.ofKes(1_500), now),
                new Ticket("REG-0001", "REG", "Kamau", Money.ofKes(1_500), now));

        assertThatThrownBy(() -> store.recordGroupSale(SampleEvent.ID, clash))
                .isInstanceOf(StoreException.class);

        assertThat(store.tickets(SampleEvent.ID)).extracting(Ticket::code).containsExactly("REG-0001");
        assertThat(store.ticketsPromisedBySales(SampleEvent.ID)).isZero();
    }

    @Test
    void aSingleSaleAfterAGroupSaleIsStillSaved() {
        gate.sellGroup("REG", "Kamau family", "", 2);
        gate.sell("REG", "Otieno");
        store.close();

        // A fresh connection only sees what was committed.
        store = TicketStore.open(folder.resolve("gate.db"));
        assertThat(store.tickets(SampleEvent.ID)).extracting(Ticket::code)
                .containsExactly("REG-0001", "REG-0002", "REG-0003");
    }

    @Test
    void theDatabaseUsesAWriteAheadLog() {
        assertThat(store.journalMode()).isEqualTo("wal");
    }
}
