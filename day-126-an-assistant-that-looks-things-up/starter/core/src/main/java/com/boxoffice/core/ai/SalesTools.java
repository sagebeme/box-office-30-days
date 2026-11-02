package com.boxoffice.core.ai;

import com.boxoffice.core.Event;
import com.boxoffice.core.Money;
import com.boxoffice.core.NairobiTime;
import com.boxoffice.core.Ticket;
import com.boxoffice.core.TicketStore;
import com.boxoffice.core.TicketType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The lookups Claude may ask for. Every one of them only reads: none can
 * sell, refund or change a thing, so the worst a confused answer can do is be
 * wrong, never break the night.
 *
 * Claude writes the inputs, so they're checked like anything else typed in
 * from outside. A bad input becomes a failed result Claude can read and fix,
 * never an exception.
 */
public final class SalesTools {

    static final Pattern TICKET_CODE = Pattern.compile("[A-Za-z0-9-]{3,20}");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TicketStore store;
    private final Event event;

    public SalesTools(TicketStore store, Event event) {
        this.store = store;
        this.event = event;
    }

    /** What Claude is told about each tool. */
    public List<ToolSpec> specs() {
        return List.of(
                new ToolSpec("find_ticket",
                        "Looks up one ticket by the code printed on it, such as VIP-0001 or A-REG-0012: its type, "
                                + "price, when it was sold, the buyer's first name and initial, the last three digits "
                                + "of their phone, and whether it has come through a gate. Use it when the staff ask "
                                + "about one particular ticket.",
                        Map.of("code", Map.of("type", "string", "description", "The ticket code, e.g. VIP-0001")),
                        List.of("code")),
                new ToolSpec("sales_between",
                        "Counts the tickets sold between two times on the night, with the money they took. Times "
                                + "are Nairobi time on a 24-hour clock, HH:mm; a time in the early morning means the "
                                + "hours after midnight. Use it for questions like \"how many did we sell in the "
                                + "last hour?\": the time now is in the facts.",
                        Map.of("from", Map.of("type", "string", "description", "Start time, e.g. 20:00"),
                                "to", Map.of("type", "string", "description", "End time, e.g. 21:00, not included")),
                        List.of("from", "to")),
                new ToolSpec("check_ins",
                        "Counts the tickets that have come through the gates so far, per ticket type, and says "
                                + "when the last one came in. Give a type code to count just that type.",
                        Map.of("type", Map.of("type", "string", "description", "Optional: REG or VIP")),
                        List.of()));
    }

    /** Runs one lookup. Never throws for bad input: that comes back as a failed result. */
    public ToolResult run(ToolCall call) {
        // TODO Day 126, step 2: read call.input() with JSON.readTree (a null or blank input counts as "{}").
        //   Not JSON (JsonProcessingException)  -> ToolResult.failed(call, "The input wasn't valid JSON")
        //   JSON but not an object (input.isObject()) -> failed "The input should be a JSON object"
        //   Then a switch on call.name(): "find_ticket", "sales_between" and "check_ins" go to the
        //   methods below; any other name -> failed "There's no tool called " + the name.
        throw new UnsupportedOperationException("Not written yet: Day 126, step 2");
    }

    private ToolResult findTicket(ToolCall call, JsonNode input) {
        // TODO Day 126, step 3: look one ticket up.
        //   input.path("code").asText("") stripped must match TICKET_CODE, or
        //       failed "code should be a ticket code like VIP-0001". Then upper-case it.
        //   Not in the store (store.findTicket) -> found "No ticket VIP-0099 was sold for Kilele Nights"
        //       (an answer, not a failure: Claude asked fine, the ticket just doesn't exist).
        //   Otherwise found, built with "%s: %s, %s, sold %s to %s, %s. It %s" from:
        //       the code; the type's name; the price, or "a guest ticket" at KES 0;
        //       NairobiTime.format(soldAt); shortName(buyer);
        //       "phone ending " + lastThree(phone), or "no phone given";
        //       "came in at " + NairobiTime.timeOfDay(...), or "hasn't come in yet".
        //   Never the full name or the full number: this text goes to the API.
        throw new UnsupportedOperationException("Not written yet: Day 126, step 3");
    }

