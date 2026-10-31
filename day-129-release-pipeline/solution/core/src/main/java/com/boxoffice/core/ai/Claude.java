package com.boxoffice.core.ai;

import java.util.List;
import java.util.function.Consumer;

/**
 * Something that answers questions about the night. The real one talks to
 * the Anthropic API (the cli module's AnthropicClaude); tests use FakeClaude,
 * so they run with no network and no API key.
 *
 * Day 126: a question can take several goes now (ask, look something up,
 * carry on), so it happens in a {@link Chat} that remembers what was said.
 */
public interface Claude {

    /**
     * Starts a conversation.
     *
     * @param system the instructions and facts Claude works from
     * @param tools  the lookups Claude may ask for
     */
    Chat chat(String system, List<ToolSpec> tools);

    /** One conversation. Each call adds to it, and Claude sees all of it. */
    interface Chat {

        /**
         * Asks a question.
         *
         * @param onText gets each piece of the answer as it arrives
         */
        Turn ask(String question, Consumer<String> onText);

        /** Hands back what the lookups Claude asked for found, so it can carry on. */
        Turn answer(List<ToolResult> results, Consumer<String> onText);
    }
}
