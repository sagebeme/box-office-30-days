package com.boxoffice.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.net.ResilientHttp;
import com.boxoffice.core.net.RetryPolicy;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class UpdateCommandTest {

    // 21:00 in Nairobi on the night, and a quiet Tuesday a week later.
    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);
    private static final Clock TUESDAY = Clock.fixed(Instant.parse("2026-11-17T09:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    private FakeGitHub github;

    record Run(int exitCode, String out, String err) {
    }

    @BeforeEach
    void start() throws Exception {
        github = new FakeGitHub();
    }

    @AfterEach
    void stop() {
        github.close();
    }

    private static ResilientHttp oneTry(Consumer<String> log) {
        return new ResilientHttp(HttpClient.newHttpClient(), new RetryPolicy(1, Duration.ofMillis(1), Duration.ofMillis(1)),
                Duration.ofSeconds(2), wait -> { }, () -> 1.0, log);
    }

    private Run run(Clock clock, String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cli = BoxOfficeCli.commandLine(clock, Map.of("BOXOFFICE_RELEASES_URL", github.api().toString()),
                UpdateCommandTest::oneTry);
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));
        String[] withDb = new String[args.length + 2];
        withDb[0] = "--db";
        withDb[1] = folder.resolve("gate.db").toString();
        System.arraycopy(args, 0, withDb, 2, args.length);
        return new Run(cli.execute(withDb), out.toString(), err.toString());
    }

    private void publishOneOne() {
        github.publishes("v1.1.0", "## Box Office 1.1.0\n\n### New\n- the gate updates itself", Map.of(
                "boxoffice_1.1.0_amd64.deb", "deb installer",
                "BoxOffice-1.1.0.msi", "msi installer",
                "BoxOffice-1.1.0.dmg", "dmg installer"));
    }

    @Test
    void saysWhenItsUpToDate() {
        github.publishes("v1.0.0", "", Map.of("boxoffice_1.0.0_amd64.deb", "deb"));

        Run run = run(TUESDAY, "update");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out().strip()).isEqualTo("Box Office 1.0.0 is up to date");
    }

    @Test
    void showsWhatsNewWithoutDownloading() {
        publishOneOne();

        Run run = run(TUESDAY, "update");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).startsWith("Box Office 1.1.0 is out (you have 1.0.0)")
                .contains("- the gate updates itself")
                .contains("Run `update --download <folder>` to fetch ");
        assertThat(github.requests()).noneMatch(request -> request.contains("/files/"));
    }

    @Test
    void downloadsAndChecksTheInstallerForThisSystem() throws IOException {
        publishOneOne();

        Run run = run(TUESDAY, "update", "--download", folder.resolve("installers").toString());

        assertThat(run.exitCode()).as(run.err()).isZero();
        assertThat(run.out()).contains("(its checksum matches). Close Box Office, then run it to update");
        try (var saved = Files.list(folder.resolve("installers"))) {
            assertThat(saved.toList()).hasSize(1);
        }
    }

    @Test
    void neverDuringTheNight() {
        publishOneOne();

        Run run = run(NINE_PM, "update", "--download", folder.resolve("installers").toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err().strip())
                .isEqualTo("The night is on: updates wait until Kilele Nights ends at Sun 15 Nov, 04:00");
        assertThat(folder.resolve("installers")).doesNotExist();
    }

    @Test
    void aTamperedDownloadIsRefused() {
        publishOneOne();
        github.corrupts("boxoffice_1.1.0_amd64.deb").corrupts("BoxOffice-1.1.0.msi").corrupts("BoxOffice-1.1.0.dmg");

        Run run = run(TUESDAY, "update", "--download", folder.resolve("installers").toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err().strip()).isEqualTo("The download doesn't match its checksum, so it was deleted. Try again");
    }

    @Test
    void noReleasesYet() {
        Run run = run(TUESDAY, "update");

        assertThat(run.out().strip()).isEqualTo("Box Office 1.0.0: no releases published yet");
    }

    @Test
    void gitHubDownSaysSo() {
        github.close();

        Run run = run(TUESDAY, "update");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err()).startsWith("Couldn't reach GitHub to check for updates: ");
    }

    @Test
    void theVersionComesFromTheBuild() {
        Run run = run(TUESDAY, "--version");

        assertThat(run.out().strip()).isEqualTo("Box Office 1.0.0");
    }
}
