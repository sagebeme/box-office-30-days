package com.boxoffice.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Device tokens: long random secrets, kept on the server only as a hash. */
final class Tokens {

    // SecureRandom, not Random: Random's numbers can be guessed from a few earlier ones.
    private static final SecureRandom RANDOM = new SecureRandom();

    private Tokens() {
    }

    /** 32 random bytes, as 43 characters that are safe in a header. */
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * The SHA-256 hash of the token, as 64 hex characters. The same token
     * always gives the same hash, but the hash can't be turned back into the
     * token. A plain hash is enough here (no salt, no bcrypt) because the token
     * is 32 random bytes: nobody can guess it, unlike a person's password.
     */
    static String hash(String token) {
        // TODO: MessageDigest.getInstance("SHA-256") gives a hasher. Its digest(bytes) takes
        //       token.getBytes(StandardCharsets.UTF_8) and returns 32 bytes.
        // TODO: return those bytes as 64 hex characters: HexFormat.of().formatHex(digest)
        // getInstance throws NoSuchAlgorithmException, which never happens for SHA-256: catch it and
        // throw new IllegalStateException("Every Java has SHA-256", e)
        throw new UnsupportedOperationException("Not written yet: Day 119, step 3");
    }

    /** Compares two secrets in the same time whatever they hold, so timing gives nothing away. */
    static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
