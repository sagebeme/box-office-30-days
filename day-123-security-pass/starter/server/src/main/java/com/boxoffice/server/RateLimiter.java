package com.boxoffice.server;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "At most N in this much time", per key (an IP address, a phone number).
 *
 * <p>A <b>token bucket</b>: each key has a bucket holding up to N tokens. Every
 * request takes one. The bucket refills a token at a time, evenly, so N per
 * minute refills one token every 60/N seconds. A burst of N is fine; a steady
 * stream faster than that runs the bucket dry and gets "wait".
 */
class RateLimiter {

    /** Allowed, or not, and how long until the next token if not. */
    record Decision(boolean allowed, Duration retryAfter) {

        /** Whole seconds, rounded up, for a Retry-After header: never 0 when the answer is "wait". */
        long retryAfterSeconds() {
            return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
        }
    }

    private record Bucket(double tokens, Instant at) {
    }

    /** Past this many keys, forget the ones whose buckets have refilled: they'd act the same if new. */
    private static final int MOST_KEYS = 10_000;

    private final int perPeriod;
    private final Duration oneToken;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** @param perPeriod how many requests a key may make in each period, all at once if it likes */
    RateLimiter(int perPeriod, Duration period, Clock clock) {
        if (perPeriod < 1) {
            throw new IllegalArgumentException("A limit needs at least 1 per period");
        }
        this.perPeriod = perPeriod;
        this.oneToken = period.dividedBy(perPeriod);
        this.clock = clock;
    }

    Decision tryTake(String key) {
        Instant now = clock.instant();
        if (buckets.size() > MOST_KEYS) {
            buckets.values().removeIf(bucket -> refilled(bucket, now) >= perPeriod);
        }
        Decision[] decision = new Decision[1];
        // compute() runs one key at a time: two requests from one phone can't both take the last token.
        buckets.compute(key, (k, bucket) -> {
            // TODO Day 123, step 2: take a token from this key's bucket, if there is one.
            //   - tokens: perPeriod for a new key (bucket is null), otherwise refilled(bucket, now)
            //   - at least 1 token: decision[0] = allowed with Duration.ZERO, return new Bucket(tokens - 1, now)
            //   - less than 1: wait for the rest of a token, (1 - tokens) * oneToken, rounded up in nanoseconds
            //     (Math.ceil). decision[0] = not allowed with that wait, return new Bucket(tokens, now)
            throw new UnsupportedOperationException("Not written yet: Day 123, step 2");
        });
        return decision[0];
    }

    /** The bucket's tokens now: what it had, plus one for every oneToken since, up to full. */
    private double refilled(Bucket bucket, Instant now) {
        double earned = (double) Duration.between(bucket.at(), now).toNanos() / oneToken.toNanos();
        return Math.min(perPeriod, bucket.tokens() + earned);
    }
}
