package com.boxoffice.core.lan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boxoffice.core.CheckInOutcome;
import com.boxoffice.core.Gate;
import com.boxoffice.core.SampleEvent;
import com.boxoffice.core.Ticket;
import com.boxoffice.core.TicketStore;
import com.boxoffice.core.net.NetworkException;
import com.boxoffice.core.net.ResilientHttp;
import com.boxoffice.core.net.RetryPolicy;
import com.boxoffice.core.sync.OutboxEntry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Two gate laptops in one test: two database files, and gate A's feed served
 * on a port of this laptop, as if A were across the room on the same router.
 */
class LanSyncTest {

    private static final Clock NINE_PM = Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path folder;

    private TicketStore storeA;
    private TicketStore storeB;
    private Gate gateA;
    private Gate gateB;
    private GateFeedServer feedA;

    /** Day 123: the secret both gates at Ngong Racecourse share. */
    static final String VENUE_KEY = "ngong-racecourse-2026-gates";

    private static TicketStore open(Path file, String gateId) {
        TicketStore store = TicketStore.open(file);
        SampleEvent.ensureIn(store);
        store.setGateId(gateId);
        store.setVenueKey(VENUE_KEY);
        return store;
    }

    @BeforeEach
    void setUpTwoGates() throws IOException {
        storeA = open(folder.resolve("gate-a.db"), "A");
        storeB = open(folder.resolve("gate-b.db"), "B");
        gateA = new Gate(storeA, SampleEvent.ID, NINE_PM);
        gateB = new Gate(storeB, SampleEvent.ID, NINE_PM);
        feedA = new GateFeedServer(folder.resolve("gate-a.db"), 0);
    }

    @AfterEach
    void closeEverything() {
        feedA.close();
        storeA.close();
        storeB.close();
    }

    private static ResilientHttp http() {
        return new ResilientHttp(HttpClient.newHttpClient(),
                new RetryPolicy(2, Duration.ofMillis(500), Duration.ofSeconds(4)),
                Duration.ofSeconds(2), wait -> { }, () -> 1.0, retry -> { });
    }

    private PullReport bPullsFromA() {
        URI a = URI.create("http://127.0.0.1:" + feedA.port());
        return new PeerPuller(storeB, a, http(), NINE_PM).pull();
    }

    @Test
    void gateBGetsTheTicketsGateASold() {
        gateA.sell("VIP", "Wanjiku");
        gateA.sellGroup("REG", "Kamau family", "", 2);

        PullReport report = bPullsFromA();

        assertThat(report).isEqualTo(new PullReport("A", 2, 3));
        assertThat(storeB.tickets(SampleEvent.ID)).extracting(Ticket::code)
                .containsExactlyInAnyOrder("A-VIP-0001", "A-REG-0001", "A-REG-0002");
        assertThat(report.describe()).isEqualTo("Got 2 sales (3 tickets) from gate A");
    }

    @Test
    void bothGatesCountTheSamePlacesLeft() {
        gateA.sell("VIP", "Wanjiku");
        gateB.sell("VIP", "Otieno");

        bPullsFromA();

        assertThat(gateB.remaining("VIP")).isEqualTo(48);
    }

    @Test
    void aTicketSoldAtGateAIsLetInAtGateB() {
        gateA.sell("VIP", "Wanjiku");
        bPullsFromA();

        assertThat(gateB.checkIn("a-vip-0001")).isInstanceOf(CheckInOutcome.Admitted.class);
    }

    @Test
    void pullingAgainBringsOnlyWhatIsNew() {
        gateA.sell("REG", "Otieno");
        bPullsFromA();
        gateA.sell("REG", "Achieng");

        PullReport second = bPullsFromA();

        assertThat(second).isEqualTo(new PullReport("A", 1, 1));
        assertThat(storeB.tickets(SampleEvent.ID)).hasSize(2);
        assertThat(bPullsFromA()).isEqualTo(new PullReport("A", 0, 0));
    }

    @Test
    void readsMoreThanOnePage() {
        gateA.sellGroup("REG", "Big group", "", 1);
        for (int i = 0; i < GateFeedServer.PAGE_SIZE + 5; i++) {
            gateA.sell("REG", "Fan " + i);
        }

        assertThat(bPullsFromA().sales()).isEqualTo(GateFeedServer.PAGE_SIZE + 6);
    }

    @Test
    void aSaleCopiedTwiceIsSavedOnce() {
        gateA.sell("REG", "Otieno");
        OutboxEntry sale = storeA.ownSalesAfter(0, 10).getFirst();

        assertThat(storeB.applyPeerSale("A", sale, NINE_PM.instant())).isTrue();
        assertThat(storeB.applyPeerSale("A", sale, NINE_PM.instant())).isFalse();
        assertThat(storeB.tickets(SampleEvent.ID)).hasSize(1);
    }

    @Test
    void copiedSalesAreNotPassedOnOrSentToTheServerAgain() {
        gateA.sell("REG", "Otieno");
        bPullsFromA();

        // B's feed and B's outbox hold only B's own sales.
        assertThat(storeB.ownSalesAfter(0, 10)).isEmpty();
        assertThat(storeB.countWaitingToSync()).isZero();
    }

    @Test
    void refusesTwoGatesWithTheSameId() {
        storeB.setGateId("A");

        assertThatThrownBy(this::bPullsFromA)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Both gates are called A. Give one of them another id");
    }

    @Test
    void aGateWithADifferentVenueKeyGetsNothing() {
        storeB.setVenueKey("someone-elses-venue-key");

        assertThatThrownBy(this::bPullsFromA)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The other gate has a different venue key. Set the same one on both: setup --venue-key ...");
    }

    @Test
    void aStrangerOnTheWifiGets401() throws Exception {
        HttpResponse<String> stranger = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + feedA.port() + "/feed/sales")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(stranger.statusCode()).isEqualTo(401);
        assertThat(stranger.body()).doesNotContain("Wanjiku").isEqualTo("{\"error\":\"Wrong venue key\"}");
    }

    @Test
    void theFeedWontStartWithoutAVenueKey() {
        Path noKey = folder.resolve("gate-c.db");
        try (TicketStore store = TicketStore.open(noKey)) {
            store.setGateId("C");
        }

        assertThatThrownBy(() -> new GateFeedServer(noKey, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Set a venue key first, the same on both gates: setup --venue-key ...");
    }

    @Test
    void aShortVenueKeyIsRefused() {
        assertThatThrownBy(() -> storeA.setVenueKey("1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A venue key is at least 16 letters, digits, - or _");
    }

    @Test
    void saysSoWhenTheOtherGateIsOff() {
        PeerPuller puller = new PeerPuller(storeB, URI.create("http://127.0.0.1:9"), http(), NINE_PM);

        assertThatThrownBy(puller::pull)
                .isInstanceOf(NetworkException.class)
                .hasMessage("Gave up after 2 attempts: Can't reach the server");
    }
}
