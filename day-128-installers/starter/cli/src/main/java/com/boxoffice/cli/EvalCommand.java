package com.boxoffice.cli;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.boxoffice.core.ai.eval.EvalRunner;
import com.boxoffice.core.ai.eval.GateEvals;
import com.boxoffice.core.ai.eval.Scorecard;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Asks the real Claude every question in GateEvals, each on its own made-up
 * night, and prints how the answers scored. It never touches gate.db.
 *
 * <pre>
 * ANTHROPIC_API_KEY=... java -jar cli/target/boxoffice.jar eval
 * </pre>
 *
 * Each run is about a dozen API calls, so it costs a little money: run it
 * after you change the prompt, the tools or the model, not on every build.
 */
@Command(name = "eval", description = "Grades Claude's answers to the gate's eval questions.",
        mixinStandardHelpOptions = true)
final class EvalCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--at-least", defaultValue = "80",
            description = "The pass rate, in percent, below which the command fails (default: ${DEFAULT-VALUE}).")
    int atLeast;

    @Override
    public Integer call() throws IOException {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        String apiKey = cli.environment.get("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            err.println("Set ANTHROPIC_API_KEY to your Anthropic API key to run the evals");
            return 1;
        }

        err.println("Asking Claude " + GateEvals.cases().size() + " questions...");
        err.flush();
        Path folder = Files.createTempDirectory("boxoffice-evals");
        try (AnthropicClaude claude = AnthropicClaude.connect(apiKey, cli.environment.get("ANTHROPIC_BASE_URL"))) {
            Scorecard card = new EvalRunner(folder).run(GateEvals.cases(), claude);
            out.print(card.render());
            out.flush();
            return Math.round(card.passRate() * 100) >= atLeast ? 0 : 1;
        } catch (AnthropicServiceException e) {
            err.println(e.statusCode() == 401
                    ? "The Anthropic API refused the key (HTTP 401). Check ANTHROPIC_API_KEY"
                    : "The Anthropic API answered HTTP " + e.statusCode() + ". Try again in a minute");
            return 1;
        } catch (AnthropicIoException e) {
            err.println("Can't reach the Anthropic API. Check the internet connection");
            return 1;
        } finally {
            deleteAll(folder);
        }
    }

    private static void deleteAll(Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            // Deepest first, so each folder is empty by the time it's deleted.
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
