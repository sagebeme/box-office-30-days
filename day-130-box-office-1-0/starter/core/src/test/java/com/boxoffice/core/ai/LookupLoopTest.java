package com.boxoffice.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SaleOutcome;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Day 126: Claude asks for lookups, the gate runs them, Claude carries on. */
class LookupLoopTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    private TicketStore store;
    private Gate gate;
    private FakeClaude claude;
    private SalesQuestions questions;
    private final List<String> pieces = new ArrayList<>();
    private final List<ToolCall> lookups = new ArrayList<>();

    @BeforeEach
    void openTheGate() {
        store = TicketStore.open(folder.resolve("gate.db"));
        SampleEvent.ensureIn(store);
        gate = new Gate(store, SampleEvent.ID, NINE_PM);
        claude = new FakeClaude();
        questions = new SalesQuestions(claude, store, gate.event(), NINE_PM);
    }

    @AfterEach
    void close() {
        store.close();
    }

    private Reply ask(String question) {
        return questions.ask(question, pieces::add, lookups::add);
    }

    @Test
    void runsTheLookupAndHandsTheResultBack() {
        SaleOutcome.Sold sold = (SaleOutcome.Sold) gate.sell("VIP", "Wanjiku Kamau", "254712345678");
        String code = sold.ticket().code();
        claude.thenLooksUp(new ToolCall("call_1", "find_ticket", "{\"code\":\"" + code + "\"}"))
                .thenAnswers("Yes, it's a VIP ticket sold to Wanjiku K.");

        Reply reply = ask("Is " + code + " a real ticket?");

        assertThat(reply).isEqualTo(new Reply.Answered("Yes, it's a VIP ticket sold to Wanjiku K."));
        List<ToolResult> sentBack = claude.results().getFirst();
        assertThat(sentBack).hasSize(1);
        assertThat(sentBack.getFirst().callId()).isEqualTo("call_1");
        assertThat(sentBack.getFirst().failed()).isFalse();
        assertThat(sentBack.getFirst().content()).startsWith(code + ": VIP, KES 5,000");
        assertThat(lookups).extracting(ToolCall::name).containsExactly("find_ticket");
    }

    @Test
    void answersEveryCallInOneTurnInOrder() {
        claude.thenLooksUp(
                        new ToolCall("call_1", "check_ins", "{}"),
                        new ToolCall("call_2", "sales_between", "{\"from\":\"20:00\",\"to\":\"21:00\"}"))
                .thenAnswers("Done.");

        ask("How's the night going?");

        assertThat(claude.results().getFirst()).extracting(ToolResult::callId).containsExactly("call_1", "call_2");
    }

    @Test
    void aBadLookupGoesBackAsAFailureClaudeCanFix() {
        claude.thenLooksUp(new ToolCall("call_1", "find_ticket", "{\"code\":\"<script>\"}"))
                .thenLooksUp(new ToolCall("call_2", "find_ticket", "{\"code\":\"VIP-0001\"}"))
                .thenAnswers("No such ticket.");

        Reply reply = ask("Is <script> a ticket?");

        assertThat(reply).isEqualTo(new Reply.Answered("No such ticket."));
        ToolResult first = claude.results().get(0).getFirst();
        assertThat(first.failed()).isTrue();
        assertThat(first.content()).isEqualTo("code should be a ticket code like VIP-0001");
        assertThat(claude.results().get(1).getFirst().failed()).isFalse();
    }

    @Test
    void givesUpAfterFiveRoundsOfLookups() {
        for (int i = 0; i < 10; i++) {
            claude.thenLooksUp(new ToolCall("call_" + i, "check_ins", "{}"));
        }

        Reply reply = ask("Keep looking");

        assertThat(reply).isEqualTo(new Reply.Refused(
                "Claude still wanted to look things up after 5 goes. Ask something narrower"));
        assertThat(claude.results()).hasSize(5);
    }

    @Test
    void aQuestionTheFactsAnswerNeedsNoLookup() {
        claude.thenAnswers("49 VIP are left.");

        ask("How many VIP are left?");

        assertThat(lookups).isEmpty();
        assertThat(claude.results()).isEmpty();
    }
}
