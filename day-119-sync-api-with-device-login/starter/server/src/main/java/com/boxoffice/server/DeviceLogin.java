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
        // TODO: read the header: request.getHeader("Authorization"). It looks like "Bearer abc123".
        //   - starts with "Bearer "? the token is the rest, stripped: devices.gateFor(token) finds the gate
        //   - no header, another kind, or gateFor finds nobody: answer 401 and stop:
        //       response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        //       response.setContentType("application/json");
        //       response.getOutputStream().write(
        //               "{\"error\":\"Unknown device. Register this laptop first\"}".getBytes(StandardCharsets.UTF_8));
        //       return false;     (false: the controller never runs)
        // TODO: a gate was found: request.setAttribute(GATE, the gate) and return true.
        // Right now everyone gets in, and nobody is told which gate they are.
        return true;
    }
}
