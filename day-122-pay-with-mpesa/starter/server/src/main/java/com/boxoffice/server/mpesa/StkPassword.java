package com.boxoffice.server.mpesa;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * The Timestamp and Password every STK push carries.
 *
 * <p>The password is base64(shortcode + passkey + timestamp). It contains the
 * passkey, so it's as secret as the passkey: never log it, never show it.
 */
public final class StkPassword {

    /** Daraja reads the timestamp on a Nairobi clock (EAT, UTC+3), whatever clock the server runs on. */
    static final ZoneOffset EAT = ZoneOffset.ofHours(3);

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private StkPassword() {
    }

    /** 15:30:05 UTC on 14 November 2026 is "20261114183005". */
    public static String timestamp(Instant now) {
        // TODO Day 122, step 2: format now on a Nairobi clock: STAMP.format(now.atOffset(EAT)).
        throw new UnsupportedOperationException("Not written yet: Day 122, step 2");
    }

    /** base64 of shortcode, passkey and timestamp, run together. Use the same timestamp as the request. */
    public static String password(String shortcode, String passkey, String timestamp) {
        // TODO Day 122, step 2: run the three together, turn them into UTF-8 bytes, and
        // base64 them with Base64.getEncoder().encodeToString(...).
        throw new UnsupportedOperationException("Not written yet: Day 122, step 2");
    }
}
