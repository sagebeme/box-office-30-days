package com.boxoffice.core.ai;

/**
 * Claude asking for one lookup.
 *
 * @param id    the API's id for this call; the result has to carry it back
 * @param input the inputs as JSON text, exactly as Claude wrote them: check them before use
 */
public record ToolCall(String id, String name, String input) {
}
