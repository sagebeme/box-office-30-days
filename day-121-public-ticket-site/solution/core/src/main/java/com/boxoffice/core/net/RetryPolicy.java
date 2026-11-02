package com.boxoffice.core.net;

import java.time.Duration;

/**
 * How hard to try before giving up: at most {@code maxAttempts} tries, waiting
 * longer after each failure (500 ms, 1 s, 2 s, 4 s...), but never longer than
 * {@code maxDelay}.
 *
 * <p>Doubling the wait is called <em>exponential backoff</em>. It gives a
 * struggling server, or a Wi-Fi router that's restarting, room to recover,
 * instead of being hammered by every gate laptop at once.
 */
public record RetryPolicy(int maxAttempts, Duration firstDelay, Duration maxDelay) {

    /** Five tries over about 15 seconds: long enough for venue Wi-Fi to come back. */
    public static final RetryPolicy GATE_WIFI = new RetryPolicy(5, Duration.ofMillis(500), Duration.ofSeconds(8));

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("A retry policy needs at least one attempt");
        }
        if (firstDelay.isNegative() || maxDelay.compareTo(firstDelay) < 0) {
            throw new IllegalArgumentException("The longest wait can't be shorter than the first");
        }
    }

    /**
     * How long to wait before attempt number {@code attempt} (2, 3, ...).
     *
     * @param roll a random number from 0 to 1. It spreads the waits out
     *             (<em>jitter</em>), so 20 laptops that lost Wi-Fi together
     *             don't all come back in the same millisecond.
     * @return somewhere between half and all of the backoff for that attempt
     */
    public Duration delayBefore(int attempt, double roll) {
        int doublings = attempt - 2;
        long max = maxDelay.toMillis();
        // Past 2^30 the number would overflow; it's far beyond maxDelay anyway.
        long backoff = doublings >= 30 ? max : Math.min(max, firstDelay.toMillis() << doublings);
        return Duration.ofMillis(backoff / 2 + Math.round(roll * (backoff / 2.0)));
    }
}
