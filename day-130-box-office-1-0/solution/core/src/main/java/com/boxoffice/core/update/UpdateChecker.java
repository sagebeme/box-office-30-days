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
        Release release = latest.get();
        if (release.version().compareTo(current) <= 0) {
            return new UpdateCheck.UpToDate(current);
        }
        return release.installerFor(osName)
                .<UpdateCheck>map(installer -> new UpdateCheck.Available(current, release, installer))
                .orElseGet(() -> new UpdateCheck.NothingForThisSystem(current, release));
    }

    /**
     * True from two hours before the doors open until the event ends: no
     * updates then. A new version can wait a night; a gate that won't start can't.
     */
    public static boolean duringTheEvent(Event event, Instant now) {
        Instant from = event.startsAt().toInstant().minus(SET_UP_TIME);
        Instant to = event.endsAt().toInstant();
        return !now.isBefore(from) && now.isBefore(to);
    }
}
