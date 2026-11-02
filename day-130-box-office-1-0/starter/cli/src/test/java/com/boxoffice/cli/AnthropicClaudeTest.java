package com.boxoffice.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.errors.UnauthorizedException;
import com.boxoffice.core.ai.Claude;
import com.boxoffice.core.ai.Reply;
import com.boxoffice.core.ai.ToolCall;
import com.boxoffice.core.ai.ToolResult;
import com.boxoffice.core.ai.ToolSpec;
import com.boxoffice.core.ai.Turn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The real SDK code, talking to a pretend API on this laptop. */
class AnthropicClaudeTest {

    private static final ToolSpec FIND_TICKET = new ToolSpec("find_ticket", "Looks up one ticket.",
            Map.of("code", Map.of("type", "string")), List.of("code"));

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

    private Claude.Chat chat() {
        return claude.chat("You help the gate staff.", List.of(FIND_TICKET));
    }

    private JsonNode body(int request) throws Exception {
        return new ObjectMapper().readTree(api.requests().get(request).body());
    }

    @Test
    void streamsTheAnswerPieceByPiece() {
        api.streams(FakeAnthropicApi.textAnswer("end_turn", "49 VIP", " tickets", " are left."));

        Turn turn = chat().ask("How many VIP are left?", pieces::add);

        assertThat(pieces).containsExactly("49 VIP", " tickets", " are left.");
        assertThat(turn).isEqualTo(new Turn.Finished(new Reply.Answered("49 VIP tickets are left.")));
    }

