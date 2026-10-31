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
        // TODO Day 125, step 4: MessageCreateParams.builder() with
        //   .model(MODEL), .maxTokens(MAX_TOKENS), .system(system), .addUserMessage(question),
        //   .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
        //       (a question about one table of numbers needs little thinking; the queue wants the answer now),
        //   .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
        //   .putAdditionalBodyProperty("fallbacks", JsonValue.from("default")), then .build().
        throw new UnsupportedOperationException("Not written yet: Day 125, step 4");
    }

    @Override
    public Reply ask(String system, String question, Consumer<String> onText) {
        // TODO Day 125, step 5: stream the answer.
        //   MessageAccumulator accumulator = MessageAccumulator.create();
        //   In try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params(...))),
        //   go through stream.stream(): pass every event to accumulator.accumulate(event) (it hands the
        //   event back), keep event.contentBlockDelta(), then delta.delta().text(), and give each
        //   text.text() to onText. After the try, return replyFrom(accumulator.message()).
        throw new UnsupportedOperationException("Not written yet: Day 125, step 5");
    }

    static Reply replyFrom(Message message) {
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        StopReason stop = message.stopReason().orElse(StopReason.END_TURN);
        // TODO Day 125, step 5: read how it stopped before trusting the text.
        //   StopReason.REFUSAL    -> Reply.Refused with message.stopDetails()'s explanation,
        //                            or "Claude declined to answer" if there isn't one
        //   StopReason.MAX_TOKENS -> Reply.CutShort(text)
        //   anything else         -> Reply.Answered(text)
        throw new UnsupportedOperationException("Not written yet: Day 125, step 5");
    }

    @Override
    public void close() {
        client.close();
    }
}
