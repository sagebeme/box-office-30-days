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
        // TODO: add the lines in the order the class comment shows:
        //   - the event's name in capitals (toUpperCase(Locale.ROOT)), then its start time (NairobiTime.format)
        //   - RULE
        //   - field("Ticket", ...), field("Type", typeName), field("Buyer", ...),
        //     field("Price", ...): "Guest" when the price is Money.ZERO, otherwise price().format()
        //     field("Sold", ...): NairobiTime.format(ticket.soldAt())
        //   - RULE, then "Show this at the gate." and "One entry per ticket."
        throw new UnsupportedOperationException("Not written yet: Day 112, step 3");
    }

    // "Buyer   Wanjiku Kamau": the label padded to 8, the value cut to fit the line.
    private static String field(String label, String value) {
        String line = "%-8s%s".formatted(label, value);
        return line.length() <= WIDTH ? line : line.substring(0, WIDTH - 1) + "…";
    }
}
