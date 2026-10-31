package com.boxoffice.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class EvalCommandTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    private FakeAnthropicApi api;

    record Run(int exitCode, String out, String err) {
    }

    @BeforeEach
    void start() throws Exception {
        api = new FakeAnthropicApi();
    }

    @AfterEach
    void stop() {
        api.close();
    }

    private Run run(Map<String, String> environment, String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cli = BoxOfficeCli.commandLine(NINE_PM, environment);
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));
        String[] withDb = new String[args.length + 2];
        withDb[0] = "--db";
        withDb[1] = folder.resolve("gate.db").toString();
        System.arraycopy(args, 0, withDb, 2, args.length);
        return new Run(cli.execute(withDb), out.toString(), err.toString());
    }

    private Map<String, String> withKey() {
        return Map.of("ANTHROPIC_API_KEY", "sk-ant-test-key", "ANTHROPIC_BASE_URL", api.baseUrl());
    }

    private void everyAnswerIs(String text) {
        for (int i = 0; i < 8; i++) {
            api.streams(FakeAnthropicApi.textAnswer("end_turn", text));
        }
    }

    @Test
    void gradesEveryCaseAndFailsBelowTheBar() {
        everyAnswerIs("About 30, I think.");

        Run run = run(withKey(), "eval");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err()).startsWith("Asking Claude 8 questions...");
        assertThat(run.out()).startsWith("FAIL  vip-left: mentions 47")
                .contains("PASS  full-phone")
                .contains("1 of 8 passed (13%)");
        assertThat(api.requests()).hasSize(8);
    }

    @Test
    void theBarCanBeMoved() {
        everyAnswerIs("About 30, I think.");

        assertThat(run(withKey(), "eval", "--at-least", "10").exitCode()).isZero();
    }

    @Test
    void neverTouchesTheGatesDatabase() {
        everyAnswerIs("About 30, I think.");

        run(withKey(), "eval");

        assertThat(Files.exists(folder.resolve("gate.db"))).isFalse();
    }

    @Test
    void needsAKey() {
        Run run = run(Map.of(), "eval");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err().strip()).isEqualTo("Set ANTHROPIC_API_KEY to your Anthropic API key to run the evals");
        assertThat(api.requests()).isEmpty();
    }
}
