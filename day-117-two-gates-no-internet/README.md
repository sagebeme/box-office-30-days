# Day 117: Two gates, no internet

> 21:30. Wanjiku bought her VIP ticket at the main gate. Now she's at the side gate, and the side gate has never heard of her.

Watch the video: *(not recorded yet)*

## The brief

Kilele Nights has two gates: the main gate by the car park (gate A) and the side gate by the food stalls (gate B). Each has its own laptop and its own `gate.db`. The internet is down, as it often is. But both laptops are on the same venue router, so they can still reach each other.

Today the gates share their sales over that local network:

- **Each gate gets an id.** `setup --gate A` on one laptop, `setup --gate B` on the other. Gate A's codes become `A-REG-0001`, gate B's `B-REG-0001`. They can never clash.
- **Each gate shares a feed.** `serve-feed` lets the other gate read this gate's sales.
- **Each gate pulls the other's.** `pull --from ...` copies the new ones in. Then a ticket sold at gate A is let in at gate B, and both gates count the same places left.

```
$ java -jar cli/target/boxoffice.jar --db b.db pull --from http://127.0.0.1:8100
Got 2 sales (4 tickets) from gate A
```

One honest warning before you start: two gates that can't reach each other can **still both sell the last VIP ticket**. Sharing sales makes it rare, not impossible. You'll see it happen at the end of today. Day 120 settles it, on the server.

## What you'll learn

- Why two gates need their own ids, and how a code **prefix** keeps codes from clashing
- Why "numbering" and "places left" count different things
- What a **LAN** is, and how to find a laptop's address on it
- How to serve JSON with the JDK's own `HttpServer`
- What a **feed** and a **cursor** are, and why reading in **pages** matters
- Why the cursor is kept per gate **id**, not per IP address
- How the `peer_sales` table makes copying a sale **idempotent**
- Why copied sales never go into the outbox or the feed: no **echo**

## New words

- **Gate id**: a short name for one gate laptop, like `A`, `B` or `G2`. One to three capital letters or digits.
- **Prefix**: text put at the front: `A-` in `A-REG-0001`.
- **LAN** (local area network): the computers on one router, like the gate laptops on the venue Wi-Fi. It works with no internet.
- **IP address**: a computer's number on the network, like `192.168.1.21`. `127.0.0.1` always means "this laptop".
- **Peer**: another computer of the same kind; here, the other gate.
- **Feed**: a list of new things that someone else can read, oldest first, a bit at a time.
- **Page**: one chunk of a feed. Here, up to 100 sales.
- **Cursor**: a bookmark: "I've read up to sale number 7". The next read starts after it.
- **Firewall**: the part of your operating system that decides which programs other computers may connect to.
- **Echo**: a sale copied from A to B, then from B back to A, then back to B... for ever. Today's design has none.
- **Settings table**: a small table of things the laptop knows about itself, by name. Today it holds `gate_id`.

## What's already written

From Days 101–116: the gate, SMS, the Wi-Fi-proof client and the outbox, working, with tests. New today, already done:

- `V6__add_gate_ids_and_peers.sql`: three new tables, `settings`, `peer_sales` and `peer_cursors`. Read it: it's short.
- `TicketStore`:
  - `gateId()` and `setGateId(id)`, which checks the id is short.
  - `countIssuedHere(eventId, typeCode, prefix)`: how many tickets this gate itself issued.
  - `ownSalesAfter(afterId, limit)`: this gate's outbox, as a feed.
  - `peerCursor(peer)`: where we got to in that gate's feed.
  - `alreadyCopied` and `insertCopiedTicket`, which `applyPeerSale` uses. You write the inside of `applyPeerSale`.
- `Gate`: numbering now uses `countIssuedHere`, and every code goes through `codeFor`. You add the prefix.
- A new package, `com.boxoffice.core.lan`:
  - `GateFeedServer`: serves this gate's sales over the network, with its own connection to `gate.db`.
  - `FeedPage`: one page of the feed, `{"gate":"A","sales":[...]}`.
  - `PeerPuller`: reads another gate's feed. The checks at the top of `pull` are written; you write the loop.
  - `PullReport`: what one pull brought in.
- The CLI: `setup` has a new option, `--gate`, and there are two new commands, `serve-feed` (`ServeFeedCommand.java`) and `pull` (`PullCommand.java`).

