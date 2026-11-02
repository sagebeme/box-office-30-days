package com.boxoffice.core;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * A tiny gate program for PowerCutTest. It starts a group sale of four VIP
 * tickets and, after writing the second ticket, prints MID_SALE and waits
 * there, so the test can pull the plug at the worst possible moment.
 *
 * <pre>java com.boxoffice.core.CrashingGate gate.db</pre>
 */
public final class CrashingGate {

    private CrashingGate() {
    }

    public static void main(String[] args) throws InterruptedException {
        TicketStore store = TicketStore.open(Path.of(args[0]));
        SampleEvent.ensureIn(store);
        Gate gate = new Gate(store, SampleEvent.ID, Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC));

        int[] written = {0};
        store.afterEachTicket(() -> {
            written[0]++;
            if (written[0] == 2) {
                System.out.println("MID_SALE");
                System.out.flush();
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        gate.sellGroup("VIP", "Kamau family", "", 4);
        System.out.println("DONE");
    }
}