    @Test
    void sendsTheModelTheQuestionAndTheKey() throws Exception {
        api.streams(FakeAnthropicApi.textAnswer("end_turn", "Fine."));

        chat().ask("How many VIP are left?", pieces::add);

        FakeAnthropicApi.Request request = api.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/messages");
        assertThat(request.headers()).containsEntry("x-api-key", "sk-ant-test-key");
        JsonNode body = body(0);
        assertThat(body.path("model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(body.path("stream").asBoolean()).isTrue();
        assertThat(body.path("system").asText()).isEqualTo("You help the gate staff.");
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo("How many VIP are left?");
    }

    @Test
    void asksForLittleThinkingAndAFallbackModel() throws Exception {
        api.streams(FakeAnthropicApi.textAnswer("end_turn", "Fine."));

        chat().ask("How many VIP are left?", pieces::add);

        assertThat(api.requests().getFirst().headers().get("anthropic-beta")).contains("server-side-fallback-2026-07-01");
        JsonNode body = body(0);
        assertThat(body.path("output_config").path("effort").asText()).isEqualTo("low");
        assertThat(body.path("fallbacks").asText()).isEqualTo("default");
        assertThat(body.path("max_tokens").asLong()).isEqualTo(8_000);
        assertThat(body.has("thinking")).isFalse();
    }

    @Test
    void offersTheToolsWithTheirInputsStreamed() throws Exception {
        api.streams(FakeAnthropicApi.textAnswer("end_turn", "Fine."));

        chat().ask("Is VIP-0001 real?", pieces::add);

        JsonNode tool = body(0).path("tools").get(0);
        assertThat(tool.path("name").asText()).isEqualTo("find_ticket");
        assertThat(tool.path("description").asText()).isEqualTo("Looks up one ticket.");
        assertThat(tool.path("input_schema").path("type").asText()).isEqualTo("object");
        assertThat(tool.path("input_schema").path("properties").path("code").path("type").asText()).isEqualTo("string");
        assertThat(tool.path("input_schema").path("required").get(0).asText()).isEqualTo("code");
        assertThat(tool.path("eager_input_streaming").asBoolean()).isTrue();
    }

    @Test
    void aLookupComesBackWithItsInputJoinedUp() {
        api.streams(FakeAnthropicApi.toolUse("tool_use", "toolu_1", "find_ticket", "{\"co", "de\": \"VIP-", "0001\"}"));

        Turn turn = chat().ask("Is VIP-0001 real?", pieces::add);

        assertThat(turn).isEqualTo(new Turn.UsesTools(List.of(
                new ToolCall("toolu_1", "find_ticket", "{\"code\":\"VIP-0001\"}"))));
    }

    @Test
    void theNextRequestCarriesTheWholeConversation() throws Exception {
        api.streams(FakeAnthropicApi.toolUse("tool_use", "toolu_1", "find_ticket", "{\"code\": \"VIP-0001\"}"))
                .streams(FakeAnthropicApi.textAnswer("end_turn", "Yes, sold at 21:05."));
        Claude.Chat chat = chat();
        chat.ask("Is VIP-0001 real?", pieces::add);

        Turn turn = chat.answer(List.of(new ToolResult("toolu_1", "VIP-0001: VIP, KES 5,000", false)), pieces::add);

        assertThat(turn).isEqualTo(new Turn.Finished(new Reply.Answered("Yes, sold at 21:05.")));
        JsonNode messages = body(1).path("messages");
        assertThat(messages).hasSize(3);
        // Claude's own turn goes back exactly as it came: the thinking first, with its signature.
        JsonNode claudesTurn = messages.get(1);
        assertThat(claudesTurn.path("role").asText()).isEqualTo("assistant");
        assertThat(claudesTurn.path("content").get(0).path("type").asText()).isEqualTo("thinking");
        assertThat(claudesTurn.path("content").get(0).path("signature").asText()).isEqualTo("c2lnbmVk");
        assertThat(claudesTurn.path("content").get(1).path("type").asText()).isEqualTo("tool_use");
        assertThat(claudesTurn.path("content").get(1).path("id").asText()).isEqualTo("toolu_1");
        JsonNode result = messages.get(2).path("content").get(0);
        assertThat(messages.get(2).path("role").asText()).isEqualTo("user");
        assertThat(result.path("type").asText()).isEqualTo("tool_result");
        assertThat(result.path("tool_use_id").asText()).isEqualTo("toolu_1");
        assertThat(result.path("content").asText()).isEqualTo("VIP-0001: VIP, KES 5,000");
        assertThat(result.path("is_error").asBoolean()).isFalse();
    }

    @Test
    void aFailedLookupIsSentAsAnError() throws Exception {
        api.streams(FakeAnthropicApi.toolUse("tool_use", "toolu_1", "find_ticket", "{\"code\": \"<b>\"}"))
                .streams(FakeAnthropicApi.textAnswer("end_turn", "That isn't a ticket code."));
        Claude.Chat chat = chat();
        chat.ask("Is <b> real?", pieces::add);

        chat.answer(List.of(new ToolResult("toolu_1", "code should be a ticket code like VIP-0001", true)), pieces::add);

        assertThat(body(1).path("messages").get(2).path("content").get(0).path("is_error").asBoolean()).isTrue();
    }

    @Test
    void aLookupCutOffByMaxTokensIsNeverRun() {
        api.streams(FakeAnthropicApi.toolUse("max_tokens", "toolu_1", "find_ticket", "{\"code\": \"VIP-0001\"}"));

        Turn turn = chat().ask("Is VIP-0001 real?", pieces::add);

        assertThat(turn).isInstanceOf(Turn.Finished.class);
        assertThat(((Turn.Finished) turn).reply()).isInstanceOf(Reply.CutShort.class);
    }

    @Test
    void brokenLookupJsonIsAskedForAgain() {
        api.streams(FakeAnthropicApi.toolUse("tool_use", "toolu_1", "find_ticket", "{\"code\": VIP-0001}"))
                .streams(FakeAnthropicApi.textAnswer("end_turn", "Fine."));

        Turn turn = chat().ask("Is VIP-0001 real?", pieces::add);

        assertThat(turn).isEqualTo(new Turn.Finished(new Reply.Answered("Fine.")));
        assertThat(api.requests()).hasSize(2);
    }

    @Test
    void brokenTwiceGivesUpWithoutRunningAnything() {
        api.streams(FakeAnthropicApi.toolUse("tool_use", "toolu_1", "find_ticket", "{\"code\": \"VIP-"))
                .streams(FakeAnthropicApi.toolUse("max_tokens", "toolu_2", "find_ticket", "{\"code\": \"VIP-"));

        Turn turn = chat().ask("Is VIP-0001 real?", pieces::add);

        assertThat(turn).isEqualTo(new Turn.Finished(
                new Reply.Refused("Claude's lookup came back garbled twice. Ask again")));
    }

    @Test
    void aRefusalSaysWhy() {
        api.streams(FakeAnthropicApi.refusal("This looks like a request for exploit code."));

        Turn turn = chat().ask("Something odd", pieces::add);

        assertThat(turn).isEqualTo(new Turn.Finished(new Reply.Refused("This looks like a request for exploit code.")));
        assertThat(pieces).isEmpty();
    }

    @Test
    void anAnswerThatRanOutOfRoomIsCutShort() {
        api.streams(FakeAnthropicApi.textAnswer("max_tokens", "Regular has 497 left and"));

        Turn turn = chat().ask("Tell me everything", pieces::add);

        assertThat(turn).isEqualTo(new Turn.Finished(new Reply.CutShort("Regular has 497 left and")));
    }

    @Test
    void aWrongKeyIsAnUnauthorizedError() {
        api.fails(401, "authentication_error", "invalid x-api-key");

        assertThatThrownBy(() -> chat().ask("How many VIP are left?", pieces::add))
                .isInstanceOf(UnauthorizedException.class);
    }
}
