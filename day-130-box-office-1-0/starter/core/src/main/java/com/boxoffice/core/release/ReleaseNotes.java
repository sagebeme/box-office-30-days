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
        List<String> breaking = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> fixed = new ArrayList<>();
        for (ConventionalCommit commit : commits) {
            String line = "- " + (commit.scope().isEmpty() ? "" : commit.scope() + ": ") + commit.description();
            switch (commit.bump()) {
                case MAJOR -> breaking.add(line);
                case MINOR -> added.add(line);
                case PATCH -> fixed.add(line);
                case NONE -> { }
            }
        }
        StringBuilder out = new StringBuilder("## Box Office ").append(version).append('\n');
        section(out, "Breaking changes", breaking);
        section(out, "New", added);
        section(out, "Fixed", fixed);
        return out.toString();
    }

    private static void section(StringBuilder out, String heading, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        out.append("\n### ").append(heading).append('\n');
        lines.forEach(line -> out.append(line).append('\n'));
    }
}
