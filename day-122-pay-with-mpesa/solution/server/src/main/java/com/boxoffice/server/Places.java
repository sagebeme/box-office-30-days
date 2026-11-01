package com.boxoffice.server;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * How many places of a ticket type are still free. A place is taken by a
 * ticket (sold at a gate or paid for online) or held by an order whose buyer
 * is still paying. The gates' sync (Day 120) and the site count the same way,
 * so they can never hand out the same place twice.
 */
@Repository
class Places {

    private final JdbcClient db;

    Places(JdbcClient db) {
        this.db = db;
    }

    /**
     * The type's capacity, and a lock on its row until the transaction ends
     * (Day 120). Call it first in a @Transactional method that's about to take
     * places. Empty if the event doesn't sell this type.
     */
    Optional<Integer> lockCapacity(String eventId, String type) {
        return db.sql("select capacity from ticket_types where event_id = ? and code = ? for update")
                .params(eventId, type)
                .query(Integer.class)
                .optional();
    }

    /** Places taken: tickets sold, plus places held by orders awaiting payment or flagged for a person. */
    int taken(String eventId, String type) {
        int sold = db.sql("select count(*) from tickets where event_id = ? and type_code = ?")
                .params(eventId, type)
                .query(Integer.class)
                .single();
        int held = db.sql("select coalesce(sum(quantity), 0) from orders"
                        + " where event_id = ? and type_code = ? and status in ('AWAITING_PAYMENT', 'FLAGGED')")
                .params(eventId, type)
                .query(Integer.class)
                .single();
        return sold + held;
    }

    /** Places nobody has: capacity minus taken, and never below 0. */
    int left(int capacity, int taken) {
        // Gates that were offline can oversell (Day 120), so taken can be more than capacity.
        return Math.max(0, capacity - taken);
    }
}
