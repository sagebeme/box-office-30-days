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
        // TODO Day 123, step 3: find the real caller.
        //   - no proxies of ours (trustedProxies 0), or no header: remoteAddress
        //   - otherwise make the whole chain: the header split on commas (strip each, drop empty ones),
        //     then remoteAddress on the end. The caller is trustedProxies places before the end:
        //     chain.get(chain.size() - 1 - trustedProxies), or the first entry if the chain is too short.
        return remoteAddress;
    }
}
