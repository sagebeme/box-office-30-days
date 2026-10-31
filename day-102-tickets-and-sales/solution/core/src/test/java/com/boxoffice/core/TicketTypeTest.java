package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TicketTypeTest {

    @Test
    void keepsWhatItWasGiven() {
        TicketType vip = new TicketType("VIP", "VIP", 50);

        assertThat(vip.code()).isEqualTo("VIP");
        assertThat(vip.capacity()).isEqualTo(50);
    }

    @Test
    void twoTypesWithTheSameValuesAreEqual() {
        assertThat(new TicketType("VIP", "VIP", 50)).isEqualTo(new TicketType("VIP", "VIP", 50));
    }

    @Test
    void refusesABlankCode() {
        assertThatThrownBy(() -> new TicketType(" ", "VIP", 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A ticket type needs a code");
    }

    @Test
    void refusesANegativeCapacity() {
        assertThatThrownBy(() -> new TicketType("VIP", "VIP", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Capacity can't be negative: -1");
    }
}
