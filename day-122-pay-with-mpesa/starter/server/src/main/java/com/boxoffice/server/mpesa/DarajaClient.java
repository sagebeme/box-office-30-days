package com.boxoffice.server.mpesa;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The real Daraja: the sandbox (https://sandbox.safaricom.co.ke) or live (https://api.safaricom.co.ke). */
public class DarajaClient implements Daraja {

    public static final URI SANDBOX = URI.create("https://sandbox.safaricom.co.ke");
    public static final URI LIVE = URI.create("https://api.safaricom.co.ke");

    /**
     * Our M-Pesa account. Every field but the shortcode is a secret.
     *
     * @param transactionType CustomerPayBillOnline for a paybill, CustomerBuyGoodsOnline for a till
     */
    public record Account(String consumerKey, String consumerSecret, String shortcode, String passkey,
                          String transactionType) {

        @Override
        public String toString() {
            // A record's own toString would print the secrets into any log line that mentions it.
            return "Account[shortcode=" + shortcode + ", transactionType=" + transactionType + ", secrets hidden]";
        }
    }

    /** Ask for a new token this long before the old one runs out, so it never runs out mid-request. */
    static final Duration REFRESH_EARLY = Duration.ofSeconds(60);

    private static final Duration WAIT = Duration.ofSeconds(15);

    private final HttpClient http;
    private final URI host;
    private final Account account;
    private final URI callbackUrl;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    private String token;
    private Instant tokenGoodUntil = Instant.MIN;

    public DarajaClient(HttpClient http, URI host, Account account, URI callbackUrl, Clock clock) {
        this.http = http;
        this.host = host;
        this.account = account;
        this.callbackUrl = callbackUrl;
        this.clock = clock;
    }

    @Override
    public StkAnswer stkPush(StkRequest request) {
        String timestamp = StkPassword.timestamp(clock.instant());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", account.shortcode());
        body.put("Password", StkPassword.password(account.shortcode(), account.passkey(), timestamp));
        body.put("Timestamp", timestamp);
        body.put("TransactionType", account.transactionType());
        body.put("Amount", request.shillings());
        body.put("PartyA", request.phone());
        body.put("PartyB", account.shortcode());
        body.put("PhoneNumber", request.phone());
        body.put("CallBackURL", callbackUrl.toString());
        body.put("AccountReference", request.accountReference());
        body.put("TransactionDesc", request.description());

        HttpResponse<String> response = send(HttpRequest.newBuilder(host.resolve("/mpesa/stkpush/v1/processrequest"))
                .timeout(WAIT)
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build());
        JsonNode answer = read(response);
        if (response.statusCode() != 200) {
            throw new DarajaException("M-Pesa refused the payment request: "
                    + answer.path("errorMessage").asString("HTTP " + response.statusCode()));
        }
        // A 200 isn't a yes: ResponseCode must be "0" too.
        if (!"0".equals(answer.path("ResponseCode").asString(""))) {
            throw new DarajaException("M-Pesa refused the payment request: "
                    + answer.path("ResponseDescription").asString("no reason given"));
        }
        return new StkAnswer(answer.path("CheckoutRequestID").asString(), answer.path("MerchantRequestID").asString());
    }

    /**
     * A token for the next request: the one we have, or a new one if it's
     * about to run out.
     *
     * <p>synchronized: when ten fans pay at once and the token has just run
     * out, the first thread fetches a new one while the other nine wait here,
     * then use it. One request to Safaricom, not ten.
     */
    synchronized String token() {
        Instant now = clock.instant();
        // TODO Day 122, step 3: keep the token instead of asking for a new one every time.
        //   1. Here: if token isn't null and now is before tokenGoodUntil, return token.
        //   2. At the end: set tokenGoodUntil to now + expires_in seconds - REFRESH_EARLY.
        // Then ten fans paying at once share one token request (the method is synchronized).
        String keyAndSecret = account.consumerKey() + ":" + account.consumerSecret();
        HttpResponse<String> response = send(HttpRequest.newBuilder(
                        host.resolve("/oauth/v1/generate?grant_type=client_credentials"))
                .timeout(WAIT)
                .header("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(keyAndSecret.getBytes(StandardCharsets.UTF_8)))
                .GET()
                .build());
        JsonNode answer = read(response);
        if (response.statusCode() != 200 || answer.path("access_token").asString("").isEmpty()) {
            throw new DarajaException("M-Pesa didn't accept our consumer key and secret (HTTP "
                    + response.statusCode() + ")");
        }
        // expires_in comes as a string: "3599".
        long seconds = Long.parseLong(answer.path("expires_in").asString("0").strip());
        token = answer.path("access_token").asString();
        return token;
    }

    /**
     * Sends once, never again: an STK push isn't idempotent. Sent twice, the
     * fan gets two prompts and might pay twice.
     */
    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new DarajaException("Couldn't reach M-Pesa. Try again in a minute", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DarajaException("Stopped waiting for M-Pesa", e);
        }
    }

    private JsonNode read(HttpResponse<String> response) {
        try {
            return json.readTree(response.body());
        } catch (JacksonException e) {
            // Gateways in front of Daraja sometimes answer an HTML error page.
            throw new DarajaException("M-Pesa sent an answer we can't read (HTTP " + response.statusCode() + ")", e);
        }
    }
}
