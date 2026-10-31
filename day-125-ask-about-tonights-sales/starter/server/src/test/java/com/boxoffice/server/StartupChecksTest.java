package com.boxoffice.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StartupChecksTest {

    private static final String GOOD_ADMIN_KEY = "Vb8Q2nX7kLp4Rt9WmZ3sHc6J";
    private static final String GOOD_SECRET = "h7Gq2LmX9pRt4Vw8Zb3Nc6Kd1Fs5Jy0A";

    private static MpesaSettings mpesa(String mode, String base, String secret) {
        return new MpesaSettings(mode, "key", "secret", "passkey", "174379", "CustomerPayBillOnline",
                base, secret, List.of());
    }

    private static MpesaSettings goodLive() {
        return mpesa("live", "https://tickets.example.co.ke", GOOD_SECRET);
    }

    @Test
    void goodSecretsPass() {
        assertThat(StartupChecks.problems(GOOD_ADMIN_KEY, goodLive(), false)).isEmpty();
        assertThat(StartupChecks.problems(GOOD_ADMIN_KEY, goodLive(), true)).isEmpty();
    }

    @Test
    void noAdminKeyAtAllIsAllowedOnYourLaptop() {
        assertThat(StartupChecks.problems("", mpesa("stub", "", ""), false)).isEmpty();
    }

    @Test
    void aShortAdminKeyIsNot() {
        assertThat(StartupChecks.problems("admin", mpesa("stub", "", ""), false))
                .containsExactly("BOXOFFICE_ADMIN_KEY is too short: use at least 24 random characters"
                        + " (openssl rand -base64 32 makes some)");
    }

    @Test
    void realMpesaNeedsALongCallbackSecretAndHttps() {
        assertThat(StartupChecks.problems(GOOD_ADMIN_KEY,
                mpesa("sandbox", "http://tickets.example.co.ke", "s3cret"), false))
                .containsExactly(
                        "MPESA_CALLBACK_SECRET is too short: use at least 32 random characters",
                        "MPESA_CALLBACK_BASE must start with https://: Safaricom won't call plain http");
    }

    @Test
    void theCallbackSecretMustBeSafeInAnAddress() {
        assertThat(StartupChecks.problems(GOOD_ADMIN_KEY,
                mpesa("live", "https://tickets.example.co.ke", "h7Gq2LmX9pRt4Vw8/b3Nc6Kd1Fs5Jy0A?x"), false))
                .containsExactly("MPESA_CALLBACK_SECRET goes in an address: use only letters, digits, - and _");
    }

    @Test
    void productionRefusesPracticePayments() {
        assertThat(StartupChecks.problems(GOOD_ADMIN_KEY, mpesa("stub", "", ""), true))
                .containsExactly("MPESA_MODE is stub: no practice payments in production. Set it to sandbox or live");
    }

    @Test
    void productionNeedsAnAdminKey() {
        assertThat(StartupChecks.problems("", goodLive(), true))
                .containsExactly("Set BOXOFFICE_ADMIN_KEY: without it, no gate can register");
    }

    @Test
    void settingsNeverPrintTheirSecrets() {
        assertThat(goodLive().toString()).doesNotContain(GOOD_SECRET, "passkey");
    }
}
