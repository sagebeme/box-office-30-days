package com.boxoffice.core.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boxoffice.core.net.ResilientHttp;
import com.boxoffice.core.net.RetryPolicy;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpSyncClientTest {

    private static final OutboxEntry SALE = new OutboxEntry(1, "6f1c2a",
            "{\"eventId\":\"kilele-2026\",\"tickets\":[]}", 0);

    private HttpServer server;
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();

    /** A sync server on this laptop that answers with each status in turn, then 200. */
    private URI answering(int... statuses) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            int status = keys.size() <= statuses.length ? statuses[keys.size() - 1] : 200;
            exchange.sendResponseHeaders(status, 2);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write("{}".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopTheServer() {
        server.stop(0);
    }

    private static ResilientHttp http() {
        return new ResilientHttp(HttpClient.newHttpClient(),
                new RetryPolicy(3, Duration.ofMillis(500), Duration.ofSeconds(4)),
                Duration.ofSeconds(2), wait -> { }, () -> 1.0, retry -> { });
    }

    @Test
    void postsTheSaleAsJsonWithItsKey() throws IOException {
        new HttpSyncClient(answering(200), http()).push(SALE);

        assertThat(path.get()).isEqualTo("/sync/sales");
        assertThat(contentType.get()).isEqualTo("application/json");
        assertThat(body.get()).isEqualTo(SALE.payload());
        assertThat(keys).containsExactly("6f1c2a");
    }

    @Test
    void theKeyLetsItTryAgainAfterAServerError() throws IOException {
        new HttpSyncClient(answering(500, 200), http()).push(SALE);

        assertThat(keys).containsExactly("6f1c2a", "6f1c2a");
    }

    @Test
    void aRefusalIsNotANetworkProblem() throws IOException {
        HttpSyncClient client = new HttpSyncClient(answering(400), http());

        assertThatThrownBy(() -> client.push(SALE))
                .isInstanceOf(SyncRefused.class)
                .hasMessage("The server said no: HTTP 400");
    }
}
