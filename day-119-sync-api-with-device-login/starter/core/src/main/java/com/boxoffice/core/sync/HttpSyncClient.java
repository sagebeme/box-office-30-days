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
 * Authorization: Bearer ...     (Day 119: this laptop's device token)
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
    private final String token;

    /** For the practice server, which lets anyone in. */
    public HttpSyncClient(URI server, ResilientHttp http) {
        this(server, http, "");
    }

    /**
     * @param server like http://127.0.0.1:8080
     * @param token  this laptop's device token from register, or "" for none
     */
    public HttpSyncClient(URI server, ResilientHttp http, String token) {
        this.salesUrl = server.resolve("/sync/sales");
        this.http = http;
        this.token = token;
    }

    @Override
    public void push(OutboxEntry entry) {
        HttpRequest.Builder request = HttpRequest.newBuilder(salesUrl)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", entry.key())
                .POST(HttpRequest.BodyPublishers.ofString(entry.payload()));
        // TODO: when token isn't empty, add the header "Authorization" with "Bearer " + token
        //       (one space after Bearer). The practice server needs none, so no token means no header.
        HttpResponse<String> response = http.send(request.build());
        if (response.statusCode() == 401) {
            throw new SyncRefused("The server doesn't know this laptop. Run register first");
        }
        if (response.statusCode() / 100 != 2) {
            throw new SyncRefused("The server said no: HTTP " + response.statusCode());
        }
    }
}
