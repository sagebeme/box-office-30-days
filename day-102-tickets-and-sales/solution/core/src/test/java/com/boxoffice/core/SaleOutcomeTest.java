package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SaleOutcomeTest {

    @Test
    void describesASale() {
        SaleOutcome sold = new SaleOutcome.Sold(new Ticket("VIP-0001", "VIP", "Wanjiku"));

        assertThat(sold.describe()).isEqualTo("Sold VIP-0001 to Wanjiku");
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
