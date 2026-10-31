package com.boxoffice.core;

import java.util.List;

/** The event the course uses: a Saturday night at Ngong Racecourse. */
public final class SampleEvent {

    public static final String ID = "kilele-2026";

    private SampleEvent() {
    }

    public static Event kileleNights() {
        return new Event(ID, "Kilele Nights",
                NairobiTime.at(2026, 11, 14, 18, 0),
                NairobiTime.at(2026, 11, 15, 4, 0),
                List.of(
                        new TicketType("REG", "Regular", Money.ofKes(1_500), 500),
                        new TicketType("VIP", "VIP", Money.ofKes(5_000), 50)));
    }
}
