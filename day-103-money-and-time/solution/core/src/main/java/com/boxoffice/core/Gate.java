package com.boxoffice.core;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** The gate: sells tickets for one event and remembers what it sold. */
public final class Gate {

    private final Event event;
    private final Clock clock;
    private final List<Ticket> sold = new ArrayList<>();

    /**
     * @param clock where the gate reads the time. The app passes the real clock;
     *              tests pass a fixed one, so "now" is the same on every run.
     */
    public Gate(Event event, Clock clock) {
        this.event = event;
        this.clock = clock;
    }

    public Event event() {
        return event;
    }

    /** Sells one ticket of this type to this buyer, if the rules allow it. */
    public SaleOutcome sell(String typeCode, String buyer) {
        if (buyer == null || buyer.isBlank()) {
            return new SaleOutcome.Rejected("A ticket needs a buyer's name");
        }
        Instant now = clock.instant();
        Instant end = event.endsAt().toInstant();
        if (!now.isBefore(end)) {
            return new SaleOutcome.Rejected(event.name() + " ended at " + NairobiTime.format(end));
        }
        Optional<TicketType> type = event.ticketType(typeCode);
        if (type.isEmpty()) {
            return new SaleOutcome.Rejected("No ticket type called " + typeCode);
        }
        if (remaining(typeCode) == 0) {
            return new SaleOutcome.SoldOut(typeCode);
        }
        Ticket ticket = new Ticket(nextCode(typeCode), typeCode, buyer.strip(), type.get().price(), now);
        sold.add(ticket);
        return new SaleOutcome.Sold(ticket);
    }

    /** How many tickets of this type are left. An unknown type has none. */
    public int remaining(String typeCode) {
        return event.ticketType(typeCode)
                .map(type -> type.capacity() - soldOf(typeCode))
                .orElse(0);
    }

    /** Every ticket sold so far, oldest first. */
    public List<Ticket> ticketsSold() {
        return Collections.unmodifiableList(sold);
    }

    /** All the money taken so far. */
    public Money takings() {
        Money total = Money.ZERO;
        for (Ticket ticket : sold) {
            total = total.plus(ticket.price());
        }
        return total;
    }

    private int soldOf(String typeCode) {
        return (int) sold.stream().filter(t -> t.typeCode().equals(typeCode)).count();
    }

    // VIP-0001, VIP-0002, ... counted per ticket type.
    private String nextCode(String typeCode) {
        return "%s-%04d".formatted(typeCode, soldOf(typeCode) + 1);
    }
}
