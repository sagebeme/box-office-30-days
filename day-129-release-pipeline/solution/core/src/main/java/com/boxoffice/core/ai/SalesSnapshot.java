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
        StringBuilder out = new StringBuilder();
        out.append("Event: ").append(event.name()).append(", ")
                .append(NairobiTime.format(event.startsAt().toInstant())).append(" to ")
                .append(NairobiTime.format(event.endsAt().toInstant())).append(" (Nairobi time)\n");
        out.append("Now: ").append(NairobiTime.format(now)).append('\n');

        int paid = 0;
        int guests = 0;
        int left = 0;
        int capacity = 0;
        List<TicketType> types = event.ticketTypes();
        // The report has one line per ticket type, in the same order as the event lists them.
        for (int i = 0; i < types.size(); i++) {
            TicketType type = types.get(i);
            SalesReport.Line line = report.lines().get(i);
            out.append(type.name()).append(" (").append(type.code()).append("): ")
                    .append(type.price().format()).append(" each. ")
                    .append(counts(line.paid(), line.guests(), line.left(), type.capacity()))
                    .append(". Takings ").append(line.takings().format()).append('\n');
            paid += line.paid();
            guests += line.guests();
            left += line.left();
            capacity += type.capacity();
        }
        out.append("Total: ").append(counts(paid, guests, left, capacity))
                .append(". Takings ").append(report.totalTakings().format()).append('\n');
        out.append("Through the gates: ").append(checkedIn).append(" of the ").append(ticketsIssued)
                .append(" tickets issued\n");
        return out.toString();
    }

    private static String counts(int paid, int guests, int left, int capacity) {
        return paid + " paid, " + guests + (guests == 1 ? " guest, " : " guests, ")
                + left + " left of " + capacity;
    }
}
