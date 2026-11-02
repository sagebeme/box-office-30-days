package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void keepsShillingsAsCents() {
        assertThat(Money.ofKes(1_500).cents()).isEqualTo(150_000);
    }

    @Test
    void addsTwoAmounts() {
        assertThat(Money.ofKes(1_500).plus(Money.ofKes(5_000))).isEqualTo(Money.ofKes(6_500));
    }

    @Test
    void tenCentsThreeTimesIsExactlyThirtyCents() {
        // In a double, 0.1 + 0.1 + 0.1 is 0.30000000000000004.
        Money tenCents = Money.ofCents(10);

        assertThat(tenCents.plus(tenCents).plus(tenCents)).isEqualTo(Money.ofCents(30));
    }

    @Test
    void multipliesByACount() {
        assertThat(Money.ofKes(1_500).times(3)).isEqualTo(Money.ofKes(4_500));
        assertThat(Money.ofKes(1_500).times(0)).isEqualTo(Money.ZERO);
    }

    @Test
    void formatsWholeShillingsWithCommas() {
        assertThat(Money.ofKes(1_500).format()).isEqualTo("KES 1,500");
        assertThat(Money.ofKes(1_234_567).format()).isEqualTo("KES 1,234,567");
        assertThat(Money.ZERO.format()).isEqualTo("KES 0");
    }

    @Test
    void showsCentsOnlyWhenThereAreSome() {
        assertThat(Money.ofCents(150_050).format()).isEqualTo("KES 1,500.50");
        assertThat(Money.ofCents(5).format()).isEqualTo("KES 0.05");
    }

    @Test
    void refusesANegativeAmount() {
        assertThatThrownBy(() -> Money.ofCents(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Money can't be negative: -1 cents");
    }

    @Test
    void refusesANegativeCount() {
        assertThatThrownBy(() -> Money.ofKes(1).times(-2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
