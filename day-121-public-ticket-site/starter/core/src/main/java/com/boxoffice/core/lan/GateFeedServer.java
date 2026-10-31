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
 */
public final class GateFeedServer implements AutoCloseable {

    static final int PAGE_SIZE = 100;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TicketStore store;
    private final HttpServer server;

    /** @param port 0 for any free port. Every network card listens, so the other laptop can reach it. */
    public GateFeedServer(Path database, int port) throws IOException {
        store = TicketStore.open(database);
        // One request at a time (the default): the store's one connection isn't shared between threads.
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/feed/sales", this::serveSales);
        server.createContext("/feed/gate",
                exchange -> answer(exchange, 200, JSON.writeValueAsString(new FeedPage(store.gateId(), List.of()))));
        server.start();
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
