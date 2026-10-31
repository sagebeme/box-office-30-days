package com.boxoffice.cli;

import com.boxoffice.core.SampleEvent;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
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
 * </pre>
 */
@Command(name = "boxoffice",
        mixinStandardHelpOptions = true,
        version = "Box Office 1.0",
        description = "Sells tickets and prints reports for Kilele Nights.",
        subcommands = {SetupCommand.class, SellCommand.class, ImportGuestsCommand.class, ReportCommand.class,
                RemindCommand.class})
public final class BoxOfficeCli {

    @Option(names = "--db", defaultValue = "gate.db",
            description = "The gate's database file (default: ${DEFAULT-VALUE}).")
    Path database;

    final Clock clock;
    final Map<String, String> environment;

    /**
     * @param clock       the real clock in main, a fixed one in tests
     * @param environment where secrets like AT_API_KEY come from: System.getenv() in main
     */
    public BoxOfficeCli(Clock clock, Map<String, String> environment) {
        this.clock = clock;
        this.environment = environment;
    }

    /** Builds the command line. Tests call this too, so they run exactly what main runs. */
    public static CommandLine commandLine(Clock clock) {
        return commandLine(clock, Map.of());
    }

    public static CommandLine commandLine(Clock clock, Map<String, String> environment) {
        return new CommandLine(new BoxOfficeCli(clock, environment));
    }

    public static void main(String[] args) {
        // The practice clock, so selling still works after the sample event's real date.
        System.exit(commandLine(SampleEvent.practiceClock(), System.getenv()).execute(args));
    }
}
