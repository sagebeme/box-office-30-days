package com.boxoffice.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StartupChecksTest {

    private static final String GOOD_ADMIN_KEY = "test-admin-key-made-up-for-tests";
    private static final String GOOD_SECRET = "made-up-callback-secret-for-tests-only";

    private static MpesaSettings mpesa(String mode, String base, String secret) {
        return new MpesaSettings(mode, "key", "secret", "passkey", "174379", "CustomerPayBillOnline",
                base, secret, List.of());
    }

    @Test
    void goodSecretsPass() {
        assertThat(StartupChecks.problems(GOOD_ADMIN_KEY,
                mpesa("live", "https://tickets.example.co.ke", GOOD_SECRET))).isEmpty();
    }

    @Test
    void noAdminKeyAtAllIsAllowed() {
        assertThat(StartupChecks.problems("", mpesa("stub", "", ""))).isEmpty();
    }

    @Test
    void aShortAdminKeyIsNot() {
        assertThat(StartupChecks.problems("admin", mpesa("stub", "", "")))
                .containsExactly("BOXOFFICE_ADMIN_KEY is too short: use at least 24 random characters"
                        + " (openssl rand -base64 32 makes some)");
    }

    @Test
    void realMpesaNeedsALongCallbackSecretAndHttps() {
        assertThat(StartupChecks.problems(GOOD_ADMIN_KEY, mpesa("sandbox", "http://tickets.example.co.ke", "s3cret")))
                .containsExactly(
                        "MPESA_CALLBACK_SECRET is too short: use at least 32 random characters",
                        "MPESA_CALLBACK_BASE must start with https://: Safaricom won't call plain http");
    }

    @Test
    void theCallbackSecretMustBeSafeInAnAddress() {
        assertThat(StartupChecks.problems(GOOD_ADMIN_KEY,
                mpesa("live", "https://tickets.example.co.ke", "made-up/callback?secret-for-tests-only")))
                .containsExactly("MPESA_CALLBACK_SECRET goes in an address: use only letters, digits, - and _");
    }

    @Test
    void settingsNeverPrintTheirSecrets() {
        assertThat(mpesa("live", "https://tickets.example.co.ke", GOOD_SECRET).toString())
                .doesNotContain(GOOD_SECRET, "passkey");
    }
}
