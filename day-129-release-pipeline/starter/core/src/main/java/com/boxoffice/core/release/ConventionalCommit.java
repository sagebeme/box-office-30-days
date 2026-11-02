package com.boxoffice.core.release;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One commit subject read the Conventional Commits way, the style this
 * repository's history uses:
 *
 * <pre>
 * feat: Day 125 asks Claude about tonight's sales
 * fix(desktop): the total no longer lags one sale behind
 * feat(sync)!: gates need a venue key
 * </pre>
 *
 * A subject that doesn't follow the style still counts, as type "other",
 * so a release never silently drops a commit.
 */
public record ConventionalCommit(String type, String scope, boolean breaking, String description) {

    // type, then (scope) if there is one, then ! if it breaks something, then ": " and the rest.
    private static final Pattern FORMAT = Pattern.compile("([A-Za-z]+)(?:\\(([^)\\s]+)\\))?(!)?: (.+)");

    public static ConventionalCommit parse(String subject) {
        // TODO Day 129, step 2: match the stripped subject against FORMAT.
        //   No match -> type "other", scope "", not breaking, the whole stripped subject as the description.
        //   A match: group(1) is the type (lower-case it, Locale.ROOT), group(2) the scope (null when
        //   there's none: use ""), group(3) is "!" when it breaks something (null otherwise), group(4)
        //   the description, stripped.
        throw new UnsupportedOperationException("Not written yet: Day 129, step 2");
    }

    public Bump bump() {
        // TODO Day 129, step 2: breaking -> MAJOR, whatever the type. Otherwise feat -> MINOR,
        //   fix or perf -> PATCH, anything else -> NONE.
        throw new UnsupportedOperationException("Not written yet: Day 129, step 2");
    }
}
