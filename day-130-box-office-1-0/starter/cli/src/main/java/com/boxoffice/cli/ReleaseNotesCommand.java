package com.boxoffice.cli;

import com.boxoffice.core.release.ConventionalCommit;
import com.boxoffice.core.release.ReleaseNotes;
import com.boxoffice.core.release.SemVer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Works out the next version and writes the release notes, from the commit
 * subjects since the last release:
 *
 * <pre>
 * git log --format=%s v1.0.0..HEAD | java -jar cli/target/boxoffice.jar release-notes --last v1.0.0
 * </pre>
 */
@Command(name = "release-notes", description = "Prints the next version's release notes, from commit subjects.",
        mixinStandardHelpOptions = true)
final class ReleaseNotesCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--last", required = true, description = "The version released last, e.g. v1.0.0.")
    String last;

    @Option(names = "--version", description = "Head the notes with this version instead of working it out.")
    String version;

    @Option(names = "--commits", description = "A file of commit subjects, one per line. Without it, they're read from the input.")
    Path commits;

    @Override
    public Integer call() throws IOException {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        SemVer lastVersion;
        try {
            lastVersion = SemVer.parse(last);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        }

        List<ConventionalCommit> parsed = subjects().stream()
                .filter(line -> !line.isBlank())
                .map(ConventionalCommit::parse)
                .toList();
        SemVer next = lastVersion.next(parsed);
        if (version == null && next.equals(lastVersion)) {
            err.println("Nothing to release since " + lastVersion + ": no feat or fix commits");
            return 1;
        }
        out.print(ReleaseNotes.render(version == null ? next : SemVer.parse(version), parsed));
        out.flush();
        return 0;
    }

    private List<String> subjects() throws IOException {
        if (commits != null) {
            return Files.readAllLines(commits);
        }
        // Not closed: closing it would close System.in for the rest of the program.
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        return in.lines().toList();
    }
}
