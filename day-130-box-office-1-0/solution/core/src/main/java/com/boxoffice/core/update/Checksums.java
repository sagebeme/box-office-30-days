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
        Map<String, String> checksums = new HashMap<>();
        for (String line : sums.lines().toList()) {
            Matcher match = LINE.matcher(line.strip());
            if (match.matches()) {
                checksums.put(match.group(2).strip(), match.group(1).toLowerCase());
            }
        }
        return checksums;
    }

    /** The file's SHA-256, as 64 lower-case hex digits. Reads it in pieces, so a big installer needs little memory. */
    public static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java has SHA-256", e);
        }
    }
}
