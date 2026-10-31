package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

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
}
