package com.boxoffice.core.release;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConventionalCommitTest {

    @Test
    void readsTheTypeAndTheDescription() {
        assertThat(ConventionalCommit.parse("feat: Day 125 asks Claude about tonight's sales"))
                .isEqualTo(new ConventionalCommit("feat", "", false, "Day 125 asks Claude about tonight's sales"));
    }

    @Test
    void readsAScope() {
        assertThat(ConventionalCommit.parse("fix(desktop): the total lagged one sale behind"))
                .isEqualTo(new ConventionalCommit("fix", "desktop", false, "the total lagged one sale behind"));
    }

    @Test
    void anExclamationMarkMeansItBreaksSomething() {
        ConventionalCommit commit = ConventionalCommit.parse("feat(sync)!: gates need a venue key");

        assertThat(commit.breaking()).isTrue();
        assertThat(commit.bump()).isEqualTo(Bump.MAJOR);
    }

    @Test
    void theTypeDecidesTheBump() {
        assertThat(ConventionalCommit.parse("feat: x").bump()).isEqualTo(Bump.MINOR);
        assertThat(ConventionalCommit.parse("fix: x").bump()).isEqualTo(Bump.PATCH);
        assertThat(ConventionalCommit.parse("perf: x").bump()).isEqualTo(Bump.PATCH);
        assertThat(ConventionalCommit.parse("Feat: x").bump()).isEqualTo(Bump.MINOR);
        for (String type : new String[] {"chore", "docs", "test", "refactor", "ci", "build", "style"}) {
            assertThat(ConventionalCommit.parse(type + ": x").bump()).as(type).isEqualTo(Bump.NONE);
        }
    }

    @Test
    void anythingElseIsKeptAsOther() {
        assertThat(ConventionalCommit.parse("Merge pull request #1 from sagebeme/feat/days-111-120"))
                .isEqualTo(new ConventionalCommit("other", "", false,
                        "Merge pull request #1 from sagebeme/feat/days-111-120"));
        assertThat(ConventionalCommit.parse("feat:no space after the colon").type()).isEqualTo("other");
        assertThat(ConventionalCommit.parse("fixed the thing").bump()).isEqualTo(Bump.NONE);
    }
}
