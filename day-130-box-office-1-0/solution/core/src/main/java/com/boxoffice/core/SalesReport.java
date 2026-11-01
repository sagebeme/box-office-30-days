package com.boxoffice.core;

import java.util.ArrayList;
import java.util.List;

/** The end-of-night sheet: what each ticket type sold and took. */
public record SalesReport(Event event, List<Line> lines) {

    /** One ticket type's numbers. Guests are tickets issued at KES 0. */
    public record Line(String typeName, int paid, int guests, int left, Money takings) {
    }

    public SalesReport {
        lines = List.copyOf(lines);
    }

    public static SalesReport of(Event event, List<Ticket> tickets) {
        List<Line> lines = new ArrayList<>();
        for (TicketType type : event.ticketTypes()) {
            int paid = 0;
            int guests = 0;
            Money takings = Money.ZERO;
            for (Ticket ticket : tickets) {
                if (!ticket.typeCode().equals(type.code())) {
                    continue;
                }
                if (ticket.price().equals(Money.ZERO)) {
                    guests++;
                } else {
                    paid++;
                }
                takings = takings.plus(ticket.price());
            }
            lines.add(new Line(type.name(), paid, guests, type.capacity() - paid - guests, takings));
        }
        return new SalesReport(event, lines);
    }

    public Money totalTakings() {
        Money total = Money.ZERO;
        for (Line line : lines) {
            total = total.plus(line.takings());
        }
        return total;
    }

    /**
     * <pre>
     * Kilele Nights · Sat 14 Nov, 18:00
     * Type        Paid  Guests   Left        Takings
     * Regular        2       1    497      KES 3,000
     * VIP            1       0     49      KES 5,000
     * Total          3       1    546      KES 8,000
     * </pre>
     */
    public String render() {
        String row = "%-10s %5s %7s %6s %14s%n";
        StringBuilder out = new StringBuilder();
        out.append(event.name()).append(" · ").append(NairobiTime.format(event.startsAt().toInstant()))
                .append(System.lineSeparator());
        out.append(row.formatted("Type", "Paid", "Guests", "Left", "Takings"));
        int paid = 0;
        int guests = 0;
        int left = 0;
        for (Line line : lines) {
            out.append(row.formatted(line.typeName(), line.paid(), line.guests(), line.left(),
                    line.takings().format()));
            paid += line.paid();
            guests += line.guests();
            left += line.left();
        }
        out.append(row.formatted("Total", paid, guests, left, totalTakings().format()));
        return out.toString();
    }
}
