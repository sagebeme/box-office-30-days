package com.boxoffice.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks the Dockerfile and .dockerignore follow the rules, the way a
 * reviewer would. The tests run in the server folder, so they're one up.
 */
class DeploymentFilesTest {

    private static final Path DOCKERFILE = Path.of("..", "Dockerfile");
    private static final Path DOCKERIGNORE = Path.of("..", ".dockerignore");

    /** The Dockerfile's instructions, without comments or blank lines. */
    private static List<String> instructions() throws IOException {
        return Files.readAllLines(DOCKERFILE).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    @Test
    void theImageIsAJava21RuntimeNotAWholeJdk() throws IOException {
        assertThat(instructions().getFirst()).startsWith("FROM eclipse-temurin:21-jre");
    }

    @Test
    void theServerDoesntRunAsRoot() throws IOException {
        List<String> lines = instructions();
        String user = lines.stream().filter(line -> line.startsWith("USER ")).reduce((first, last) -> last).orElse("");

        assertThat(user).isNotEmpty().isNotEqualTo("USER root").isNotEqualTo("USER 0");
        // It switches user before it starts the server.
        assertThat(lines.indexOf(user)).isLessThan(indexOf(lines, "ENTRYPOINT"));
    }

    @Test
    void itRunsTheProdProfileWithItsDataInAVolume() throws IOException {
        String all = String.join("\n", instructions());

        assertThat(all).contains("SPRING_PROFILES_ACTIVE=prod", "BOXOFFICE_DATA_DIR=/data", "VOLUME /data",
                "EXPOSE 8080");
    }

    @Test
    void noSecretIsBakedIntoTheImage() throws IOException {
        String all = String.join("\n", instructions());

        assertThat(all).doesNotContain("BOXOFFICE_ADMIN_KEY", "MPESA_CONSUMER", "MPESA_PASSKEY",
                "MPESA_CALLBACK_SECRET", ".env");
    }

    @Test
    void dockerIsSentOnlyTheJar() throws IOException {
        List<String> rules = Files.readAllLines(DOCKERIGNORE).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();

        assertThat(rules).containsExactly("*", "!server/target/boxoffice-server.jar");
    }

    private static int indexOf(List<String> lines, String start) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(start)) {
                return i;
            }
        }
        return -1;
    }
}
