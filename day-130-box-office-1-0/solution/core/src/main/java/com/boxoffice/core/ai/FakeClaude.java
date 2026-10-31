package com.boxoffice.core.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pretends to be Claude: plays back the turns it was handed, in order, and
 * streams any answer a word at a time, like the real thing. It remembers
 * everything it was sent, so a test can check what would have gone out.
 */
public final class FakeClaude implements Claude {

    private final Deque<Turn> turns = new ArrayDeque<>();
    private final List<String> systems = new ArrayList<>();
    private final List<List<ToolSpec>> tools = new ArrayList<>();
    private final List<String> questions = new ArrayList<>();
    private final List<List<ToolResult>> results = new ArrayList<>();

    public FakeClaude(Turn... turns) {
        this.turns.addAll(List.of(turns));
    }

    /** Queues another turn. */
    public FakeClaude then(Turn turn) {
        turns.add(turn);
        return this;
    }

    /** Queues a finished answer. */
    public FakeClaude thenAnswers(String text) {
        return then(answered(text));
    }

    /** Queues a turn that asks for lookups. */
    public FakeClaude thenLooksUp(ToolCall... calls) {
        return then(new Turn.UsesTools(List.of(calls)));
    }

    public static Turn answered(String text) {
        return new Turn.Finished(new Reply.Answered(text));
    }

    @Override
    public Chat chat(String system, List<ToolSpec> specs) {
        systems.add(system);
        tools.add(List.copyOf(specs));
        return new Chat() {
            @Override
            public Turn ask(String question, Consumer<String> onText) {
                questions.add(question);
                return next(onText);
            }

            @Override
            public Turn answer(List<ToolResult> found, Consumer<String> onText) {
                results.add(List.copyOf(found));
                return next(onText);
            }
        };
    }

    private Turn next(Consumer<String> onText) {
        Turn turn = turns.isEmpty() ? answered("(The fake has no more answers.)") : turns.poll();
        if (turn instanceof Turn.Finished(Reply reply)) {
            String text = switch (reply) {
                case Reply.Answered answered -> answered.text();
                case Reply.CutShort cut -> cut.text();
                case Reply.Refused refused -> "";
            };
            pieces(text).forEach(onText);
        }
        return turn;
    }

    /** The system prompt of each chat, oldest first. */
    public List<String> systems() {
        return List.copyOf(systems);
    }

    /** The tools each chat offered. */
    public List<List<ToolSpec>> tools() {
        return List.copyOf(tools);
    }

    public List<String> questions() {
        return List.copyOf(questions);
    }

    /** Every batch of lookup results handed back, oldest first. */
    public List<List<ToolResult>> results() {
        return List.copyOf(results);
    }

    // "Two VIP left" -> "Two", " VIP", " left": joined back up, they're the whole text.
    static List<String> pieces(String text) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == ' ') {
                pieces.add(text.substring(start, i));
                start = i;
            }
        }
        return pieces;
    }
}
