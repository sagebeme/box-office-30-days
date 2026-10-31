package com.boxoffice.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

    /**
     * A clock set to the night of the event, 19:00 in Nairobi, that ticks on from
     * the moment it's made. The app and the CLI use it so the gate still sells when
     * you practise long after 14 November 2026. A real gate would use Clock.systemUTC().
     */
    public static Clock practiceClock() {
        Instant eventNight = NairobiTime.at(2026, 11, 14, 19, 0).toInstant();
        return Clock.offset(Clock.systemUTC(), Duration.between(Instant.now(), eventNight));
    }

    /** Puts Kilele Nights in the store if it isn't there yet, and returns it. */
    public static Event ensureIn(TicketStore store) {
        return store.findEvent(ID).orElseGet(() -> {
            Event event = kileleNights();
            store.saveEvent(event);
            return event;
        });
    }
}
