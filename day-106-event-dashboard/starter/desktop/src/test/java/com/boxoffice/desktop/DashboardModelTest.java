package com.boxoffice.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Gate;
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
}
