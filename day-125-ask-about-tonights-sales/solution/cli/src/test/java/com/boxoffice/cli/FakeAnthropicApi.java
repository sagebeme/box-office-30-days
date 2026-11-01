package com.boxoffice.cli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A pretend Anthropic API on this laptop, so the real SDK code runs in tests
 * with no network and no key. It answers each request with the next queued
 * answer and remembers what it was sent.
 */
final class FakeAnthropicApi implements AutoCloseable {

    /** One request, as it arrived. Header names are lower case. */
    record Request(String method, String path, Map<String, String> headers, String body) {
    }

    private record Answer(int status, String contentType, String body) {
    }

    private final HttpServer server;
    private final Deque<Answer> answers = new ArrayDeque<>();
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());

    FakeAnthropicApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Queues a streamed answer: Server-Sent Events, the way the API streams. */
    synchronized FakeAnthropicApi streams(String events) {
        answers.add(new Answer(200, "text/event-stream", events));
        return this;
    }

    /** Queues an error answer, like a wrong key (401). */
    synchronized FakeAnthropicApi fails(int status, String type, String message) {
        answers.add(new Answer(status, "application/json",
                "{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"" + message + "\"}}"));
        return this;
    }

    List<Request> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new TreeMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), String.join(",", values)));
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, body));

        Answer answer = answers.isEmpty() ? new Answer(500, "application/json",
                "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"The fake has no more answers\"}}")
                : answers.poll();
        byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", answer.contentType());
        // No retries: the SDK would otherwise ask again after a 500, and eat the next queued answer.
        exchange.getResponseHeaders().set("x-should-retry", "false");
        exchange.sendResponseHeaders(answer.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ---- Building streamed answers ----

    /** A whole streamed message: some hidden thinking, then the text in pieces, then how it stopped. */
    static String textAnswer(String stopReason, String... pieces) {
        StringBuilder events = new StringBuilder(start());
        events.append(thinkingBlock(0));
        events.append(event("content_block_start",
                "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}"));
        for (String piece : pieces) {
            events.append(event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":1,"
                    + "\"delta\":{\"type\":\"text_delta\",\"text\":" + quoted(piece) + "}}"));
        }
        events.append(event("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":1}"));
        events.append(stop(stopReason, null));
        return events.toString();
    }

    /** A message Claude declined, with the reason the API gives. */
    static String refusal(String explanation) {
        return start() + thinkingBlock(0) + stop("refusal", explanation);
    }

    static String start() {
        return event("message_start", "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_fake\","
                + "\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\",\"content\":[],"
                + "\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":120,\"output_tokens\":1}}}");
    }

    // With display omitted, a thinking block streams no words, only a signature.
    static String thinkingBlock(int index) {
        return event("content_block_start", "{\"type\":\"content_block_start\",\"index\":" + index
                + ",\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"\"}}")
                + event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":" + index
                + ",\"delta\":{\"type\":\"signature_delta\",\"signature\":\"c2lnbmVk\"}}")
                + event("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":" + index + "}");
    }

    static String stop(String stopReason, String refusalExplanation) {
        String details = refusalExplanation == null ? "null"
                : "{\"type\":\"refusal\",\"category\":\"cyber\",\"explanation\":" + quoted(refusalExplanation) + "}";
        return event("message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"" + stopReason
                + "\",\"stop_sequence\":null,\"stop_details\":" + details + "},\"usage\":{\"output_tokens\":42}}")
                + event("message_stop", "{\"type\":\"message_stop\"}");
    }

    static String event(String name, String json) {
        return "event: " + name + "\ndata: " + json + "\n\n";
    }

    static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
