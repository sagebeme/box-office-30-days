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
    void anEventsListOfTypesCannotBeChanged() {
        Event event = SampleEvent.kileleNights();

        assertThatThrownBy(() -> event.ticketTypes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
