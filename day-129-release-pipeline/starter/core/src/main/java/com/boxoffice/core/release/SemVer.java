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
        // TODO Day 129, step 3: a switch over the bump. NONE keeps this version; PATCH adds one to patch;
        //   MINOR adds one to minor and sets patch to 0; MAJOR adds one to major and sets the rest to 0.
        throw new UnsupportedOperationException("Not written yet: Day 129, step 3");
    }

    /** The version after these commits: the biggest bump among them wins. */
    public SemVer next(List<ConventionalCommit> commits) {
        // TODO Day 129, step 3: find the biggest bump among the commits (Bump is an enum listed
        //   smallest first, so compareTo says which is bigger), starting from NONE, and bump by it.
        throw new UnsupportedOperationException("Not written yet: Day 129, step 3");
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
