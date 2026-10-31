package com.boxoffice.cli;

import com.boxoffice.core.Event;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "setup", description = "Adds Kilele Nights to the database, if it isn't there yet.",
        mixinStandardHelpOptions = true)
final class SetupCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--gate", description = "This laptop's gate id, like A or B (Day 117).")
    String gateId;

    @Override
    public Integer call() {
        try (TicketStore store = TicketStore.open(cli.database)) {
            Event event = SampleEvent.ensureIn(store);
            if (gateId != null) {
                try {
                    store.setGateId(gateId);
                } catch (IllegalArgumentException e) {
                    spec.commandLine().getErr().println(e.getMessage());
                    return 1;
                }
            }
            String gate = store.gateId().isEmpty() ? "" : " as gate " + store.gateId();
            spec.commandLine().getOut().println(event.name() + " is ready in " + cli.database + gate);
        }
        return 0;
    }
}
