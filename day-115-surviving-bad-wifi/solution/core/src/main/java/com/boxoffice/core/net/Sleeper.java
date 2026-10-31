package com.boxoffice.core.net;

import java.time.Duration;

/**
 * Waits. The real one calls Thread.sleep; tests pass one that only writes the
 * wait down, so a test of "wait 8 seconds and try again" takes no time at all.
 */
@FunctionalInterface
public interface Sleeper {

    Sleeper REAL = Thread::sleep;

    void sleep(Duration duration) throws InterruptedException;
}
