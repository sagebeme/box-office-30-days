package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalesReportTest {

    private static final Instant NINE_PM = Instant.parse("2026-11-14T18:00:00Z");

    private static Ticket ticket(String code, String type, long kes) {
        return new Ticket(code, type, "Someone", Money.ofKes(kes), NINE_PM);
    }

    private final List<Ticket> tonight = List.of(
            ticket("REG-0001", "REG", 1_500),
            ticket("VIP-0001", "VIP", 5_000),
            ticket("REG-0002", "REG", 1_500),
            ticket("REG-0003", "REG", 0));

    @Test
    void countsPaidTicketsAndGuestsForEachType() {
        SalesReport report = SalesReport.of(SampleEvent.kileleNights(), tonight);

        assertThat(report.lines()).containsExactly(
                new SalesReport.Line("Regular", 2, 1, 497, Money.ofKes(3_000)),
                new SalesReport.Line("VIP", 1, 0, 49, Money.ofKes(5_000)));
        assertThat(report.totalTakings()).isEqualTo(Money.ofKes(8_000));
    }

    @Test
    void rendersATableThatLinesUp() {
        String text = SalesReport.of(SampleEvent.kileleNights(), tonight).render();

        assertThat(text.lines()).containsExactly(
                "Kilele Nights · Sat 14 Nov, 18:00",
                "Type        Paid  Guests   Left        Takings",
                "Regular        2       1    497      KES 3,000",
                "VIP            1       0     49      KES 5,000",
                "Total          3       1    546      KES 8,000");
    }

    @Test
    void aNightWithNoSalesShowsZeros() {
        String text = SalesReport.of(SampleEvent.kileleNights(), List.of()).render();

        assertThat(text.lines()).last().isEqualTo("Total          0       0    550          KES 0");
    }
}
