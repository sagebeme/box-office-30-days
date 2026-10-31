package com.boxoffice.core.sync;

import com.boxoffice.core.net.ResilientHttp;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Sends sales to the sync server:
 *
 * <pre>
 * POST /sync/sales
 * Content-Type: application/json
 * Idempotency-Key: 6f1c...      (the same key every time this sale is sent)
 *
 * {"eventId":"kilele-2026","tickets":[...]}
 * </pre>
 *
 * The key is what makes the POST safe to send again: if the first try got
 * through and only the answer was lost, the server sees the key again and
 * doesn't save the sale twice. And because the key is there, ResilientHttp
 * (Day 115) is allowed to retry it.
 */
public final class HttpSyncClient implements SyncClient {

    private final URI salesUrl;
    private final ResilientHttp http;

    /** @param server like http://127.0.0.1:8099 */
    public HttpSyncClient(URI server, ResilientHttp http) {
        this.salesUrl = server.resolve("/sync/sales");
        this.http = http;
    }

    @Override
    public void push(OutboxEntry entry) {
        HttpRequest request = HttpRequest.newBuilder(salesUrl)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", entry.key())
                .POST(HttpRequest.BodyPublishers.ofString(entry.payload()))
                .build();
        HttpResponse<String> response = http.send(request);
        if (response.statusCode() / 100 != 2) {
            throw new SyncRefused("The server said no: HTTP " + response.statusCode());
        }
    }
}
