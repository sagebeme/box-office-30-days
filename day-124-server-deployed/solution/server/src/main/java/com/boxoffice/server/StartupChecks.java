package com.boxoffice.server;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start with weak or missing secrets. Better the server won't
 * start on Friday afternoon than it runs all Saturday night with an admin key
 * of "admin".
 */
@Component
class StartupChecks {

    static final int SHORTEST_ADMIN_KEY = 24;
    static final int SHORTEST_CALLBACK_SECRET = 32;

    StartupChecks(@Value("${boxoffice.admin-key}") String adminKey, MpesaSettings mpesa, Environment environment) {
        boolean production = environment.matchesProfiles("prod");
        List<String> problems = problems(adminKey, mpesa, production);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The server won't start:\n  " + String.join("\n  ", problems));
        }
    }

    static List<String> problems(String adminKey, MpesaSettings mpesa, boolean production) {
        List<String> problems = new ArrayList<>();
        if (production) {
            // Day 124: the "Pretend to pay" buttons on the real site would be free tickets for anyone.
            if (mpesa.stub()) {
                problems.add("MPESA_MODE is stub: no practice payments in production. Set it to sandbox or live");
            }
            if (adminKey.isBlank()) {
                problems.add("Set BOXOFFICE_ADMIN_KEY: without it, no gate can register");
            }
        }
        // No key at all is allowed: then nobody can register gates (Day 119). A short one isn't.
        if (!adminKey.isBlank() && adminKey.length() < SHORTEST_ADMIN_KEY) {
            problems.add("BOXOFFICE_ADMIN_KEY is too short: use at least " + SHORTEST_ADMIN_KEY
                    + " random characters (openssl rand -base64 32 makes some)");
        }
        if (!mpesa.stub()) {
            String secret = mpesa.callbackSecret() == null ? "" : mpesa.callbackSecret();
            if (secret.length() < SHORTEST_CALLBACK_SECRET) {
                problems.add("MPESA_CALLBACK_SECRET is too short: use at least " + SHORTEST_CALLBACK_SECRET
                        + " random characters");
            } else if (!secret.matches("[A-Za-z0-9_-]+")) {
                problems.add("MPESA_CALLBACK_SECRET goes in an address: use only letters, digits, - and _");
            }
            String base = mpesa.callbackBase() == null ? "" : mpesa.callbackBase();
            if (!base.startsWith("https://")) {
                problems.add("MPESA_CALLBACK_BASE must start with https://: Safaricom won't call plain http");
            }
        }
        return problems;
    }
}