New, for you to finish: the code prefix in `Gate`, `TicketStore.applyPeerSale`, and the loop in `PeerPuller.pull`. New tests: `GateIdTest` (6), `LanSyncTest` (9), and four more in `BoxOfficeCliTest`.

## What you'll use

**Why ids.** Until today, every gate numbered its own tickets `REG-0001`, `REG-0002`... Two gates selling at once would both sell a `REG-0001`. When B copies A's, two different tickets have the same code, and the scanner can't tell them apart. With a prefix, A's first is `A-REG-0001` and B's is `B-REG-0001`. A gate with no id keeps the old `REG-0001`, so a single gate works exactly as before.

**Two different counts.** This is easy to get wrong:

| Question | What it counts | Method |
|---|---|---|
| What's the number on my next ticket? | Only **this gate's** own codes: `A-REG-...` | `countIssuedHere` |
| How many places are left? | **Every** gate's tickets: A's, and B's copied in | `countSold` |

If gate A numbered by every ticket, then after copying B's five tickets its next code would jump to `A-REG-0006`, and A's own numbers would have gaps. If it counted places by only its own, it would happily sell 500 Regular while B also sold 500. `Gate` already uses the right count for each; you only add the prefix.

**The prefix, worked out once.** The `Gate` constructor reads `store.gateId()`. Empty: the prefix is `""`. Otherwise it's the id and a dash, `"A-"`. `codeFor` puts it in front: `codePrefix + "%s-%04d".formatted(typeCode, number)`.

**The feed.** `GateFeedServer` uses the `HttpServer` you've met in the tests since Day 114, but for real. It answers two addresses:

```
GET /feed/gate             {"gate":"A","sales":[]}
GET /feed/sales?after=1    {"gate":"A","sales":[{"id":2,"key":"b1c4f3fe-...","payload":"{...}","attempts":0}, ...]}
```

The sales are this gate's **outbox** rows from Day 116: each already has an id in sale order, a unique key and the sale as JSON. The feed hands out up to 100 at a time, after the id you ask for. It opens **its own connection** to `gate.db`: with WAL (Day 111), a reader never sees a half-saved sale, and never holds up the gate that's selling.

**Reading page by page.** `pull` asks for `after=` the cursor. It copies what comes back, which moves the cursor, and asks again. When a page comes back empty, it's up to date. With 106 sales to copy, that's a page of 100, a page of 6, and an empty page.

**The cursor belongs to the gate id, not the address.** The router may give gate A's laptop a new IP address tomorrow, or gate A may move to a new laptop. So `peer_cursors` is keyed by `A`, not by `192.168.1.21`. And if the address you pull from suddenly answers as another gate, `pull` stops rather than mix two gates' bookmarks.

**Copied once, never twice.** A pull can be cut off halfway: the Wi-Fi drops, the laptop sleeps. The next pull may get some of the same sales again. `applyPeerSale` checks the sale's key in `peer_sales`. Already there: skip the tickets. Not there: insert the key and the tickets. Either way, move the cursor past it. **All in one transaction** (Day 111): the copied tickets and the cursor move together, or not at all.

**SQLite's upsert.** "Insert, or if it's there, update" in one statement:

```sql
insert into peer_cursors (peer, last_id) values (?, ?)
    on conflict (peer) do update set last_id = excluded.last_id
```

`excluded.last_id` means "the value I just tried to insert".

**No echo.** Copied tickets go straight into `tickets` with `insertCopiedTicket`. They don't go through `recordSale`, so they leave **no outbox note**. That means two things: B never passes A's sales back to A through its feed, and B never sends A's sales to the sync server (A does that itself). Each sale has one owner.

**A lambda can't change a local variable.** `applyPeerSale` returns whether the sale was new, but the answer is decided inside the `inTransaction(() -> { ... })` lambda. Java doesn't let a lambda change a local `boolean`. It can change what's inside an array, so the code uses `boolean[] isNew = {false};` and the lambda sets `isNew[0] = true`.

## Steps

1. **Open the starter and run the tests.** `cd day-117-two-gates-no-internet/starter`, then `../../mvnw test`. You should see `Tests run: 166, Failures: 3, Errors: 7` in `core`, and `BUILD FAILURE`. `cli` and `desktop` are `SKIPPED`. The failures:
   - `GateIdTest`: `expected: "A-VIP-0001" but was: "VIP-0001"`, and the same for `A-REG-0001` and the group `B-REG-0001`, `B-REG-0002`;
   - `LanSyncTest`: `Not written yet: Day 117, step 5`, and one `step 4`.

   V6 is complete, so opening the app is safe.

