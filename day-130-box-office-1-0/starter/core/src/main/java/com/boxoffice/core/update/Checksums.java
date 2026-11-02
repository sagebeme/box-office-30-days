package com.boxoffice.core.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SHA-256 checksums: a 64-character fingerprint of a file's bytes. Change
 * one byte, and the fingerprint is completely different, so a download that
 * matches its published checksum is the file that was published.
 */
public final class Checksums {

    // What `sha256sum` writes: the checksum, two spaces (or a space and *), the file name.
    private static final Pattern LINE = Pattern.compile("([0-9a-fA-F]{64}) [ *](.+)");

    private Checksums() {
    }

    /** Reads a SHA256SUMS file into file name -> checksum, lower case. Lines it can't read are skipped. */
    public static Map<String, String> parse(String sums) {
        // TODO Day 130, step 3: for each line, stripped, that matches LINE: group(2) (stripped) is the
        //   file name, group(1) the checksum, lower-cased. Skip lines that don't match.
        throw new UnsupportedOperationException("Not written yet: Day 130, step 3");
    }

    /** The file's SHA-256, as 64 lower-case hex digits. Reads it in pieces, so a big installer needs little memory. */
    public static String sha256(Path file) {
        // TODO Day 130, step 2: MessageDigest.getInstance("SHA-256"), then read the file through
        //   Files.newInputStream in 64 KB pieces, handing each piece to digest.update(buffer, 0, read)
        //   until read() gives -1. Never Files.readAllBytes: an installer is 50 MB.
        //   HexFormat.of().formatHex(digest.digest()) writes the result as lower-case hex.
        //   An IOException becomes an UncheckedIOException.
        throw new UnsupportedOperationException("Not written yet: Day 130, step 2");
    }
}
