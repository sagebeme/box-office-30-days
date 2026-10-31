package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class NairobiTimeTest {

    @Test
    void showsAMomentOnANairobiClock() {
        // 18:05 in London (UTC) is 21:05 in Nairobi, three hours ahead.
        assertThat(NairobiTime.format(Instant.parse("2026-11-14T18:05:00Z")))
                .isEqualTo("Sat 14 Nov, 21:05");
    }

    @Test
    void crossesMidnightIntoTheNextDay() {
        assertThat(NairobiTime.format(Instant.parse("2026-11-14T22:30:00Z")))
                .isEqualTo("Sun 15 Nov, 01:30");
    }

    @Test
    void givesTheSameAnswerWhateverTheLaptopsTimeZone() {
        TimeZone before = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

            assertThat(NairobiTime.format(Instant.parse("2026-11-14T18:05:00Z")))
                    .isEqualTo("Sat 14 Nov, 21:05");
        } finally {
            TimeZone.setDefault(before);
        }
    }

    @Test
    void makesANairobiDateAndTime() {
        assertThat(NairobiTime.at(2026, 11, 14, 18, 0).toInstant())
                .isEqualTo(Instant.parse("2026-11-14T15:00:00Z"));
    }
}
