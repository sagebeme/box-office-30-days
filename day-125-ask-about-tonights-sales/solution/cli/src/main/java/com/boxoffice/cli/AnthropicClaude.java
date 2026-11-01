package com.boxoffice.cli;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.RefusalStopDetails;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.boxoffice.core.ai.Claude;
import com.boxoffice.core.ai.Reply;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The real Claude, through the Anthropic Java SDK. The answer is streamed:
 * each piece goes to the screen as it arrives, and the SDK's accumulator
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

    static MessageCreateParams params(String system, String question) {
        return MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(system)
                // A question about one table of numbers needs little thinking; the queue wants the answer now.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .addUserMessage(question)
                .build();
    }

    @Override
    public Reply ask(String system, String question, Consumer<String> onText) {
        MessageAccumulator accumulator = MessageAccumulator.create();
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params(system, question))) {
            stream.stream()
                    .map(accumulator::accumulate)
                    .flatMap(event -> event.contentBlockDelta().stream())
                    .flatMap(delta -> delta.delta().text().stream())
                    .forEach(text -> onText.accept(text.text()));
        }
        return replyFrom(accumulator.message());
    }

    static Reply replyFrom(Message message) {
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        StopReason stop = message.stopReason().orElse(StopReason.END_TURN);
        if (stop.equals(StopReason.REFUSAL)) {
            return new Reply.Refused(message.stopDetails()
                    .flatMap(RefusalStopDetails::explanation)
                    .orElse("Claude declined to answer"));
        }
        if (stop.equals(StopReason.MAX_TOKENS)) {
            return new Reply.CutShort(text);
        }
        return new Reply.Answered(text);
    }

    @Override
    public void close() {
        client.close();
    }
}
