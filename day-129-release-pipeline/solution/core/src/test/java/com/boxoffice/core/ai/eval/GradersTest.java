package com.boxoffice.core.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.ai.Reply;
import com.boxoffice.core.ai.ToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The graders, on answers written by hand: no Claude, fake or real. */
class GradersTest {

    private static Transcript answer(String text, ToolCall... lookups) {
        return new Transcript("A question?", new Reply.Answered(text), List.of(lookups));
    }

    private static boolean passes(Grader grader, Transcript transcript) {
        return grader.passes().test(transcript);
    }

    @Test
    void aNumberCountsOnlyAsAWholeNumber() {
        Grader fortySeven = Graders.mentionsNumber(47);

        assertThat(passes(fortySeven, answer("47 VIP are left."))).isTrue();
        assertThat(passes(fortySeven, answer("There are 47."))).isTrue();
        assertThat(passes(fortySeven, answer("470 left"))).isFalse();
        assertThat(passes(fortySeven, answer("147 left"))).isFalse();
        assertThat(passes(fortySeven, answer("forty-seven left"))).isFalse();
    }

    @Test
    void thousandsCommasAreRead() {
        assertThat(Graders.numbersIn("KES 1,500 each, 47 left, 2026")).containsExactlyInAnyOrder(1500L, 47L, 2026L);
        assertThat(passes(Graders.mentionsNumber(8000), answer("We took KES 8,000."))).isTrue();
    }

    @Test
    void mentionsIgnoresCase() {
        assertThat(passes(Graders.mentions("Wanjiku"), answer("It's WANJIKU K.'s ticket."))).isTrue();
        assertThat(passes(Graders.neverMentions("Kamau"), answer("It's Wanjiku kamau's."))).isFalse();
    }

    @Test
    void knowsWhichToolsWereUsed() {
        ToolCall lookup = new ToolCall("call_1", "find_ticket", "{\"code\":\"VIP-0001\"}");

        assertThat(passes(Graders.usedTool("find_ticket"), answer("Wanjiku K.", lookup))).isTrue();
        assertThat(passes(Graders.usedTool("check_ins"), answer("Wanjiku K.", lookup))).isFalse();
        assertThat(passes(Graders.usedNoTools(), answer("47 left"))).isTrue();
        assertThat(passes(Graders.usedNoTools(), answer("47 left", lookup))).isFalse();
    }

    @Test
    void hearsWhenItCantTell() {
        Grader cantTell = Graders.saysItCannotTell();

        assertThat(passes(cantTell, answer("I can't see the weather from here."))).isTrue();
        assertThat(passes(cantTell, answer("I can’t see tomorrow's forecast."))).isTrue();
        assertThat(passes(cantTell, answer("I don't have weather data, only tonight's sales."))).isTrue();
        assertThat(passes(cantTell, answer("Yes, light showers after 2am."))).isFalse();
    }

    @Test
    void spotsMarkdown() {
        Grader plain = Graders.plainText();

        assertThat(passes(plain, answer("47 VIP are left. Regular has 497."))).isTrue();
        assertThat(passes(plain, answer("**47** VIP are left."))).isFalse();
        assertThat(passes(plain, answer("# Tonight\n47 left"))).isFalse();
        assertThat(passes(plain, answer("Left:\n- VIP 47\n- Regular 497"))).isFalse();
        assertThat(passes(plain, answer("Use `report` for more."))).isFalse();
    }

    @Test
    void aRefusalOrACutShortAnswerIsNotAnAnswer() {
        Transcript refused = new Transcript("Q?", new Reply.Refused("No"), List.of());
        Transcript cut = new Transcript("Q?", new Reply.CutShort("47 VIP and"), List.of());

        assertThat(passes(Graders.answered(), answer("47"))).isTrue();
        assertThat(passes(Graders.answered(), refused)).isFalse();
        assertThat(passes(Graders.answered(), cut)).isFalse();
        assertThat(refused.answer()).isEmpty();
    }

    @Test
    void countsWords() {
        assertThat(passes(Graders.atMostWords(3), answer("47 are left."))).isTrue();
        assertThat(passes(Graders.atMostWords(3), answer("There are 47 left."))).isFalse();
    }
}
