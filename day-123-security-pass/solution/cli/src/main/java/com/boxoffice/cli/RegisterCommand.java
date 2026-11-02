package com.boxoffice.cli;

import com.boxoffice.core.TicketStore;
import com.boxoffice.core.net.NetworkException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Logs this laptop in to the sync server, once, before the night:
 *
 * <pre>
 * BOXOFFICE_ADMIN_KEY=... java -jar cli/target/boxoffice.jar register --server http://127.0.0.1:8080
 * </pre>
 *
 * The server answers with a token, which goes into gate.db. From then on
 * sync logs in with it. The token is never printed: it's a password.
 */
@Command(name = "register", description = "Logs this gate laptop in to the sync server.",
        mixinStandardHelpOptions = true)
final class RegisterCommand implements Callable<Integer> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--server", defaultValue = "http://127.0.0.1:8080",
            description = "The sync server (default: ${DEFAULT-VALUE}).")
    URI server;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        String adminKey = cli.environment.get("BOXOFFICE_ADMIN_KEY");
        if (adminKey == null || adminKey.isBlank()) {
            err.println("Set BOXOFFICE_ADMIN_KEY to the key the sync server was started with");
            return 1;
        }
        try (TicketStore store = TicketStore.open(cli.database)) {
            String gate = store.gateId();
            if (gate.isEmpty()) {
                err.println("Give this laptop a gate id first: setup --gate A");
                return 1;
            }
            HttpRequest request = HttpRequest.newBuilder(server.resolve("/admin/devices"))
                    .header("Content-Type", "application/json")
                    .header("X-Admin-Key", adminKey)
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                            JSON.createObjectNode().put("gate", gate))))
                    .build();
            HttpResponse<String> response = cli.http.apply(out::println).send(request);
            JsonNode answer = JSON.readTree(response.body());
            if (response.statusCode() != 201) {
                err.println("The server said no: " + answer.path("error").asText("HTTP " + response.statusCode()));
                return 1;
            }
            store.setSyncToken(answer.path("token").asText());
            out.println("Gate " + gate + " is registered with " + server + ". sync logs in with it from now on.");
            return 0;
        } catch (NetworkException e) {
            err.println("Couldn't reach " + server + ": " + e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println("The server's answer made no sense");
            return 1;
        }
    }
}
