package com.boxoffice.core.ai;

import java.util.function.Consumer;

/**
 * Something that answers a question about the night. The real one talks to
 * the Anthropic API (the cli module's AnthropicClaude); tests use FakeClaude,
 * so they run with no network and no API key.
 */
public interface Claude {

    /**
     * Asks one question.
     *
     * @param system   the instructions and facts Claude works from
     * @param question what the gate staff typed
     * @param onText   gets each piece of the answer as it arrives, so the screen fills while Claude is still writing
     */
    Reply ask(String system, String question, Consumer<String> onText);
}
