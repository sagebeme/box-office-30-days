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
        // TODO: a switch over this, like SaleOutcome.describe on Day 102:
        //   Admitted  -> "Admitted: VIP-0001 · Wanjiku (VIP)"      (code · buyer (type code))
        //   AlreadyIn -> "Already in: VIP-0001 came in at 21:00"   (NairobiTime.timeOfDay(at))
        //   NotFound with an empty code -> "Nothing was scanned"
        //   NotFound  -> "No such ticket: VIP-9999"
        // Hint: "case NotFound(String code) when code.isEmpty() ->" has to come before the plain NotFound case.
        throw new UnsupportedOperationException("Not written yet: Day 110, step 4");
    }
}
