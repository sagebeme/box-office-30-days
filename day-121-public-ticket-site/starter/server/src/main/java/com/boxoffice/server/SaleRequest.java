package com.boxoffice.server;

import java.util.List;

/** A sale as the gate sends it: the same JSON as the gate's SalePayload (Day 116). */
record SaleRequest(String eventId, List<TicketRequest> tickets) {

    record TicketRequest(String code, String type, String buyer, String phone, long priceCents, String soldAt) {
    }
}
