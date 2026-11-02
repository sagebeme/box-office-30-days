package com.boxoffice.core.sync;

import com.boxoffice.core.Ticket;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * A sale the way the server sees it, turned into JSON by Jackson:
 *
 * <pre>
 * {"eventId":"kilele-2026","tickets":[{"code":"REG-0001","type":"REG","buyer":"Otieno",
 *  "phone":"","priceCents":150000,"soldAt":"2026-11-14T18:00:00Z"}]}
 * </pre>
 *
 * Money goes as whole cents and times as UTC text, so nothing is lost on the way.
 */
public record SalePayload(String eventId, List<TicketPayload> tickets) {

    public record TicketPayload(String code, String type, String buyer, String phone, long priceCents,
                                String soldAt) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    public static SalePayload of(String eventId, List<Ticket> tickets) {
        return new SalePayload(eventId, tickets.stream()
                .map(t -> new TicketPayload(t.code(), t.typeCode(), t.buyer(), t.phone(), t.price().cents(),
                        t.soldAt().toString()))
                .toList());
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A sale should always turn into JSON", e);
        }
    }

    public static SalePayload fromJson(String json) {
        try {
            return JSON.readValue(json, SalePayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("That isn't a sale: " + e.getOriginalMessage(), e);
        }
    }
}
