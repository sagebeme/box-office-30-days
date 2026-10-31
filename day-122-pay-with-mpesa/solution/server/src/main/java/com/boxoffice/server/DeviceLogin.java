package com.boxoffice.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs before every /sync request: finds which gate is calling from its
 * {@code Authorization: Bearer <token>} header, or answers 401 and stops
 * the request there.
 */
@Component
class DeviceLogin implements HandlerInterceptor {

    /** Where the controller finds the gate: @RequestAttribute(DeviceLogin.GATE) String gate. */
    static final String GATE = "gate";

    private final Devices devices;

    DeviceLogin(Devices devices) {
        this.devices = devices;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String header = request.getHeader("Authorization");
        Optional<String> gate = header != null && header.startsWith("Bearer ")
                ? devices.gateFor(header.substring("Bearer ".length()).strip())
                : Optional.empty();
        if (gate.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getOutputStream().write(
                    "{\"error\":\"Unknown device. Register this laptop first\"}".getBytes(StandardCharsets.UTF_8));
            return false;
        }
        request.setAttribute(GATE, gate.get());
        return true;
    }
}
