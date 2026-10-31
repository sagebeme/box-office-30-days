package com.boxoffice.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SaleOutcome;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DashboardModelTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    // Runs the "background" work straight away, on the thread that asked. Task still hands its
    // result to the JavaFX thread a moment later, so the tests below wait for busy to go false.
    private static final Executor RIGHT_HERE = Runnable::run;

    @TempDir
    Path folder;

    private TicketStore store;
    private Gate gate;
    private DashboardModel model;

    @BeforeEach
    void openTheGate() {
        store = TicketStore.open(folder.resolve("gate.db"));
        SampleEvent.ensureIn(store);
        gate = new Gate(store, SampleEvent.ID, NINE_PM);
        model = new DashboardModel(gate, RIGHT_HERE);
        FxThread.run(model::refresh);
    }

    @AfterEach
    void closeTheStore() {
        store.close();
    }

    /** Sells the way a click does, on the JavaFX thread, and waits until the sale is done. */
    private void sell(String type, String buyer) {
        FxThread.run(() -> model.sell(type, buyer));
        FxThread.waitUntil(() -> !model.busyProperty().get());
    }

    @Test
    void showsTheEventsNameAndTimes() {
        assertThat(model.titleProperty().get()).isEqualTo("Kilele Nights");
        assertThat(model.whenProperty().get()).isEqualTo("Sat 14 Nov, 18:00 to Sun 15 Nov, 04:00");
    }

    @Test
    void showsEachTicketTypeWithItsPriceAndWhatIsLeft() {
        assertThat(model.types()).containsExactly(
                new TypeSummary("Regular", "KES 1,500", 500, 500),
                new TypeSummary("VIP", "KES 5,000", 50, 50));
    }

    @Test
    void aTypeSummaryReadsLikeASentence() {
        assertThat(new TypeSummary("VIP", "KES 5,000", 49, 50)).hasToString("VIP · KES 5,000 · 49 of 50 left");
    }

    @Test
    void sellingPutsTheNewSaleAtTheTopOfTheTable() {
        sell("REG", "Otieno");
        sell("VIP", "Wanjiku");

        assertThat(model.sales()).containsExactly(
                new SaleRow("VIP-0001", "VIP", "Wanjiku", "KES 5,000", "21:00"),
                new SaleRow("REG-0001", "Regular", "Otieno", "KES 1,500", "21:00"));
    }

    @Test
    void sellingUpdatesWhatIsLeftTheTakingsAndTheMessage() {
        sell("VIP", "Wanjiku");

        assertThat(model.types()).contains(new TypeSummary("VIP", "KES 5,000", 49, 50));
        assertThat(model.takingsProperty().get()).isEqualTo("KES 5,000");
        assertThat(model.messageProperty().get()).isEqualTo("Sold VIP-0001 to Wanjiku for KES 5,000");
    }

    @Test
    void aRefusedSaleChangesNothingButTheMessage() {
        sell("REG", "   ");

        assertThat(model.messageProperty().get()).isEqualTo("Rejected: A ticket needs a buyer's name");
        assertThat(model.sales()).isEmpty();
    }

    @Test
    void theSoldCountFollowsTheTableByItself() {
        sell("REG", "Otieno");
        sell("REG", "Achieng");

        assertThat(model.soldCount().get()).isEqualTo("2 sold");
    }

    @Test
    void sellsWithThePhoneNumber() {
        FxThread.run(() -> model.sell("VIP", "Wanjiku", "0712 345 678"));
        FxThread.waitUntil(() -> !model.busyProperty().get());

        assertThat(gate.ticketsSold().getFirst().phone()).isEqualTo("254712345678");
    }

    @Test
    void handsTheOutcomeOverWhenTheSaleIsDone() {
        List<SaleOutcome> outcomes = new ArrayList<>();

        FxThread.run(() -> model.sell("VIP", "Wanjiku", "", outcomes::add));
        FxThread.waitUntil(() -> !model.busyProperty().get());

        assertThat(outcomes).singleElement().isInstanceOf(SaleOutcome.Sold.class);
    }

    // Day 109: the screen never waits for the disk.

    @Test
    void sellingReturnsAtOnceAndSavesInTheBackground() {
        CountDownLatch diskIsDone = new CountDownLatch(1);
        // A worker that can't finish until the test says so, like a very slow disk.
        Executor slowWorker = task -> new Thread(() -> {
            await(diskIsDone);
            task.run();
        }).start();
        DashboardModel slowModel = new DashboardModel(gate, slowWorker);
        FxThread.run(slowModel::refresh);

        FxThread.run(() -> slowModel.sell("VIP", "Wanjiku"));

        // The JavaFX thread is free again while the sale is still saving.
        assertThat(FxThread.get(() -> slowModel.busyProperty().get())).isTrue();
        assertThat(FxThread.get(() -> slowModel.messageProperty().get())).isEqualTo("Saving…");
        assertThat(FxThread.get(() -> slowModel.sales().size())).isZero();

        diskIsDone.countDown();

        FxThread.waitUntil(() -> !slowModel.busyProperty().get());
        assertThat(FxThread.get(() -> slowModel.sales().size())).isEqualTo(1);
        assertThat(FxThread.get(() -> slowModel.messageProperty().get()))
                .isEqualTo("Sold VIP-0001 to Wanjiku for KES 5,000");
    }

    @Test
    void ignoresASecondSaleWhileTheFirstIsSaving() {
        AtomicInteger started = new AtomicInteger();
        CountDownLatch diskIsDone = new CountDownLatch(1);
        Executor slowWorker = task -> {
            started.incrementAndGet();
            new Thread(() -> {
                await(diskIsDone);
                task.run();
            }).start();
        };
        DashboardModel slowModel = new DashboardModel(gate, slowWorker);
        FxThread.run(slowModel::refresh);

        FxThread.run(() -> {
            slowModel.sell("VIP", "Wanjiku");
            slowModel.sell("VIP", "Wanjiku");
        });
        diskIsDone.countDown();
        FxThread.waitUntil(() -> !slowModel.busyProperty().get());

        assertThat(started).hasValue(1);
        assertThat(gate.ticketsSold()).hasSize(1);
    }

    @Test
    void aSaleThatCannotBeSavedSaysSoAndFreesTheGate() {
        store.close();

        sell("VIP", "Wanjiku");

        assertThat(model.messageProperty().get()).startsWith("Couldn't save the sale: ");
        assertThat(model.busyProperty().get()).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Day 110: QR codes and check-in.

    @Test
    void remembersTheLastTicketSoldForItsQrCode() {
        assertThat(model.lastSoldCodeProperty().get()).isEmpty();

        sell("VIP", "Wanjiku");

        assertThat(model.lastSoldCodeProperty().get()).isEqualTo("VIP-0001");
    }

    @Test
    void checksATicketInAndShowsItInGreen() {
        sell("VIP", "Wanjiku");

        FxThread.run(() -> model.checkIn("VIP-0001"));
        FxThread.waitUntil(() -> model.checkInStateProperty().get() != CheckInState.WAITING);

        assertThat(model.checkInMessageProperty().get()).isEqualTo("Admitted: VIP-0001 · Wanjiku (VIP)");
        assertThat(model.checkInStateProperty().get()).isEqualTo(CheckInState.ADMITTED);
    }

    @Test
    void aSecondScanOfTheSameTicketIsAmber() {
        sell("VIP", "Wanjiku");
        FxThread.run(() -> model.checkIn("VIP-0001"));
        FxThread.waitUntil(() -> model.checkInStateProperty().get() == CheckInState.ADMITTED);

        FxThread.run(() -> model.checkIn("VIP-0001"));
        FxThread.waitUntil(() -> model.checkInStateProperty().get() == CheckInState.ALREADY_IN);

        assertThat(model.checkInMessageProperty().get()).isEqualTo("Already in: VIP-0001 came in at 21:00");
    }

    @Test
    void anUnknownCodeIsRed() {
        FxThread.run(() -> model.checkIn("FAKE-1"));
        FxThread.waitUntil(() -> model.checkInStateProperty().get() == CheckInState.NOT_FOUND);

        assertThat(model.checkInMessageProperty().get()).isEqualTo("No such ticket: FAKE-1");
    }

    // Day 112: receipts.

    @Test
    void remembersTheLastTicketForItsReceipt() {
        assertThat(model.lastSoldTicketProperty().get()).isNull();

        sell("VIP", "Wanjiku");

        assertThat(model.lastSoldTicketProperty().get().code()).isEqualTo("VIP-0001");
        assertThat(model.event().name()).isEqualTo("Kilele Nights");
    }
}
