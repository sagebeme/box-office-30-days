package com.boxoffice.cli;

import com.boxoffice.core.BoxOffice;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.net.ResilientHttp;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The gate from a terminal:
 *
 * <pre>
 * java -jar cli/target/boxoffice.jar setup
 * java -jar cli/target/boxoffice.jar sell VIP "Wanjiku Kamau"
 * java -jar cli/target/boxoffice.jar import-guests guests.csv
 * java -jar cli/target/boxoffice.jar report
 * java -jar cli/target/boxoffice.jar remind --fake
 * java -jar cli/target/boxoffice.jar ping http://127.0.0.1:9
 * java -jar cli/target/boxoffice.jar practice-server     (then, in another terminal)
 * java -jar cli/target/boxoffice.jar sync
 * java -jar cli/target/boxoffice.jar register --server http://127.0.0.1:8080
 * java -jar cli/target/boxoffice.jar conflicts
 * java -jar cli/target/boxoffice.jar serve-feed          (on gate A)
 * java -jar cli/target/boxoffice.jar pull --from http://192.168.1.21:8100   (on gate B)
 * java -jar cli/target/boxoffice.jar ask How many VIP are left?   (needs ANTHROPIC_API_KEY)
 * java -jar cli/target/boxoffice.jar eval                          (needs ANTHROPIC_API_KEY)
 * git log --format=%s v1.0.0..HEAD | java -jar cli/target/boxoffice.jar release-notes --last v1.0.0
 * java -jar cli/target/boxoffice.jar update --download installers
 * </pre>
 */
@Command(name = "boxoffice",
        mixinStandardHelpOptions = true,
        versionProvider = BoxOfficeCli.Version.class,
        description = "Sells tickets and prints reports for Kilele Nights.",
        subcommands = {SetupCommand.class, SellCommand.class, ImportGuestsCommand.class, ReportCommand.class,
                RemindCommand.class, PingCommand.class, SyncCommand.class, PracticeServerCommand.class,
                ServeFeedCommand.class, PullCommand.class, RegisterCommand.class,
                ConflictsCommand.class, AskCommand.class, EvalCommand.class, ReleaseNotesCommand.class, UpdateCommand.class})
public final class BoxOfficeCli {

    @Option(names = "--db", defaultValue = "gate.db",
            description = "The gate's database file (default: ${DEFAULT-VALUE}).")
    Path database;

    final Clock clock;
    // Day 130: the real time, for "is the night on?". The practice clock always says it is.
    final Clock realClock;
    final Map<String, String> environment;
    // Builds the HTTP client, given where to report retries. Tests swap in one that doesn't really sleep.
    final Function<Consumer<String>, ResilientHttp> http;

    /**
     * @param clock       the real clock in main, a fixed one in tests
     * @param environment where secrets like AT_API_KEY come from: System.getenv() in main
     */
    public BoxOfficeCli(Clock clock, Map<String, String> environment) {
        this(clock, Clock.systemUTC(), environment, ResilientHttp::forTheGate);
    }

    /** For tests: one fixed clock for everything. */
    BoxOfficeCli(Clock clock, Map<String, String> environment, Function<Consumer<String>, ResilientHttp> http) {
        this(clock, clock, environment, http);
    }

    private BoxOfficeCli(Clock clock, Clock realClock, Map<String, String> environment,
                         Function<Consumer<String>, ResilientHttp> http) {
        this.clock = clock;
        this.realClock = realClock;
        this.environment = environment;
        this.http = http;
    }

    /** Builds the command line. Tests call this too, so they run exactly what main runs. */
    public static CommandLine commandLine(Clock clock) {
        return commandLine(clock, Map.of());
    }

    public static CommandLine commandLine(Clock clock, Map<String, String> environment) {
        return new CommandLine(new BoxOfficeCli(clock, environment));
    }

    static CommandLine commandLine(Clock clock, Map<String, String> environment,
                                   Function<Consumer<String>, ResilientHttp> http) {
        return new CommandLine(new BoxOfficeCli(clock, environment, http));
    }

    public static void main(String[] args) {
        // The practice clock, so selling still works after the sample event's real date.
        System.exit(commandLine(SampleEvent.practiceClock(), System.getenv()).execute(args));
    }

    /** Day 130: --version says the version Maven built, not one typed into the code. */
    static final class Version implements CommandLine.IVersionProvider {

        @Override
        public String[] getVersion() {
            return new String[] {BoxOffice.NAME + " " + BoxOffice.version()};
        }
    }
}
