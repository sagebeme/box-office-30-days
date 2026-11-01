package com.boxoffice.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** The server started the way the Docker image starts it: with the prod profile. */
class ProductionTest {

    @TempDir
    Path data;

    /**
     * What a real server's environment would hold, with sandbox M-Pesa. The
     * same names as the environment variables, because that's what
     * application.properties reads: boxoffice.mpesa.mode=${MPESA_MODE:stub}.
     */
    private Map<String, Object> goodSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("PORT", "0");
        settings.put("BOXOFFICE_DATA_DIR", data.toString());
        settings.put("BOXOFFICE_ADMIN_KEY", "test-admin-key-made-up-for-tests-prod");
        settings.put("MPESA_MODE", "sandbox");
        settings.put("MPESA_CONSUMER_KEY", "sandbox-key");
        settings.put("MPESA_CONSUMER_SECRET", "sandbox-secret");
        settings.put("MPESA_PASSKEY", "sandbox-passkey");
        settings.put("MPESA_CALLBACK_BASE", "https://tickets.example.co.ke");
        settings.put("MPESA_CALLBACK_SECRET", "made-up-callback-secret-for-tests-only");
        return settings;
    }

    private ConfigurableApplicationContext start(Map<String, Object> settings) {
        return new SpringApplicationBuilder(SyncServerApplication.class)
                .profiles("prod")
                .properties(settings)
                .run();
    }

    private static HttpResponse<String> get(ConfigurableApplicationContext server, String path) throws Exception {
        String port = server.getEnvironment().getProperty("local.server.port");
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void practicePaymentsStopTheServerStarting() {
        Map<String, Object> settings = goodSettings();
        settings.put("MPESA_MODE", "stub");

        assertThatThrownBy(() -> start(settings))
                .rootCause()
                .hasMessageContaining("MPESA_MODE is stub: no practice payments in production");
    }

    @Test
    void withGoodSettingsItStartsAndKeepsItsDataInTheDataFolder() throws Exception {
        try (ConfigurableApplicationContext server = start(goodSettings())) {
            assertThat(get(server, "/actuator/health/readiness").statusCode()).isEqualTo(200);
            assertThat(data.resolve("sync-server-db.mv.db")).exists();
        }
    }

    @Test
    void strangersSeeUpOrDownAndNothingMore() throws Exception {
        try (ConfigurableApplicationContext server = start(goodSettings())) {
            assertThat(get(server, "/actuator/health").body())
                    .contains("\"status\":\"UP\"")
                    .doesNotContain("components", "details", "flaggedOrders", "sandbox");
        }
    }

    @Test
    void thePretendToPayButtonsDontExist() throws Exception {
        try (ConfigurableApplicationContext server = start(goodSettings())) {
            HttpResponse<String> pretend = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                                    + server.getEnvironment().getProperty("local.server.port")
                                    + "/practice/orders/ABCD-EFGH/pay"))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(pretend.statusCode()).isEqualTo(404);
        }
    }

    @Test
    void theDataFolderIsEmptyBeforeTheServerStarts() throws Exception {
        try (var files = Files.list(data)) {
            assertThat(files).isEmpty();
        }
    }
}
