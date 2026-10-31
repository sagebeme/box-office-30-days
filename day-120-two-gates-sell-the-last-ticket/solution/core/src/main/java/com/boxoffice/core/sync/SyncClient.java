package com.boxoffice.core.sync;

/** Tells the server about one sale. HttpSyncClient really does; tests use a fake. */
@FunctionalInterface
public interface SyncClient {

    /**
     * Returns when the server has the sale.
     *
     * @throws com.boxoffice.core.net.NetworkException when the server couldn't be reached: try later
     * @throws SyncRefused when the server answered, and the answer was no
     */
    void push(OutboxEntry entry);
}
