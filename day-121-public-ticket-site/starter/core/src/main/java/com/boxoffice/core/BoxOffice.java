package com.boxoffice.core;

/** The first piece of the gate app: the line the screen shows when the gate opens. */
public final class BoxOffice {

    public static final String NAME = "Box Office";

    private BoxOffice() {
    }

    /** "Box Office is open for Kilele Nights". */
    public static String welcome(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("An event needs a name");
        }
        return NAME + " is open for " + eventName.strip();
    }
}
