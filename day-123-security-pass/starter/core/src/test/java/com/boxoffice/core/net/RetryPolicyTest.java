package com.boxoffice.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(5, Duration.ofMillis(500), Duration.ofSeconds(8));

    @Test
    void doublesTheWaitAfterEachFailure() {
        assertThat(policy.delayBefore(2, 1.0)).isEqualTo(Duration.ofMillis(500));
        assertThat(policy.delayBefore(3, 1.0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayBefore(4, 1.0)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayBefore(5, 1.0)).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void neverWaitsLongerThanTheLongestWait() {
        assertThat(policy.delayBefore(6, 1.0)).isEqualTo(Duration.ofSeconds(8));
        assertThat(policy.delayBefore(9, 1.0)).isEqualTo(Duration.ofSeconds(8));
        assertThat(policy.delayBefore(66, 1.0)).isEqualTo(Duration.ofSeconds(8));
        assertThat(policy.delayBefore(100, 1.0)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void jitterWaitsBetweenHalfAndAllOfTheBackoff() {
        assertThat(policy.delayBefore(4, 0.0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayBefore(4, 0.5)).isEqualTo(Duration.ofMillis(1_500));
    }

    @Test
    void needsAtLeastOneAttempt() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofMillis(500), Duration.ofSeconds(8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A retry policy needs at least one attempt");
    }
}
