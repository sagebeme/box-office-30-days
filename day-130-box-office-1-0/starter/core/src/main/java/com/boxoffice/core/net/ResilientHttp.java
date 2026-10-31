package com.boxoffice.core.net;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/**
 * An HTTP client for venue Wi-Fi: every request has a time limit, and a
 * request that failed is tried again after a wait, when that's safe.
 *
 * <p><b>When is it safe to try again?</b> If the request never reached the
 * server (it couldn't connect), always. If the server answered 429 or 503
 * ("too busy, come back later"), always: it says it did nothing. But if a POST
 * timed out, the server may have done the work and only the answer got lost.
 * Sending it again could sell a ticket twice. So a POST is only repeated after
 * a timeout or a 500 when it carries an {@code Idempotency-Key} header (Day 116),
 * which lets the server spot the repeat.
 */
public final class ResilientHttp {

    private static final Set<String> REPEATABLE_METHODS = Set.of("GET", "HEAD", "PUT", "DELETE", "OPTIONS");

    private final HttpClient client;
    private final RetryPolicy policy;
    private final Duration timeout;
    private final Sleeper sleeper;
    private final DoubleSupplier rolls;
    private final Consumer<String> log;

    /**
     * @param timeout how long to wait for an answer to one attempt
     * @param rolls   random numbers from 0 to 1 for the jitter
     * @param log     told about every retry, so a person can see what's going on
     */
    public ResilientHttp(HttpClient client, RetryPolicy policy, Duration timeout,
                         Sleeper sleeper, DoubleSupplier rolls, Consumer<String> log) {
        this.client = client;
        this.policy = policy;
        this.timeout = timeout;
        this.sleeper = sleeper;
        this.rolls = rolls;
        this.log = log;
    }

    /** The settings the gate uses: 3 s to connect, 5 s for an answer, GATE_WIFI retries. */
    public static ResilientHttp forTheGate(Consumer<String> log) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        return new ResilientHttp(client, RetryPolicy.GATE_WIFI, Duration.ofSeconds(5),
                Sleeper.REAL, () -> ThreadLocalRandom.current().nextDouble(), log);
    }

    /**
     * Sends the request, trying again when that's safe and worth it.
     *
     * @return the server's answer. A 4xx comes back as an answer, not an exception:
     *         asking again wouldn't change it.
     * @throws NetworkException when every attempt failed, or the one that failed can't be repeated
     */
    public HttpResponse<String> send(HttpRequest request) {
        HttpRequest timed = withTimeout(request);
        boolean repeatable = repeatable(request);
        String problem = "";
        Duration serverAskedUsToWait = null;

        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            if (attempt > 1) {
                Duration wait = serverAskedUsToWait != null
                        ? serverAskedUsToWait
                        : policy.delayBefore(attempt, rolls.getAsDouble());
                log.accept(problem + ". Trying again in " + seconds(wait)
                        + " (attempt " + attempt + " of " + policy.maxAttempts() + ")");
                pause(wait, attempt);
            }
            try {
                HttpResponse<String> response = client.send(timed, HttpResponse.BodyHandlers.ofString());
                if (!worthRetrying(response.statusCode(), repeatable)) {
                    return response;
                }
                problem = "HTTP " + response.statusCode();
                serverAskedUsToWait = retryAfter(response).orElse(null);
            } catch (IOException e) {
                problem = describe(e);
                serverAskedUsToWait = null;
                if (!neverArrived(e) && !repeatable) {
                    throw new NetworkException(problem + ", and it isn't safe to send again", attempt, e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NetworkException("Stopped", attempt, e);
            }
        }
        throw new NetworkException("Gave up after " + policy.maxAttempts() + " attempts: " + problem,
                policy.maxAttempts(), null);
    }

    /** The same request, with our time limit on it. */
    HttpRequest withTimeout(HttpRequest request) {
        return HttpRequest.newBuilder(request, (name, value) -> true).timeout(timeout).build();
    }

    /** Could sending this twice do the work twice? GET and friends: no. POST: only if it has an Idempotency-Key. */
    static boolean repeatable(HttpRequest request) {
        return REPEATABLE_METHODS.contains(request.method())
                || request.headers().firstValue("Idempotency-Key").isPresent();
    }

    /**
     * 429 Too Many Requests and 503 Service Unavailable mean "I did nothing, try later".
     * 500, 502 and 504 mean "something broke", maybe halfway, so only a repeatable request tries again.
     * Anything else (200, 404...) is the answer: asking again won't change it.
     */
    static boolean worthRetrying(int status, boolean repeatable) {
        return switch (status) {
            case 429, 503 -> true;
            case 500, 502, 504 -> repeatable;
            default -> false;
        };
    }

    // Couldn't connect at all, so the server can't have seen the request.
    private static boolean neverArrived(IOException e) {
        return e instanceof ConnectException || e instanceof HttpConnectTimeoutException;
    }

    private static String describe(IOException e) {
        if (e instanceof HttpConnectTimeoutException) {
            return "Couldn't connect in time";
        }
        if (e instanceof HttpTimeoutException) {
            return "No answer in time";
        }
        if (e instanceof ConnectException) {
            return "Can't reach the server";
        }
        return "Connection dropped";
    }

    // "Retry-After: 2" means wait 2 seconds. We never wait longer than the policy's longest wait.
    private Optional<Duration> retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .filter(value -> value.strip().matches("\\d{1,6}"))
                .map(value -> Duration.ofSeconds(Long.parseLong(value.strip())))
                .map(wait -> wait.compareTo(policy.maxDelay()) > 0 ? policy.maxDelay() : wait);
    }

    private void pause(Duration wait, int attempt) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetworkException("Stopped", attempt - 1, e);
        }
    }

    private static String seconds(Duration wait) {
        return String.format(Locale.ROOT, "%.1f s", wait.toMillis() / 1000.0);
    }
}
