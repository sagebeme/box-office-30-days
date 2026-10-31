package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TicketStoreTest {

    // JUnit makes a fresh empty folder for every test and deletes it afterwards.
    @TempDir
    Path folder;

    private static final Instant NINE_PM = Instant.parse("2026-11-14T18:00:00Z");

    private Path database() {
        return folder.resolve("gate.db");
    }

    private static Ticket ticket(String code, String type, String buyer, long kes) {
        return new Ticket(code, type, buyer, Money.ofKes(kes), NINE_PM);
    }

    @Test
    void makesTheFileTheFirstTimeItOpens() {
        try (TicketStore store = TicketStore.open(database())) {
            assertThat(database()).exists();
            assertThat(store.findEvent(SampleEvent.ID)).isEmpty();
        }
    }

    @Test
    void savesAnEventAndReadsItBack() {
        try (TicketStore store = TicketStore.open(database())) {
            store.saveEvent(SampleEvent.kileleNights());

            assertThat(store.findEvent(SampleEvent.ID)).contains(SampleEvent.kileleNights());
        }
    }

    @Test
    void recordsASaleAndReadsItBack() {
        try (TicketStore store = TicketStore.open(database())) {
            store.saveEvent(SampleEvent.kileleNights());

            store.recordSale(SampleEvent.ID, ticket("VIP-0001", "VIP", "Wanjiku", 5_000));

            assertThat(store.tickets(SampleEvent.ID))
                    .containsExactly(ticket("VIP-0001", "VIP", "Wanjiku", 5_000));
        }
    }

    @Test
    void keepsSalesAfterTheAppIsClosedAndOpenedAgain() {
        try (TicketStore store = TicketStore.open(database())) {
            store.saveEvent(SampleEvent.kileleNights());
            store.recordSale(SampleEvent.ID, ticket("REG-0001", "REG", "Otieno", 1_500));
        }

        try (TicketStore reopened = TicketStore.open(database())) {
            assertThat(reopened.tickets(SampleEvent.ID))
                    .containsExactly(ticket("REG-0001", "REG", "Otieno", 1_500));
        }
    }

    @Test
    void readsTicketsBackInTheOrderTheyWereSold() {
        try (TicketStore store = TicketStore.open(database())) {
            store.saveEvent(SampleEvent.kileleNights());
            store.recordSale(SampleEvent.ID, ticket("VIP-0001", "VIP", "Wanjiku", 5_000));
            store.recordSale(SampleEvent.ID, ticket("REG-0001", "REG", "Otieno", 1_500));

            assertThat(store.tickets(SampleEvent.ID))
                    .extracting(Ticket::code)
                    .containsExactly("VIP-0001", "REG-0001");
        }
    }

    @Test
    void countsSoldTicketsOneTypeAtATime() {
        try (TicketStore store = TicketStore.open(database())) {
            store.saveEvent(SampleEvent.kileleNights());
            store.recordSale(SampleEvent.ID, ticket("REG-0001", "REG", "Otieno", 1_500));
            store.recordSale(SampleEvent.ID, ticket("REG-0002", "REG", "Achieng", 1_500));
            store.recordSale(SampleEvent.ID, ticket("VIP-0001", "VIP", "Wanjiku", 5_000));

            assertThat(store.countSold(SampleEvent.ID, "REG")).isEqualTo(2);
            assertThat(store.countSold(SampleEvent.ID, "VIP")).isEqualTo(1);
        }
    }

    @Test
    void storesANameWithAnApostropheSafely() {
        // Gluing the name into the SQL by hand would break on the ' in Ng'ang'a.
        try (TicketStore store = TicketStore.open(database())) {
            store.saveEvent(SampleEvent.kileleNights());

            store.recordSale(SampleEvent.ID, ticket("REG-0001", "REG", "Ng'ang'a", 1_500));

            assertThat(store.tickets(SampleEvent.ID).getFirst().buyer()).isEqualTo("Ng'ang'a");
        }
    }

    @Test
    void keepsTheBuyersPhoneNumber() {
        try (TicketStore store = TicketStore.open(database())) {
            store.saveEvent(SampleEvent.kileleNights());
            Ticket withPhone = new Ticket("VIP-0001", "VIP", "Wanjiku", "254712345678", Money.ofKes(5_000), NINE_PM);

            store.recordSale(SampleEvent.ID, withPhone);

            assertThat(store.tickets(SampleEvent.ID)).containsExactly(withPhone);
        }
    }

    @Test
    void aSlowDiskMakesSavingASaleSlow() {
        try (TicketStore store = TicketStore.open(database())) {
            store.saveEvent(SampleEvent.kileleNights());
            store.simulateSlowDisk(Duration.ofMillis(300));

            long start = System.nanoTime();
            store.recordSale(SampleEvent.ID, ticket("REG-0001", "REG", "Otieno", 1_500));

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(300));
        }
    }
}
