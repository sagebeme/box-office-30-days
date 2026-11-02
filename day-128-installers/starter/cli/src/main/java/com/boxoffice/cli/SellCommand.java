package com.boxoffice.cli;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SaleOutcome;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "sell", description = "Sells one ticket, or several with --quantity.",
        mixinStandardHelpOptions = true)
final class SellCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Parameters(index = "0", description = "The ticket type: REG or VIP.")
    String type;

    @Parameters(index = "1", description = "The buyer's name. Use quotes if it has a space.")
    String buyer;

    @Option(names = {"-q", "--quantity"}, defaultValue = "1",
            description = "How many tickets, all saved as one sale (default: ${DEFAULT-VALUE}).")
    int quantity;

    @Override
    public Integer call() {
        try (TicketStore store = TicketStore.open(cli.database)) {
            SampleEvent.ensureIn(store);
            Gate gate = new Gate(store, SampleEvent.ID, cli.clock);
            SaleOutcome outcome = quantity == 1 ? gate.sell(type, buyer) : gate.sellGroup(type, buyer, "", quantity);
            spec.commandLine().getOut().println(outcome.describe());
            // Exit code 0 means it worked; anything else tells a script it didn't.
            return outcome instanceof SaleOutcome.Sold || outcome instanceof SaleOutcome.SoldGroup ? 0 : 1;
        }
    }
}
