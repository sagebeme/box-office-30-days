package com.boxoffice.core;

/**
 * What can happen when the gate tries to sell a ticket. "sealed" means these
 * three are the only kinds there will ever be, so a switch over them can
 * check that every case is handled.
 */
public sealed interface SaleOutcome {

    /** The sale went through. */
    record Sold(Ticket ticket) implements SaleOutcome {
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
            case SoldOut(String typeCode) -> typeCode + " is sold out";
            case Rejected(String reason) -> "Rejected: " + reason;
        };
    }
}
