package com.boxoffice.cli;

import com.boxoffice.core.Event;
import com.boxoffice.core.PdfExport;
import com.boxoffice.core.SalesReport;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "report", description = "Prints tonight's sales, one line per ticket type.",
        mixinStandardHelpOptions = true)
final class ReportCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--pdf", description = "Also save the report as a PDF file.")
    Path pdf;

    @Override
    public Integer call() {
        try (TicketStore store = TicketStore.open(cli.database)) {
            Event event = SampleEvent.ensureIn(store);
            SalesReport report = SalesReport.of(event, store.tickets(event.id()));
            PrintWriter out = spec.commandLine().getOut();
            out.print(report.render());
            // TODO: when --pdf was given (pdf isn't null): PdfExport.writeReport(report, pdf),
            //       then out.println("Saved " + pdf)
            // print() doesn't flush on its own, and main's System.exit won't wait for it.
            out.flush();
        }
        return 0;
    }
}
