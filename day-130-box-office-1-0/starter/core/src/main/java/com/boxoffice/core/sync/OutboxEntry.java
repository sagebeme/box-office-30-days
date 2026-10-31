package com.boxoffice.core.sync;

/**
 * One sale waiting for the server.
 *
 * @param key      the idempotency key: the same every time this sale is sent
 * @param payload  the sale as JSON, exactly as the server gets it
 * @param attempts how many times sending it has failed so far
 */
public record OutboxEntry(long id, String key, String payload, int attempts) {
}
