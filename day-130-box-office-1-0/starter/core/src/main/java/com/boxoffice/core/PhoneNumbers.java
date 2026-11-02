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
        if (typed == null) {
            return Optional.empty();
        }
        String digits = typed.replace(" ", "").replace("-", "");
        String rest;
        if (digits.startsWith("+254")) {
            rest = digits.substring(4);
        } else if (digits.startsWith("254")) {
            rest = digits.substring(3);
        } else if (digits.startsWith("0")) {
            rest = digits.substring(1);
        } else {
            return Optional.empty();
        }
        // What's left is the 9-digit number: 7XXXXXXXX (Safaricom, Airtel...) or 1XXXXXXXX (newer ranges).
        if (!rest.matches("[71]\\d{8}")) {
            return Optional.empty();
        }
        return Optional.of("254" + rest);
    }
}
