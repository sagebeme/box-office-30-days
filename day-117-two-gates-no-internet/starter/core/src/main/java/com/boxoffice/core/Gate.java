package com.boxoffice.core;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The gate: sells tickets for one event and keeps every sale in the store. */
public final class Gate {

    private final TicketStore store;
    private final Event event;
    private final Clock clock;
    // "A-" for gate A, or "" for a gate with no id (codes then look like VIP-0001).
    private final String codePrefix;

    /**
     * @param clock where the gate reads the time. The app passes the real clock;
     *              tests pass a fixed one, so "now" is the same on every run.
     */
    public Gate(TicketStore store, String eventId, Clock clock) {
        this.store = store;
        this.event = store.findEvent(eventId)
                .orElseThrow(() -> new IllegalArgumentException("No event " + eventId + " in the store"));
        this.clock = clock;
        // TODO: read store.gateId(). Empty? the prefix is "". Otherwise it's the id and a dash: "A-".
        this.codePrefix = "";
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

    /**
     * Sells several tickets of one type to one buyer as a single sale: a
     * family at the gate. Either every ticket is saved, or none is.
     */
    public SaleOutcome sellGroup(String typeCode, String buyer, String phone, int quantity) {
        if (quantity < 1) {
            return new SaleOutcome.Rejected("A sale needs at least one ticket");
        }
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
        int left = remaining(typeCode);
        if (left == 0) {
            return new SaleOutcome.SoldOut(typeCode);
        }
        if (left < quantity) {
            return new SaleOutcome.Rejected("Only " + left + " " + typeCode + " left");
        }
        int issuedHere = store.countIssuedHere(event.id(), typeCode, codePrefix);
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 1; i <= quantity; i++) {
            String code = codeFor(typeCode, issuedHere + i);
            tickets.add(new Ticket(code, typeCode, buyer.strip(), savedPhone, type.get().price(), now));
        }
        store.recordGroupSale(event.id(), tickets);
        return new SaleOutcome.SoldGroup(tickets, type.get().price().times(quantity));
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

    /**
     * Checks a scanned ticket in. Scanners can add spaces or send lower case,
     * so the code is tidied first: " vip-0001 " is VIP-0001.
     */
    public CheckInOutcome checkIn(String scanned) {
        String code = scanned == null ? "" : scanned.strip().toUpperCase(Locale.ROOT);
        Optional<Ticket> ticket = store.findTicket(event.id(), code);
        if (ticket.isEmpty()) {
            return new CheckInOutcome.NotFound(code);
        }
        if (store.markCheckedIn(event.id(), code, clock.instant())) {
            return new CheckInOutcome.Admitted(ticket.get());
        }
        Instant cameInAt = store.checkedInAt(event.id(), code).orElseThrow();
        return new CheckInOutcome.AlreadyIn(ticket.get(), cameInAt);
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

    // VIP-0001, VIP-0002, ... counted per ticket type, and per gate: gate A's
    // fifth VIP is A-VIP-0005, however many gate B has sold.
    private String nextCode(String typeCode) {
        return codeFor(typeCode, store.countIssuedHere(event.id(), typeCode, codePrefix) + 1);
    }

    private String codeFor(String typeCode, int number) {
        // TODO: put codePrefix in front, so gate A's codes look like A-VIP-0001
        return "%s-%04d".formatted(typeCode, number);
    }
}
