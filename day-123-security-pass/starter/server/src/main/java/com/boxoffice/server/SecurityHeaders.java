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
        // TODO Day 123, step 5: set these headers on every answer, before chain.doFilter:
        //   Content-Security-Policy   POLICY
        //   X-Content-Type-Options    nosniff      don't guess that a text answer is really a script
        //   X-Frame-Options           DENY         nobody can put our pages in a frame
        //   Referrer-Policy           no-referrer  an order's address is its secret: never send it on
        // and for addresses starting /orders/ only:
        //   Cache-Control             no-store     a fan's order must not sit in a shared cache
        chain.doFilter(request, response);
    }
}
