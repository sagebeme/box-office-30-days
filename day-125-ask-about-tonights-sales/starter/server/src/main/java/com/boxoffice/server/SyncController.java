package com.boxoffice.server;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where gates send their sales:
 *
 * <pre>
 * POST /sync/sales
 * Authorization: Bearer the-gate's-token
 * Idempotency-Key: 6f1c...
 * {"eventId":"kilele-2026","tickets":[...]}
 *                     -> 201 {"result":"accepted"}, or 200 {"result":"duplicate"} for a repeat,
 *                        or 409 {"result":"oversold","error":"Only 0 VIP places were left"}
 * </pre>
 */
@RestController
class SyncController {

    private final Sales sales;

    SyncController(Sales sales) {
        this.sales = sales;
    }

    @PostMapping("/sync/sales")
    ResponseEntity<Map<String, String>> receive(@RequestAttribute(DeviceLogin.GATE) String gate,
                                                @RequestHeader(name = "Idempotency-Key", required = false) String key,
                                                @RequestBody SaleRequest sale) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "Send an Idempotency-Key header with every sale");
        }
        Sales.Verdict verdict = sales.receive(gate, key, sale);
        return switch (verdict.result()) {
            case ACCEPTED -> ResponseEntity.status(HttpStatus.CREATED).body(Map.of("result", "accepted"));
            case DUPLICATE -> ResponseEntity.ok(Map.of("result", "duplicate"));
            // 409 Conflict: the server got it, kept it, and the answer is no. Sending it again won't change that.
            case OVERSOLD -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("result", "oversold", "error", verdict.detail()));
        };
    }

    /** For ping (Day 115) and for whoever watches the server: no token needed. */
    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
