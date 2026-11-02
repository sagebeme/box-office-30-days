package com.boxoffice.cli;

import com.boxoffice.core.lan.GateFeedServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** Lets the other gate read this gate's sales over the local network, until Ctrl+C. */
@Command(name = "serve-feed", description = "Shares this gate's sales with the other gate on the same network.",
        mixinStandardHelpOptions = true)
final class ServeFeedCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--port", defaultValue = "8100", description = "The port to listen on (default: ${DEFAULT-VALUE}).")
    int port;

    @Override
    public Integer call() throws IOException, InterruptedException {
        PrintWriter out = spec.commandLine().getOut();
        GateFeedServer started;
        try {
            started = new GateFeedServer(cli.database, port);
        } catch (IllegalStateException e) {
            spec.commandLine().getErr().println(e.getMessage());
            return 1;
        }
        try (GateFeedServer feed = started) {
            out.println("Sharing sales from " + cli.database + " on port " + feed.port() + " (Ctrl+C to stop)");
            out.println("On the other gate: pull --from http://<this laptop's IP address>:" + feed.port());
            out.flush();
            new CountDownLatch(1).await();
        }
        return 0;
    }
}
