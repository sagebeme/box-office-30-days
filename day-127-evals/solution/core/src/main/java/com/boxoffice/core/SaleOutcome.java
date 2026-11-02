package com.boxoffice.core;

import java.util.List;

/**
 * What can happen when the gate tries to sell. "sealed" means these are the
 * only kinds there are, so a switch over them can check that every case is
 * handled: add a kind, and the compiler points at every switch to update.
 */
public sealed interface SaleOutcome {

    /** The sale went through. */
    record Sold(Ticket ticket) implements SaleOutcome {
    }

    /** A group sale went through: several tickets for one buyer, saved together. */
    record SoldGroup(List<Ticket> tickets, Money total) implements SaleOutcome {

        public SoldGroup {
            tickets = List.copyOf(tickets);
        }
    }

    /** That kind of ticket has none left. */
    record SoldOut(String typeCode) implements SaleOutcome {
    }

    /** The gate refused the sale, and says why. */
    record Rejected(String reason) implements SaleOutcome {
    }

    /** The line the gate screen shows for this outcome. */
    default String describe() {
        return switch (this) {
            case Sold(Ticket ticket) -> "Sold " + ticket.code() + " to " + ticket.buyer() + " for " + ticket.price().format();
            case SoldGroup(List<Ticket> tickets, Money total) -> "Sold " + tickets.size() + " tickets to "
                    + tickets.getFirst().buyer() + " for " + total.format() + ": "
                    + tickets.getFirst().code() + " to " + tickets.getLast().code();
            case SoldOut(String typeCode) -> typeCode + " is sold out";
            case Rejected(String reason) -> "Rejected: " + reason;
        };
    }
}
