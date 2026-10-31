package com.boxoffice.cli;

import com.boxoffice.core.sync.SalePayload;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;

/**
 * A pretend sync server for practising on one laptop, before the real one
 * (Day 119). It keeps sales in memory, saves each idempotency key's sale only
 * once, and with a fail rate it plays dead now and then, like a server at the
 * end of bad Wi-Fi.
 */
final class PracticeServer implements AutoCloseable {

    private final HttpServer server;
    private final double failRate;
    private final DoubleSupplier rolls;
    private final Consumer<String> log;
    private final Map<String, SalePayload> sales = new LinkedHashMap<>();

    /**
     * @param port     0 for any free port
     * @param failRate 0 never fails, 0.5 answers 503 to about half the requests
     */
    PracticeServer(int port, double failRate, DoubleSupplier rolls, Consumer<String> log) throws IOException {
        this.failRate = failRate;
        this.rolls = rolls;
        this.log = log;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/sync/sales", this::receiveSale);
        server.createContext("/", exchange -> answer(exchange, 200, "Box Office practice server"));
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    /** Every sale it has, by idempotency key. */
    synchronized Map<String, SalePayload> sales() {
        return Map.copyOf(sales);
    }

    private void receiveSale(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        if (!exchange.getRequestMethod().equals("POST")) {
            answer(exchange, 405, "POST a sale here");
            return;
        }
        if (key == null || key.isBlank()) {
            log.accept("Refused a sale with no Idempotency-Key");
            answer(exchange, 400, "Idempotency-Key header missing");
            return;
        }
        if (rolls.getAsDouble() < failRate) {
            log.accept("Pretending to be down: answered 503");
            answer(exchange, 503, "Try again later");
            return;
        }
        SalePayload sale;
        try {
            sale = SalePayload.fromJson(body);
        } catch (IllegalArgumentException e) {
            answer(exchange, 400, e.getMessage());
            return;
        }
        String codes = sale.tickets().stream().map(SalePayload.TicketPayload::code).collect(Collectors.joining(", "));
        boolean isNew;
        synchronized (this) {
            isNew = sales.putIfAbsent(key, sale) == null;
        }
        if (isNew) {
            String buyer = sale.tickets().isEmpty() ? "nobody" : sale.tickets().getFirst().buyer();
            log.accept("Got " + codes + " for " + buyer);
            answer(exchange, 200, "{\"result\":\"accepted\"}");
        } else {
            log.accept("Already had " + codes + ": not saved twice");
            answer(exchange, 200, "{\"result\":\"duplicate\"}");
        }
    }

    private static void answer(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
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
