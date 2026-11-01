package com.boxoffice.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReceiptTest {

    private static final Instant NINE_PM = Instant.parse("2026-11-14T18:00:00Z");
    private final Event kilele = SampleEvent.kileleNights();

    @Test
    void laysOutATicketForTheTillPrinter() {
        Ticket ticket = new Ticket("VIP-0001", "VIP", "Wanjiku Kamau", Money.ofKes(5_000), NINE_PM);

        assertThat(Receipt.forTicket(kilele, ticket)).containsExactly(
                "KILELE NIGHTS",
                "Sat 14 Nov, 18:00",
                "--------------------------------",
                "Ticket  VIP-0001",
                "Type    VIP",
                "Buyer   Wanjiku Kamau",
                "Price   KES 5,000",
                "Sold    Sat 14 Nov, 21:00",
                "--------------------------------",
                "Show this at the gate.",
                "One entry per ticket.");
    }

    @Test
    void usesTheTypesNameNotItsCode() {
        Ticket ticket = new Ticket("REG-0001", "REG", "Otieno", Money.ofKes(1_500), NINE_PM);

        assertThat(Receipt.forTicket(kilele, ticket)).contains("Type    Regular");
    }

    @Test
    void saysGuestInsteadOfKesZero() {
        Ticket guest = new Ticket("VIP-0002", "VIP", "Achieng", Money.ZERO, NINE_PM);

        assertThat(Receipt.forTicket(kilele, guest)).contains("Price   Guest");
    }

    @Test
    void cutsALongNameToFitTheRoll() {
        Ticket ticket = new Ticket("REG-0001", "REG", "Wanjiku Kamau Njoroge Wambui Otieno", Money.ofKes(1_500), NINE_PM);

        String buyerLine = Receipt.forTicket(kilele, ticket).get(5);

        assertThat(buyerLine).hasSize(Receipt.WIDTH).startsWith("Buyer   Wanjiku").endsWith("…");
    }

    @Test
    void noLineIsWiderThanTheRoll() {
        Ticket ticket = new Ticket("VIP-0001", "VIP", "Wanjiku Kamau", Money.ofKes(5_000), NINE_PM);

        assertThat(Receipt.forTicket(kilele, ticket)).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(Receipt.WIDTH));
    }
}
