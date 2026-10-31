package com.boxoffice.cli;

import com.boxoffice.core.Event;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.TicketStore;
import com.boxoffice.core.sms.AfricasTalkingSms;
import com.boxoffice.core.sms.FakeSms;
import com.boxoffice.core.sms.ReminderSender;
import com.boxoffice.core.sms.SmsGateway;
import com.boxoffice.core.sms.SmsResult;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Texts a reminder to everyone who left a phone number.
 *
 * <pre>
 * java -jar cli/target/boxoffice.jar remind --fake          prints the texts, sends nothing
 * AT_API_KEY=... java -jar cli/target/boxoffice.jar remind  sends through the Africa's Talking sandbox
 * </pre>
 *
 * The API key comes from an environment variable, never from the code or a
 * file in Git: anyone who has it can send texts on your account.
 */
@Command(name = "remind", description = "Texts every buyer who left a phone number.",
        mixinStandardHelpOptions = true)
final class RemindCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Option(names = "--fake", description = "Print the texts instead of sending them.")
    boolean fake;

    @Option(names = "--at-once", defaultValue = "10",
            description = "Most texts sent at the same moment (default: ${DEFAULT-VALUE}).")
    int atOnce;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        SmsGateway gateway;
        if (fake) {
            gateway = new FakeSms(Duration.ZERO);
        } else {
            String apiKey = cli.environment.get("AT_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                err.println("Set AT_API_KEY to your Africa's Talking sandbox key, or use --fake");
                return 1;
            }
            gateway = new AfricasTalkingSms(AfricasTalkingSms.SANDBOX,
                    cli.environment.getOrDefault("AT_USERNAME", "sandbox"), apiKey);
        }

        Map<String, String> messages;
        try (TicketStore store = TicketStore.open(cli.database)) {
            Event event = SampleEvent.ensureIn(store);
            messages = ReminderSender.messages(event, store.tickets(event.id()));
        }
        List<SmsResult> results = new ReminderSender(gateway, atOnce).sendAll(messages);

        int sent = 0;
        for (SmsResult result : results) {
            if (result.sent()) {
                sent++;
                if (fake) {
                    out.println(result.phone() + ": " + messages.get(result.phone()));
                }
            } else {
                err.println(result.phone() + ": " + result.detail());
            }
        }
        out.println("Sent " + sent + " of " + results.size() + " reminders");
        return sent == results.size() ? 0 : 1;
    }
}
