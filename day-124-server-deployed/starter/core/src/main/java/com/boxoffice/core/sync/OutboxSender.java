package com.boxoffice.core.sync;

import com.boxoffice.core.TicketStore;
import com.boxoffice.core.net.NetworkException;
import java.time.Clock;
import java.util.List;

/**
 * Empties the outbox: sends each waiting sale to the server, oldest first,
 * and ticks it off once the server has it.
 *
 * <p>It stops at the first sale that fails, so a later sale never reaches the
 * server before an earlier one. What's left waits in the outbox, on disk, for
 * the next try: in a minute, or tomorrow morning.
 */
public final class OutboxSender {

    private static final int BATCH = 50;

    private final TicketStore store;
    private final SyncClient client;
    private final Clock clock;

    public OutboxSender(TicketStore store, SyncClient client, Clock clock) {
        this.store = store;
        this.client = client;
        this.clock = clock;
    }

    public SyncReport sendWaiting() {
        int sent = 0;
        int conflicts = 0;
        while (true) {
            List<OutboxEntry> batch = store.waitingToSync(BATCH);
            if (batch.isEmpty()) {
                return new SyncReport(sent, 0, "", conflicts);
            }
            for (OutboxEntry entry : batch) {
                try {
                    client.push(entry);
                } catch (SaleConflict e) {
                    // The server has decided, and won't change its mind: tell staff, and carry on.
                    store.markConflict(entry.id(), e.getMessage(), clock.instant());
                    conflicts++;
                    continue;
                } catch (NetworkException | SyncRefused e) {
                    store.markSyncFailed(entry.id(), e.getMessage());
                    return new SyncReport(sent, store.countWaitingToSync(), e.getMessage(), conflicts);
                }
                store.markSynced(entry.id(), clock.instant());
                sent++;
            }
        }
    }
}
