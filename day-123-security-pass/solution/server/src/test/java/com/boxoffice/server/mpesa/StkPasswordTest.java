package com.boxoffice.server.mpesa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boxoffice.core.Money;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StkPasswordTest {

    /** The sandbox's public test passkey, from Safaricom's docs. Never a real one in a test. */
    static final String SANDBOX_PASSKEY = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919";

    @Test
    void theTimestampIsOnANairobiClock() {
        // 15:30:05 in London (UTC) is 18:30:05 in Nairobi.
        assertThat(StkPassword.timestamp(Instant.parse("2026-11-14T15:30:05Z"))).isEqualTo("20261114183005");
    }

    @Test
    void justBeforeMidnightInUtcItIsAlreadyTomorrowInNairobi() {
        assertThat(StkPassword.timestamp(Instant.parse("2026-11-14T22:15:00Z"))).isEqualTo("20261115011500");
    }

    @Test
    void thePasswordIsBase64OfShortcodePasskeyAndTimestamp() {
        assertThat(StkPassword.password("174379", SANDBOX_PASSKEY, "20261114183005"))
                .isEqualTo("MTc0Mzc5YmZiMjc5ZjlhYTliZGJjZjE1OGU5N2RkNzFhNDY3Y2QyZTBjODkzMDU5YjEwZjc4ZTZiNzJhZGExZWQyYzkxOTIwMjYxMTE0MTgzMDA1");
    }

    @Test
    void anAmountWithCentsIsRefusedNotRounded() {
        assertThatThrownBy(() -> new StkRequest("254712345678", Money.ofCents(150_050), "K7MPQ3XW", "Kilele tix"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("M-Pesa takes whole shillings only, not KES 1,500.50");
    }

    @Test
    void aPhoneMustBeTwelveDigitsWithNoPlus() {
        assertThatThrownBy(() -> new StkRequest("+254712345678", Money.ofKes(1_500), "K7MPQ3XW", "Kilele tix"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void longReferencesAndDescriptionsAreCutToWhatDarajaTakes() {
        StkRequest request = new StkRequest("254712345678", Money.ofKes(1_500),
                "KILELE-NIGHTS-2026", "Two tickets for Kilele Nights");

        assertThat(request.accountReference()).isEqualTo("KILELE-NIGHT");
        assertThat(request.description()).isEqualTo("Two tickets f");
        assertThat(request.shillings()).isEqualTo(1_500);
    }
}
