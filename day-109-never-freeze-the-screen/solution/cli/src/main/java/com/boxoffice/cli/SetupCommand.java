package com.boxoffice.cli;

import com.boxoffice.core.Event;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "setup", description = "Adds Kilele Nights to the database, if it isn't there yet.")
final class SetupCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        try (TicketStore store = TicketStore.open(cli.database)) {
            Event event = SampleEvent.ensureIn(store);
            spec.commandLine().getOut().println(event.name() + " is ready in " + cli.database);
        }
        return 0;
    }
}
