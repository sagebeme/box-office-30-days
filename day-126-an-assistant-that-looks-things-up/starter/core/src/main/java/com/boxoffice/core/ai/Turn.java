package com.boxoffice.core.ai;

import java.util.List;

/** One go from Claude: either it has finished, or it wants lookups first. */
public sealed interface Turn {

    /** Claude is done: here's how it ended. */
    record Finished(Reply reply) implements Turn {
    }

    /** Claude wants these looked up, and will carry on once it has the results. */
    record UsesTools(List<ToolCall> calls) implements Turn {

        public UsesTools {
            calls = List.copyOf(calls);
        }
    }
}
