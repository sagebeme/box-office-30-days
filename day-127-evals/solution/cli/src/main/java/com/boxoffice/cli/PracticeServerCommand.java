package com.boxoffice.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Runs the practice sync server until Ctrl+C. In a second terminal:
 *
 * <pre>
 * java -jar cli/target/boxoffice.jar practice-server --flaky 0.5
 * java -jar cli/target/boxoffice.jar sync
 * </pre>
 */
@Command(name = "practice-server", description = "Runs a pretend sync server on this laptop.",
        mixinStandardHelpOptions = true)
final class PracticeServerCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--port", defaultValue = "8099", description = "The port to listen on (default: ${DEFAULT-VALUE}).")
    int port;

    @Option(names = "--flaky", defaultValue = "0",
            description = "How often to play dead, from 0 (never) to 1 (always).")
    double failRate;

    @Override
    public Integer call() throws IOException, InterruptedException {
        PrintWriter out = spec.commandLine().getOut();
        Runnable flush = out::flush;
        try (PracticeServer server = new PracticeServer(port, failRate,
                () -> ThreadLocalRandom.current().nextDouble(), line -> {
                    out.println(line);
                    flush.run();
                })) {
            out.println("Practice sync server on http://127.0.0.1:" + server.port() + " (Ctrl+C to stop)");
            out.flush();
            new CountDownLatch(1).await();
        }
        return 0;
    }
}
