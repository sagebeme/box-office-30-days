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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// No window opens here: properties and observable lists work without one.
class DashboardModelTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

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
        model = new DashboardModel(gate);
    }

    @AfterEach
    void closeTheStore() {
        store.close();
    }

    @Test
    void showsNothingBeforeTheFirstRefresh() {
        assertThat(model.titleProperty().get()).isEmpty();
        assertThat(model.types()).isEmpty();
    }

    @Test
    void showsTheEventsNameAndTimes() {
        model.refresh();

        assertThat(model.titleProperty().get()).isEqualTo("Kilele Nights");
        assertThat(model.whenProperty().get()).isEqualTo("Sat 14 Nov, 18:00 to Sun 15 Nov, 04:00");
    }

    @Test
    void showsEachTicketTypeWithItsPriceAndWhatIsLeft() {
        model.refresh();

        assertThat(model.types()).containsExactly(
                new TypeSummary("Regular", "KES 1,500", 500, 500),
                new TypeSummary("VIP", "KES 5,000", 50, 50));
    }

    @Test
    void showsTheTakings() {
        gate.sell("VIP", "Wanjiku");
        gate.sell("REG", "Otieno");

        model.refresh();

        assertThat(model.takingsProperty().get()).isEqualTo("KES 6,500");
    }

    @Test
    void catchesUpWithSalesOnTheNextRefresh() {
        model.refresh();
        gate.sell("VIP", "Wanjiku");

        model.refresh();

        assertThat(model.types()).contains(new TypeSummary("VIP", "KES 5,000", 49, 50));
    }

    @Test
    void tellsAnythingWatchingWhenTheTakingsChange() {
        StringBuilder seen = new StringBuilder();
        model.takingsProperty().addListener((property, before, after) -> seen.append(after).append(';'));

        model.refresh();
        gate.sell("REG", "Otieno");
        model.refresh();

        assertThat(seen.toString()).isEqualTo("KES 0;KES 1,500;");
    }

    @Test
    void aTypeSummaryReadsLikeASentence() {
        assertThat(new TypeSummary("VIP", "KES 5,000", 49, 50)).hasToString("VIP · KES 5,000 · 49 of 50 left");
    }

    @Test
    void sellingPutsTheNewSaleAtTheTopOfTheTable() {
        model.refresh();

        model.sell("REG", "Otieno");
        model.sell("VIP", "Wanjiku");

        assertThat(model.sales()).containsExactly(
                new SaleRow("VIP-0001", "VIP", "Wanjiku", "KES 5,000", "21:00"),
                new SaleRow("REG-0001", "Regular", "Otieno", "KES 1,500", "21:00"));
    }

    @Test
    void sellingUpdatesWhatIsLeftAndTheTakings() {
        model.refresh();

        model.sell("VIP", "Wanjiku");

        assertThat(model.types()).contains(new TypeSummary("VIP", "KES 5,000", 49, 50));
        assertThat(model.takingsProperty().get()).isEqualTo("KES 5,000");
    }

    @Test
    void saysWhatHappenedWithTheLastSale() {
        model.refresh();

        SaleOutcome outcome = model.sell("VIP", "Wanjiku");

        assertThat(outcome).isInstanceOf(SaleOutcome.Sold.class);
        assertThat(model.messageProperty().get()).isEqualTo("Sold VIP-0001 to Wanjiku for KES 5,000");
    }

    @Test
    void aRefusedSaleChangesNothingButTheMessage() {
        model.refresh();

        model.sell("REG", "   ");

        assertThat(model.messageProperty().get()).isEqualTo("Rejected: A ticket needs a buyer's name");
        assertThat(model.sales()).isEmpty();
        assertThat(model.takingsProperty().get()).isEqualTo("KES 0");
    }

    @Test
    void theSoldCountFollowsTheTableByItself() {
        model.refresh();
        assertThat(model.soldCount().get()).isEqualTo("0 sold");

        model.sell("REG", "Otieno");
        model.sell("REG", "Achieng");

        assertThat(model.soldCount().get()).isEqualTo("2 sold");
    }

    @Test
    void refreshShowsSalesMadeByAnotherProgramOnTheSameFile() {
        model.refresh();
        // The CLI from Day 105, run on the same gate.db, sells a ticket behind the window's back.
        new Gate(store, SampleEvent.ID, NINE_PM).sell("VIP", "Kamau");

        model.refresh();

        assertThat(model.sales()).extracting(SaleRow::buyer).containsExactly("Kamau");
    }

    @Test
    void offersTheEventsTicketTypesInOrder() {
        assertThat(model.ticketTypes()).extracting(type -> type.code()).containsExactly("REG", "VIP");
    }

    @Test
    void sellsWithThePhoneNumber() {
        model.refresh();

        model.sell("VIP", "Wanjiku", "0712 345 678");

        assertThat(gate.ticketsSold().getFirst().phone()).isEqualTo("254712345678");
    }
}
