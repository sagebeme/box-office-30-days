package com.boxoffice.core;

import java.time.Instant;

/** What can happen when a ticket is scanned at the gate. */
public sealed interface CheckInOutcome {

    /** A good ticket, used for the first time: let them in. */
    record Admitted(Ticket ticket) implements CheckInOutcome {
    }

    /** A real ticket that already came in: a copy, or someone trying it twice. */
    record AlreadyIn(Ticket ticket, Instant at) implements CheckInOutcome {
    }

    /** No ticket with that code was ever sold for this event. */
    record NotFound(String code) implements CheckInOutcome {
    }

    /** The line the check-in screen shows, big. */
    default String describe() {
        return switch (this) {
            case Admitted(Ticket ticket) ->
                    "Admitted: " + ticket.code() + " · " + ticket.buyer() + " (" + ticket.typeCode() + ")";
            case AlreadyIn(Ticket ticket, Instant at) ->
                    "Already in: " + ticket.code() + " came in at " + NairobiTime.timeOfDay(at);
            case NotFound(String code) when code.isEmpty() -> "Nothing was scanned";
            case NotFound(String code) -> "No such ticket: " + code;
        };
    }
}
