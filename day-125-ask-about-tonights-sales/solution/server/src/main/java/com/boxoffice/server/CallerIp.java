package com.boxoffice.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The address a request really came from.
 *
 * <p>Behind a proxy or load balancer (Day 124), every request seems to come
 * from the proxy. The proxy adds the real address to the end of the
 * X-Forwarded-For header. But anyone can <b>send</b> that header too, with any
 * address they like at the start. So read it from the end, and skip exactly as
 * many entries as there are proxies you run: the one before them is the caller.
 */
final class CallerIp {

    private CallerIp() {
    }

    /**
     * @param remoteAddress  who connected to us: the caller, or our last proxy
     * @param forwardedFor   the X-Forwarded-For header, or null
     * @param trustedProxies how many proxies of ours stand in front of the server. 0: none, ignore the header
     */
    static String of(String remoteAddress, String forwardedFor, int trustedProxies) {
        if (trustedProxies <= 0 || forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        // The whole chain, the caller first and the proxy that connected to us last.
        List<String> chain = new ArrayList<>(Arrays.stream(forwardedFor.split(","))
                .map(String::strip)
                .filter(address -> !address.isEmpty())
                .toList());
        chain.add(remoteAddress);
        int caller = chain.size() - 1 - trustedProxies;
        return chain.get(Math.max(0, caller));
    }
}
