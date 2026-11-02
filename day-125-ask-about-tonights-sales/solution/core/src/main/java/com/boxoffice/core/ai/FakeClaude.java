package com.boxoffice.core.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pretends to be Claude: gives back the replies it was handed, in order, and
 * streams each answer a word at a time, like the real thing. It remembers
 * every question, so a test can check what would have been sent.
 */
public final class FakeClaude implements Claude {

    /** One question the fake was asked. */
    public record Asked(String system, String question) {
    }

    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<Asked> asked = new ArrayList<>();

    public FakeClaude(Reply... replies) {
        this.replies.addAll(List.of(replies));
    }

    /** Queues another reply. */
    public FakeClaude then(Reply reply) {
        replies.add(reply);
        return this;
    }

    @Override
    public Reply ask(String system, String question, Consumer<String> onText) {
        asked.add(new Asked(system, question));
        Reply reply = replies.isEmpty() ? new Reply.Answered("(The fake has no more answers.)") : replies.poll();
        String text = switch (reply) {
            case Reply.Answered answered -> answered.text();
            case Reply.CutShort cut -> cut.text();
            case Reply.Refused refused -> "";
        };
        for (String piece : pieces(text)) {
            onText.accept(piece);
        }
        return reply;
    }

    public List<Asked> asked() {
        return List.copyOf(asked);
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
