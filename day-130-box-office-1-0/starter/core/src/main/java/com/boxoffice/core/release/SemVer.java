package com.boxoffice.core.release;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A version number the Semantic Versioning way: MAJOR.MINOR.PATCH. */
public record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {

    private static final Pattern FORMAT = Pattern.compile("v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)");

    private static final Comparator<SemVer> ORDER = Comparator.comparingInt(SemVer::major)
            .thenComparingInt(SemVer::minor)
            .thenComparingInt(SemVer::patch);

    public SemVer {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("A version has no negative parts");
        }
    }

    /** "1.2.3" or "v1.2.3", the way a Git tag writes it. */
    public static SemVer parse(String text) {
        Matcher match = FORMAT.matcher(text.strip());
        if (!match.matches()) {
            throw new IllegalArgumentException("Not a version like 1.2.3: " + text);
        }
        return new SemVer(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                Integer.parseInt(match.group(3)));
    }

    /** The next version for a change this big. A bump resets the smaller parts: 1.4.2 + MINOR = 1.5.0. */
    public SemVer bump(Bump bump) {
        return switch (bump) {
            case NONE -> this;
            case PATCH -> new SemVer(major, minor, patch + 1);
            case MINOR -> new SemVer(major, minor + 1, 0);
            case MAJOR -> new SemVer(major + 1, 0, 0);
        };
    }

    /** The version after these commits: the biggest bump among them wins. */
    public SemVer next(List<ConventionalCommit> commits) {
        Bump biggest = Bump.NONE;
        for (ConventionalCommit commit : commits) {
            if (commit.bump().compareTo(biggest) > 0) {
                biggest = commit.bump();
            }
        }
        return bump(biggest);
    }

    @Override
    public int compareTo(SemVer other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
