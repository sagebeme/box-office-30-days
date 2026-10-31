package com.boxoffice.core;

/**
 * A kind of ticket an event sells, such as Regular or VIP.
 *
 * @param code     short code printed on the ticket, e.g. "VIP"
 * @param name     what the gate staff see, e.g. "VIP"
 * @param capacity how many of this kind the event can sell
 */
public record TicketType(String code, String name, int capacity) {

    public TicketType {
        // TODO: a null or blank code -> throw new IllegalArgumentException("A ticket type needs a code")
        // TODO: a capacity below 0 -> throw new IllegalArgumentException("Capacity can't be negative: " + capacity)
    }
}
