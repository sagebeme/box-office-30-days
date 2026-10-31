package com.boxoffice.cli;

import com.boxoffice.core.Gate;
import com.boxoffice.core.Guest;
import com.boxoffice.core.GuestListCsv;
import com.boxoffice.core.GuestListException;
import com.boxoffice.core.SaleOutcome;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "import-guests", description = "Lets everyone on a guest-list CSV in for free.")
final class ImportGuestsCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Parameters(index = "0", description = "The CSV file, with the header name,type.")
    Path file;

    @Override
    public Integer call() throws IOException {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        List<Guest> guests;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            guests = GuestListCsv.parse(reader);
        } catch (NoSuchFileException e) {
            err.println("There's no file called " + file);
            return 1;
        } catch (GuestListException e) {
            // Nothing is imported from a file with a bad line, so a fixed file can be run again.
            err.println(file + ": " + e.getMessage());
            return 1;
        }

        int admitted = 0;
        try (TicketStore store = TicketStore.open(cli.database)) {
            SampleEvent.ensureIn(store);
            Gate gate = new Gate(store, SampleEvent.ID, cli.clock);
            for (Guest guest : guests) {
                SaleOutcome outcome = gate.admitGuest(guest.typeCode(), guest.name());
                if (outcome instanceof SaleOutcome.Sold) {
                    admitted++;
                } else {
                    err.println(guest.name() + ": " + outcome.describe());
                }
            }
        }
        out.println("Admitted " + admitted + " of " + guests.size() + " guests");
        return admitted == guests.size() ? 0 : 1;
    }
}
