package com.boxoffice.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.errors.UnauthorizedException;
import com.boxoffice.core.ai.Reply;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The real SDK code, talking to a pretend API on this laptop. */
class AnthropicClaudeTest {

    private FakeAnthropicApi api;
    private AnthropicClaude claude;
    private final List<String> pieces = new ArrayList<>();

    @BeforeEach
    void start() throws Exception {
        api = new FakeAnthropicApi();
        claude = AnthropicClaude.connect("sk-ant-test-key", api.baseUrl());
    }

    @AfterEach
    void stop() {
        claude.close();
        api.close();
    }

    @Test
    void streamsTheAnswerPieceByPiece() {
        api.streams(FakeAnthropicApi.textAnswer("end_turn", "49 VIP", " tickets", " are left."));

        Reply reply = claude.ask("You help the gate staff.", "How many VIP are left?", pieces::add);

        assertThat(pieces).containsExactly("49 VIP", " tickets", " are left.");
        assertThat(reply).isEqualTo(new Reply.Answered("49 VIP tickets are left."));
    }

    @Test
    void sendsTheModelTheQuestionAndTheKey() throws Exception {
        api.streams(FakeAnthropicApi.textAnswer("end_turn", "Fine."));

        claude.ask("You help the gate staff.", "How many VIP are left?", pieces::add);

        FakeAnthropicApi.Request request = api.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/messages");
        assertThat(request.headers()).containsEntry("x-api-key", "sk-ant-test-key");
        JsonNode body = new ObjectMapper().readTree(request.body());
        assertThat(body.path("model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(body.path("stream").asBoolean()).isTrue();
        assertThat(body.path("system").asText()).isEqualTo("You help the gate staff.");
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo("How many VIP are left?");
    }

    @Test
    void asksForLittleThinkingAndAFallbackModel() throws Exception {
        api.streams(FakeAnthropicApi.textAnswer("end_turn", "Fine."));

        claude.ask("You help the gate staff.", "How many VIP are left?", pieces::add);

        FakeAnthropicApi.Request request = api.requests().getFirst();
        assertThat(request.headers().get("anthropic-beta")).contains("server-side-fallback-2026-07-01");
        JsonNode body = new ObjectMapper().readTree(request.body());
        assertThat(body.path("output_config").path("effort").asText()).isEqualTo("low");
        assertThat(body.path("fallbacks").asText()).isEqualTo("default");
        assertThat(body.path("max_tokens").asLong()).isEqualTo(8_000);
        assertThat(body.has("thinking")).isFalse();
    }

    @Test
    void aRefusalSaysWhy() {
        api.streams(FakeAnthropicApi.refusal("This looks like a request for exploit code."));

        Reply reply = claude.ask("You help the gate staff.", "Something odd", pieces::add);

        assertThat(reply).isEqualTo(new Reply.Refused("This looks like a request for exploit code."));
        assertThat(pieces).isEmpty();
    }

    @Test
    void anAnswerThatRanOutOfRoomIsCutShort() {
        api.streams(FakeAnthropicApi.textAnswer("max_tokens", "Regular has 497 left and"));

        Reply reply = claude.ask("You help the gate staff.", "Tell me everything", pieces::add);

        assertThat(reply).isEqualTo(new Reply.CutShort("Regular has 497 left and"));
    }

    @Test
    void aWrongKeyIsAnUnauthorizedError() {
        api.fails(401, "authentication_error", "invalid x-api-key");

        assertThatThrownBy(() -> claude.ask("You help the gate staff.", "How many VIP are left?", pieces::add))
                .isInstanceOf(UnauthorizedException.class);
    }
}
