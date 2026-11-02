package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SaleOutcomeTest {

    @Test
    void describesASale() {
        SaleOutcome sold = new SaleOutcome.Sold(new Ticket("VIP-0001", "VIP", "Wanjiku",
                Money.ofKes(5_000), Instant.parse("2026-11-14T16:00:00Z")));

        assertThat(sold.describe()).isEqualTo("Sold VIP-0001 to Wanjiku for KES 5,000");
    }

    @Test
    void describesASoldOutType() {
        assertThat(new SaleOutcome.SoldOut("VIP").describe()).isEqualTo("VIP is sold out");
    }

    @Test
    void describesARejectionWithItsReason() {
        assertThat(new SaleOutcome.Rejected("No ticket type called VVIP").describe())
                .isEqualTo("Rejected: No ticket type called VVIP");
    }
}
