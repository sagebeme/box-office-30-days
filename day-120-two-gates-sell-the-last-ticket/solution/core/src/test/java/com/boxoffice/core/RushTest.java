package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Saturday, 20:00: twenty tills sell at once. Each till is a thread, and they
 * all start on the same signal, so their sales really do overlap.
 */
class RushTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);
    private static final int TILLS = 20;

    @TempDir
    Path folder;

    private TicketStore store;
    private Gate gate;
    private ExecutorService tills;

    @BeforeEach
    void openTheGate() {
        store = TicketStore.open(folder.resolve("gate.db"));
        SampleEvent.ensureIn(store);
        gate = new Gate(store, SampleEvent.ID, NINE_PM);
        // A slow old laptop: a moment's pause between "any left?" and the save,
        // which is exactly when another till can sneak in.
        gate.betweenCheckAndSave(() -> {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        tills = Executors.newFixedThreadPool(TILLS);
    }

    @AfterEach
    void closeTheGate() {
        tills.shutdownNow();
        store.close();
    }

    /** Runs the same job on every till at once, and waits for all of them. Any exception fails the test. */
    private <T> List<T> rush(int timesPerTill, Callable<T> job) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> results = new ArrayList<>();
        for (int till = 0; till < TILLS; till++) {
            for (int i = 0; i < timesPerTill; i++) {
                results.add(tills.submit(() -> {
                    go.await();
                    return job.call();
                }));
            }
        }
        go.countDown();
        List<T> outcomes = new ArrayList<>();
        for (Future<T> result : results) {
            outcomes.add(result.get());
        }
        return outcomes;
    }

    @Test
    void twentyTillsNeverSellMoreThanFiftyVip() throws Exception {
        List<SaleOutcome> outcomes = rush(5, () -> gate.sell("VIP", "Fan"));

        assertThat(outcomes).filteredOn(SaleOutcome.Sold.class::isInstance).hasSize(50);
        assertThat(outcomes).filteredOn(SaleOutcome.SoldOut.class::isInstance).hasSize(50);
        assertThat(store.countSold(SampleEvent.ID, "VIP")).isEqualTo(50);
    }

    @Test
    void everyTicketSoldInARushHasItsOwnCode() throws Exception {
        rush(2, () -> gate.sell("REG", "Fan"));

        assertThat(store.tickets(SampleEvent.ID)).extracting(Ticket::code)
                .hasSize(TILLS * 2)
                .doesNotHaveDuplicates()
                .contains("REG-0001", "REG-0040");
    }

    @Test
    void groupSalesInARushStillAddUp() throws Exception {
        List<SaleOutcome> outcomes = rush(1, () -> gate.sellGroup("VIP", "Family", "", 6));

        // Eight families of six fit in 50 places; the others are told only 2 are left.
        assertThat(outcomes).filteredOn(SaleOutcome.SoldGroup.class::isInstance).hasSize(8);
        assertThat(store.countSold(SampleEvent.ID, "VIP")).isEqualTo(48);
        assertThat(store.ticketsInSales(SampleEvent.ID)).isEqualTo(store.ticketsPromisedBySales(SampleEvent.ID));
    }

    @Test
    void twoScannersReadingOneTicketLetOnePersonIn() throws Exception {
        gate.sell("VIP", "Wanjiku");

        List<CheckInOutcome> outcomes = rush(1, () -> gate.checkIn("VIP-0001"));

        assertThat(outcomes).filteredOn(CheckInOutcome.Admitted.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(CheckInOutcome.AlreadyIn.class::isInstance).hasSize(TILLS - 1);
        assertThat(gate.admittedHere()).isEqualTo(1);
    }

    @Test
    void theTurnstileNeverLosesCount() throws Exception {
        Turnstile turnstile = new Turnstile();

        rush(1, () -> {
            for (int i = 0; i < 200_000; i++) {
                turnstile.admit();
            }
            return null;
        });

        assertThat(turnstile.count()).isEqualTo(TILLS * 200_000L);
    }
}
