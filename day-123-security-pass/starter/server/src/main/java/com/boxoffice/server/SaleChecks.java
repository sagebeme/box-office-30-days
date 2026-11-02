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
        // TODO Day 123, step 4: check the rest, returning the first problem, in this order:
        //   more than MOST_TICKETS tickets          "A sale has at most 50 tickets"
        //   then for each ticket:
        //   code isn't like A-REG-0001              "A ticket code looks like A-REG-0001, not " + shorten(code)
        //     (regex: [A-Z0-9]{1,3}-[A-Z0-9]{1,10}-\d{4,6})
        //   type isn't [A-Z0-9]{1,10}               "A ticket type is a short code like REG"
        //   buyer null, blank or over 200           "A buyer's name is 1 to 200 characters"
        //   buyer has a control character           "A buyer's name can't have control characters in it"
        //     (buyer.chars().anyMatch(Character::isISOControl))
        //   phone neither empty nor 254[17]\d{8}    "A phone number is empty or like 254712345678"
        //   priceCents below 0 or over 10,000,000   "A ticket costs between KES 0 and KES 100,000"
        //   soldAt that Instant.parse can't read    "soldAt is a time like 2026-11-14T18:00:00Z"
        //     (it throws DateTimeParseException; treat null as "")
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
