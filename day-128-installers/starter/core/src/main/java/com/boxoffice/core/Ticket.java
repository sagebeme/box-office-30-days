package com.boxoffice.core;

import java.time.Instant;

/**
 * A ticket the gate has issued. The code is what gets printed and scanned.
 *
 * @param phone the buyer's number as 2547XXXXXXXX, or "" when they didn't give one
 */
public record Ticket(String code, String typeCode, String buyer, String phone, Money price, Instant soldAt) {

    /** A ticket with no phone number. Everything written before Day 108 makes tickets this way. */
    public Ticket(String code, String typeCode, String buyer, Money price, Instant soldAt) {
        this(code, typeCode, buyer, "", price, soldAt);
    }
}
