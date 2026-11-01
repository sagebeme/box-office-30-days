package com.boxoffice.core.update;

import com.boxoffice.core.release.SemVer;

/** What the update check found. */
public sealed interface UpdateCheck {

    /** Nothing has been released yet. */
    record NoReleases(SemVer current) implements UpdateCheck {
    }

    /** This is the newest version (or newer: a build of your own). */
    record UpToDate(SemVer current) implements UpdateCheck {
    }

    /** A newer version is out, with an installer for this system. */
    record Available(SemVer current, Release release, Asset installer) implements UpdateCheck {
    }

    /** A newer version is out, but nobody built it for this system. */
    record NothingForThisSystem(SemVer current, Release release) implements UpdateCheck {
    }
}
