package com.boxoffice.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final TestClock clock = new TestClock();
    // 6 a minute: a burst of 6, then one more every 10 seconds.
    private final RateLimiter limiter = new RateLimiter(6, Duration.ofMinutes(1), clock);

    @Test
    void aBurstUpToTheLimitIsFine() {
        for (int i = 0; i < 6; i++) {
            assertThat(limiter.tryTake("196.201.1.1").allowed()).isTrue();
        }
    }

    @Test
    void oneMoreThanTheLimitWaitsForTheNextToken() {
        for (int i = 0; i < 6; i++) {
            limiter.tryTake("196.201.1.1");
        }

        RateLimiter.Decision seventh = limiter.tryTake("196.201.1.1");

        assertThat(seventh.allowed()).isFalse();
        assertThat(seventh.retryAfterSeconds()).isEqualTo(10);
    }

    @Test
    void tokensComeBackEvenlyOverTime() {
        for (int i = 0; i < 6; i++) {
            limiter.tryTake("196.201.1.1");
        }
        clock.moveOn(Duration.ofSeconds(4));
        assertThat(limiter.tryTake("196.201.1.1").retryAfterSeconds()).isEqualTo(6);

        clock.moveOn(Duration.ofSeconds(6));
        assertThat(limiter.tryTake("196.201.1.1").allowed()).isTrue();
        assertThat(limiter.tryTake("196.201.1.1").allowed()).isFalse();
    }

    @Test
    void aBucketNeverHoldsMoreThanTheLimit() {
        clock.moveOn(Duration.ofHours(5));

        for (int i = 0; i < 6; i++) {
            assertThat(limiter.tryTake("196.201.1.1").allowed()).isTrue();
        }
        assertThat(limiter.tryTake("196.201.1.1").allowed()).isFalse();
    }

    @Test
    void eachKeyHasItsOwnBucket() {
        for (int i = 0; i < 6; i++) {
            limiter.tryTake("196.201.1.1");
        }

        assertThat(limiter.tryTake("41.90.2.2").allowed()).isTrue();
    }

    @Test
    void askingAgainWhileWaitingDoesntMakeTheWaitLonger() {
        for (int i = 0; i < 6; i++) {
            limiter.tryTake("196.201.1.1");
        }
        limiter.tryTake("196.201.1.1");
        limiter.tryTake("196.201.1.1");

        assertThat(limiter.tryTake("196.201.1.1").retryAfterSeconds()).isEqualTo(10);
    }
}
