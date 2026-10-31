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
        // TODO: a new Money holding both amounts of cents added together.
        //       Math.addExact(a, b) adds and throws if the total is too big for a long.
        throw new UnsupportedOperationException("Not written yet: Day 103, step 3");
    }

    public Money times(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Can't multiply money by a negative count: " + count);
        }
        // TODO: a new Money holding cents times count (Math.multiplyExact, like ofKes above)
        throw new UnsupportedOperationException("Not written yet: Day 103, step 3");
    }

    /** "KES 1,500", or "KES 1,500.50" when there are cents. */
    public String format() {
        // TODO: shillings are cents / 100, the cents left over are cents % 100.
        // TODO: format the shillings with commas: String.format(Locale.ROOT, "%,d", shillings)
        // TODO: no cents left over -> "KES 1,500"; otherwise -> "KES 1,500.50" (always two digits: %02d)
        throw new UnsupportedOperationException("Not written yet: Day 103, step 4");
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }
}
