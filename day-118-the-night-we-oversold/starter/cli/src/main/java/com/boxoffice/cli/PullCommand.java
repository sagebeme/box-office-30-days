package com.boxoffice.cli;

import com.boxoffice.core.TicketStore;
import com.boxoffice.core.lan.PeerPuller;
import com.boxoffice.core.lan.PullReport;
import com.boxoffice.core.net.NetworkException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** Copies the other gate's new sales into this gate's database. Safe to run as often as you like. */
@Command(name = "pull", description = "Copies the other gate's new sales over the local network.",
        mixinStandardHelpOptions = true)
final class PullCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--from", required = true, description = "The other gate's feed, like http://192.168.1.21:8100")
    URI from;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        try (TicketStore store = TicketStore.open(cli.database)) {
            PullReport report = new PeerPuller(store, from, cli.http.apply(out::println), cli.clock).pull();
            out.println(report.describe());
            return 0;
        } catch (NetworkException | IllegalStateException e) {
            err.println("Couldn't pull from " + from + ": " + e.getMessage());
            return 1;
        }
    }
}
