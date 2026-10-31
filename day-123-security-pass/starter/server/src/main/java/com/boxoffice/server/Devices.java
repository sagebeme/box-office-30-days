package com.boxoffice.server;

import java.time.Clock;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The gate laptops allowed to sync, in the devices table. */
@Repository
class Devices {

    private final JdbcClient db;
    private final Clock clock;

    Devices(JdbcClient db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /**
     * Registers the gate with a new token. A gate registered again (a new
     * laptop, or a stolen one) gets a new token, and the old one stops working.
     *
     * @return the token, shown this once and never kept
     */
    String register(String gate) {
        String token = Tokens.newToken();
        db.sql("merge into devices (gate, token_hash, created_at) key (gate) values (?, ?, ?)")
                .params(gate, Tokens.hash(token), clock.instant().toString())
                .update();
        return token;
    }

    /** Which gate this token belongs to, if any. */
    Optional<String> gateFor(String token) {
        return db.sql("select gate from devices where token_hash = ?")
                .param(Tokens.hash(token))
                .query(String.class)
                .optional();
    }
}
