package com.boxoffice.core.net;

import static com.boxoffice.core.net.ScriptedServer.answer;
import static com.boxoffice.core.net.ScriptedServer.answerWithRetryAfter;
import static com.boxoffice.core.net.ScriptedServer.hang;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResilientHttpTest {

    // Waits are written down instead of slept, so these tests run in a blink.
    private final List<Duration> waits = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    /** Three attempts, 500 ms then 1 s, and an answer within 300 ms or it counts as no answer. */
    private ResilientHttp http() {
        return new ResilientHttp(HttpClient.newHttpClient(),
                new RetryPolicy(3, Duration.ofMillis(500), Duration.ofSeconds(4)),
                Duration.ofMillis(300), waits::add, () -> 1.0, log::add);
    }

    private static HttpRequest get(URI uri) {
        return HttpRequest.newBuilder(uri).GET().build();
    }

    private static HttpRequest post(URI uri) {
        return HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofString("{}")).build();
    }

    @Test
    void returnsTheAnswerStraightAwayWhenTheServerIsFine() throws IOException {
        try (ScriptedServer server = new ScriptedServer(answer(200))) {
            HttpResponse<String> response = http().send(get(server.uri()));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(server.requests()).isEqualTo(1);
            assertThat(waits).isEmpty();
        }
    }

    @Test
    void triesAgainWhileTheServerIsBusy() throws IOException {
        try (ScriptedServer server = new ScriptedServer(answer(503), answer(503), answer(200))) {
            HttpResponse<String> response = http().send(post(server.uri()));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(server.requests()).isEqualTo(3);
            assertThat(waits).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1));
        }
    }

    @Test
    void saysWhatItIsDoingBeforeEachRetry() throws IOException {
        try (ScriptedServer server = new ScriptedServer(answer(503))) {
            http().send(get(server.uri()));

            assertThat(log).containsExactly("HTTP 503. Trying again in 0.5 s (attempt 2 of 3)");
        }
    }

    @Test
    void givesUpAfterTheLastAttempt() throws IOException {
        try (ScriptedServer server = new ScriptedServer(answer(503), answer(503), answer(503), answer(200))) {
            assertThatThrownBy(() -> http().send(get(server.uri())))
                    .isInstanceOf(NetworkException.class)
                    .hasMessage("Gave up after 3 attempts: HTTP 503");
            assertThat(server.requests()).isEqualTo(3);
        }
    }

    @Test
    void doesNotAskAgainWhenTheAnswerIsNo() throws IOException {
        try (ScriptedServer server = new ScriptedServer(answer(404))) {
            HttpResponse<String> response = http().send(get(server.uri()));

            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(server.requests()).isEqualTo(1);
        }
    }

    @Test
    void stopsWaitingForAServerThatHangs() throws IOException {
        try (ScriptedServer server = new ScriptedServer(hang(), answer(200))) {
            long start = System.nanoTime();

            HttpResponse<String> response = http().send(get(server.uri()));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(server.requests()).isEqualTo(2);
            // The hang lasts 2 s; the time limit is 300 ms.
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1_500));
        }
    }

    @Test
    void neverResendsAPostThatMayHaveArrived() throws IOException {
        try (ScriptedServer server = new ScriptedServer(hang(), answer(200))) {
            assertThatThrownBy(() -> http().send(post(server.uri())))
                    .isInstanceOf(NetworkException.class)
                    .hasMessage("No answer in time, and it isn't safe to send again");
            assertThat(server.requests()).isEqualTo(1);
        }
    }

    @Test
    void neverResendsAPostAfterAServerError() throws IOException {
        try (ScriptedServer server = new ScriptedServer(answer(500), answer(200))) {
            HttpResponse<String> response = http().send(post(server.uri()));

            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(server.requests()).isEqualTo(1);
        }
    }

    @Test
    void resendsAPostThatCarriesAnIdempotencyKey() throws IOException {
        try (ScriptedServer server = new ScriptedServer(answer(500), answer(200))) {
            HttpRequest request = HttpRequest.newBuilder(server.uri())
                    .header("Idempotency-Key", "sale-42")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            assertThat(http().send(request).statusCode()).isEqualTo(200);
            assertThat(server.idempotencyKeys()).containsExactly("sale-42", "sale-42");
        }
    }

    @Test
    void resendsAPostThatNeverGotThrough() {
        // Nothing listens on port 9: the request can't have arrived, so trying again is safe.
        assertThatThrownBy(() -> http().send(post(URI.create("http://127.0.0.1:9/sync"))))
                .isInstanceOf(NetworkException.class)
                .hasMessage("Gave up after 3 attempts: Can't reach the server");
        assertThat(waits).hasSize(2);
    }

    @Test
    void waitsAsLongAsTheServerAsks() throws IOException {
        try (ScriptedServer server = new ScriptedServer(answerWithRetryAfter(429, "2"), answer(200))) {
            http().send(get(server.uri()));

            assertThat(waits).containsExactly(Duration.ofSeconds(2));
        }
    }

    @Test
    void neverWaitsLongerThanThePolicyAllows() throws IOException {
        try (ScriptedServer server = new ScriptedServer(answerWithRetryAfter(429, "3600"), answer(200))) {
            http().send(get(server.uri()));

            assertThat(waits).containsExactly(Duration.ofSeconds(4));
        }
    }
}
