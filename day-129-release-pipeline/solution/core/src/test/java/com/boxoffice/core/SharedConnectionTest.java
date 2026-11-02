package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Two threads, one database connection. A group sale stops halfway (its
 * first ticket is written, then it fails), and right then another thread
 * writes. The latches make the timing the same on every run.
 */
class SharedConnectionTest {

    private static final Instant NINE_PM = Instant.parse("2026-11-14T18:00:00Z");

    @TempDir
    Path folder;

    private TicketStore store;
    private final ExecutorService threads = Executors.newFixedThreadPool(2);
    private final CountDownLatch halfway = new CountDownLatch(1);
    private final CountDownLatch otherThreadDone = new CountDownLatch(1);

    @BeforeEach
    void openTheStore() {
        store = TicketStore.open(folder.resolve("gate.db"));
        SampleEvent.ensureIn(store);
    }

    @AfterEach
    void closeTheStore() {
        threads.shutdownNow();
        store.close();
    }

    private static Ticket ticket(String code, String type) {
        return new Ticket(code, type, "Fan", Money.ofKes(1_500), NINE_PM);
    }

    private Future<?> aGroupSaleThatFailsHalfway() throws InterruptedException {
        store.afterEachTicket(() -> {
            halfway.countDown();
            try {
                // Give the other thread its chance to write, then fail.
                otherThreadDone.await(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("The power went");
        });
        Future<?> sale = threads.submit(() -> store.recordGroupSale(SampleEvent.ID,
                List.of(ticket("REG-0001", "REG"), ticket("REG-0002", "REG"))));
        halfway.await();
        return sale;
    }

    @Test
    void anotherThreadsSaleNeverCommitsHalfAGroupSale() throws Exception {
        Future<?> group = aGroupSaleThatFailsHalfway();
        Future<?> single = threads.submit(() -> {
            store.recordSale(SampleEvent.ID, ticket("VIP-0001", "VIP"));
            otherThreadDone.countDown();
        });

        assertThatThrownBy(group::get).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        single.get(5, TimeUnit.SECONDS);
        assertThat(store.tickets(SampleEvent.ID)).extracting(Ticket::code).containsExactly("VIP-0001");
    }

    @Test
    void aCheckInIsNotLostWhenSomeoneElsesSaleRollsBack() throws Exception {
        store.recordSale(SampleEvent.ID, ticket("VIP-0001", "VIP"));

        Future<?> group = aGroupSaleThatFailsHalfway();
        Future<?> checkIn = threads.submit(() -> {
            store.markCheckedIn(SampleEvent.ID, "VIP-0001", NINE_PM);
            otherThreadDone.countDown();
        });

        assertThatThrownBy(group::get).isInstanceOf(ExecutionException.class);
        checkIn.get(5, TimeUnit.SECONDS);
        assertThat(store.checkedInAt(SampleEvent.ID, "VIP-0001")).contains(NINE_PM);
    }
}
