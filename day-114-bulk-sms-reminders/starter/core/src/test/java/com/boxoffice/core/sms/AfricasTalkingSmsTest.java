package com.boxoffice.core.sms;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Runs a pretend Africa's Talking on this laptop (port 0 means "any free port"),
 * so the test checks exactly what we send, with no account and no internet.
 */
class AfricasTalkingSmsTest {

    private static final String SUCCESS = """
            {"SMSMessageData":{"Message":"Sent to 1/1 Total Cost: KES 0.8000",
             "Recipients":[{"statusCode":101,"number":"+254712345678","status":"Success","cost":"KES 0.8000","messageId":"ATXid_1"}]}}""";

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();

    private URI answering(int status, String answer) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("apiKey"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopTheServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsTheMessageTheWayAfricasTalkingWantsIt() throws IOException {
        AfricasTalkingSms sms = new AfricasTalkingSms(answering(201, SUCCESS), "sandbox", "test-key");

        SmsResult result = sms.send("254712345678", "See you at 18:00!");

        assertThat(result).isEqualTo(SmsResult.sent("254712345678"));
        assertThat(path.get()).isEqualTo("/version1/messaging");
        assertThat(apiKeyHeader.get()).isEqualTo("test-key");
        String form = URLDecoder.decode(body.get(), StandardCharsets.UTF_8);
        assertThat(form).contains("username=sandbox", "to=+254712345678", "message=See you at 18:00!");
    }

    @Test
    void encodesThePlusSoItIsNotReadAsASpace() throws IOException {
        AfricasTalkingSms sms = new AfricasTalkingSms(answering(201, SUCCESS), "sandbox", "test-key");

        sms.send("254712345678", "Hi");

        assertThat(body.get()).contains("to=%2B254712345678");
    }

    @Test
    void reportsARecipientThatFailed() throws IOException {
        String invalid = """
                {"SMSMessageData":{"Message":"Sent to 0/1","Recipients":[{"statusCode":403,"number":"+254712345678","status":"InvalidPhoneNumber"}]}}""";
        AfricasTalkingSms sms = new AfricasTalkingSms(answering(201, invalid), "sandbox", "test-key");

        assertThat(sms.send("254712345678", "Hi")).isEqualTo(SmsResult.failed("254712345678", "InvalidPhoneNumber"));
    }

    @Test
    void reportsAWrongApiKey() throws IOException {
        AfricasTalkingSms sms = new AfricasTalkingSms(answering(401, "The supplied authentication is invalid"),
                "sandbox", "wrong-key");

        assertThat(sms.send("254712345678", "Hi")).isEqualTo(SmsResult.failed("254712345678", "HTTP 401"));
    }

    @Test
    void reportsNoConnectionInsteadOfCrashing() {
        // Nothing listens on port 9 here: like venue Wi-Fi that's down.
        AfricasTalkingSms sms = new AfricasTalkingSms(URI.create("http://127.0.0.1:9"), "sandbox", "test-key");

        SmsResult result = sms.send("254712345678", "Hi");

        assertThat(result.sent()).isFalse();
        assertThat(result.detail()).startsWith("No connection");
    }
}
