package com.boxoffice.cli;

import com.boxoffice.core.net.NetworkException;
import com.boxoffice.core.net.ResilientHttp;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Checks a server can be reached, the way the gate will reach its sync server,
 * and shows every retry:
 *
 * <pre>
 * java -jar cli/target/boxoffice.jar ping http://127.0.0.1:9
 * </pre>
 */
@Command(name = "ping", description = "Checks a server answers, trying again on bad Wi-Fi.",
        mixinStandardHelpOptions = true)
final class PingCommand implements Callable<Integer> {

    @ParentCommand
    BoxOfficeCli cli;

    @Spec
    CommandSpec spec;

    @Parameters(index = "0", description = "The address to try, like http://192.168.1.20:8080")
    URI url;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        ResilientHttp http = cli.http.apply(out::println);
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(url).GET().build());
            out.println(url + " answered HTTP " + response.statusCode());
            return response.statusCode() < 400 ? 0 : 1;
        } catch (NetworkException e) {
            out.println(e.getMessage());
            return 1;
        } catch (IllegalArgumentException e) {
            out.println("That isn't a web address: " + url);
            return 1;
        }
    }
}
