package com.boxoffice.core.lan;

import com.boxoffice.core.TicketStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

/**
 * Lets the other gate read this gate's sales over the local network:
 *
 * <pre>
 * GET /feed/gate               which gate this is: {"gate":"A","sales":[]}
 * GET /feed/sales?after=7      the sales after outbox id 7, oldest first, up to 100
 * </pre>
 *
 * It opens its own connection to gate.db. With WAL, a reader never sees a
 * sale that's half saved, and never holds up the gate that's selling.
 *
 * <p>Day 123: every request needs {@code Authorization: Bearer <venue key>}.
 * The feed has buyers' names and phone numbers in it, and anyone can join the
 * venue Wi-Fi.
 */
public final class GateFeedServer implements AutoCloseable {

    static final int PAGE_SIZE = 100;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TicketStore store;
    private final String venueKey;
    private final HttpServer server;

    /**
     * @param port 0 for any free port. Every network card listens, so the other laptop can reach it.
     * @throws IllegalStateException if this gate has no venue key yet
     */
    public GateFeedServer(Path database, int port) throws IOException {
        store = TicketStore.open(database);
        venueKey = store.venueKey();
        if (venueKey.isEmpty()) {
            store.close();
            throw new IllegalStateException("Set a venue key first, the same on both gates: setup --venue-key ...");
        }
        // One request at a time (the default): the store's one connection isn't shared between threads.
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/feed/sales", exchange -> {
            if (knowsTheKey(exchange)) {
                serveSales(exchange);
            }
        });
        server.createContext("/feed/gate", exchange -> {
            if (knowsTheKey(exchange)) {
                answer(exchange, 200, JSON.writeValueAsString(new FeedPage(store.gateId(), List.of())));
            }
        });
        server.start();
    }

    /** Lets the request through if it has our venue key, or answers 401 and says no more. */
    private boolean knowsTheKey(HttpExchange exchange) throws IOException {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        String sent = header != null && header.startsWith("Bearer ") ? header.substring("Bearer ".length()) : "";
        // MessageDigest.isEqual takes as long whatever the bytes, so timing gives nothing away.
        if (MessageDigest.isEqual(sent.getBytes(StandardCharsets.UTF_8), venueKey.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        exchange.getRequestBody().readAllBytes();
        answer(exchange, 401, "{\"error\":\"Wrong venue key\"}");
        return false;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void serveSales(HttpExchange exchange) throws IOException {
        long after;
        try {
            after = afterFrom(exchange.getRequestURI().getRawQuery());
        } catch (NumberFormatException e) {
            answer(exchange, 400, "after must be a number");
            return;
        }
        FeedPage page = new FeedPage(store.gateId(), store.ownSalesAfter(after, PAGE_SIZE));
        answer(exchange, 200, JSON.writeValueAsString(page));
    }

    // "after=7" -> 7. No query at all means from the start.
    private static long afterFrom(String query) {
        if (query == null) {
            return 0;
        }
        for (String part : query.split("&")) {
            if (part.startsWith("after=")) {
                return Long.parseLong(part.substring("after=".length()));
            }
        }
        return 0;
    }

    private static void answer(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        store.close();
    }
}
