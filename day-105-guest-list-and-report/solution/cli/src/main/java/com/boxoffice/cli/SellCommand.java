package com.boxoffice.cli;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SaleOutcome;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "sell", description = "Sells one ticket.")
final class SellCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Parameters(index = "0", description = "The ticket type: REG or VIP.")
    String type;

    @Parameters(index = "1", description = "The buyer's name. Use quotes if it has a space.")
    String buyer;

    @Override
    public Integer call() {
        try (TicketStore store = TicketStore.open(cli.database)) {
            SampleEvent.ensureIn(store);
            SaleOutcome outcome = new Gate(store, SampleEvent.ID, cli.clock).sell(type, buyer);
            spec.commandLine().getOut().println(outcome.describe());
            // Exit code 0 means it worked; anything else tells a script it didn't.
            return outcome instanceof SaleOutcome.Sold ? 0 : 1;
        }
    }
}
