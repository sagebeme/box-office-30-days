package com.boxoffice.server;

import java.security.SecureRandom;

/**
 * Order references like "K7MP-Q3XW". Random, not counted up: with W-0001,
 * W-0002... anyone could read the next fan's order page by adding one.
 */
final class References {

    // No 0/O or 1/I/L: a fan reading one out over the phone can't mix them up.
    private static final String LETTERS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private References() {
    }

    /** Eight random characters (about 40 bits), split in two for reading aloud. */
    static String next() {
        StringBuilder reference = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                reference.append('-');
            }
            reference.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        return reference.toString();
    }
}
