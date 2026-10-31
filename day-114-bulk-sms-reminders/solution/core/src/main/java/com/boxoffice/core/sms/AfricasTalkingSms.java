package com.boxoffice.core.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sends texts through Africa's Talking. Practise on their sandbox: free, no
 * real messages, username "sandbox", and an API key from the sandbox dashboard.
 *
 * <pre>
 * POST https://api.sandbox.africastalking.com/version1/messaging
 * apiKey: ...                     (a header, never in the URL)
 * username=sandbox&amp;to=%2B254712345678&amp;message=...
 * </pre>
 */
public final class AfricasTalkingSms implements SmsGateway {

    public static final URI SANDBOX = URI.create("https://api.sandbox.africastalking.com");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI baseUrl;
    private final String username;
    private final String apiKey;
    // One client for every send: it keeps connections open and is safe to share between threads.
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** @param baseUrl SANDBOX, the live API, or a test server on this laptop */
    public AfricasTalkingSms(URI baseUrl, String username, String apiKey) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.apiKey = apiKey;
    }

    @Override
    public SmsResult send(String phone, String message) {
        String form = formBody(Map.of("username", username, "to", "+" + phone, "message", message));
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("/version1/messaging"))
                .timeout(Duration.ofSeconds(20))
                .header("apiKey", apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201 && response.statusCode() != 200) {
                return SmsResult.failed(phone, "HTTP " + response.statusCode());
            }
            return readAnswer(phone, response.body());
        } catch (IOException e) {
            return SmsResult.failed(phone, "No connection: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SmsResult.failed(phone, "Interrupted");
        }
    }

    /**
     * Africa's Talking answers like this, one recipient per number sent to:
     * {"SMSMessageData":{"Message":"Sent to 1/1","Recipients":[{"number":"+254712345678","status":"Success"}]}}
     */
    static SmsResult readAnswer(String phone, String body) throws IOException {
        JsonNode recipients = JSON.readTree(body).path("SMSMessageData").path("Recipients");
        if (recipients.isEmpty()) {
            return SmsResult.failed(phone, "No recipients in the answer");
        }
        String status = recipients.get(0).path("status").asText();
        return status.equals("Success") ? SmsResult.sent(phone) : SmsResult.failed(phone, status);
    }

    // username=sandbox&to=%2B254712345678&message=Hello%21  ("+" has to become %2B, or it means a space)
    private static String formBody(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(field -> field.getKey() + "=" + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
