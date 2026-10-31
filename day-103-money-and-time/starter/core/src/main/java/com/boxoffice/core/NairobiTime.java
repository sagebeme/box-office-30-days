package com.boxoffice.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The gate keeps moments as Instants (one point in time, no time zone) and
 * shows them in Nairobi time, whatever time zone the laptop is set to.
 */
public final class NairobiTime {

    public static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");

    // Locale.ENGLISH, so a laptop set to French still prints "Sat", not "sam.".
    private static final DateTimeFormatter LABEL =
            DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    private NairobiTime() {
    }

    /** A date and time on a Nairobi clock. */
    public static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE);
    }

    /** "Sat 14 Nov, 21:05". */
    public static String format(Instant instant) {
        // TODO: put the instant on a Nairobi clock (instant.atZone(ZONE)), then format it with LABEL
        throw new UnsupportedOperationException("Not written yet: Day 103, step 5");
    }
}
