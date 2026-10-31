package com.boxoffice.server;

import java.time.Clock;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves the sales gates send, each one once, and settles the last ticket.
 *
 * <p>Two gates offline can both sell the last VIP place. Neither did anything
 * wrong: each counted what it knew. The server is the one place that sees
 * every sale, so it decides: <b>the first sale to reach the server gets the
 * place</b>. The other is kept as OVERSOLD, and its gate shows it to staff.
 */
@Service
class Sales {

    enum Result { ACCEPTED, DUPLICATE, OVERSOLD }

    /** The server's answer: what it decided, and why when it said no. */
    record Verdict(Result result, String detail) {
    }

    private final JdbcClient db;
    private final Clock clock;
    private Runnable afterCounting = () -> { };

    Sales(JdbcClient db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /**
     * @Transactional: Spring opens a transaction before this method and commits
     * after it, or rolls back if it throws. The sale and its tickets are saved
     * together or not at all, like inTransaction on the gate.
     */
    @Transactional
    Verdict receive(String gate, String key, SaleRequest sale) {
        if (sale.eventId() == null || sale.tickets() == null || sale.tickets().isEmpty()) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "A sale needs an event and at least one ticket");
        }
        // A gate may only send tickets it sold itself: gate A's codes start with "A-".
        for (SaleRequest.TicketRequest ticket : sale.tickets()) {
            if (ticket.code() == null || !ticket.code().startsWith(gate + "-")) {
                throw new ApiProblem(HttpStatus.FORBIDDEN,
                        "Gate " + gate + " can't send ticket " + ticket.code() + ": its codes start with " + gate + "-");
            }
        }
        String type = sale.tickets().getFirst().type();
        if (sale.tickets().stream().anyMatch(ticket -> !type.equals(ticket.type()))) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "A sale is tickets of one type");
        }

        // TODO (step 4): two requests can both get here, both count 49 VIP, and both take the last place.
        //       Add " for update" to the end of this select. It locks this ticket type's row until we
        //       commit, so a second request for the same type waits here until the first is saved.
        Optional<Integer> capacity = db.sql(
                        "select capacity from ticket_types where event_id = ? and code = ?")
                .params(sale.eventId(), type)
                .query(Integer.class)
                .optional();
        if (capacity.isEmpty()) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "No ticket type " + type + " at " + sale.eventId());
        }

        // Asked before? Then the same answer as last time, whatever has changed since.
        Optional<Verdict> earlier = db.sql("select result, detail from sales where idempotency_key = ?")
                .param(key)
                .query((row, n) -> new Verdict(Result.valueOf(row.getString("result")), row.getString("detail")))
                .optional();
        if (earlier.isPresent()) {
            return earlier.get().result() == Result.ACCEPTED ? new Verdict(Result.DUPLICATE, "") : earlier.get();
        }

        int sold = db.sql("select count(*) from tickets where event_id = ? and type_code = ?")
                .params(sale.eventId(), type)
                .query(Integer.class)
                .single();
        afterCounting.run();
        int left = capacity.get() - sold;
        // TODO (step 3): more tickets in this sale than places left? Then it's oversold:
        //   Verdict oversold = new Verdict(Result.OVERSOLD,
        //           "Only " + left + " " + type + (left == 1 ? " place was" : " places were") + " left");
        //   saveSale(gate, key, sale, oversold);    (kept without its tickets, so a repeat gets the same answer)
        //   return oversold;
        Verdict accepted = new Verdict(Result.ACCEPTED, "");
        saveSale(gate, key, sale, accepted);
        for (SaleRequest.TicketRequest ticket : sale.tickets()) {
            db.sql("insert into tickets (event_id, code, sale_key, type_code, buyer, phone, price_cents, sold_at)"
                            + " values (?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(sale.eventId(), ticket.code(), key, ticket.type(), ticket.buyer(),
                            ticket.phone() == null ? "" : ticket.phone(), ticket.priceCents(), ticket.soldAt())
                    .update();
        }
        return accepted;
    }

    // An oversold sale is kept too (without its tickets), so a repeat gets the same answer.
    private void saveSale(String gate, String key, SaleRequest sale, Verdict verdict) {
        db.sql("insert into sales (idempotency_key, gate, event_id, received_at, result, detail)"
                        + " values (?, ?, ?, ?, ?, ?)")
                .params(key, gate, sale.eventId(), clock.instant().toString(), verdict.result().name(),
                        verdict.detail())
                .update();
    }

    int ticketCount(String eventId) {
        return db.sql("select count(*) from tickets where event_id = ?").param(eventId).query(Integer.class).single();
    }

    int ticketCount(String eventId, String type) {
        return db.sql("select count(*) from tickets where event_id = ? and type_code = ?")
                .params(eventId, type).query(Integer.class).single();
    }

    /** For the race test only: runs after counting and before saving, to make a race easy to hit. */
    void afterCounting(Runnable hook) {
        afterCounting = hook;
    }
}
