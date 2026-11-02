package com.boxoffice.core.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SemVerTest {

    @Test
    void readsATagOrAPlainVersion() {
        assertThat(SemVer.parse("v1.2.3")).isEqualTo(new SemVer(1, 2, 3));
        assertThat(SemVer.parse("1.2.3")).isEqualTo(new SemVer(1, 2, 3));
        assertThat(SemVer.parse("  10.0.12 ").toString()).isEqualTo("10.0.12");
    }

    @Test
    void refusesWhatIsntAVersion() {
        for (String bad : List.of("1.2", "1.2.3.4", "v1.02.3", "one.two.three", "1.2.3-beta", "")) {
            assertThatThrownBy(() -> SemVer.parse(bad))
                    .as(bad)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not a version like 1.2.3: " + bad);
        }
    }

    @Test
    void comparesPartByPartNotAsText() {
        // As text, "1.10.0" sorts before "1.9.0". As versions, it's newer.
        assertThat(SemVer.parse("1.10.0")).isGreaterThan(SemVer.parse("1.9.0"));
        assertThat(SemVer.parse("2.0.0")).isGreaterThan(SemVer.parse("1.99.99"));
        assertThat(SemVer.parse("1.0.1")).isGreaterThan(SemVer.parse("1.0.0"));
        assertThat(SemVer.parse("1.0.0")).isEqualByComparingTo(SemVer.parse("v1.0.0"));
    }

    @Test
    void aBumpResetsTheSmallerParts() {
        SemVer version = SemVer.parse("1.4.2");

        assertThat(version.bump(Bump.PATCH)).hasToString("1.4.3");
        assertThat(version.bump(Bump.MINOR)).hasToString("1.5.0");
        assertThat(version.bump(Bump.MAJOR)).hasToString("2.0.0");
        assertThat(version.bump(Bump.NONE)).isEqualTo(version);
    }

    @Test
    void theBiggestChangeDecidesTheNextVersion() {
        SemVer version = SemVer.parse("1.0.0");

        assertThat(version.next(commits("fix: a", "docs: b"))).hasToString("1.0.1");
        assertThat(version.next(commits("fix: a", "feat: b", "fix: c"))).hasToString("1.1.0");
        assertThat(version.next(commits("feat: a", "fix(sync)!: b"))).hasToString("2.0.0");
        assertThat(version.next(commits("chore: a", "test: b"))).isEqualTo(version);
        assertThat(version.next(List.of())).isEqualTo(version);
    }

    private static List<ConventionalCommit> commits(String... subjects) {
        return List.of(subjects).stream().map(ConventionalCommit::parse).toList();
    }
}
