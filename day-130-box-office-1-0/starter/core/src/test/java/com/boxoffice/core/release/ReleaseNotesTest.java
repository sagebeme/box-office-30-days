package com.boxoffice.core.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseNotesTest {

    private static List<ConventionalCommit> commits(String... subjects) {
        return List.of(subjects).stream().map(ConventionalCommit::parse).toList();
    }

    @Test
    void groupsWhatUsersNotice() {
        String notes = ReleaseNotes.render(SemVer.parse("2.0.0"), commits(
                "feat: Day 125 asks Claude about tonight's sales",
                "chore: add .gitattributes",
                "fix(desktop): the total lagged one sale behind",
                "feat(sync)!: gates need a venue key",
                "docs: Day 125 README",
                "feat: Day 126 looks things up"));

        assertThat(notes).isEqualTo("""
                ## Box Office 2.0.0

                ### Breaking changes
                - sync: gates need a venue key

                ### New
                - Day 125 asks Claude about tonight's sales
                - Day 126 looks things up

                ### Fixed
                - desktop: the total lagged one sale behind
                """);
    }

    @Test
    void emptySectionsAreLeftOut() {
        assertThat(ReleaseNotes.render(SemVer.parse("1.0.1"), commits("fix: receipts print the right date", "test: more")))
                .isEqualTo("""
                        ## Box Office 1.0.1

                        ### Fixed
                        - receipts print the right date
                        """);
    }
}
