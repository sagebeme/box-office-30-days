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
        String line = subject.strip();
        Matcher match = FORMAT.matcher(line);
        if (!match.matches()) {
            return new ConventionalCommit("other", "", false, line);
        }
        return new ConventionalCommit(match.group(1).toLowerCase(Locale.ROOT),
                match.group(2) == null ? "" : match.group(2), match.group(3) != null, match.group(4).strip());
    }

    public Bump bump() {
        if (breaking) {
            return Bump.MAJOR;
        }
        return switch (type) {
            case "feat" -> Bump.MINOR;
            case "fix", "perf" -> Bump.PATCH;
            default -> Bump.NONE;
        };
    }
}
