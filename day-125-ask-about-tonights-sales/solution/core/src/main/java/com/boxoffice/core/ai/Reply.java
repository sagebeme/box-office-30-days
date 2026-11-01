package com.boxoffice.core.ai;

/** How a question to Claude ended. */
public sealed interface Reply {

    /** The whole answer. */
    record Answered(String text) implements Reply {
    }

    /** Claude ran out of room before it finished: the text stops mid-answer. */
    record CutShort(String text) implements Reply {
    }

    /** Claude declined to answer. Throw away any text that came before. */
    record Refused(String why) implements Reply {
    }
}
