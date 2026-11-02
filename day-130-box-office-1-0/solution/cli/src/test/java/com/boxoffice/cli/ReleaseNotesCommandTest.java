package com.boxoffice.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ReleaseNotesCommandTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    record Run(int exitCode, String out, String err) {
    }

    private Run run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cli = BoxOfficeCli.commandLine(NINE_PM);
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));
        return new Run(cli.execute(args), out.toString(), err.toString());
    }

    // What `git log --format=%s v1.0.0..HEAD` prints: newest first, one subject a line.
    private Path log(String... subjects) throws IOException {
        return Files.write(folder.resolve("subjects.txt"), java.util.List.of(subjects));
    }

    @Test
    void worksOutTheVersionAndWritesTheNotes() throws IOException {
        Path log = log("feat: Day 130 updates itself", "fix: a typo on the receipt", "chore: tidy", "");

        Run run = run("release-notes", "--last", "v1.0.0", "--commits", log.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).isEqualTo("""
                ## Box Office 1.1.0

                ### New
                - Day 130 updates itself

                ### Fixed
                - a typo on the receipt
                """);
    }

    @Test
    void theHeadingCanBeGiven() throws IOException {
        Run run = run("release-notes", "--last", "v1.0.0", "--version", "v1.1.0",
                "--commits", log("fix: a typo").toString());

        assertThat(run.out()).startsWith("## Box Office 1.1.0\n");
    }

    @Test
    void onlyChoresMeansNothingToRelease() throws IOException {
        Run run = run("release-notes", "--last", "v1.0.0", "--commits", log("chore: tidy", "docs: more words").toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err().strip()).isEqualTo("Nothing to release since 1.0.0: no feat or fix commits");
    }

    @Test
    void aBadVersionSaysSo() throws IOException {
        Run run = run("release-notes", "--last", "latest", "--commits", log("fix: a").toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err().strip()).isEqualTo("Not a version like 1.2.3: latest");
    }

    @Test
    void aBadHeadingVersionSaysSoToo() throws IOException {
        Run run = run("release-notes", "--last", "v1.0.0", "--version", "banana", "--commits", log("fix: a").toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err().strip()).isEqualTo("Not a version like 1.2.3: banana");
    }

    @Test
    void helpWorks() {
        Run run = run("release-notes", "--help");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).contains("--last").contains("--version").contains("--commits");
    }
}
