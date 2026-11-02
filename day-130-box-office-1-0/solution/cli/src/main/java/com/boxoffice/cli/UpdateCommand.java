package com.boxoffice.cli;

import com.boxoffice.core.BoxOffice;
import com.boxoffice.core.Event;
import com.boxoffice.core.NairobiTime;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import com.boxoffice.core.net.NetworkException;
import com.boxoffice.core.release.SemVer;
import com.boxoffice.core.update.Downloader;
import com.boxoffice.core.update.GitHubReleases;
import com.boxoffice.core.update.UpdateCheck;
import com.boxoffice.core.update.UpdateChecker;
import com.boxoffice.core.update.UpdateException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Checks GitHub for a newer Box Office, and with --download fetches its
 * installer for this system and checks it against the release's SHA256SUMS.
 * It never runs the installer: close Box Office and run it yourself, after the night.
 *
 * <pre>
 * java -jar cli/target/boxoffice.jar update
 * java -jar cli/target/boxoffice.jar update --download installers
 * </pre>
 */
@Command(name = "update", description = "Checks for a newer Box Office, and can download it.",
        mixinStandardHelpOptions = true)
final class UpdateCommand implements Callable<Integer> {

    static final String REPOSITORY = "sagebeme/box-office-30-days";

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--download", paramLabel = "FOLDER", description = "Download the new installer into this folder.")
    Path download;

    @Option(names = "--repo", defaultValue = REPOSITORY,
            description = "The GitHub repository whose releases to check (default: ${DEFAULT-VALUE}).")
    String repository;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        Event event;
        try (TicketStore store = TicketStore.open(cli.database)) {
            event = SampleEvent.ensureIn(store);
        }

        UpdateCheck check;
        try {
            URI api = URI.create(cli.environment.getOrDefault("BOXOFFICE_RELEASES_URL", GitHubReleases.API.toString()));
            GitHubReleases feed = new GitHubReleases(cli.http.apply(err::println), api, repository);
            check = new UpdateChecker(feed, SemVer.parse(BoxOffice.version()), System.getProperty("os.name")).check();
        } catch (UpdateException | IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        } catch (NetworkException e) {
            err.println("Couldn't reach GitHub to check for updates: " + e.getMessage());
            return 1;
        }

        switch (check) {
            case UpdateCheck.NoReleases none -> {
                out.println("Box Office " + none.current() + ": no releases published yet");
                return 0;
            }
            case UpdateCheck.UpToDate upToDate -> {
                out.println("Box Office " + upToDate.current() + " is up to date");
                return 0;
            }
            case UpdateCheck.NothingForThisSystem nothing -> {
                out.println("Box Office " + nothing.release().version() + " is out (you have " + nothing.current()
                        + "), but it has no installer for " + System.getProperty("os.name"));
                return 0;
            }
            case UpdateCheck.Available available -> {
                out.println("Box Office " + available.release().version() + " is out (you have "
                        + available.current() + ")");
                out.println();
                out.println(available.release().notes().strip());
                out.println();
                if (download == null) {
                    out.println("Run `update --download <folder>` to fetch " + available.installer().name());
                    return 0;
                }
                if (UpdateChecker.duringTheEvent(event, cli.realClock.instant())) {
                    err.println("The night is on: updates wait until " + event.name() + " ends at "
                            + NairobiTime.format(event.endsAt().toInstant()));
                    return 1;
                }
                try {
                    Path saved = Downloader.forUpdates().download(available.release(), available.installer(), download);
                    out.println("Saved " + saved + " (its checksum matches). Close Box Office, then run it to update");
                    return 0;
                } catch (UpdateException e) {
                    err.println(e.getMessage());
                    return 1;
                }
            }
        }
    }
}
