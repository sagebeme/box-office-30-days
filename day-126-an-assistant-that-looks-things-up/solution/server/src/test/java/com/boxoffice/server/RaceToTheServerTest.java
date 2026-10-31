package com.boxoffice.server;

import static com.boxoffice.server.ServerTestSupport.saleOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Both gates get their Wi-Fi back in the same second and send the last VIP
 * at the same moment, over real HTTP, each request on its own server thread.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RaceToTheServerTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient db;

    @Autowired
    Devices devices;

    @Autowired
    Sales sales;

    private final ExecutorService gates = Executors.newFixedThreadPool(2);
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void fortyNineVipAreSold() {
        ServerTestSupport.emptyTheServer(db);
        tokenA = devices.register("A");
        tokenB = devices.register("B");
        sales.receive("A", "a-early", ServerTestSupport.sale(saleOf("A", "VIP", 1, 49)));
        // A slow server: a moment between counting and saving, when the other request can sneak in.
        sales.afterCounting(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @AfterEach
    void stopTheGates() {
        sales.afterCounting(() -> { });
        gates.shutdownNow();
    }

    private int send(String token, String key, String sale) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/sync/sales"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(sale))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void onlyOneGateGetsTheLastVip() throws Exception {
        CountDownLatch go = new CountDownLatch(1);

        Future<Integer> a = gates.submit(() -> {
            go.await();
            return send(tokenA, "a-last", saleOf("A", "VIP", 50, 1));
        });
        Future<Integer> b = gates.submit(() -> {
            go.await();
            return send(tokenB, "b-last", saleOf("B", "VIP", 1, 1));
        });
        go.countDown();

        assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 409);
        assertThat(sales.ticketCount("kilele-2026", "VIP")).isEqualTo(50);
    }
}
