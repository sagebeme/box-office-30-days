package com.boxoffice.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Gate;
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

class SalesQuestionsTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    private TicketStore store;
    private Gate gate;
    private FakeClaude claude;
    private SalesQuestions questions;
    private final List<String> pieces = new ArrayList<>();

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

    @Test
    void sendsTheQuestionWithTonightsFacts() {
        gate.sell("VIP", "Wanjiku");

        questions.ask("  How many VIP are left?  ", pieces::add);

        FakeClaude.Asked asked = claude.asked().getFirst();
        assertThat(asked.question()).isEqualTo("How many VIP are left?");
        assertThat(asked.system())
                .contains("<facts>\n" + questions.snapshot().facts() + "</facts>")
                .contains("VIP (VIP): KES 5,000 each. 1 paid, 0 guests, 49 left of 50");
    }

    @Test
    void theInstructionsSayToStickToTheFacts() {
        String system = SalesQuestions.systemPrompt(questions.snapshot());

        assertThat(system)
                .startsWith("You help the gate staff at Kilele Nights")
                .contains("Answer from these facts only")
                .contains("without Markdown");
    }

    @Test
    void streamsTheAnswerAsItArrives() {
        claude.then(new Reply.Answered("49 VIP tickets are left."));

        Reply reply = questions.ask("How many VIP are left?", pieces::add);

        assertThat(reply).isEqualTo(new Reply.Answered("49 VIP tickets are left."));
        assertThat(pieces).containsExactly("49", " VIP", " tickets", " are", " left.");
    }

    @Test
    void looksAtTheDatabaseAgainForEachQuestion() {
        questions.ask("How many VIP are left?", pieces::add);
        gate.sell("VIP", "Otieno");
        questions.ask("And now?", pieces::add);

        assertThat(claude.asked().get(0).system()).contains("50 left of 50");
        assertThat(claude.asked().get(1).system()).contains("49 left of 50");
    }

    @Test
    void aBlankQuestionIsNeverSent() {
        Reply reply = questions.ask("   ", pieces::add);

        assertThat(reply).isEqualTo(new Reply.Refused("Type a question first"));
        assertThat(claude.asked()).isEmpty();
    }

    @Test
    void aVeryLongQuestionIsNeverSent() {
        Reply reply = questions.ask("why ".repeat(200), pieces::add);

        assertThat(reply).isEqualTo(new Reply.Refused("Keep the question under 500 characters"));
        assertThat(claude.asked()).isEmpty();
    }

    @Test
    void passesARefusalOnWithNoText() {
        claude.then(new Reply.Refused("Claude declined to answer"));

        Reply reply = questions.ask("How many VIP are left?", pieces::add);

        assertThat(reply).isInstanceOf(Reply.Refused.class);
        assertThat(pieces).isEmpty();
    }
}
