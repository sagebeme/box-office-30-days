package com.boxoffice.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The gate: sells tickets for one event and remembers what it sold. */
public final class Gate {

    private final Event event;
    private final List<Ticket> sold = new ArrayList<>();

    public Gate(Event event) {
        this.event = event;
    }

    public Event event() {
        return event;
    }

    /** Sells one ticket of this type to this buyer, if the rules allow it. */
    public SaleOutcome sell(String typeCode, String buyer) {
        // TODO: a null or blank buyer -> Rejected("A ticket needs a buyer's name")
        // TODO: a type the event doesn't sell (event.ticketType(typeCode) is empty)
        //       -> Rejected("No ticket type called " + typeCode)
        // TODO: none left (remaining(typeCode) == 0) -> SoldOut(typeCode)
        // TODO: otherwise make a Ticket with nextCode(typeCode) and the buyer's name stripped,
        //       add it to sold, and return Sold(ticket)
        throw new UnsupportedOperationException("Not written yet: Day 102, step 4");
    }

    /** How many tickets of this type are left. An unknown type has none. */
    public int remaining(String typeCode) {
        // TODO: find the type with event.ticketType(typeCode); its capacity minus soldOf(typeCode).
        //       An unknown type has 0 left.
        return 0;
    }

    /** Every ticket sold so far, oldest first. */
    public List<Ticket> ticketsSold() {
        return Collections.unmodifiableList(sold);
    }

    private int soldOf(String typeCode) {
        return (int) sold.stream().filter(t -> t.typeCode().equals(typeCode)).count();
    }

    // VIP-0001, VIP-0002, ... counted per ticket type.
    private String nextCode(String typeCode) {
        return "%s-%04d".formatted(typeCode, soldOf(typeCode) + 1);
    }
}