2. **Read the tables and the feed.** Open `starter/core/src/main/resources/db/migration/V6__add_gate_ids_and_peers.sql`. Then open `starter/core/src/main/java/com/boxoffice/core/lan/GateFeedServer.java` and find the two `createContext` lines and `ownSalesAfter`. Then, in `PeerPuller.pull`, read the checks above your TODO. Nothing to write.

3. **Put the gate id on every code.** Open `Gate.java`.
   - In the constructor, where the TODO is: `String gateId = store.gateId();` then `this.codePrefix = gateId.isEmpty() ? "" : gateId + "-";` (in place of `this.codePrefix = "";`).
   - In `codeFor`, return `codePrefix + "%s-%04d".formatted(typeCode, number)`.

   Run `../../mvnw test -Dtest=GateIdTest -Dsurefire.failIfNoSpecifiedTests=false`. All 6 pass.

4. **Copy one sale, once.** Open `TicketStore.java` and find `applyPeerSale`. Inside the `inTransaction` lambda, replace the `throw`, as the TODOs say:
   - only when `!alreadyCopied(entry.key())`: insert into `peer_sales` (`idempotency_key`, `peer`, `received_at`) with `entry.key()`, `peer` and `receivedAt.toString()`; then `insertCopiedTicket(sale.eventId(), ticket)` for every ticket in `sale.tickets()`; then `isNew[0] = true;`
   - always, even for a sale you already had: the upsert into `peer_cursors` from "What you'll use", with `peer` and `entry.id()`.

   Each insert is a `PreparedStatement` in a `try (...)`, like `queueForSync` yesterday. Run `LanSyncTest`: `aSaleCopiedTwiceIsSavedOnce` passes now; the others still say `step 5`.

5. **Pull page by page.** Open `PeerPuller.java` and find the TODO in `pull`. Replace the `throw` with a `while (true)` loop:
   - `FeedPage page = fetch("/feed/sales?after=" + store.peerCursor(peerId));`
   - if `!page.gate().equals(peerId)`, throw `new IllegalStateException("That address was gate " + peerId + " a moment ago, and now it's gate " + page.gate())`;
   - if `page.sales().isEmpty()`, you're up to date: `return new PullReport(peerId, sales, tickets);`
   - for each `OutboxEntry entry` in `page.sales()`: if `store.applyPeerSale(peerId, entry, clock.instant())` is `true`, `sales++` and add `SalePayload.fromJson(entry.payload()).tickets().size()` to `tickets`.

   Run `LanSyncTest` again: all 9 pass. They run two gates in one test: two database files, and gate A's feed on a port of your laptop.

6. **Run all the tests.** `../../mvnw test`. You should see 230 tests in all and `BUILD SUCCESS`.

7. **Run two gates on one laptop.** Follow "Run it and test it" below. Then, if you have a second laptop, do it for real over your Wi-Fi.

8. **Stretch (not required): pull both ways, on a timer.** A gate should pull every few seconds, not when someone remembers. In your own copy, add a `--every 10` option to `pull` that loops: pull, print the report, `Thread.sleep` 10 seconds, until Ctrl+C. Make sure a `NetworkException` (the other gate is off) prints a line and carries on, instead of ending the loop.

## Run it and test it

From the day's `starter` folder:

