package com.boxoffice.server;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Checks a sale from a gate before it goes near the database. A registered
 * gate is trusted to sell, not to send anything at all: a stolen laptop's
 * token, or a bug, mustn't be able to fill the server with nonsense.
 */
final class SaleChecks {

    /** The most tickets one sale can have. A family is 6; a whole school trip is 40. */
    static final int MOST_TICKETS = 50;

    private SaleChecks() {
    }

    /** What's wrong with the sale, or empty if nothing is. Stops at the first problem. */
    static Optional<String> problem(SaleRequest sale) {
        if (sale.eventId() == null || !sale.eventId().matches("[a-z0-9-]{1,50}")) {
            return Optional.of("An event id is lower-case letters, digits and dashes, like kilele-2026");
        }
        if (sale.tickets() == null || sale.tickets().isEmpty()) {
            return Optional.of("A sale needs at least one ticket");
        }
        if (sale.tickets().size() > MOST_TICKETS) {
            return Optional.of("A sale has at most " + MOST_TICKETS + " tickets");
        }
        for (SaleRequest.TicketRequest ticket : sale.tickets()) {
            if (ticket.code() == null || !ticket.code().matches("[A-Z0-9]{1,3}-[A-Z0-9]{1,10}-\\d{4,6}")) {
                return Optional.of("A ticket code looks like A-REG-0001, not " + shorten(ticket.code()));
            }
            if (ticket.type() == null || !ticket.type().matches("[A-Z0-9]{1,10}")) {
                return Optional.of("A ticket type is a short code like REG");
            }
            if (ticket.buyer() == null || ticket.buyer().isBlank() || ticket.buyer().length() > 200) {
                return Optional.of("A buyer's name is 1 to 200 characters");
            }
            if (ticket.buyer().chars().anyMatch(Character::isISOControl)) {
                return Optional.of("A buyer's name can't have control characters in it");
            }
            if (ticket.phone() != null && !ticket.phone().isEmpty() && !ticket.phone().matches("254[17]\\d{8}")) {
                return Optional.of("A phone number is empty or like 254712345678");
            }
            // Up to KES 100,000 a ticket: more than that is a typo, or worse.
            if (ticket.priceCents() < 0 || ticket.priceCents() > 10_000_000) {
                return Optional.of("A ticket costs between KES 0 and KES 100,000");
            }
            try {
                Instant.parse(ticket.soldAt() == null ? "" : ticket.soldAt());
            } catch (DateTimeParseException e) {
                return Optional.of("soldAt is a time like 2026-11-14T18:00:00Z");
            }
        }
        return Optional.empty();
    }

    // Never echo back 10,000 characters of whatever was sent.
    private static String shorten(String text) {
        if (text == null) {
            return "nothing";
        }
        return text.length() <= 20 ? text : text.substring(0, 20) + "...";
    }
}
