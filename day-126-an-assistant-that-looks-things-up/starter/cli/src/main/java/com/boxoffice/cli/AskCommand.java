package com.boxoffice.cli;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.boxoffice.core.Event;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import com.boxoffice.core.ai.Reply;
import com.boxoffice.core.ai.SalesQuestions;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Asks Claude about tonight's sales.
 *
 * <pre>
 * java -jar cli/target/boxoffice.jar ask --fake How many VIP are left?      shows what would be sent
 * ANTHROPIC_API_KEY=... java -jar cli/target/boxoffice.jar ask How many VIP are left?
 * </pre>
 *
 * Like AT_API_KEY on Day 114, the key comes from an environment variable and
 * never from the code: anyone who has it can spend money on your account.
 */
@Command(name = "ask", description = "Asks Claude a question about tonight's sales.",
        mixinStandardHelpOptions = true)
final class AskCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--fake", description = "Show the facts Claude would get, and send nothing.")
    boolean fake;

    @Parameters(arity = "1..*", paramLabel = "QUESTION", description = "The question, in your own words.")
    List<String> words;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        String question = String.join(" ", words);

        String problem = SalesQuestions.problemWith(question);
        if (problem != null) {
            err.println(problem);
            return 1;
        }

        String apiKey = cli.environment.get("ANTHROPIC_API_KEY");
        if (!fake && (apiKey == null || apiKey.isBlank())) {
            err.println("Set ANTHROPIC_API_KEY to your Anthropic API key, or use --fake to see what would be sent");
            return 1;
        }

        try (TicketStore store = TicketStore.open(cli.database)) {
            Event event = SampleEvent.ensureIn(store);
            if (fake) {
                out.println("Practice mode: nothing was sent. Claude would get your question and these facts:");
                out.print(new SalesQuestions(null, store, event, cli.clock).snapshot().facts());
                out.flush();
                return 0;
            }
            try (AnthropicClaude claude = AnthropicClaude.connect(apiKey, cli.environment.get("ANTHROPIC_BASE_URL"))) {
                Reply reply = new SalesQuestions(claude, store, event, cli.clock).ask(question, piece -> {
                    out.print(piece);
                    // Flush each piece, or the answer appears all at once at the end.
                    out.flush();
                }, call -> {
                    // Day 126: say what Claude is looking up, on err so it stays out of the answer.
                    err.println("(looking up " + call.name() + " " + call.input() + ")");
                    err.flush();
                });
                return finish(reply, out, err);
            } catch (AnthropicServiceException e) {
                err.println(e.statusCode() == 401
                        ? "The Anthropic API refused the key (HTTP 401). Check ANTHROPIC_API_KEY"
                        : "The Anthropic API answered HTTP " + e.statusCode() + ". Try again in a minute");
                return 1;
            } catch (AnthropicIoException e) {
                err.println("Can't reach the Anthropic API. Check the internet connection");
                return 1;
            }
        }
    }

    private static int finish(Reply reply, PrintWriter out, PrintWriter err) {
        switch (reply) {
            case Reply.Answered answered -> {
                out.println();
                return 0;
            }
            case Reply.CutShort cut -> {
                out.println();
                err.println("(The answer was cut short. Ask a narrower question.)");
                return 1;
            }
            case Reply.Refused refused -> {
                err.println("No answer: " + refused.why());
                return 1;
            }
        }
    }
}
