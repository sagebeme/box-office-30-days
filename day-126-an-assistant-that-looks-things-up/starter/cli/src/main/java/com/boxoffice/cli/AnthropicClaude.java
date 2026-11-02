package com.boxoffice.cli;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.RefusalStopDetails;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.boxoffice.core.ai.Claude;
import com.boxoffice.core.ai.Reply;
import com.boxoffice.core.ai.ToolCall;
import com.boxoffice.core.ai.ToolResult;
import com.boxoffice.core.ai.ToolSpec;
import com.boxoffice.core.ai.Turn;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The real Claude, through the Anthropic Java SDK. Each answer is streamed:
 * every piece goes to the screen as it arrives, and the SDK's accumulator
 * builds the whole message alongside, so we can read how it ended.
 */
final class AnthropicClaude implements Claude, AutoCloseable {

    static final String MODEL = "claude-opus-5-5";
    // Room for Claude's thinking and the answer together.
    static final long MAX_TOKENS = 8_000;
    // If Claude Opus 5.5 declines, the API asks the model Anthropic recommends instead, in the same call.
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;

    AnthropicClaude(AnthropicClient client) {
        this.client = client;
    }

    /** @param baseUrl null for the real API; tests point it at a fake one on this laptop */
    static AnthropicClaude connect(String apiKey, String baseUrl) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().apiKey(apiKey);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return new AnthropicClaude(builder.build());
    }

    @Override
    public Chat chat(String system, List<ToolSpec> tools) {
        return new AnthropicChat(system, tools.stream().map(AnthropicClaude::tool).toList());
    }

    /** Our ToolSpec as the SDK's Tool. */
    static Tool tool(ToolSpec spec) {
        // TODO Day 126, step 6: build the SDK's Tool.
        //   Tool.InputSchema.Properties.builder(): for each of spec.properties(),
        //       putAdditionalProperty(name, JsonValue.from(schema)).
        //   Tool.builder().name(...).description(...)
        //       .inputSchema(Tool.InputSchema.builder().properties(...).required(spec.required()).build())
        //       .eagerInputStreaming(true)   (inputs stream as Claude writes them; SalesTools checks them)
        //       .build()
        throw new UnsupportedOperationException("Not written yet: Day 126, step 6");
    }

    static MessageCreateParams params(String system, List<Tool> tools, List<MessageParam> conversation) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(system)
                // Questions about one night's numbers need little thinking; the queue wants the answer now.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .messages(conversation);
        tools.forEach(builder::addTool);
        return builder.build();
    }

    /**
     * One conversation. It keeps every message, Claude's own replies included
     * exactly as they came (thinking and all), because the API needs the whole
     * conversation each time and checks that Claude's part is unchanged.
     */
    private final class AnthropicChat implements Chat {

        private final String system;
        private final List<Tool> tools;
        private final List<MessageParam> conversation = new ArrayList<>();

        AnthropicChat(String system, List<Tool> tools) {
            this.system = system;
            this.tools = tools;
        }

        @Override
        public Turn ask(String question, Consumer<String> onText) {
            conversation.add(MessageParam.builder().role(MessageParam.Role.USER).content(question).build());
            return send(onText);
        }

        @Override
        public Turn answer(List<ToolResult> results, Consumer<String> onText) {
            List<ContentBlockParam> blocks = new ArrayList<>();
            for (ToolResult result : results) {
                blocks.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(result.callId())
                        .content(result.content())
                        .isError(result.failed())
                        .build()));
            }
            conversation.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(blocks).build());
            return send(onText);
        }

        private Turn send(Consumer<String> onText) {
            Message message = streamOrNull(onText);
            if (message == null) {
                // A lookup's input arrived as broken JSON, so there's no call to answer. Ask once more.
                message = streamOrNull(onText);
            }
            if (message == null) {
                return new Turn.Finished(new Reply.Refused("Claude's lookup came back garbled twice. Ask again"));
            }
            conversation.add(message.toParam());
            return turnFrom(message);
        }

        /** The whole message, or null if a lookup's input wasn't JSON. */
        private Message streamOrNull(Consumer<String> onText) {
            try {
                return stream(onText);
            } catch (Exception e) {
                // The SDK reads each lookup's input with a strict JSON parser, and lets the parser's
                // error out as it is. Catch only that: a wrong key or a rate limit must still go up.
                if (e instanceof JsonProcessingException) {
                    return null;
                }
                throw e;
            }
        }

        private Message stream(Consumer<String> onText) {
            MessageAccumulator accumulator = MessageAccumulator.create();
            try (StreamResponse<RawMessageStreamEvent> stream =
                         client.messages().createStreaming(params(system, tools, conversation))) {
                stream.stream()
                        .map(accumulator::accumulate)
                        .flatMap(event -> event.contentBlockDelta().stream())
                        .flatMap(delta -> delta.delta().text().stream())
                        .forEach(text -> onText.accept(text.text()));
            }
            return accumulator.message();
        }
    }

    /** How the message ended decides what it is: read that before trusting anything in it. */
    static Turn turnFrom(Message message) {
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        StopReason stop = message.stopReason().orElse(StopReason.END_TURN);
        if (stop.equals(StopReason.REFUSAL)) {
            // A refusal can cut a lookup off halfway: never run this turn's tools.
            return new Turn.Finished(new Reply.Refused(message.stopDetails()
                    .flatMap(RefusalStopDetails::explanation)
                    .orElse("Claude declined to answer")));
        }
        if (stop.equals(StopReason.MAX_TOKENS)) {
            // Out of room: a lookup's input may be cut short too, so don't run it.
            return new Turn.Finished(new Reply.CutShort(text));
        }
        if (stop.equals(StopReason.TOOL_USE)) {
            // TODO Day 126, step 7: a Turn.UsesTools with one ToolCall per tool_use block:
            //   message.content(), each block's toolUse() (an Optional), through callFrom.
            throw new UnsupportedOperationException("Not written yet: Day 126, step 7");
        }
        return new Turn.Finished(new Reply.Answered(text));
    }

    private static ToolCall callFrom(ToolUseBlock block) {
        try {
            return new ToolCall(block.id(), block.name(), ObjectMappers.jsonMapper().writeValueAsString(block._input()));
        } catch (JsonProcessingException e) {
            // Can't happen for a value that was itself read from JSON; SalesTools refuses an empty input anyway.
            return new ToolCall(block.id(), block.name(), "");
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
