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
        return STAMP.format(now.atOffset(EAT));
    }

    /** base64 of shortcode, passkey and timestamp, run together. Use the same timestamp as the request. */
    public static String password(String shortcode, String passkey, String timestamp) {
        return Base64.getEncoder().encodeToString((shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));
    }
}
