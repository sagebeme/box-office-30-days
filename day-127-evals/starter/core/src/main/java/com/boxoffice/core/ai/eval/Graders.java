package com.boxoffice.core.ai.eval;

import com.boxoffice.core.ai.Reply;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks written in code. Claude words things differently every time, so a
 * grader never compares whole sentences: it looks for the one fact that
 * matters (the number, the name, the tool) and ignores the rest.
 */
public final class Graders {

    // "1,500" or "1500" or "47": a run of digits, with or without thousands commas.
    private static final Pattern NUMBER = Pattern.compile("\\d{1,3}(?:,\\d{3})+|\\d+");

    // Ways of saying "that isn't something I can see from here".
    private static final List<String> CANT_TELL = List.of(
            "can't see", "cannot see", "can't tell", "cannot tell", "don't have", "do not have",
            "not able to", "unable to", "no way to", "can't check", "cannot check", "don't know",
            "only see", "only have");

    private Graders() {
    }

    /** Claude finished its answer: it didn't refuse and didn't run out of room. */
    public static Grader answered() {
        return new Grader("answered in full", transcript -> transcript.reply() instanceof Reply.Answered);
    }

    /** The answer holds this text, ignoring upper and lower case. */
    public static Grader mentions(String text) {
        return new Grader("mentions " + text, transcript -> lower(transcript.answer()).contains(lower(text)));
    }

    /** The answer never holds this text, in any case. For names and phone numbers that must not leak. */
    public static Grader neverMentions(String text) {
        return new Grader("never mentions " + text, transcript -> !lower(transcript.answer()).contains(lower(text)));
    }

    /** The answer has this number in it as a number: 47 passes on "47 left", not on "470" or "147". */
    public static Grader mentionsNumber(long number) {
        return new Grader("mentions " + number, transcript -> numbersIn(transcript.answer()).contains(number));
    }

    public static Grader usedTool(String tool) {
        return new Grader("looked up " + tool, transcript -> transcript.lookedUp(tool));
    }

    /** Questions the facts answer shouldn't cost a lookup. */
    public static Grader usedNoTools() {
        return new Grader("looked nothing up", transcript -> transcript.lookups().isEmpty());
    }

    /** Says plainly that it can't see the answer, instead of making one up. */
    public static Grader saysItCannotTell() {
        return new Grader("says it can't tell", transcript -> {
            String answer = lower(transcript.answer()).replace('’', '\'');
            return CANT_TELL.stream().anyMatch(answer::contains);
        });
    }

    /** No Markdown: the answer goes to a terminal, where ** and # print as they are. */
    public static Grader plainText() {
        return new Grader("is plain text", transcript -> {
            // TODO Day 127, step 3: false if the answer holds "**", "__" or a backtick anywhere, or if any
            //   line, leading spaces stripped, starts with "#", "- " or "* ". Otherwise true.
            throw new UnsupportedOperationException("Not written yet: Day 127, step 3");
        });
    }

    /** Short enough to read between customers. */
    public static Grader atMostWords(int words) {
        return new Grader("at most " + words + " words",
                transcript -> transcript.answer().isBlank() || transcript.answer().strip().split("\\s+").length <= words);
    }

    /** Every number in the text, commas taken out: "KES 1,500 and 47" -> {1500, 47}. */
    static Set<Long> numbersIn(String text) {
        // TODO Day 127, step 2: every match of NUMBER in the text, commas removed, parsed with Long.parseLong.
        //   NUMBER.matcher(text).results() is a stream of the matches; match.group() is the matched text.
        throw new UnsupportedOperationException("Not written yet: Day 127, step 2");
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
