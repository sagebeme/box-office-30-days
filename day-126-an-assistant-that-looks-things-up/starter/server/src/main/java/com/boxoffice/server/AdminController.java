package com.boxoffice.server;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * For the organiser, before the night: registers each gate laptop.
 *
 * <pre>
 * POST /admin/devices
 * X-Admin-Key: the key the server was started with
 * {"gate":"A"}
 *                     -> 201 {"gate":"A","token":"..."}
 * </pre>
 */
@RestController
class AdminController {

    record Registration(String gate) {
    }

    private final Devices devices;
    private final String adminKey;

    AdminController(Devices devices, @Value("${boxoffice.admin-key}") String adminKey) {
        this.devices = devices;
        this.adminKey = adminKey;
    }

    @PostMapping("/admin/devices")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> register(@RequestHeader(name = "X-Admin-Key", required = false) String key,
                                 @RequestBody Registration registration) {
        if (adminKey.isBlank()) {
            // 500, not 503: 503 means "try again soon", and the gate would retry it for nothing.
            throw new ApiProblem(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Start the server with BOXOFFICE_ADMIN_KEY set to register gates");
        }
        if (key == null || !Tokens.same(key, adminKey)) {
            throw new ApiProblem(HttpStatus.UNAUTHORIZED, "Wrong admin key");
        }
        String gate = registration.gate();
        if (gate == null || !gate.matches("[A-Z0-9]{1,3}")) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "A gate id is 1 to 3 capital letters or digits, like A");
        }
        if (gate.equals(Payments.WEB)) {
            // Tickets paid for on the site are W-REG-0001...: a gate called W would clash with them.
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "W is the ticket site's. Pick another gate id");
        }
        return Map.of("gate", gate, "token", devices.register(gate));
    }
}
