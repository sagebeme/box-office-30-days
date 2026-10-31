package com.boxoffice.core.lan;

import com.boxoffice.core.TicketStore;
import com.boxoffice.core.net.NetworkException;
import com.boxoffice.core.net.ResilientHttp;
import com.boxoffice.core.sync.OutboxEntry;
import com.boxoffice.core.sync.SalePayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;

/**
 * Copies another gate's new sales into this gate's store, so a ticket sold
 * at gate A is let in at gate B, and both count the same places left.
 *
 * <p>Our place in the other gate's feed is kept per gate id, not per address:
 * the router may give that laptop a new IP address tomorrow.
 */
public final class PeerPuller {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TicketStore store;
    private final URI peer;
    private final ResilientHttp http;
    private final Clock clock;

    /** @param peer the other gate's feed, like http://192.168.1.21:8100 */
    public PeerPuller(TicketStore store, URI peer, ResilientHttp http, Clock clock) {
        this.store = store;
        this.peer = peer;
        this.http = http;
        this.clock = clock;
    }

    /**
     * Reads the other gate's feed page by page, from where we got to last time,
     * until there's nothing new.
     *
     * @throws NetworkException when the other gate can't be reached
     * @throws IllegalStateException when both gates have the same id
     */
    public PullReport pull() {
        String peerId = fetch("/feed/gate").gate();
        String ourId = store.gateId();
        if (ourId.isEmpty() || peerId.isEmpty()) {
            throw new IllegalStateException("Give both gates an id first: setup --gate A on one, setup --gate B on the other");
        }
        if (peerId.equals(ourId)) {
            throw new IllegalStateException("Both gates are called " + ourId + ". Give one of them another id");
        }
        int sales = 0;
        int tickets = 0;
        // TODO: loop for ever (while (true)), one page each time round:
        //   1. FeedPage page = fetch("/feed/sales?after=" + store.peerCursor(peerId));
        //      (applyPeerSale moves the cursor, so each page starts where the last one stopped)
        //   2. page.gate() isn't peerId? Someone else answers at that address now:
        //      throw new IllegalStateException("That address was gate " + peerId
        //              + " a moment ago, and now it's gate " + page.gate());
        //   3. no sales in the page? We're up to date: return new PullReport(peerId, sales, tickets)
        //   4. for each entry: if store.applyPeerSale(peerId, entry, clock.instant()) says it was new,
        //      sales++ and add SalePayload.fromJson(entry.payload()).tickets().size() to tickets
        throw new UnsupportedOperationException("Not written yet: Day 117, step 5");
    }

    private FeedPage fetch(String path) {
        HttpRequest request = HttpRequest.newBuilder(peer.resolve(path)).GET().build();
        HttpResponse<String> response = http.send(request);
        if (response.statusCode() != 200) {
            throw new NetworkException("Gate feed answered HTTP " + response.statusCode(), 1, null);
        }
        try {
            return JSON.readValue(response.body(), FeedPage.class);
        } catch (JsonProcessingException e) {
            throw new NetworkException("That isn't a gate feed: " + e.getOriginalMessage(), 1, e);
        }
    }
}
