package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GateTest {

    private final Event smallEvent = new Event("test", "Test Night", List.of(
            new TicketType("REG", "Regular", 3),
            new TicketType("VIP", "VIP", 1)));

    @Test
    void sellsATicketWithACodeForItsType() {
        Gate gate = new Gate(smallEvent);

        SaleOutcome outcome = gate.sell("VIP", "Wanjiku");

        assertThat(outcome).isEqualTo(new SaleOutcome.Sold(new Ticket("VIP-0001", "VIP", "Wanjiku")));
    }

    @Test
    void numbersTicketsOneTypeAtATime() {
        Gate gate = new Gate(smallEvent);

        gate.sell("REG", "Otieno");
        gate.sell("VIP", "Wanjiku");
        SaleOutcome second = gate.sell("REG", "Achieng");

        assertThat(second).isEqualTo(new SaleOutcome.Sold(new Ticket("REG-0002", "REG", "Achieng")));
    }

    @Test
    void countsDownWhatIsLeft() {
        Gate gate = new Gate(smallEvent);

        gate.sell("REG", "Otieno");

        assertThat(gate.remaining("REG")).isEqualTo(2);
        assertThat(gate.remaining("VIP")).isEqualTo(1);
    }

    @Test
    void saysSoldOutWhenNoneAreLeft() {
        Gate gate = new Gate(smallEvent);
        gate.sell("VIP", "Wanjiku");

        assertThat(gate.sell("VIP", "Kamau")).isEqualTo(new SaleOutcome.SoldOut("VIP"));
        assertThat(gate.ticketsSold()).hasSize(1);
    }

    @Test
    void rejectsATypeTheEventDoesNotSell() {
        Gate gate = new Gate(smallEvent);

        assertThat(gate.sell("VVIP", "Kamau"))
                .isEqualTo(new SaleOutcome.Rejected("No ticket type called VVIP"));
        assertThat(gate.remaining("VVIP")).isZero();
    }

    @Test
    void rejectsASaleWithNoBuyer() {
        Gate gate = new Gate(smallEvent);

        assertThat(gate.sell("REG", "  "))
                .isEqualTo(new SaleOutcome.Rejected("A ticket needs a buyer's name"));
        assertThat(gate.ticketsSold()).isEmpty();
    }

    @Test
    void tidiesTheBuyersName() {
        Gate gate = new Gate(smallEvent);

        gate.sell("REG", "  Otieno ");

        assertThat(gate.ticketsSold().getFirst().buyer()).isEqualTo("Otieno");
    }
}
