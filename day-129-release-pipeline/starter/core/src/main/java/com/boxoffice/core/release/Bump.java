package com.boxoffice.core.release;

/** How much a change moves the version, smallest first, so the biggest one in a release wins. */
public enum Bump {
    /** Chores, docs, tests: nothing a user notices. No new version. */
    NONE,
    /** A fix: 1.0.0 -> 1.0.1. */
    PATCH,
    /** Something new that breaks nothing: 1.0.1 -> 1.1.0. */
    MINOR,
    /** Something that breaks what worked before: 1.1.0 -> 2.0.0. */
    MAJOR
}
