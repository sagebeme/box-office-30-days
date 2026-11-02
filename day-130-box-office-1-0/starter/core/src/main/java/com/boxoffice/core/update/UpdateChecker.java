package com.boxoffice.core.update;

import com.boxoffice.core.Event;
import com.boxoffice.core.release.SemVer;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Asks whether a newer Box Office is out. It only looks: installing is the
 * person's choice, and never while the gates are busy.
 */
public final class UpdateChecker {

    /** Doors open about now: the laptop has to be ready, not halfway through an update. */
    public static final Duration SET_UP_TIME = Duration.ofHours(2);

    private final ReleaseFeed feed;
    private final SemVer current;
    private final String osName;

    public UpdateChecker(ReleaseFeed feed, SemVer current, String osName) {
        this.feed = feed;
        this.current = current;
        this.osName = osName;
    }

    public UpdateCheck check() {
        Optional<Release> latest = feed.latest();
        if (latest.isEmpty()) {
            return new UpdateCheck.NoReleases(current);
        }
        // TODO Day 130, step 4: compare versions with compareTo (as numbers, Day 129: 1.10.0 is newer than 1.9.0).
        //   Not newer than current -> UpToDate. Newer -> Available with release.installerFor(osName),
        //   or NothingForThisSystem when there's no installer for this system.
        throw new UnsupportedOperationException("Not written yet: Day 130, step 4");
    }

    /**
     * True from two hours before the doors open until the event ends: no
     * updates then. A new version can wait a night; a gate that won't start can't.
     */
    public static boolean duringTheEvent(Event event, Instant now) {
        // TODO Day 130, step 5: true from SET_UP_TIME before the event starts (that moment included)
        //   until it ends (that moment not included).
        throw new UnsupportedOperationException("Not written yet: Day 130, step 5");
    }
}
