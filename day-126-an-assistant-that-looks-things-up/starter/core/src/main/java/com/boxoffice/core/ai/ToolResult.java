package com.boxoffice.core.ai;

/**
 * What a lookup found, going back to Claude.
 *
 * @param failed true when the lookup couldn't run (bad input, unknown tool), so Claude can try again
 */
public record ToolResult(String callId, String content, boolean failed) {

    public static ToolResult found(ToolCall call, String content) {
        return new ToolResult(call.id(), content, false);
    }

    public static ToolResult failed(ToolCall call, String problem) {
        return new ToolResult(call.id(), problem, true);
    }
}
