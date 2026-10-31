package com.boxoffice.server;

import com.boxoffice.server.mpesa.Daraja;
import com.boxoffice.server.mpesa.DarajaClient;
import com.boxoffice.server.mpesa.StubDaraja;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks which M-Pesa the server talks to, from MPESA_MODE. */
@Configuration
@EnableConfigurationProperties(MpesaSettings.class)
class MpesaConfig {

    @Bean
    Daraja daraja(MpesaSettings settings, Clock clock) {
        return switch (settings.mode()) {
            case "stub" -> new StubDaraja();
            case "sandbox" -> client(settings, DarajaClient.SANDBOX, clock);
            case "live" -> client(settings, DarajaClient.LIVE, clock);
            default -> throw new IllegalStateException(
                    "MPESA_MODE is stub, sandbox or live, not " + settings.mode());
        };
    }

    private static DarajaClient client(MpesaSettings settings, java.net.URI host, Clock clock) {
        // Stop at start-up, not at the first fan's payment.
        require(settings.consumerKey(), "MPESA_CONSUMER_KEY");
        require(settings.consumerSecret(), "MPESA_CONSUMER_SECRET");
        require(settings.passkey(), "MPESA_PASSKEY");
        require(settings.callbackBase(), "MPESA_CALLBACK_BASE");
        require(settings.callbackSecret(), "MPESA_CALLBACK_SECRET");
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new DarajaClient(http, host, settings.account(), settings.callbackUrl(), clock);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + name + " to use M-Pesa " + "(or MPESA_MODE=stub to practise)");
        }
    }
}
