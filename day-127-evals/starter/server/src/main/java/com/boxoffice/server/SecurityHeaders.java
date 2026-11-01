package com.boxoffice.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Headers on every answer that tell the browser what this site will never do,
 * so it can refuse anything else. A filter runs for every request, before any
 * controller, so no page can forget them.
 */
@Component
class SecurityHeaders extends OncePerRequestFilter {

    /**
     * Content-Security-Policy: scripts, styles and images only from this
     * server. A name like {@code <script>...} that slipped past escaping still
     * wouldn't run: inline scripts aren't allowed at all.
     */
    static final String POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;"
            + " form-action 'self'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", POLICY);
        // Don't guess that a text answer is really a script.
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Nobody can put our pages in a frame and trick a fan into clicking "Pay".
        response.setHeader("X-Frame-Options", "DENY");
        // An order's address is its secret: never send it to another site as the "page you came from".
        response.setHeader("Referrer-Policy", "no-referrer");
        if (request.getRequestURI().startsWith("/orders/")) {
            // A fan's order (name, phone, ticket codes) must not sit in a shared or proxy cache.
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
