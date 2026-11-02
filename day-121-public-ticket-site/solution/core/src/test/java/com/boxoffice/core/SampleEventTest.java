package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SampleEventTest {

    @Test
    void kileleNightsSellsRegularAndVip() {
        Event event = SampleEvent.kileleNights();

        assertThat(event.name()).isEqualTo("Kilele Nights");
        assertThat(event.ticketType("REG")).map(TicketType::capacity).contains(500);
        assertThat(event.ticketType("VIP")).map(TicketType::capacity).contains(50);
    }

    @Test
    void kileleNightsRunsFromSixInTheEveningToFourInTheMorning() {
        Event event = SampleEvent.kileleNights();

        assertThat(NairobiTime.format(event.startsAt().toInstant())).isEqualTo("Sat 14 Nov, 18:00");
        assertThat(NairobiTime.format(event.endsAt().toInstant())).isEqualTo("Sun 15 Nov, 04:00");
        assertThat(event.ticketType("REG")).map(TicketType::price).contains(Money.ofKes(1_500));
    }

    @Test
    void anEventsListOfTypesCannotBeChanged() {
        Event event = SampleEvent.kileleNights();

        assertThatThrownBy(() -> event.ticketTypes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ensureInAddsTheEventOnlyOnce(@TempDir Path folder) {
        try (TicketStore store = TicketStore.open(folder.resolve("gate.db"))) {
            SampleEvent.ensureIn(store);

            // A second time finds the saved event instead of saving it again.
            assertThat(SampleEvent.ensureIn(store)).isEqualTo(SampleEvent.kileleNights());
        }
    }

    @Test
    void thePracticeClockReadsSevenInTheEveningOnTheNight() {
        Instant now = SampleEvent.practiceClock().instant();

        assertThat(now).isCloseTo(Instant.parse("2026-11-14T16:00:00Z"), within(Duration.ofSeconds(5)));
    }
}
