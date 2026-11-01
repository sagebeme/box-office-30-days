package com.boxoffice.core.release;

import java.util.ArrayList;
import java.util.List;

/**
 * Release notes written from the commits, in Markdown for the GitHub
 * release page. Only what a user would notice goes in: chores, docs and
 * tests are left out.
 *
 * <pre>
 * ## Box Office 1.1.0
 *
 * ### Breaking changes
 * - sync: gates need a venue key
 *
 * ### New
 * - Day 125 asks Claude about tonight's sales
 *
 * ### Fixed
 * - desktop: the total no longer lags one sale behind
 * </pre>
 */
public final class ReleaseNotes {

    private ReleaseNotes() {
    }

    public static String render(SemVer version, List<ConventionalCommit> commits) {
        // TODO Day 129, step 4: sort the commits by their bump into three lists of lines, each line
        //   "- " + (the scope and ": ", if there is a scope) + the description:
        //   MAJOR -> breaking, MINOR -> new, PATCH -> fixed, NONE -> left out.
        //   Then "## Box Office " + version + "\n", and section(...) for "Breaking changes", "New"
        //   and "Fixed", in that order.
        throw new UnsupportedOperationException("Not written yet: Day 129, step 4");
    }

    private static void section(StringBuilder out, String heading, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        out.append("\n### ").append(heading).append('\n');
        lines.forEach(line -> out.append(line).append('\n'));
    }
}
