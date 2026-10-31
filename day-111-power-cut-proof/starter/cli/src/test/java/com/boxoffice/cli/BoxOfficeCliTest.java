package com.boxoffice.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class BoxOfficeCliTest {

    // 21:00 in Nairobi on the night.
    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    /** What one run of the program printed, and the exit code it ended with. */
    record Run(int exitCode, String out, String err) {
    }

    private Run run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cli = BoxOfficeCli.commandLine(NINE_PM);
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));

        String[] withDb = new String[args.length + 2];
        withDb[0] = "--db";
        withDb[1] = folder.resolve("gate.db").toString();
        System.arraycopy(args, 0, withDb, 2, args.length);

        int exitCode = cli.execute(withDb);
        return new Run(exitCode, out.toString(), err.toString());
    }

    private Path guestList(String csv) throws IOException {
        return Files.writeString(folder.resolve("guests.csv"), csv);
    }

    @Test
    void setupAddsTheEvent() {
        Run run = run("setup");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).startsWith("Kilele Nights is ready in ");
    }

    @Test
    void sellsATicket() {
        Run run = run("sell", "VIP", "Wanjiku Kamau");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out().strip()).isEqualTo("Sold VIP-0001 to Wanjiku Kamau for KES 5,000");
    }

    @Test
    void endsWithExitCodeOneWhenASaleIsRejected() {
        Run run = run("sell", "VVIP", "Kamau");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.out().strip()).isEqualTo("Rejected: No ticket type called VVIP");
    }

    @Test
    void importsAGuestList() throws IOException {
        Path csv = guestList("name,type\nWanjiku Kamau,VIP\nOtieno Odhiambo,REG\n");

        Run run = run("import-guests", csv.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.out().strip()).isEqualTo("Admitted 2 of 2 guests");
    }

    @Test
    void importsNothingFromAFileWithABadLine() throws IOException {
        Path csv = guestList("name,type\nWanjiku Kamau,VIP\nOtieno\n");

        Run run = run("import-guests", csv.toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err()).contains("Line 3: expected 2 columns (name,type), found 1");
        assertThat(run("report").out()).contains("Total          0       0    550          KES 0");
    }

    @Test
    void namesTheGuestsThatCouldNotGetIn() throws IOException {
        Path csv = guestList("name,type\nWanjiku Kamau,VIP\nKamau,VVIP\n");

        Run run = run("import-guests", csv.toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.out().strip()).isEqualTo("Admitted 1 of 2 guests");
        assertThat(run.err().strip()).isEqualTo("Kamau: Rejected: No ticket type called VVIP");
    }

    @Test
    void reportsSalesAndGuestsTogether() throws IOException {
        run("sell", "REG", "Otieno");
        run("sell", "VIP", "Wanjiku");
        run("import-guests", guestList("name,type\nAchieng,REG\n").toString());

        Run report = run("report");

        assertThat(report.out().lines()).containsExactly(
                "Kilele Nights · Sat 14 Nov, 18:00",
                "Type        Paid  Guests   Left        Takings",
                "Regular        1       1    498      KES 1,500",
                "VIP            1       0     49      KES 5,000",
                "Total          2       1    547      KES 6,500");
    }

    @Test
    void saysSoWhenTheGuestListIsNotThere() {
        Run run = run("import-guests", folder.resolve("nothere.csv").toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err().strip()).endsWith("There's no file called " + folder.resolve("nothere.csv"));
    }

    @Test
    void sellsAGroupWithTheQuantityOption() {
        Run run = run("sell", "REG", "Kamau family", "--quantity", "3");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out().strip()).isEqualTo("Sold 3 tickets to Kamau family for KES 4,500: REG-0001 to REG-0003");
    }
}
