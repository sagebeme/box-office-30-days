package com.boxoffice.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SaleOutcome;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SalesSnapshotTest {

    // 21:00 in Nairobi on the night.
    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    private TicketStore store;
    private Gate gate;

    @BeforeEach
    void openTheGate() {
        store = TicketStore.open(folder.resolve("gate.db"));
        SampleEvent.ensureIn(store);
        gate = new Gate(store, SampleEvent.ID, NINE_PM);
    }

    @AfterEach
    void close() {
        store.close();
    }

    private SalesSnapshot snapshot() {
        return new SalesQuestions(new FakeClaude(), store, gate.event(), NINE_PM).snapshot();
    }

    @Test
    void aQuietNightIsAllZeros() {
        assertThat(snapshot().facts()).isEqualTo("""
                Event: Kilele Nights, Sat 14 Nov, 18:00 to Sun 15 Nov, 04:00 (Nairobi time)
                Now: Sat 14 Nov, 21:00
                Regular (REG): KES 1,500 each. 0 paid, 0 guests, 500 left of 500. Takings KES 0
                VIP (VIP): KES 5,000 each. 0 paid, 0 guests, 50 left of 50. Takings KES 0
                Total: 0 paid, 0 guests, 550 left of 550. Takings KES 0
                Through the gates: 0 of the 0 tickets issued
                """);
    }

    @Test
    void countsSalesGuestsAndWhoCameIn() {
        SaleOutcome.Sold vip = (SaleOutcome.Sold) gate.sell("VIP", "Wanjiku");
        gate.sellGroup("REG", "Kamau family", "", 2);
        gate.admitGuest("REG", "DJ Kalonje");
        gate.checkIn(vip.ticket().code());

        assertThat(snapshot().facts()).isEqualTo("""
                Event: Kilele Nights, Sat 14 Nov, 18:00 to Sun 15 Nov, 04:00 (Nairobi time)
                Now: Sat 14 Nov, 21:00
                Regular (REG): KES 1,500 each. 2 paid, 1 guest, 497 left of 500. Takings KES 3,000
                VIP (VIP): KES 5,000 each. 1 paid, 0 guests, 49 left of 50. Takings KES 5,000
                Total: 3 paid, 1 guest, 546 left of 550. Takings KES 8,000
                Through the gates: 1 of the 4 tickets issued
                """);
    }

    @Test
    void holdsNoNamesAndNoPhoneNumbers() {
        gate.sell("VIP", "Wanjiku Kamau", "254712345678");
        gate.admitGuest("REG", "DJ Kalonje");

        assertThat(snapshot().facts())
                .doesNotContain("Wanjiku")
                .doesNotContain("Kalonje")
                .doesNotContain("254712345678")
                .doesNotContain("0712");
    }

    @Test
    void theStoreCountsCheckIns() {
        SaleOutcome.Sold first = (SaleOutcome.Sold) gate.sell("REG", "Otieno");
        gate.sell("REG", "Achieng");
        gate.checkIn(first.ticket().code());
        gate.checkIn(first.ticket().code());

        assertThat(store.countCheckedIn(SampleEvent.ID)).isEqualTo(1);
    }
}
