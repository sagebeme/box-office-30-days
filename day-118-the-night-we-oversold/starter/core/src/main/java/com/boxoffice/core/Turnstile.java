package com.boxoffice.core;

import java.util.concurrent.atomic.LongAdder;

/**
 * Counts the people let in through this laptop since the app opened. Several
 * scanners may count at the same moment, each on its own thread.
 *
 * <p>{@code count++} on a plain int is three steps: read, add one, write back.
 * Two threads can both read 41 and both write 42, and a person goes missing.
 * A LongAdder does the adding as one step that can't be split, and stays fast
 * however many threads add at once.
 */
public final class Turnstile {

    // TODO: swap this int for a LongAdder: private final LongAdder inside = new LongAdder();
    private int inside;

    public void admit() {
        // TODO: inside.increment()
        inside++;
    }

    public long count() {
        // TODO: inside.sum()
        return inside;
    }
}
