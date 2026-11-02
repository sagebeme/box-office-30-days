package com.boxoffice.server;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The server's night watch, once a minute: gives back the places of orders
 * nobody paid for, and keeps shouting about paid orders that need a person.
 */
@Component
class Reconciler {

    private static final Logger log = LoggerFactory.getLogger(Reconciler.class);

    private final Payments payments;
    private final Clock clock;

    Reconciler(Payments payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    void everyMinute() {
        int expired = payments.expireOrders(clock.instant());
        if (expired > 0) {
            log.info("{} unpaid order(s) ran out; their places are back on sale", expired);
        }
        List<String> flagged = payments.flagged();
        if (!flagged.isEmpty()) {
            log.error("{} paid order(s) need a person: {}", flagged.size(), flagged);
        }
    }
}
