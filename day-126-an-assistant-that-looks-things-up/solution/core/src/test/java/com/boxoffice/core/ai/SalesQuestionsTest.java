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

        assertThat(claude.questions()).containsExactly("How many VIP are left?");
        assertThat(claude.systems().getFirst())
                .contains("<facts>\n" + questions.snapshot().facts() + "</facts>")
                .contains("VIP (VIP): KES 5,000 each. 1 paid, 0 guests, 49 left of 50");
    }

    @Test
    void theInstructionsSayToStickToWhatItCanSee() {
        String system = SalesQuestions.systemPrompt(questions.snapshot());

        assertThat(system)
                .startsWith("You help the gate staff at Kilele Nights")
                .contains("Answer from the facts and what your lookups find")
                .contains("They only read")
                .contains("without Markdown");
    }

    @Test
    void offersTheThreeLookups() {
        questions.ask("How many VIP are left?", pieces::add);

        assertThat(claude.tools().getFirst()).extracting(ToolSpec::name)
                .containsExactly("find_ticket", "sales_between", "check_ins");
    }

    @Test
    void streamsTheAnswerAsItArrives() {
        claude.thenAnswers("49 VIP tickets are left.");

        Reply reply = questions.ask("How many VIP are left?", pieces::add);

        assertThat(reply).isEqualTo(new Reply.Answered("49 VIP tickets are left."));
        assertThat(pieces).containsExactly("49", " VIP", " tickets", " are", " left.");
    }

    @Test
    void looksAtTheDatabaseAgainForEachQuestion() {
        questions.ask("How many VIP are left?", pieces::add);
        gate.sell("VIP", "Otieno");
        questions.ask("And now?", pieces::add);

        assertThat(claude.systems().get(0)).contains("50 left of 50");
        assertThat(claude.systems().get(1)).contains("49 left of 50");
    }

    @Test
    void aBlankQuestionIsNeverSent() {
        Reply reply = questions.ask("   ", pieces::add);

        assertThat(reply).isEqualTo(new Reply.Refused("Type a question first"));
        assertThat(claude.questions()).isEmpty();
    }

    @Test
    void aVeryLongQuestionIsNeverSent() {
        Reply reply = questions.ask("why ".repeat(200), pieces::add);

        assertThat(reply).isEqualTo(new Reply.Refused("Keep the question under 500 characters"));
        assertThat(claude.questions()).isEmpty();
    }

    @Test
    void passesARefusalOnWithNoText() {
        claude.then(new Turn.Finished(new Reply.Refused("Claude declined to answer")));

        Reply reply = questions.ask("How many VIP are left?", pieces::add);

        assertThat(reply).isInstanceOf(Reply.Refused.class);
        assertThat(pieces).isEmpty();
    }
}
