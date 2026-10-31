package com.boxoffice.core;

import java.util.Optional;

/**
 * Kenyan mobile numbers, the way M-Pesa and SMS gateways want them:
 * 12 digits, starting 2547 or 2541, no plus sign.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /**
     * "0712 345 678", "+254 712-345-678" and "254712345678" all become
     * "254712345678". Anything that isn't a Kenyan mobile number is empty.
     */
    public static Optional<String> normalise(String typed) {
        // TODO: null -> Optional.empty()
        // TODO: remove the spaces and dashes
        // TODO: take off the front: "+254", or "254", or "0". Anything else -> Optional.empty()
        // TODO: what's left must be 9 digits starting with 7 or 1: rest.matches("[71]\\d{8}")
        //       If not -> Optional.empty(). If so -> Optional.of("254" + rest)
        throw new UnsupportedOperationException("Not written yet: Day 108, step 3");
    }
}
