package com.boxoffice.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs before placing an order and before /admin: too many from one address
 * gets 429 Too Many Requests and a Retry-After header saying when to come back.
 */
@Component
class RateLimits implements HandlerInterceptor {

    private final Limits limits;

    RateLimits(Limits limits) {
        this.limits = limits;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String ip = CallerIp.of(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"), limits.trustedProxies());
        boolean admin = request.getRequestURI().startsWith("/admin/");
        if (!admin && !"POST".equals(request.getMethod())) {
            return true;
        }
        RateLimiter.Decision decision = (admin ? limits.admin() : limits.orders()).tryTake(ip);
        if (decision.allowed()) {
            return true;
        }
        long seconds = decision.retryAfterSeconds();
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(seconds));
        if (admin) {
            response.setContentType("application/json");
            write(response, "{\"error\":\"Too many tries. Wait " + seconds + " seconds\"}");
        } else {
            // htmx: put the message in the page's notice line, and leave the form alone.
            response.setHeader("HX-Retarget", "#notice");
            response.setHeader("HX-Reswap", "innerHTML");
            response.setContentType("text/html;charset=UTF-8");
            write(response, "Too many orders from here. Wait " + seconds + " seconds and try again.");
        }
        return false;
    }

    private static void write(HttpServletResponse response, String body) throws IOException {
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
