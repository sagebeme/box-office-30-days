package com.boxoffice.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** The first piece of the gate app: the line the screen shows when the gate opens. */
public final class BoxOffice {

    public static final String NAME = "Box Office";

    // Day 130: read once, from the file Maven fills in at build time.
    private static final String VERSION = readVersion();

    private BoxOffice() {
    }

    /** "Box Office is open for Kilele Nights". */
    public static String welcome(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("An event needs a name");
        }
        return NAME + " is open for " + eventName.strip();
    }

    /** This build's version, the one in pom.xml: "1.0.0". The update check compares against it. */
    public static String version() {
        return VERSION;
    }

    private static String readVersion() {
        try (InputStream in = BoxOffice.class.getResourceAsStream("version.properties")) {
            if (in == null) {
                throw new IllegalStateException("version.properties is missing: build with Maven");
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
