package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GateIdTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    private TicketStore store;

    @BeforeEach
    void openTheStore() {
        store = TicketStore.open(folder.resolve("gate.db"));
        SampleEvent.ensureIn(store);
    }

    @AfterEach
    void closeTheStore() {
        store.close();
    }

    private static String code(SaleOutcome outcome) {
        return ((SaleOutcome.Sold) outcome).ticket().code();
    }

    @Test
    void aGateWithNoIdKeepsTheOldCodes() {
        assertThat(store.gateId()).isEmpty();
        assertThat(code(new Gate(store, SampleEvent.ID, NINE_PM).sell("VIP", "Wanjiku"))).isEqualTo("VIP-0001");
    }

    @Test
    void codesStartWithTheGateId() {
        store.setGateId("A");
        Gate gate = new Gate(store, SampleEvent.ID, NINE_PM);

        assertThat(code(gate.sell("VIP", "Wanjiku"))).isEqualTo("A-VIP-0001");
        assertThat(code(gate.sell("VIP", "Kamau"))).isEqualTo("A-VIP-0002");
    }

    @Test
    void groupSalesUseTheGateIdToo() {
        store.setGateId("B");

        SaleOutcome outcome = new Gate(store, SampleEvent.ID, NINE_PM).sellGroup("REG", "Kamau family", "", 2);

        assertThat(((SaleOutcome.SoldGroup) outcome).tickets()).extracting(Ticket::code)
                .containsExactly("B-REG-0001", "B-REG-0002");
    }

    @Test
    void theGateIdIsRememberedAfterARestart() {
        store.setGateId("G2");
        store.close();

        store = TicketStore.open(folder.resolve("gate.db"));

        assertThat(store.gateId()).isEqualTo("G2");
    }

    @Test
    void aGateIdIsShort() {
        assertThatThrownBy(() -> store.setGateId("main-gate"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Give the gate a short id like A, B or G2");
    }

    @Test
    void numbersCountOnlyThisGatesTickets() {
        // Gate B's tickets, already copied over the network.
        store.recordSale(SampleEvent.ID, new Ticket("B-REG-0001", "REG", "Achieng", Money.ofKes(1_500),
                NINE_PM.instant()));
        store.setGateId("A");
        Gate gate = new Gate(store, SampleEvent.ID, NINE_PM);

        assertThat(code(gate.sell("REG", "Otieno"))).isEqualTo("A-REG-0001");
        // But places left count every gate's tickets.
        assertThat(gate.remaining("REG")).isEqualTo(498);
    }
}
