package com.boxoffice.core.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boxoffice.core.net.ResilientHttp;
import com.boxoffice.core.net.RetryPolicy;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloaderTest {

    private static final String DEB = "boxoffice_1.1.0_amd64.deb";

    @TempDir
    Path folder;

    private FakeGitHub github;

    @BeforeEach
    void start() throws Exception {
        github = new FakeGitHub();
    }

    @AfterEach
    void stop() {
        github.close();
    }

    private Release latest() {
        ResilientHttp http = new ResilientHttp(HttpClient.newHttpClient(),
                new RetryPolicy(1, Duration.ofMillis(1), Duration.ofMillis(1)), Duration.ofSeconds(2),
                wait -> { }, () -> 1.0, retry -> { });
        return new GitHubReleases(http, github.api(), "sagebeme/box-office-30-days").latest().orElseThrow();
    }

    private Path download(Release release) {
        return Downloader.forUpdates().download(release, release.asset(DEB).orElseThrow(), folder.resolve("installers"));
    }

    @Test
    void downloadsAndChecksTheInstaller() throws IOException {
        github.publishes("v1.1.0", "", Map.of(DEB, "the new installer"));

        Path saved = download(latest());

        assertThat(saved).isEqualTo(folder.resolve("installers").resolve(DEB));
        assertThat(Files.readString(saved)).isEqualTo("the new installer");
        assertThat(Files.exists(folder.resolve("installers").resolve(DEB + ".part"))).isFalse();
    }

    @Test
    void followsGitHubsRedirect() {
        github.publishes("v1.1.0", "", Map.of(DEB, "the new installer"));

        download(latest());

        assertThat(github.requests()).anyMatch(request -> request.startsWith("GET /download/" + DEB))
                .anyMatch(request -> request.startsWith("GET /files/" + DEB));
    }

    @Test
    void aFileThatDoesntMatchItsChecksumIsDeleted() {
        github.publishes("v1.1.0", "", Map.of(DEB, "the new installer")).corrupts(DEB);
        Release release = latest();

        assertThatThrownBy(() -> download(release))
                .isInstanceOf(UpdateException.class)
                .hasMessage("The download doesn't match its checksum, so it was deleted. Try again");
        assertThat(folder.resolve("installers").resolve(DEB)).doesNotExist();
        assertThat(folder.resolve("installers").resolve(DEB + ".part")).doesNotExist();
    }

    @Test
    void noChecksumsNoDownload() {
        github.publishes("v1.1.0", "", Map.of(DEB, "the new installer"));
        Release release = latest();
        Release withoutSums = new Release(release.version(), release.notes(),
                release.assets().stream().filter(asset -> !asset.name().equals(Release.CHECKSUMS)).toList());

        assertThatThrownBy(() -> download(withoutSums))
                .isInstanceOf(UpdateException.class)
                .hasMessage("Release 1.1.0 has no SHA256SUMS, so there's no way to check the download");
        assertThat(github.requests()).noneMatch(request -> request.contains(DEB));
    }

    @Test
    void anInstallerTheChecksumsDontListIsRefused() {
        github.publishes("v1.1.0", "", Map.of("other.deb", "x"));
        Release release = latest();
        Release withDeb = new Release(release.version(), "", java.util.List.of(release.assets().get(0),
                release.assets().get(1), new Asset(DEB, github.download(DEB), 1)));

        assertThatThrownBy(() -> download(withDeb))
                .isInstanceOf(UpdateException.class)
                .hasMessage("SHA256SUMS doesn't list " + DEB);
    }

    @Test
    void aMissingFileSaysSo() {
        github.publishes("v1.1.0", "", Map.of(DEB, "the new installer"));
        Release release = latest();
        github.removes(DEB);

        assertThatThrownBy(() -> download(release))
                .isInstanceOf(UpdateException.class)
                .hasMessage("The download answered HTTP 404");
    }
}
