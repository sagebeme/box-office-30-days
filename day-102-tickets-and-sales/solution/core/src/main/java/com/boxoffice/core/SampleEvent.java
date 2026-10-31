package com.boxoffice.core;

import java.util.List;

/** The event the course uses: a Saturday night at Ngong Racecourse. */
public final class SampleEvent {

    public static final String ID = "kilele-2026";

    private SampleEvent() {
    }

    public static Event kileleNights() {
        return new Event(ID, "Kilele Nights", List.of(
                new TicketType("REG", "Regular", 500),
                new TicketType("VIP", "VIP", 50)));
    }
}
