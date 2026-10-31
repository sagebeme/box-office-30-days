package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateTest {

    // 19:00 in Nairobi on the night of the event.
    private static final Instant DURING_THE_EVENT = Instant.parse("2026-11-14T16:00:00Z");

    private final Event smallEvent = new Event("test", "Test Night",
            NairobiTime.at(2026, 11, 14, 18, 0),
            NairobiTime.at(2026, 11, 15, 4, 0),
            List.of(
                    new TicketType("REG", "Regular", Money.ofKes(1_500), 3),
                    new TicketType("VIP", "VIP", Money.ofKes(5_000), 1)));

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private Gate gate() {
        return new Gate(smallEvent, clockAt(DURING_THE_EVENT));
    }

    @Test
    void sellsATicketAtItsTypesPriceAndStampsTheTime() {
        SaleOutcome outcome = gate().sell("VIP", "Wanjiku");

        assertThat(outcome).isEqualTo(new SaleOutcome.Sold(
                new Ticket("VIP-0001", "VIP", "Wanjiku", Money.ofKes(5_000), DURING_THE_EVENT)));
    }

    @Test
    void numbersTicketsOneTypeAtATime() {
        Gate gate = gate();

        gate.sell("REG", "Otieno");
        gate.sell("VIP", "Wanjiku");
        SaleOutcome second = gate.sell("REG", "Achieng");

        assertThat(second).isInstanceOfSatisfying(SaleOutcome.Sold.class,
                sold -> assertThat(sold.ticket().code()).isEqualTo("REG-0002"));
    }

    @Test
    void countsDownWhatIsLeft() {
        Gate gate = gate();

        gate.sell("REG", "Otieno");

        assertThat(gate.remaining("REG")).isEqualTo(2);
        assertThat(gate.remaining("VIP")).isEqualTo(1);
    }

    @Test
    void addsUpTheTakings() {
        Gate gate = gate();

        gate.sell("REG", "Otieno");
        gate.sell("REG", "Achieng");
        gate.sell("VIP", "Wanjiku");

        assertThat(gate.takings()).isEqualTo(Money.ofKes(8_000));
    }

    @Test
    void hasTakenNothingBeforeTheFirstSale() {
        assertThat(gate().takings()).isEqualTo(Money.ZERO);
    }

    @Test
    void saysSoldOutWhenNoneAreLeft() {
        Gate gate = gate();
        gate.sell("VIP", "Wanjiku");

        assertThat(gate.sell("VIP", "Kamau")).isEqualTo(new SaleOutcome.SoldOut("VIP"));
        assertThat(gate.ticketsSold()).hasSize(1);
    }

    @Test
    void stopsSellingOnceTheEventIsOver() {
        // 04:30 in Nairobi the next morning, half an hour after the end.
        Gate gate = new Gate(smallEvent, clockAt(Instant.parse("2026-11-15T01:30:00Z")));

        assertThat(gate.sell("REG", "Kamau"))
                .isEqualTo(new SaleOutcome.Rejected("Test Night ended at Sun 15 Nov, 04:00"));
        assertThat(gate.ticketsSold()).isEmpty();
    }

    @Test
    void rejectsATypeTheEventDoesNotSell() {
        Gate gate = gate();

        assertThat(gate.sell("VVIP", "Kamau"))
                .isEqualTo(new SaleOutcome.Rejected("No ticket type called VVIP"));
        assertThat(gate.remaining("VVIP")).isZero();
    }

    @Test
    void rejectsASaleWithNoBuyer() {
        Gate gate = gate();

        assertThat(gate.sell("REG", "  "))
                .isEqualTo(new SaleOutcome.Rejected("A ticket needs a buyer's name"));
        assertThat(gate.ticketsSold()).isEmpty();
    }

    @Test
    void tidiesTheBuyersName() {
        Gate gate = gate();

        gate.sell("REG", "  Otieno ");

        assertThat(gate.ticketsSold().getFirst().buyer()).isEqualTo("Otieno");
    }
}
