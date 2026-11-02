package com.boxoffice.core.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A bad server on this laptop, for tests: it answers each request with the next
 * step of its script ("503", "hang for a second", "200"...), then 200 for ever.
 */
final class ScriptedServer implements AutoCloseable {

    /** One answer: a status, an optional Retry-After, and how long to stall first. */
    record Step(int status, String retryAfter, long stallMillis) {
    }

    static Step answer(int status) {
        return new Step(status, null, 0);
    }

    static Step answerWithRetryAfter(int status, String seconds) {
        return new Step(status, seconds, 0);
    }

    /** Stalls longer than any client in the tests waits, then answers 200. */
    static Step hang() {
        return new Step(200, null, 2_000);
    }

    private final HttpServer server;
    private final ConcurrentLinkedQueue<Step> script;
    private final AtomicInteger requests = new AtomicInteger();
    private final List<String> idempotencyKeys = new ArrayList<>();

    ScriptedServer(Step... steps) throws IOException {
        script = new ConcurrentLinkedQueue<>(List.of(steps));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // A thread per request, so one that hangs doesn't hold up the next.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        synchronized (idempotencyKeys) {
            idempotencyKeys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
        }
        exchange.getRequestBody().readAllBytes();
        Step step = script.poll();
        if (step == null) {
            step = answer(200);
        }
        if (step.stallMillis() > 0) {
            try {
                Thread.sleep(step.stallMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (step.retryAfter() != null) {
            exchange.getResponseHeaders().add("Retry-After", step.retryAfter());
        }
        byte[] body = ("answer " + step.status()).getBytes(StandardCharsets.UTF_8);
        try {
            exchange.sendResponseHeaders(step.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } catch (IOException clientAlreadyGaveUp) {
            // The client timed out and hung up: nobody to answer.
        }
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/sync");
    }

    int requests() {
        return requests.get();
    }

    List<String> idempotencyKeys() {
        synchronized (idempotencyKeys) {
            return List.copyOf(idempotencyKeys);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
