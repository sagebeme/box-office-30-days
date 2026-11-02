package com.boxoffice.server;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The server's rate limits, set in application.properties under boxoffice.limits. */
@Component
class Limits {

    private final RateLimiter orders;
    private final RateLimiter admin;
    private final RateLimiter prompts;
    private final int trustedProxies;

    Limits(@Value("${boxoffice.limits.orders-per-minute}") int ordersPerMinute,
           @Value("${boxoffice.limits.admin-per-minute}") int adminPerMinute,
           @Value("${boxoffice.limits.prompts-per-phone}") int promptsPerPhone,
           @Value("${boxoffice.trusted-proxies}") int trustedProxies,
           Clock clock) {
        orders = new RateLimiter(ordersPerMinute, Duration.ofMinutes(1), clock);
        admin = new RateLimiter(adminPerMinute, Duration.ofMinutes(1), clock);
        // Per phone, not per IP: a stranger mustn't be able to make someone's phone ring with prompts all night.
        prompts = new RateLimiter(promptsPerPhone, Duration.ofMinutes(10), clock);
        this.trustedProxies = trustedProxies;
    }

    /** Orders placed on the site, per caller's IP address. Each one holds places for 10 minutes. */
    RateLimiter orders() {
        return orders;
    }

    /** Tries at the admin key, per IP address. */
    RateLimiter admin() {
        return admin;
    }

    /** M-Pesa prompts, per phone number. */
    RateLimiter prompts() {
        return prompts;
    }

    int trustedProxies() {
        return trustedProxies;
    }
}
