package com.boxoffice.core.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.ai.FakeClaude;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalRunnerTest {

    @TempDir
    Path folder;

    private static final EvalCase VIP_LEFT = new EvalCase("vip-left",
            night -> night.at("20:00").sellGroup("VIP", "Kamau family", "", 3),
            "How many VIP tickets are left?",
            List.of(Graders.mentionsNumber(47)));

    @Test
    void aGoodAnswerPasses() {
        Scorecard card = new EvalRunner(folder).run(List.of(VIP_LEFT), new FakeClaude().thenAnswers("47 VIP are left."));

        assertThat(card.passed()).isEqualTo(1);
        assertThat(card.render()).isEqualTo("""
                PASS  vip-left
                1 of 1 passed (100%)
                """);
    }

    @Test
    void aWrongAnswerSaysWhichGradersFailedAndWhatItSaid() {
        Scorecard card = new EvalRunner(folder).run(List.of(VIP_LEFT),
                new FakeClaude().thenAnswers("**Fifty** VIP are left."));

        assertThat(card.passed()).isZero();
        assertThat(card.render()).isEqualTo("""
                FAIL  vip-left: is plain text, mentions 47
                      answer: **Fifty** VIP are left.
                0 of 1 passed (0%)
                """);
    }

    @Test
    void eachCaseGetsItsOwnNight() {
        FakeClaude claude = new FakeClaude().thenAnswers("47.").thenAnswers("50.");
        EvalCase nothingSold = new EvalCase("nothing-sold", night -> { }, "How many VIP tickets are left?", List.of());

        new EvalRunner(folder).run(List.of(VIP_LEFT, nothingSold), claude);

        assertThat(claude.systems().get(0)).contains("47 left of 50");
        assertThat(claude.systems().get(1)).contains("50 left of 50");
    }

    @Test
    void theQuestionsAreAskedAtNinePm() {
        FakeClaude claude = new FakeClaude().thenAnswers("47.");

        new EvalRunner(folder).run(List.of(VIP_LEFT), claude);

        assertThat(claude.systems().getFirst()).contains("Now: Sat 14 Nov, 21:00");
    }

    @Test
    void thePassRateIsAFraction() {
        FakeClaude claude = new FakeClaude().thenAnswers("47.").thenAnswers("No idea.").thenAnswers("47 left").thenAnswers("?");

        Scorecard card = new EvalRunner(folder).run(List.of(VIP_LEFT, VIP_LEFT, VIP_LEFT, VIP_LEFT), claude);

        assertThat(card.passRate()).isEqualTo(0.5);
    }
}
