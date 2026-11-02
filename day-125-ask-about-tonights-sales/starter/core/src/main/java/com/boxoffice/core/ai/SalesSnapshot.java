package com.boxoffice.core.ai;

import com.boxoffice.core.Event;
import com.boxoffice.core.NairobiTime;
import com.boxoffice.core.SalesReport;
import com.boxoffice.core.Ticket;
import com.boxoffice.core.TicketType;
import java.time.Instant;
import java.util.List;

/**
 * The night in numbers, ready to hand to Claude. Only counts and money: no
 * buyer names and no phone numbers, because whatever goes in a prompt leaves
 * the laptop.
 */
public record SalesSnapshot(Event event, SalesReport report, int checkedIn, int ticketsIssued, Instant now) {

    public static SalesSnapshot of(Event event, List<Ticket> tickets, int checkedIn, Instant now) {
        return new SalesSnapshot(event, SalesReport.of(event, tickets), checkedIn, tickets.size(), now);
    }

    /**
     * The facts, one per line:
     *
     * <pre>
     * Event: Kilele Nights, Sat 14 Nov, 18:00 to Sun 15 Nov, 04:00 (Nairobi time)
     * Now: Sat 14 Nov, 21:00
     * Regular (REG): KES 1,500 each. 2 paid, 1 guest, 497 left of 500. Takings KES 3,000
     * VIP (VIP): KES 5,000 each. 1 paid, 0 guests, 49 left of 50. Takings KES 5,000
     * Total: 3 paid, 1 guest, 546 left of 550. Takings KES 8,000
     * Through the gates: 1 of the 4 tickets issued
     * </pre>
     */
    public String facts() {
        // TODO Day 125, step 2: build the text in the comment above, one fact per line, each ending in \n.
        //   NairobiTime.format(...) writes "Sat 14 Nov, 21:00"; Money.format() writes "KES 1,500".
        //   report.lines().get(i) is the numbers for event.ticketTypes().get(i): same order.
        //   counts(...) below writes the "2 paid, 1 guest, 497 left of 500" part, guest or guests included.
        //   Only counts and money: no buyer names, no phone numbers.
        throw new UnsupportedOperationException("Not written yet: Day 125, step 2");
    }

    private static String counts(int paid, int guests, int left, int capacity) {
        return paid + " paid, " + guests + (guests == 1 ? " guest, " : " guests, ")
                + left + " left of " + capacity;
    }
}
