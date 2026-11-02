package com.boxoffice.core;

import java.util.Locale;

/**
 * An amount of Kenyan shillings, kept as a whole number of cents.
 * Never a double: 0.1 + 0.2 is not 0.3 in a double, and a till that's off
 * by a cent every few hundred sales is a till nobody trusts.
 */
public record Money(long cents) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    public Money {
        if (cents < 0) {
            throw new IllegalArgumentException("Money can't be negative: " + cents + " cents");
        }
    }

    public static Money ofKes(long shillings) {
        return new Money(Math.multiplyExact(shillings, 100));
    }

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    public Money times(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Can't multiply money by a negative count: " + count);
        }
        return new Money(Math.multiplyExact(cents, count));
    }

    /** "KES 1,500", or "KES 1,500.50" when there are cents. */
    public String format() {
        // Locale.ROOT, so the comma is a comma on every laptop, whatever its language.
        String shillings = String.format(Locale.ROOT, "%,d", cents / 100);
        long rest = cents % 100;
        return rest == 0 ? "KES " + shillings : "KES %s.%02d".formatted(shillings, rest);
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }
}