```
cd day-117-two-gates-no-internet/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=LanSyncTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=LanSyncTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **166 tests** in `core` (including GateId 6, LanSync 9), **23** in `cli` (BoxOfficeCli 20, PracticeServer 3) and **41** in `desktop`, **230** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

### Two gates on one laptop

Each gate is a database file: `a.db` for gate A, `b.db` for gate B. `--db` picks the file, and it goes **before** the command (`--db a.db sell ...`, not `sell ... --db a.db`). Build first (on Windows, write `cli\target\boxoffice.jar` in every command below):

```
../../mvnw package -DskipTests
```

**Give each gate its id:**

```
java -jar cli/target/boxoffice.jar --db a.db setup --gate A
```

```
java -jar cli/target/boxoffice.jar --db b.db setup --gate B
```

```
Kilele Nights is ready in a.db as gate A
Kilele Nights is ready in b.db as gate B
```

**Sell at both gates:**

```
java -jar cli/target/boxoffice.jar --db a.db sell VIP Wanjiku
```

```
java -jar cli/target/boxoffice.jar --db a.db sell REG "Kamau family" --quantity 3
```

```
java -jar cli/target/boxoffice.jar --db b.db sell VIP Otieno
```

```
Sold A-VIP-0001 to Wanjiku for KES 5,000
Sold 3 tickets to Kamau family for KES 4,500: A-REG-0001 to A-REG-0003
Sold B-VIP-0001 to Otieno for KES 5,000
```

**Share gate A's sales.** Open a **second terminal**, in the same folder, and leave this running:

```
java -jar cli/target/boxoffice.jar --db a.db serve-feed
```

```
Sharing sales from a.db on port 8100 (Ctrl+C to stop)
On the other gate: pull --from http://<this laptop's IP address>:8100
```

**Pull them into gate B.** Back in the first terminal. Run it twice: the second time there's nothing new.

```
java -jar cli/target/boxoffice.jar --db b.db pull --from http://127.0.0.1:8100
```

```
Got 2 sales (4 tickets) from gate A
Got 0 sales (0 tickets) from gate A
```

**And the other way.** Open a **third terminal** and share gate B's on another port:

```
java -jar cli/target/boxoffice.jar --db b.db serve-feed --port 8101
```

Then, in the first terminal:

```
java -jar cli/target/boxoffice.jar --db a.db pull --from http://127.0.0.1:8101
```

```
Got 1 sale (1 ticket) from gate B
```

Only Otieno's sale came back: B's feed holds only B's own sales, not the ones it copied from A. Now both gates agree:

```
java -jar cli/target/boxoffice.jar --db b.db report
```

```
Kilele Nights · Sat 14 Nov, 18:00
Type        Paid  Guests   Left        Takings
Regular        3       0    497      KES 4,500
VIP            2       0     48     KES 10,000
Total          5       0    545     KES 14,500
```

`--db a.db report` shows the same. Stop both feeds with Ctrl+C. (You can also look at a feed in your browser: open `http://127.0.0.1:8100/feed/sales?after=0` while it runs.)

### Two real laptops

Put both laptops on the same Wi-Fi (a phone hotspot works too, with its mobile data switched off). Copy the built `boxoffice.jar` to the second laptop, or build the course there. Each laptop uses its own plain `gate.db`, so you can leave `--db` out. On laptop A, `setup --gate A`; on laptop B, `setup --gate B`.

**Find laptop A's IP address.** On laptop A:

- macOS: `ipconfig getifaddr en0` (on Wi-Fi). It prints something like `192.168.1.21`.
- Linux: `hostname -I`. The first number is usually the one.
- Windows: `ipconfig`, then look for `IPv4 Address` under your Wi-Fi adapter.

It usually starts with `192.168.` or `10.`. Never `127.0.0.1`: that's "this laptop", so on laptop B it would mean laptop B.

