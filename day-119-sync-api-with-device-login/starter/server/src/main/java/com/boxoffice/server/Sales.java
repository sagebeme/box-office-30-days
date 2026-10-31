package com.boxoffice.server;

import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saves the sales gates send, each one once. */
@Service
class Sales {

    enum Result { ACCEPTED, DUPLICATE }

    private final JdbcClient db;
    private final Clock clock;

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
    Result receive(String gate, String key, SaleRequest sale) {
        if (sale.eventId() == null || sale.tickets() == null || sale.tickets().isEmpty()) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "A sale needs an event and at least one ticket");
        }
        // TODO: a gate may only send tickets it sold itself: gate A's codes start with "A-".
        //       For each ticket whose code is null or doesn't start with gate + "-", throw
        //       new ApiProblem(HttpStatus.FORBIDDEN,
        //               "Gate " + gate + " can't send ticket " + ticket.code() + ": its codes start with " + gate + "-")
        boolean seen = db.sql("select count(*) from sales where idempotency_key = ?")
                .param(key).query(Integer.class).single() > 0;
        if (seen) {
            return Result.DUPLICATE;
        }
        db.sql("insert into sales (idempotency_key, gate, event_id, received_at) values (?, ?, ?, ?)")
                .params(key, gate, sale.eventId(), clock.instant().toString())
                .update();
        for (SaleRequest.TicketRequest ticket : sale.tickets()) {
            db.sql("insert into tickets (event_id, code, sale_key, type_code, buyer, phone, price_cents, sold_at)"
                            + " values (?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(sale.eventId(), ticket.code(), key, ticket.type(), ticket.buyer(),
                            ticket.phone() == null ? "" : ticket.phone(), ticket.priceCents(), ticket.soldAt())
                    .update();
        }
        return Result.ACCEPTED;
    }

    int ticketCount(String eventId) {
        return db.sql("select count(*) from tickets where event_id = ?").param(eventId).query(Integer.class).single();
    }
}
