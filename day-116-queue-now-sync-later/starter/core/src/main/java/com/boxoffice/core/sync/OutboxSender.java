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
        // TODO: loop for ever (while (true)):
        //   1. List<OutboxEntry> batch = store.waitingToSync(BATCH);
        //      empty? everything's sent: return new SyncReport(sent, 0, "")
        //   2. for each entry: client.push(entry)
        //      - it threw NetworkException or SyncRefused? store.markSyncFailed(entry.id(), e.getMessage())
        //        and stop straight away, so no later sale overtakes this one:
        //        return new SyncReport(sent, store.countWaitingToSync(), e.getMessage())
        //      - it worked? store.markSynced(entry.id(), clock.instant()) and sent++
        throw new UnsupportedOperationException("Not written yet: Day 116, step 4");
    }
}
