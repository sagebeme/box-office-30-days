package com.boxoffice.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.boxoffice.core.Gate;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import com.boxoffice.core.net.ResilientHttp;
import com.boxoffice.core.net.RetryPolicy;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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

    // The gate's retry settings, but waits are skipped so the tests stay fast.
    private static ResilientHttp httpThatDoesNotSleep(Consumer<String> log) {
        return new ResilientHttp(HttpClient.newHttpClient(), RetryPolicy.GATE_WIFI, Duration.ofSeconds(2),
                wait -> { }, () -> 1.0, log);
    }

    private Run run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cli = BoxOfficeCli.commandLine(NINE_PM, Map.of(), BoxOfficeCliTest::httpThatDoesNotSleep);
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

    @Test
    void savesTheReportAsAPdfToo() throws IOException {
        Path pdf = folder.resolve("report.pdf");

        Run run = run("report", "--pdf", pdf.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).contains("Saved " + pdf);
        assertThat(Files.readAllBytes(pdf)).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void remindsEveryoneWhoLeftAPhoneNumber() {
        run("setup");
        // A sale with a phone, through the core, the way the desktop app makes them.
        try (TicketStore store = TicketStore.open(folder.resolve("gate.db"))) {
            new Gate(store, SampleEvent.ID, NINE_PM)
                    .sell("VIP", "Wanjiku", "0712 345 678");
        }
        run("sell", "REG", "Otieno");

        Run run = run("remind", "--fake");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out().lines()).containsExactly(
                "254712345678: Kilele Nights, Sat 14 Nov, 18:00. Your ticket: VIP-0001. Show the QR code at the gate.",
                "Sent 1 of 1 reminders");
    }

    @Test
    void refusesToSendForRealWithoutAnApiKey() {
        Run run = run("remind");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err().strip()).isEqualTo("Set AT_API_KEY to your Africa's Talking sandbox key, or use --fake");
    }

    @Test
    void pingKeepsTryingUntilTheServerAnswers() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int status = requests.incrementAndGet() == 1 ? 503 : 200;
            exchange.sendResponseHeaders(status, 2);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write("ok".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        try {
            Run run = run("ping", url);

            assertThat(run.exitCode()).isZero();
            assertThat(run.out().lines()).containsExactly(
                    "HTTP 503. Trying again in 0.5 s (attempt 2 of 5)",
                    url + " answered HTTP 200");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void pingGivesUpOnAServerThatIsNotThere() {
        Run run = run("ping", "http://127.0.0.1:9/");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.out().lines()).hasSize(5)
                .last().isEqualTo("Gave up after 5 attempts: Can't reach the server");
    }

    @Test
    void syncSendsEverySaleToTheServer() throws IOException {
        run("sell", "REG", "Otieno");
        run("sell", "VIP", "Wanjiku");
        try (PracticeServer server = new PracticeServer(0, 0, () -> 1.0, line -> { })) {
            Run run = run("sync", "--server", "http://127.0.0.1:" + server.port());

            assertThat(run.exitCode()).isZero();
            assertThat(run.out().strip()).isEqualTo("Sent 2 sales. Nothing waiting.");
            assertThat(server.sales()).hasSize(2);
        }
    }

    @Test
    void syncKeepsTheSalesWhenTheServerIsNotThere() {
        run("sell", "REG", "Otieno");

        Run run = run("sync", "--server", "http://127.0.0.1:9");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.out().lines()).last()
                .isEqualTo("Sent 0 sales. 1 still waiting: Gave up after 5 attempts: Can't reach the server");
    }
}
