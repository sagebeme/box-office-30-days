package com.boxoffice.core;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** The gate: sells tickets for one event and keeps every sale in the store. */
public final class Gate {

    private final TicketStore store;
    private final Event event;
    private final Clock clock;

    /**
     * @param clock where the gate reads the time. The app passes the real clock;
     *              tests pass a fixed one, so "now" is the same on every run.
     */
    public Gate(TicketStore store, String eventId, Clock clock) {
        this.store = store;
        this.event = store.findEvent(eventId)
                .orElseThrow(() -> new IllegalArgumentException("No event " + eventId + " in the store"));
        this.clock = clock;
    }

    public Event event() {
        return event;
    }

    /** Sells one ticket of this type to this buyer, at the type's price. */
    public SaleOutcome sell(String typeCode, String buyer) {
        return sell(typeCode, buyer, "");
    }

    /** Sells one ticket and keeps the buyer's phone number. A blank phone means none. */
    public SaleOutcome sell(String typeCode, String buyer, String phone) {
        return issue(typeCode, buyer, phone, false);
    }

    /** Lets a guest from the guest list in for free. A guest still takes a place. */
    public SaleOutcome admitGuest(String typeCode, String name) {
        return issue(typeCode, name, "", true);
    }

    private SaleOutcome issue(String typeCode, String buyer, String phone, boolean guest) {
        if (buyer == null || buyer.isBlank()) {
            return new SaleOutcome.Rejected("A ticket needs a buyer's name");
        }
        String savedPhone = "";
        if (phone != null && !phone.isBlank()) {
            Optional<String> normalised = PhoneNumbers.normalise(phone);
            if (normalised.isEmpty()) {
                return new SaleOutcome.Rejected("That doesn't look like a Kenyan mobile number: " + phone.strip());
            }
            savedPhone = normalised.get();
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
        Money price = guest ? Money.ZERO : type.get().price();
        Ticket ticket = new Ticket(nextCode(typeCode), typeCode, buyer.strip(), savedPhone, price, now);
        store.recordSale(event.id(), ticket);
        return new SaleOutcome.Sold(ticket);
    }

    /** How many tickets of this type are left. An unknown type has none. */
    public int remaining(String typeCode) {
        return event.ticketType(typeCode)
                .map(type -> type.capacity() - store.countSold(event.id(), typeCode))
                .orElse(0);
    }

    /** Every ticket sold so far, oldest first. */
    public List<Ticket> ticketsSold() {
        return store.tickets(event.id());
    }

    /** All the money taken so far. */
    public Money takings() {
        Money total = Money.ZERO;
        for (Ticket ticket : ticketsSold()) {
            total = total.plus(ticket.price());
        }
        return total;
    }

    // VIP-0001, VIP-0002, ... counted per ticket type.
    private String nextCode(String typeCode) {
        return "%s-%04d".formatted(typeCode, store.countSold(event.id(), typeCode) + 1);
    }
}
