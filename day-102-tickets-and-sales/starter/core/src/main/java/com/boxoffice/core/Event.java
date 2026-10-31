package com.boxoffice.core;

import java.util.List;
import java.util.Optional;

/** One night of an event, and the kinds of ticket it sells. */
public record Event(String id, String name, List<TicketType> ticketTypes) {

    public Event {
        // A copy nobody can change, so an Event never changes after it's made.
        ticketTypes = List.copyOf(ticketTypes);
    }

    /** The ticket type with this code, or empty if the event doesn't sell one. */
    public Optional<TicketType> ticketType(String code) {
        return ticketTypes.stream()
                .filter(type -> type.code().equals(code))
                .findFirst();
    }
}
