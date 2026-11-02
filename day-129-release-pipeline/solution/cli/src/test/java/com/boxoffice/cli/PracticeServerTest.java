package com.boxoffice.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeServerTest {

    private static final String SALE = """
            {"eventId":"kilele-2026","tickets":[{"code":"REG-0001","type":"REG","buyer":"Otieno",\
            "phone":"","priceCents":150000,"soldAt":"2026-11-14T18:00:00Z"}]}""";

    private final List<String> log = new ArrayList<>();

    private static int post(PracticeServer server, String key) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + server.port() + "/sync/sales"))
                .POST(HttpRequest.BodyPublishers.ofString(SALE));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void savesASaleSentTwiceOnlyOnce() throws Exception {
        try (PracticeServer server = new PracticeServer(0, 0, () -> 1.0, log::add)) {
            assertThat(post(server, "key-1")).isEqualTo(200);
            assertThat(post(server, "key-1")).isEqualTo(200);

            assertThat(server.sales()).containsOnlyKeys("key-1");
            assertThat(log).containsExactly("Got REG-0001 for Otieno", "Already had REG-0001: not saved twice");
        }
    }

    @Test
    void refusesASaleWithNoKey() throws Exception {
        try (PracticeServer server = new PracticeServer(0, 0, () -> 1.0, log::add)) {
            assertThat(post(server, null)).isEqualTo(400);
            assertThat(server.sales()).isEmpty();
        }
    }

    @Test
    void aFlakyServerSometimesPlaysDead() throws Exception {
        try (PracticeServer server = new PracticeServer(0, 0.5, () -> 0.1, log::add)) {
            assertThat(post(server, "key-1")).isEqualTo(503);
            assertThat(server.sales()).isEmpty();
        }
    }
}
