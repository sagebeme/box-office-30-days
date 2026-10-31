package com.boxoffice.server;

import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Small helpers the server tests share. */
final class ServerTestSupport {

    static final String ADMIN_KEY = "test-admin-key";

    static final String OTIENO = """
            {"eventId":"kilele-2026","tickets":[{"code":"A-REG-0001","type":"REG","buyer":"Otieno",\
            "phone":"","priceCents":150000,"soldAt":"2026-11-14T18:00:00Z"}]}""";

    private ServerTestSupport() {
    }

    /** Every test starts with an empty server. */
    static void emptyTheServer(JdbcClient db) {
        db.sql("delete from tickets").update();
        db.sql("delete from sales").update();
        db.sql("delete from devices").update();
    }

    static MvcTestResult register(MockMvcTester mvc, String adminKey, String gate) {
        return mvc.post().uri("/admin/devices")
                .header("X-Admin-Key", adminKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gate\":\"" + gate + "\"}")
                .exchange();
    }

    /** Registers the gate and returns its token. */
    static String tokenFor(MockMvcTester mvc, String gate) {
        try {
            return JsonPath.read(register(mvc, ADMIN_KEY, gate).getResponse().getContentAsString(), "$.token");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A sale of {@code count} tickets of one type, as the gate's JSON. Codes run from number {@code first}. */
    static String saleOf(String gate, String type, int first, int count) {
        StringBuilder tickets = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                tickets.append(',');
            }
            tickets.append("{\"code\":\"%s-%s-%04d\",\"type\":\"%s\",\"buyer\":\"Fan\",\"phone\":\"\",\"priceCents\":500000,\"soldAt\":\"2026-11-14T18:00:00Z\"}"
                    .formatted(gate, type, first + i, type));
        }
        return "{\"eventId\":\"kilele-2026\",\"tickets\":[" + tickets + "]}";
    }

    /** The JSON as the server reads it, for calling Sales directly. */
    static SaleRequest sale(String json) {
        return tools.jackson.databind.json.JsonMapper.builder().build().readValue(json, SaleRequest.class);
    }

    static MvcTestResult sendSale(MockMvcTester mvc, String token, String key, String sale) {
        MockMvcTester.MockMvcRequestBuilder request = mvc.post().uri("/sync/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sale);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return request.exchange();
    }
}
