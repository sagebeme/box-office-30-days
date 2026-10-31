package com.boxoffice.server.mpesa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boxoffice.core.Money;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DarajaClientTest {

    private static final DarajaClient.Account ACCOUNT = new DarajaClient.Account(
            "practice-key", "practice-secret", "174379", StkPasswordTest.SANDBOX_PASSKEY, "CustomerPayBillOnline");
    private static final URI CALLBACK = URI.create("https://tickets.example.co.ke/mpesa/callback/s3cret");

    private FakeDaraja safaricom;
    private MovableClock clock;
    private DarajaClient daraja;

    @BeforeEach
    void start() throws Exception {
        safaricom = new FakeDaraja();
        clock = new MovableClock(Instant.parse("2026-11-14T15:30:05Z"));
        daraja = new DarajaClient(HttpClient.newHttpClient(), safaricom.address(), ACCOUNT, CALLBACK, clock);
    }

    @AfterEach
    void stop() {
        safaricom.close();
    }

    private static StkRequest twoRegular() {
        return new StkRequest("254712345678", Money.ofKes(3_000), "K7MPQ3XW", "Kilele tix");
    }

    @Test
    void anStkPushSendsWhatDarajaAsksFor() {
        StkAnswer answer = daraja.stkPush(twoRegular());

        assertThat(answer.checkoutRequestId()).isEqualTo("ws_CO_191220191020363925");
        FakeDaraja.Received push = safaricom.pushes().getFirst();
        assertThat(push.authorization()).isEqualTo("Bearer token-1");
        JsonNode body = JsonMapper.builder().build().readTree(push.body());
        assertThat(body.path("BusinessShortCode").asString()).isEqualTo("174379");
        assertThat(body.path("Timestamp").asString()).isEqualTo("20261114183005");
        assertThat(body.path("Password").asString())
                .isEqualTo(StkPassword.password("174379", StkPasswordTest.SANDBOX_PASSKEY, "20261114183005"));
        assertThat(body.path("TransactionType").asString()).isEqualTo("CustomerPayBillOnline");
        // A whole number of shillings, as a number: 3000, not "3000" or 300000 cents.
        assertThat(body.path("Amount").isNumber()).isTrue();
        assertThat(body.path("Amount").asLong()).isEqualTo(3_000);
        assertThat(body.path("PartyA").asString()).isEqualTo("254712345678");
        assertThat(body.path("PartyB").asString()).isEqualTo("174379");
        assertThat(body.path("PhoneNumber").asString()).isEqualTo("254712345678");
        assertThat(body.path("CallBackURL").asString()).isEqualTo(CALLBACK.toString());
        assertThat(body.path("AccountReference").asString()).isEqualTo("K7MPQ3XW");
        assertThat(body.path("TransactionDesc").asString()).isEqualTo("Kilele tix");
    }

    @Test
    void theTokenIsAskedForWithTheConsumerKeyAndSecret() {
        daraja.stkPush(twoRegular());

        // base64("practice-key:practice-secret")
        assertThat(safaricom.tokenAuthorizations()).containsExactly("Basic cHJhY3RpY2Uta2V5OnByYWN0aWNlLXNlY3JldA==");
    }

    @Test
    void oneTokenServesManyPayments() {
        daraja.stkPush(twoRegular());
        daraja.stkPush(twoRegular());
        clock.moveOn(Duration.ofMinutes(50));
        daraja.stkPush(twoRegular());

        assertThat(safaricom.tokensGiven()).isEqualTo(1);
    }

    @Test
    void aNewTokenIsFetchedAMinuteBeforeTheOldOneRunsOut() {
        daraja.stkPush(twoRegular());
        // The token lasts 3599 seconds. 3540 seconds later it has 59 left: time for a new one.
        clock.moveOn(Duration.ofSeconds(3_540));
        daraja.stkPush(twoRegular());

        assertThat(safaricom.tokensGiven()).isEqualTo(2);
        assertThat(safaricom.pushes().getLast().authorization()).isEqualTo("Bearer token-2");
    }

    @Test
    void tenPaymentsAtOnceShareOneTokenRequest() throws Exception {
        List<Callable<StkAnswer>> fans = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            fans.add(() -> daraja.stkPush(twoRegular()));
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (Future<StkAnswer> answer : pool.invokeAll(fans)) {
                answer.get();
            }
        }

        assertThat(safaricom.tokensGiven()).isEqualTo(1);
    }

    @Test
    void a200WithANonZeroResponseCodeIsStillANo() {
        safaricom.answerPushesWith(200, """
                {"ResponseCode":"1","ResponseDescription":"The balance is insufficient for the transaction"}""");

        assertThatThrownBy(() -> daraja.stkPush(twoRegular()))
                .isInstanceOf(DarajaException.class)
                .hasMessage("M-Pesa refused the payment request: The balance is insufficient for the transaction");
    }

    @Test
    void anErrorAnswerSaysWhatDarajaSaid() {
        safaricom.answerPushesWith(400, """
                {"requestId":"1-2-3","errorCode":"400.002.02","errorMessage":"Bad Request - Invalid PhoneNumber"}""");

        assertThatThrownBy(() -> daraja.stkPush(twoRegular()))
                .isInstanceOf(DarajaException.class)
                .hasMessage("M-Pesa refused the payment request: Bad Request - Invalid PhoneNumber");
    }

    @Test
    void anHtmlErrorPageIsStillADarajaException() {
        safaricom.answerPushesWith(502, "<html><body>Bad Gateway</body></html>");

        assertThatThrownBy(() -> daraja.stkPush(twoRegular()))
                .isInstanceOf(DarajaException.class)
                .hasMessageContaining("HTTP 502");
    }

    @Test
    void aWrongKeyOrSecretStopsBeforeAnyPush() {
        safaricom.refuseTokens();

        assertThatThrownBy(() -> daraja.stkPush(twoRegular()))
                .isInstanceOf(DarajaException.class)
                .hasMessage("M-Pesa didn't accept our consumer key and secret (HTTP 400)");
        assertThat(safaricom.pushes()).isEmpty();
    }

    @Test
    void aPushIsSentOnceEvenWhenItFails() {
        safaricom.answerPushesWith(503, "{\"errorMessage\":\"Service unavailable\"}");

        assertThatThrownBy(() -> daraja.stkPush(twoRegular())).isInstanceOf(DarajaException.class);
        // Not retried: a second push is a second prompt, and maybe a second payment.
        assertThat(safaricom.pushes()).hasSize(1);
    }

    @Test
    void noSafaricomAtAllIsADarajaException() {
        safaricom.close();

        assertThatThrownBy(() -> daraja.stkPush(twoRegular()))
                .isInstanceOf(DarajaException.class)
                .hasMessage("Couldn't reach M-Pesa. Try again in a minute");
    }

    @Test
    void theAccountNeverPrintsItsSecrets() {
        assertThat(ACCOUNT.toString()).doesNotContain("practice-secret", StkPasswordTest.SANDBOX_PASSKEY, "practice-key");
    }
}