    private ToolResult salesBetween(ToolCall call, JsonNode input) {
        Optional<Instant> from = onTheNight(input.path("from").asText(""));
        Optional<Instant> to = onTheNight(input.path("to").asText(""));
        if (from.isEmpty() || to.isEmpty()) {
            return ToolResult.failed(call, "from and to should be times like 20:00");
        }
        if (!from.get().isBefore(to.get())) {
            return ToolResult.failed(call, "from should be before to");
        }
        List<String> parts = new ArrayList<>();
        Money takings = Money.ZERO;
        for (TicketType type : event.ticketTypes()) {
            int paid = 0;
            int guests = 0;
            for (Ticket ticket : store.tickets(event.id())) {
                boolean inWindow = !ticket.soldAt().isBefore(from.get()) && ticket.soldAt().isBefore(to.get());
                if (!inWindow || !ticket.typeCode().equals(type.code())) {
                    continue;
                }
                if (ticket.price().equals(Money.ZERO)) {
                    guests++;
                } else {
                    paid++;
                }
                takings = takings.plus(ticket.price());
            }
            parts.add(type.name() + " " + paid + " paid and " + guests + (guests == 1 ? " guest" : " guests"));
        }
        return ToolResult.found(call, "Between %s and %s: %s. Takings %s".formatted(
                NairobiTime.timeOfDay(from.get()), NairobiTime.timeOfDay(to.get()), String.join(", ", parts),
                takings.format()));
    }

    private ToolResult checkIns(ToolCall call, JsonNode input) {
        String only = input.path("type").asText("").strip().toUpperCase();
        if (!only.isEmpty() && event.ticketType(only).isEmpty()) {
            List<String> codes = event.ticketTypes().stream().map(TicketType::code).toList();
            return ToolResult.failed(call, "There's no ticket type " + only + ". " + event.name() + " sells "
                    + String.join(", ", codes));
        }
        List<String> parts = new ArrayList<>();
        Instant last = null;
        for (TicketType type : event.ticketTypes()) {
            if (!only.isEmpty() && !type.code().equals(only)) {
                continue;
            }
            int sold = 0;
            int inside = 0;
            for (Ticket ticket : store.tickets(event.id())) {
                if (!ticket.typeCode().equals(type.code())) {
                    continue;
                }
                sold++;
                Optional<Instant> at = store.checkedInAt(event.id(), ticket.code());
                if (at.isPresent()) {
                    inside++;
                    if (last == null || at.get().isAfter(last)) {
                        last = at.get();
                    }
                }
            }
            parts.add(type.name() + " " + inside + " of " + sold);
        }
        String lastOne = last == null ? "Nobody has come in yet" : "The last came in at " + NairobiTime.timeOfDay(last);
        return ToolResult.found(call, "Came in so far: " + String.join(", ", parts) + ". " + lastOne);
    }

    /** "21:30" on the night of the event; "02:00" is the morning after. Empty if it isn't a time. */
    Optional<Instant> onTheNight(String text) {
        LocalTime time;
        try {
            time = LocalTime.parse(text.strip());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
        // TODO Day 126, step 4: put the time on the event's start day: event.startsAt().with(time).
        //   If that's more than 6 hours before the start, it means after midnight: add a day.
        //   (02:00 is the small hours of Sunday, not Saturday afternoon.) Return it as an Instant.
        throw new UnsupportedOperationException("Not written yet: Day 126, step 4");
    }

    /** "Wanjiku Kamau" -> "Wanjiku K.": enough to say "is this yours?", not a full name to send away. */
    static String shortName(String buyer) {
        // TODO Day 126, step 3: the first word, a space, then the last word's first letter and a dot.
        //   One word stays as it is. Split on runs of spaces: buyer.strip().split("\\s+").
        throw new UnsupportedOperationException("Not written yet: Day 126, step 3");
    }

    private static String lastThree(String phone) {
        return phone.substring(Math.max(0, phone.length() - 3));
    }
}
