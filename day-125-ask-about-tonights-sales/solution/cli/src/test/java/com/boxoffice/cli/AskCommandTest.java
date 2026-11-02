package com.boxoffice.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.io.PrintWriter;
import java.io.StringWriter;
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

class AskCommandTest {

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

    private Map<String, String> withKey() {
        return Map.of("ANTHROPIC_API_KEY", "sk-ant-test-key", "ANTHROPIC_BASE_URL", api.baseUrl());
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

    private void sellAVipTicket() {
        try (TicketStore store = TicketStore.open(folder.resolve("gate.db"))) {
            SampleEvent.ensureIn(store);
            new Gate(store, SampleEvent.ID, NINE_PM).sell("VIP", "Wanjiku Kamau", "254712345678");
        }
    }

    @Test
    void printsTheAnswer() {
        api.streams(FakeAnthropicApi.textAnswer("end_turn", "49 VIP", " tickets are left."));

        Run run = run(withKey(), "ask", "How", "many", "VIP", "are", "left?");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).isEqualTo("49 VIP tickets are left." + System.lineSeparator());
    }

    @Test
    void sendsTonightsNumbersButNoNamesOrPhones() {
        sellAVipTicket();
        api.streams(FakeAnthropicApi.textAnswer("end_turn", "49."));

        run(withKey(), "ask", "How many VIP are left?");

        String body = api.requests().getFirst().body();
        assertThat(body).contains("1 paid, 0 guests, 49 left of 50")
                .doesNotContain("Wanjiku")
                .doesNotContain("254712345678");
    }

    @Test
    void needsAKey() {
        Run run = run(Map.of(), "ask", "How many VIP are left?");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err().strip())
                .isEqualTo("Set ANTHROPIC_API_KEY to your Anthropic API key, or use --fake to see what would be sent");
        assertThat(api.requests()).isEmpty();
    }

    @Test
    void fakeShowsTheFactsAndSendsNothing() {
        sellAVipTicket();

        Run run = run(withKey(), "ask", "--fake", "How many VIP are left?");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).startsWith("Practice mode: nothing was sent.")
                .contains("VIP (VIP): KES 5,000 each. 1 paid, 0 guests, 49 left of 50");
        assertThat(api.requests()).isEmpty();
    }

    @Test
    void aWrongKeySaysSo() {
        api.fails(401, "authentication_error", "invalid x-api-key");

        Run run = run(withKey(), "ask", "How many VIP are left?");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err().strip()).isEqualTo("The Anthropic API refused the key (HTTP 401). Check ANTHROPIC_API_KEY");
    }

    @Test
    void aRefusalSaysWhy() {
        api.streams(FakeAnthropicApi.refusal("Claude can't help with that."));

        Run run = run(withKey(), "ask", "Something odd");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err().strip()).isEqualTo("No answer: Claude can't help with that.");
    }

    @Test
    void aLongQuestionIsRefusedBeforeSending() {
        Run run = run(withKey(), "ask", "why ".repeat(200));

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err().strip()).isEqualTo("Keep the question under 500 characters");
        assertThat(api.requests()).isEmpty();
    }
}
