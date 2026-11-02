package com.boxoffice.server;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Day 124: the "payments" part of /actuator/health. Always UP (a flagged order
 * is a job for a person, not a reason to stop taking orders), with the numbers
 * whoever watches the server wants at a glance.
 */
@Component
class PaymentsHealthIndicator implements HealthIndicator {

    private final JdbcClient db;
    private final MpesaSettings mpesa;

    PaymentsHealthIndicator(JdbcClient db, MpesaSettings mpesa) {
        this.db = db;
        this.mpesa = mpesa;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("mode", mpesa.mode())
                .withDetail("awaitingPayment", count("AWAITING_PAYMENT"))
                .withDetail("flaggedOrders", count("FLAGGED"))
                .build();
    }

    private int count(String status) {
        return db.sql("select count(*) from orders where status = ?").param(status).query(Integer.class).single();
    }
}
