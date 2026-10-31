package com.boxoffice.core.update;

import java.util.Optional;

/** Where new versions are announced. GitHubReleases in real life, a fake in tests. */
public interface ReleaseFeed {

    /** The newest published release, or empty if there's none yet. */
    Optional<Release> latest();
}
