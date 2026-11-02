package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TicketTypeTest {

    private static final Money FIVE_THOUSAND = Money.ofKes(5_000);

    @Test
    void keepsWhatItWasGiven() {
        TicketType vip = new TicketType("VIP", "VIP", FIVE_THOUSAND, 50);

        assertThat(vip.code()).isEqualTo("VIP");
        assertThat(vip.price()).isEqualTo(FIVE_THOUSAND);
        assertThat(vip.capacity()).isEqualTo(50);
    }

    @Test
    void twoTypesWithTheSameValuesAreEqual() {
        assertThat(new TicketType("VIP", "VIP", FIVE_THOUSAND, 50))
                .isEqualTo(new TicketType("VIP", "VIP", FIVE_THOUSAND, 50));
    }

    @Test
    void refusesABlankCode() {
        assertThatThrownBy(() -> new TicketType(" ", "VIP", FIVE_THOUSAND, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A ticket type needs a code");
    }

    @Test
    void refusesANegativeCapacity() {
        assertThatThrownBy(() -> new TicketType("VIP", "VIP", FIVE_THOUSAND, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Capacity can't be negative: -1");
    }
}