**Share and pull.** On laptop A, run `java -jar cli/target/boxoffice.jar serve-feed`. The first time, your operating system may ask whether Java may accept connections from the network: allow it on **private** networks only. On laptop B, check it can reach A, then pull (use A's real address):

```
java -jar cli/target/boxoffice.jar ping http://192.168.1.21:8100/feed/gate
```

```
java -jar cli/target/boxoffice.jar pull --from http://192.168.1.21:8100
```

Then do the same the other way round. Sell at A, pull at B, and scan A's ticket at B's Check-in tab: green.

### Both gates sell the last ticket

Today's honest limit. Use two fresh files, `c.db` (gate A) and `d.db` (gate B), so the numbers are easy to follow:

```
java -jar cli/target/boxoffice.jar --db c.db setup --gate A
```

```
java -jar cli/target/boxoffice.jar --db d.db setup --gate B
```

Sell 49 of the 50 VIP places at gate A:

```
java -jar cli/target/boxoffice.jar --db c.db sell VIP "Big group" --quantity 49
```

In the second terminal, share them with `java -jar cli/target/boxoffice.jar --db c.db serve-feed`. In the first, pull them into gate B:

```
java -jar cli/target/boxoffice.jar --db d.db pull --from http://127.0.0.1:8100
```

```
Got 1 sale (49 tickets) from gate A
```

Now **stop A's feed** with Ctrl+C: the gates can't reach each other any more, like two laptops when the router dies. Each gate thinks one VIP place is left, and each sells it:

```
java -jar cli/target/boxoffice.jar --db c.db sell VIP Wanjiku
```

```
java -jar cli/target/boxoffice.jar --db d.db sell VIP Kamau
```

```
Sold A-VIP-0050 to Wanjiku for KES 5,000
Sold B-VIP-0001 to Kamau for KES 5,000
```

Start A's feed again (`--db c.db serve-feed`), pull into B the same way (`Got 1 sale (1 ticket) from gate A`), and look at B's report:

```
java -jar cli/target/boxoffice.jar --db d.db report
```

```
Kilele Nights · Sat 14 Nov, 18:00
Type        Paid  Guests   Left        Takings
Regular        0       0    500          KES 0
VIP           51       0     -1    KES 255,000
Total         51       0    499    KES 255,000
```

Fifty places, 51 tickets. Nothing in two offline laptops can prevent this: neither knew about the other's sale when it made its own. Pulling often makes the window small, but it's always there. On Day 120, one server decides who gets the last ticket, and the other gate finds out.

## When you're stuck

- **`codesStartWithTheGateId` says `expected: "A-VIP-0001" but was: "VIP-0001"`**: the prefix isn't there yet. Do step 3: both the constructor and `codeFor`.
- **`expected: "A-VIP-0001" but was: "AVIP-0001"`**: the prefix needs a dash: `gateId + "-"`.
- **`aGateWithNoIdKeepsTheOldCodes` says `expected: "VIP-0001" but was: "-VIP-0001"`**: a gate with no id must get an empty prefix, not a lone dash. Check `gateId.isEmpty()` first.
- **`Not written yet: Day 117, step 4`** or **`step 5`**: that method still has its `throw`.
- **`aSaleCopiedTwiceIsSavedOnce` says `Couldn't copy A-REG-0001 from gate A. Is it already here? Each gate needs its own gate id.`** with `UNIQUE constraint failed: peer_sales.idempotency_key`: the second copy tried to insert again. Wrap the inserts in `if (!alreadyCopied(entry.key()))`.
- **The tests stop at `Running com.boxoffice.core.lan.LanSyncTest` and never finish**: the cursor never moves, so `pull` asks for the same page for ever. Check the `peer_cursors` upsert is there, runs `executeUpdate()`, and is **outside** the `if`. Press Ctrl+C to stop Maven.
- **`readsMoreThanOnePage` says `expected: 106 but was: 100`**: `pull` stopped after the first page. It must loop until a page comes back empty.
- **`setup` says `Give the gate a short id like A, B or G2`**: one to three capital letters or digits. `main-gate` is too long; `a` must be `A`.
- **`Couldn't pull from http://127.0.0.1:8100: Gave up after 5 attempts: Can't reach the server`**: no feed is running on that port. Start `serve-feed` in another terminal, and check the port. With two laptops, also check both are on the same Wi-Fi, that you used A's address and not `127.0.0.1`, and that A's firewall lets Java in.
- **`Couldn't pull from ...: Both gates are called B. Give one of them another id`**: you pulled from your own feed (`--db b.db pull` from B's port), or both laptops ran `setup --gate B`.
- **`Couldn't pull from ...: Give both gates an id first: setup --gate A on one, setup --gate B on the other`**: one of the two `gate.db` files has no id yet.
- **`Unknown options: '--db', 'a.db'`**: `--db` goes before the command: `--db a.db setup --gate A`.
- **`serve-feed` stops with `java.net.BindException: Address already in use`**: a feed is already running on 8100, maybe in another terminal. Stop it, or use `--port 8101`.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/lan/PeerPuller.java` (or `TicketStore.java`, `Gate.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say why each gate needs its own id
- [ ] Can say which count numbers the next ticket, and which counts places left
- [ ] Can explain what the cursor is, and why it's kept by gate id
- [ ] Can explain how `peer_sales` stops a sale being copied twice
- [ ] Can say why a copied sale never goes into the outbox
- [ ] Wrote the prefix, `applyPeerSale` and the pull loop
- [ ] All 230 tests pass
- [ ] Ran two gates on one laptop, and pulled both ways
- [ ] Watched both gates sell the last VIP ticket, and can say why it can't be prevented offline
- [ ] Committed your work to Git (not `gate.db` or the practice `.db` files)

## Next

Two gates now share their sales, but every sale still goes through one database connection on each laptop, one at a time. Tomorrow's gate is busier: twenty tills selling at once at a festival rush, and a test that makes them race for the same tickets until something gives. You find out how an app oversells, and make it stop.
