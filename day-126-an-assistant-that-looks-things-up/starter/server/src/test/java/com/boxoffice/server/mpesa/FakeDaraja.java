package com.boxoffice.server.mpesa;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pretend Safaricom on this laptop, for tests: hands out tokens and accepts
 * STK pushes, and remembers what it was sent.
 */
final class FakeDaraja implements AutoCloseable {

    static final String SUCCESS = """
            {"MerchantRequestID":"29115-34620561-1","CheckoutRequestID":"ws_CO_191220191020363925",\
            "ResponseCode":"0","ResponseDescription":"Success. Request accepted for processing",\
            "CustomerMessage":"Success. Request accepted for processing"}""";

    record Received(String authorization, String body) {
    }

    private final HttpServer server;
    private final AtomicInteger tokensGiven = new AtomicInteger();
    private final List<Received> pushes = new CopyOnWriteArrayList<>();
    private final List<String> tokenAuthorizations = new CopyOnWriteArrayList<>();
    private volatile int pushStatus = 200;
    private volatile String pushAnswer = SUCCESS;
    private volatile int tokenStatus = 200;

    FakeDaraja() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/oauth/v1/generate", this::token);
        server.createContext("/mpesa/stkpush/v1/processrequest", this::push);
        server.start();
    }

    URI address() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** The next STK pushes get this answer. */
    void answerPushesWith(int status, String body) {
        pushStatus = status;
        pushAnswer = body;
    }

    void refuseTokens() {
        tokenStatus = 400;
    }

    int tokensGiven() {
        return tokensGiven.get();
    }

    List<String> tokenAuthorizations() {
        return List.copyOf(tokenAuthorizations);
    }

    List<Received> pushes() {
        return List.copyOf(pushes);
    }

    private void token(HttpExchange exchange) throws IOException {
        tokenAuthorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
        if (tokenStatus != 200) {
            answer(exchange, tokenStatus, "{\"errorCode\":\"400.008.01\",\"errorMessage\":\"Invalid Authentication passed\"}");
            return;
        }
        try {
            // A slow Safaricom, so threads asking at once really do overlap.
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int n = tokensGiven.incrementAndGet();
        // expires_in is a string, as Daraja sends it.
        answer(exchange, 200, "{\"access_token\":\"token-" + n + "\",\"expires_in\":\"3599\"}");
    }

    private void push(HttpExchange exchange) throws IOException {
        pushes.add(new Received(exchange.getRequestHeaders().getFirst("Authorization"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        answer(exchange, pushStatus, pushAnswer);
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
