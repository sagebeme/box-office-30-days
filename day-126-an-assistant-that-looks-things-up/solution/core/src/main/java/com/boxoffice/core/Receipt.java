package com.boxoffice.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The words on a ticket receipt, laid out for a narrow till printer:
 * 32 characters a line, the width of an 80 mm thermal roll in a mono font.
 *
 * <pre>
 * KILELE NIGHTS
 * Sat 14 Nov, 18:00
 * --------------------------------
 * Ticket  VIP-0001
 * Type    VIP
 * Buyer   Wanjiku Kamau
 * Price   KES 5,000
 * Sold    Sat 14 Nov, 21:00
 * --------------------------------
 * Show this at the gate.
 * One entry per ticket.
 * </pre>
 */
public final class Receipt {

    public static final int WIDTH = 32;
    private static final String RULE = "-".repeat(WIDTH);

    private Receipt() {
    }

    public static List<String> forTicket(Event event, Ticket ticket) {
        String typeName = event.ticketType(ticket.typeCode()).map(TicketType::name).orElse(ticket.typeCode());
        List<String> lines = new ArrayList<>();
        lines.add(event.name().toUpperCase(Locale.ROOT));
        lines.add(NairobiTime.format(event.startsAt().toInstant()));
        lines.add(RULE);
        lines.add(field("Ticket", ticket.code()));
        lines.add(field("Type", typeName));
        lines.add(field("Buyer", ticket.buyer()));
        lines.add(field("Price", ticket.price().equals(Money.ZERO) ? "Guest" : ticket.price().format()));
        lines.add(field("Sold", NairobiTime.format(ticket.soldAt())));
        lines.add(RULE);
        lines.add("Show this at the gate.");
        lines.add("One entry per ticket.");
        return lines;
    }

    // "Buyer   Wanjiku Kamau": the label padded to 8, the value cut to fit the line.
    private static String field(String label, String value) {
        String line = "%-8s%s".formatted(label, value);
        return line.length() <= WIDTH ? line : line.substring(0, WIDTH - 1) + "…";
    }
}
