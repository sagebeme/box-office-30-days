package com.boxoffice.server.mpesa;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the test moves on by hand: "an hour later". */
final class MovableClock extends Clock {

    private volatile Instant now;

    MovableClock(Instant start) {
        now = start;
    }

    void moveOn(Duration by) {
        now = now.plus(by);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
