package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckInTest {

    private static final Instant NINE_PM = Instant.parse("2026-11-14T18:00:00Z");
    private static final Instant TEN_PM = Instant.parse("2026-11-14T19:00:00Z");

    @TempDir
    Path folder;

    private TicketStore store;
    private Ticket wanjikus;

    @BeforeEach
    void sellATicket() {
        store = TicketStore.open(folder.resolve("gate.db"));
        SampleEvent.ensureIn(store);
        SaleOutcome sold = gateAt(NINE_PM).sell("VIP", "Wanjiku");
        wanjikus = ((SaleOutcome.Sold) sold).ticket();
    }

    @AfterEach
    void closeTheStore() {
        store.close();
    }

    private Gate gateAt(Instant now) {
        return new Gate(store, SampleEvent.ID, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void letsAGoodTicketIn() {
        assertThat(gateAt(TEN_PM).checkIn("VIP-0001")).isEqualTo(new CheckInOutcome.Admitted(wanjikus));
    }

    @Test
    void aTicketWorksOnlyOnce() {
        gateAt(NINE_PM).checkIn("VIP-0001");

        assertThat(gateAt(TEN_PM).checkIn("VIP-0001"))
                .isEqualTo(new CheckInOutcome.AlreadyIn(wanjikus, NINE_PM));
    }

    @Test
    void remembersCheckInsAfterTheAppRestarts() {
        gateAt(NINE_PM).checkIn("VIP-0001");
        store.close();
        store = TicketStore.open(folder.resolve("gate.db"));

        assertThat(gateAt(TEN_PM).checkIn("VIP-0001")).isInstanceOf(CheckInOutcome.AlreadyIn.class);
    }

    @Test
    void refusesACodeThatWasNeverSold() {
        assertThat(gateAt(TEN_PM).checkIn("VIP-9999")).isEqualTo(new CheckInOutcome.NotFound("VIP-9999"));
    }

    @Test
    void tidiesWhatTheScannerSends() {
        assertThat(gateAt(TEN_PM).checkIn("  vip-0001 \n")).isEqualTo(new CheckInOutcome.Admitted(wanjikus));
    }

    @Test
    void anEmptyScanFindsNothing() {
        assertThat(gateAt(TEN_PM).checkIn("   ")).isEqualTo(new CheckInOutcome.NotFound(""));
    }

    @Test
    void describesEachOutcomeForTheScreen() {
        assertThat(new CheckInOutcome.Admitted(wanjikus).describe())
                .isEqualTo("Admitted: VIP-0001 · Wanjiku (VIP)");
        assertThat(new CheckInOutcome.AlreadyIn(wanjikus, NINE_PM).describe())
                .isEqualTo("Already in: VIP-0001 came in at 21:00");
        assertThat(new CheckInOutcome.NotFound("VIP-9999").describe()).isEqualTo("No such ticket: VIP-9999");
        assertThat(new CheckInOutcome.NotFound("").describe()).isEqualTo("Nothing was scanned");
    }
}
