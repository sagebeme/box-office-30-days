package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PowerCutTest {

    @Test
    void aSaleIsNeverHalfSaved(@TempDir Path folder) throws Exception {
        Path database = folder.resolve("gate.db");
        Process gate = GateProcess.start(database);
        GateProcess.awaitLine(gate, "MID_SALE");

        // Two of the four tickets are written. Pull the plug: no goodbye, no clean-up.
        gate.destroyForcibly().waitFor();

        try (TicketStore store = TicketStore.open(database)) {
            assertThat(store.integrityCheck()).isEqualTo("ok");
            assertThat(store.ticketsInSales(SampleEvent.ID))
                    .isEqualTo(store.ticketsPromisedBySales(SampleEvent.ID));
            // Nothing was committed, so nothing is there: the family simply buys again.
            assertThat(store.tickets(SampleEvent.ID)).isEmpty();
        }
    }
}
