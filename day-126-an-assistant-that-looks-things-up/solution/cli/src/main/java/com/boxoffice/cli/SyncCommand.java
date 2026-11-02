package com.boxoffice.cli;

import com.boxoffice.core.TicketStore;
import com.boxoffice.core.sync.HttpSyncClient;
import com.boxoffice.core.sync.OutboxSender;
import com.boxoffice.core.sync.SyncReport;
import java.io.PrintWriter;
import java.net.URI;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** Sends every sale in the outbox to the sync server. Safe to run as often as you like. */
@Command(name = "sync", description = "Sends waiting sales to the sync server.", mixinStandardHelpOptions = true)
final class SyncCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--server", defaultValue = "http://127.0.0.1:8099",
            description = "The sync server (default: ${DEFAULT-VALUE}).")
    URI server;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        try (TicketStore store = TicketStore.open(cli.database)) {
            // The token from register (Day 119), or "" for the practice server, which lets anyone in.
            HttpSyncClient client = new HttpSyncClient(server, cli.http.apply(out::println), store.syncToken());
            SyncReport report = new OutboxSender(store, client, cli.clock).sendWaiting();
            out.println(report.describe());
            return report.allSent() ? 0 : 1;
        }
    }
}
