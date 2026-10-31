package com.boxoffice.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Starts CrashingGate as a separate Java program, the way a real gate app runs. */
final class GateProcess {

    private GateProcess() {
    }

    static Process start(Path database) throws IOException {
        String java = ProcessHandle.current().info().command().orElse("java");
        return new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                CrashingGate.class.getName(), database.toString())
                .redirectErrorStream(true)
                .start();
    }

    /** Waits (up to 30 seconds) until the program prints this exact line. */
    static void awaitLine(Process process, String expected) throws Exception {
        CompletableFuture<Void> seen = CompletableFuture.runAsync(() -> {
            try (BufferedReader out = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = out.readLine()) != null) {
                    if (line.equals(expected)) {
                        return;
                    }
                }
                throw new IllegalStateException("The gate program ended without printing " + expected);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        seen.get(30, TimeUnit.SECONDS);
    }
}
