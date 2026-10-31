package com.boxoffice.core.ai.eval;

import com.boxoffice.core.ai.Reply;
import com.boxoffice.core.ai.ToolCall;
import java.util.List;

/** Everything that happened when one question was asked: what came back, and what Claude looked up. */
public record Transcript(String question, Reply reply, List<ToolCall> lookups) {

    public Transcript {
        lookups = List.copyOf(lookups);
    }

    /** The answer's text, or "" when Claude refused. */
    public String answer() {
        return switch (reply) {
            case Reply.Answered answered -> answered.text();
            case Reply.CutShort cut -> cut.text();
            case Reply.Refused refused -> "";
        };
    }

    public boolean lookedUp(String tool) {
        return lookups.stream().anyMatch(call -> call.name().equals(tool));
    }
}
