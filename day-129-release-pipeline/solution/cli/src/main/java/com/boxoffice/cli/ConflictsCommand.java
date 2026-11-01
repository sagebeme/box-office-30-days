package com.boxoffice.cli;

import com.boxoffice.core.TicketStore;
import com.boxoffice.core.sync.SyncConflict;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** Lists the sales the sync server refused because another gate sold the last places first. */
@Command(name = "conflicts", description = "Lists sales the sync server says were oversold.",
        mixinStandardHelpOptions = true)
final class ConflictsCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        try (TicketStore store = TicketStore.open(cli.database)) {
            List<SyncConflict> conflicts = store.conflicts();
            if (conflicts.isEmpty()) {
                out.println("No conflicts.");
                return 0;
            }
            for (SyncConflict conflict : conflicts) {
                out.println(conflict.describe());
            }
            out.println("Offer each buyer a refund or another ticket type. Their ticket still works at this gate.");
            return 0;
        }
    }
}
