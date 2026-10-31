package com.boxoffice.core.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boxoffice.core.net.ResilientHttp;
import com.boxoffice.core.net.RetryPolicy;
import com.boxoffice.core.release.SemVer;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitHubReleasesTest {

    private FakeGitHub github;
    private GitHubReleases releases;

    @BeforeEach
    void start() throws Exception {
        github = new FakeGitHub();
        ResilientHttp http = new ResilientHttp(HttpClient.newHttpClient(),
                new RetryPolicy(1, Duration.ofMillis(1), Duration.ofMillis(1)), Duration.ofSeconds(2),
                wait -> { }, () -> 1.0, retry -> { });
        releases = new GitHubReleases(http, github.api(), "sagebeme/box-office-30-days");
    }

    @AfterEach
    void stop() {
        github.close();
    }

    @Test
    void readsTheLatestRelease() {
        github.publishes("v1.1.0", "## Box Office 1.1.0\n\n### New\n- updates", Map.of("boxoffice_1.1.0_amd64.deb", "deb"));

        Release release = releases.latest().orElseThrow();

        assertThat(release.version()).isEqualTo(SemVer.parse("1.1.0"));
        assertThat(release.notes()).isEqualTo("## Box Office 1.1.0\n\n### New\n- updates");
        assertThat(release.assets()).extracting(Asset::name).containsExactly("boxoffice_1.1.0_amd64.deb", "SHA256SUMS");
        assertThat(release.asset("boxoffice_1.1.0_amd64.deb").orElseThrow().url())
                .isEqualTo(github.download("boxoffice_1.1.0_amd64.deb"));
    }

    @Test
    void asksTheWayGitHubsDocsSay() {
        github.publishes("v1.1.0", "", Map.of());

        releases.latest();

        assertThat(github.requests())
                .containsExactly("GET /repos/sagebeme/box-office-30-days/releases/latest application/vnd.github+json");
    }

    @Test
    void noReleasesIsA404() {
        assertThat(releases.latest()).isEqualTo(Optional.empty());
    }

    @Test
    void otherErrorsSaySo() {
        github.answersLatestWith(403, "{\"message\":\"API rate limit exceeded\"}");

        assertThatThrownBy(releases::latest)
                .isInstanceOf(UpdateException.class)
                .hasMessage("GitHub answered HTTP 403 about sagebeme/box-office-30-days");
    }

    @Test
    void aTagThatIsntAVersionIsRefused() {
        github.answersLatestWith(200, "{\"tag_name\":\"nightly\",\"assets\":[]}");

        assertThatThrownBy(releases::latest)
                .isInstanceOf(UpdateException.class)
                .hasMessage("GitHub's answer about the latest release didn't make sense");
    }

    @Test
    void theRepositoryMustLookLikeOne() {
        assertThatThrownBy(() -> new GitHubReleases(null, github.api(), "../../etc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A repository is owner/name, like sagebeme/box-office-30-days");
    }
}
