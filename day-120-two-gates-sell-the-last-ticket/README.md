# Day 120: Two gates sell the last ticket

> 22:10. The Wi-Fi has been down for an hour. At gate A, Kamau buys the last VIP place. At gate B, so does Wanjiku.

Watch the video: *(not recorded yet)*

## The brief

Kilele Nights has two gates. When the venue Wi-Fi works, they tell each other about every sale (Day 117) and sync to the server (Day 119). Tonight it doesn't. Each gate keeps selling from what it knows, and that's the right thing to do: a gate that stops selling every time the Wi-Fi drops is useless.

So both gates sell "the last" VIP place. Neither did anything wrong. Each counted what it knew. Now there are 51 VIP tickets for 50 places, and nobody knows yet.

When the Wi-Fi comes back, both gates sync. The server is the one place that sees every sale, so the server decides. Today's rule:

**The first sale to reach the server gets the place.**

The other sale is **oversold**. The server keeps it, answers `409 Conflict`, and doesn't keep its tickets. Gate B hears that, keeps going with its other sales, and puts Wanjiku's sale on a list for staff:

```
$ java -jar cli/target/boxoffice.jar --db b.db sync --server http://127.0.0.1:8080
Sent 0 sales. Nothing waiting. 1 sale was oversold: run conflicts to see who.

$ java -jar cli/target/boxoffice.jar --db b.db conflicts
B-VIP-0001 (Wanjiku): Only 0 VIP places were left
Offer each buyer a refund or another ticket type. Their ticket still works at this gate.
```

You write four pieces: the oversold check and a row lock on the server, and on the gate, what to do when the server says "oversold".

## What you'll learn

- Why the server, not a gate, settles the last ticket, and why "first to reach the server" is the rule
- Why "the earliest `sold_at` wins" sounds fairer but can't work
- What a **row lock** is, and how `select ... for update` stops two requests counting at once
- Why the lock from Day 118 can't help here
- What **409 Conflict** means, and why it's different from a refusal like 401
- What **idempotency** really means: the same request gets the **same answer**, not just "saved once"
- Why an oversold sale must not stop the outbox, when a refusal does
- Why the oversold ticket still works at the gate

## New words

- **Conflict**: two sales that can't both be true, like two buyers for the last place.
- **Oversold**: more tickets sold than there are places.
- **Verdict**: what the server decided about a sale: accepted, duplicate or oversold.
- **Capacity**: how many places a ticket type has. VIP has 50.
- **Row lock**: a database's "wait your turn" on one row. Another transaction that wants the same row waits until the first commits or rolls back.
- **`select ... for update`**: "read this row, and lock it until my transaction ends".
- **409 Conflict**: an HTTP answer meaning "I understood, and it clashes with what's already here".
- **Idempotent**: doing it twice has the same effect, and gets the same answer, as doing it once.
- **Refund**: giving the buyer their money back.

## What's already written

From Days 101–119: the gate app and the sync server with device login, working, with tests. New today, already done:

On the server:

- `V2__ticket_types_and_verdicts.sql`: a `ticket_types` table with each type's `capacity` (REG 500, VIP 50), and two new columns on `sales`: `result` and `detail`. Read it: it's short.
- `Sales.receive` now returns a `Verdict` (`ACCEPTED`, `DUPLICATE` or `OVERSOLD`, with a `detail`). It checks the sale is one type, reads the type's capacity, looks for an earlier verdict for the same key, and counts the tickets sold. `saveSale` keeps a sale with its verdict. There's a hook, `afterCounting`, that runs after the count and before the save.
- `SyncController` answers `409 {"result":"oversold","error":"..."}` for `OVERSOLD`.
- Tests: `LastTicketTest` (5), `RaceToTheServerTest` (1), and a third test in `GateToServerTest` where two real gates sell the last VIP.

On the gate:

- `V7__add_sync_result.sql`: a `sync_result` column on `outbox`.
- `SaleConflict.java`: the exception for "oversold". `SyncConflict.java`: one line on the staff's list, with `describe()`.
- `TicketStore`: `markConflict(...)` and `conflicts()`. `markSynced` now also sets `sync_result` to `accepted`.
- `SyncReport` has a `conflicts` count, and `describe()` says `1 sale was oversold: run conflicts to see who.`
- The CLI's new `conflicts` command.
- Tests: 4 more in `OutboxSenderTest`, 1 in `HttpSyncClientTest`, 2 in `BoxOfficeCliTest`.

New, for you to finish: the oversold check (step 3) and `for update` (step 4) in `Sales.java`, catching `SaleConflict` in `OutboxSender.java` (step 5), and turning a 409 into a `SaleConflict` in `HttpSyncClient.java` (step 6). The TODOs are labelled with their step numbers.

## What you'll use

**Why the server decides.** Gate A doesn't know about gate B's sale, and B doesn't know about A's. Only the server ends up with both. So it's the only place that can say "you got it, you didn't".

**Why "first to reach the server", not "first sold".** "Kamau bought at 22:10:03, Wanjiku at 22:10:07, so Kamau wins" sounds fairer. But to use it, the server would have to wait before answering anyone. Gate B might be offline for another two hours, holding a sale from 22:09. Until every gate has synced, the server can never be sure who was first, and gate A would be told "maybe" all night. (Laptop clocks also disagree by seconds or minutes, so `sold_at` isn't a fair referee either.) "First to reach the server" can be answered the moment a sale arrives, and the answer never changes.

**Counting what's left.** For each sale, the server reads the type's capacity, counts the tickets it already has, and compares:

```java
int left = capacity.get() - sold;
if (sale.tickets().size() > left) {
    // oversold
}
```

A family of 3 when 1 place is left is refused whole, the same as on the gate on Day 111. The message says `Only 1 VIP place was left` or `Only 0 VIP places were left`.

**Keeping the oversold sale, without its tickets.** The server saves the sale row with `result = 'OVERSOLD'` and the detail, but no tickets. The tickets would take places that aren't there. The row is kept so a repeat can be answered.

**Idempotency means the same answer.** On Day 116, the idempotency key meant "a sale sent twice is saved once". Today it means more. Gate B sends Wanjiku's sale, and the Wi-Fi drops before the answer comes back. B sends it again with the same key. The server must say **409 again**, because that's what it decided. If it counted again, the answer could change (one day, an organiser may cancel a ticket and free a place), and gate B would be told two different things about one sale. Whatever has changed since, a repeat gets the **same verdict** as the first time. That's why `receive` looks for an earlier verdict first, and why the oversold sale is saved. (An accepted sale sent again gets `200 duplicate`, as yesterday.)

**The race on the server.** The server handles each request on its own thread, so this is Day 118 again, between two gates. A and B send the last VIP at the same moment. Both requests count 49, both see 1 left, both save: 51 tickets. Day 118's `ReentrantLock` would work only while there's one server program; the database is what every request shares. So the lock goes in the database:

```sql
select capacity from ticket_types where event_id = ? and code = ? for update
```

`for update` locks the `VIP` row of `ticket_types` until this transaction commits. (`Sales.receive` is `@Transactional`, so the transaction is the whole method.) A second request for VIP waits at that line. When the first commits, the second goes on, counts 50, and is oversold. A request for REG locks a different row, so it doesn't wait.

**How the test forces the race.** `RaceToTheServerTest` sets `sales.afterCounting(...)` to sleep 100 ms between the count and the save. Two threads wait on a `CountDownLatch` and send at the same moment over real HTTP. Without `for update`, both requests count 49 inside those 100 ms, and both get `201`. With it, the second one can't even read the capacity until the first has saved.

**409 is not a refusal.** On Day 119, a `401` meant "the server doesn't know this laptop". That's `SyncRefused`: nothing will work until someone runs `register`, so the outbox **stops** and tries again later, in order. A `409` is different. The server has the sale, has decided, and won't change its mind. Stopping would hold every later sale behind one that can never go through. So the outbox **marks it and moves on**:

```java
} catch (SaleConflict e) {
    store.markConflict(entry.id(), e.getMessage(), clock.instant());
    conflicts++;
    continue;
}
```

`continue` jumps to the next sale in the loop, skipping `markSynced` and `sent++` below it. `markConflict` takes the sale out of the outbox's waiting list (it sets `sent_at`) and marks it `oversold`, so it's never sent again and it shows in `conflicts`.

**The ticket still works at the gate.** By the time the server answers, Wanjiku may be inside, dancing. Turning her ticket off now would make it the gate staff's problem at the worst moment. So the gate keeps the ticket, and `conflicts` tells staff who to talk to: a refund, or a Regular ticket instead. People sort this out, not the code.

## Steps

1. **Open the starter and run the tests.** `cd day-120-two-gates-sell-the-last-ticket/starter`, then `../../mvnw test`. You should see `Tests run: 181, Failures: 1, Errors: 4` in `core` and `BUILD FAILURE`; `cli`, `desktop` and `sync server` are `SKIPPED`:
   - four `OutboxSenderTest` tests (`anOversoldSaleDoesNotHoldUpTheRest` and three more) end with `com.boxoffice.core.sync.SaleConflict: Only 0 VIP places were left`. Nothing catches it yet (step 5);
   - `HttpSyncClientTest.aConflictCarriesTheServersReason`: `Expecting actual throwable to be an instance of: com.boxoffice.core.sync.SaleConflict but was: com.boxoffice.core.sync.SyncRefused: The server said no: HTTP 409` (step 6).

   To see the server's tests now, run only them:

   ```
   ../../mvnw test -Dtest="LastTicketTest,RaceToTheServerTest,GateToServerTest" -Dsurefire.failIfNoSpecifiedTests=false
   ```

   On Windows: `..\..\mvnw.cmd test "-Dtest=LastTicketTest,RaceToTheServerTest,GateToServerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`. You should see `Tests run: 9, Failures: 5`. `theLastVipGoesToTheSaleThatReachesTheServerFirst` says `expected: 409 but was: 201`: right now the server takes a 51st VIP ticket without blinking.

2. **Read the server's side.** Open `starter/server/src/main/resources/db/server/V2__ticket_types_and_verdicts.sql`, then `Sales.java` from top to bottom: the capacity, the earlier verdict, the count, `afterCounting`, `saveSale`. Then `SyncController.java` for the 409, and `LastTicketTest.java` and `RaceToTheServerTest.java`. Nothing to write.

3. **Refuse what doesn't fit.** In `Sales.receive`, find the TODO marked `step 3`, after `int left = ...`. If `sale.tickets().size() > left`:
   - make `new Verdict(Result.OVERSOLD, "Only " + left + " " + type + (left == 1 ? " place was" : " places were") + " left")`;
   - `saveSale(gate, key, sale, oversold)`, so the sale is kept, without its tickets;
   - `return oversold`, before the tickets are inserted.

   Run the server tests. `LastTicketTest` passes now (5). `RaceToTheServerTest` still fails with `Expecting actual: [201, 201] to contain exactly in any order: [201, 409]`: both requests counted at the same moment.

4. **Lock the ticket type's row.** Find the TODO marked `step 4`, above the `select capacity ...`. Add ` for update` to the end of the SQL string (mind the space): `"select capacity from ticket_types where event_id = ? and code = ? for update"`.

   Run the server tests. `RaceToTheServerTest` passes. Only `twoGatesOfflineSellTheLastVipAndTheServerSettlesIt` still fails, with `but was: SyncReport[sent=0, waiting=1, problem=The server said no: HTTP 409, conflicts=0]`. The server is right now; the gate doesn't understand the answer yet, so it stops and waits.

5. **Don't stop for an oversold sale.** Open `starter/core/src/main/java/com/boxoffice/core/sync/OutboxSender.java`. Where the TODO marked `step 5` is, add a `catch (SaleConflict e)` above the `catch (NetworkException | SyncRefused e)`. Inside it: `store.markConflict(entry.id(), e.getMessage(), clock.instant())`, then `conflicts++`, then `continue`.

6. **Turn a 409 into a `SaleConflict`.** Open `HttpSyncClient.java`. Where the TODO marked `step 6` is, **above** the `/ 100 != 2` check: if `response.statusCode() == 409`, `throw new SaleConflict(serverSays(response.body()))`. `serverSays` is already written: it reads the `error` from the server's JSON.

7. **Run all the tests.** `../../mvnw test`. You should see 273 tests in all and `BUILD SUCCESS`. Run it once or twice more: `RaceToTheServerTest` forces the race on every run, so it should pass every time.

8. **Two gates, one last ticket, on your laptop.** Follow "Run it and test it" below. You play the organiser, gate A and gate B, from two terminals.

9. **Stretch (not required): a test of your own.** In `LastTicketTest`, write one more test: gate B's oversold sale, sent again with the same key, gets the same `error` as the first time (`aRepeatGetsTheSameAnswerAsTheFirstTime` only checks the `result`). Or: a REG sale from gate A still goes through while VIP is full. Copy the shape of the tests above it. Your test isn't part of the course's tests, so keep it in your own Git.

## Run it and test it

From the day's `starter` folder:

```
cd day-120-two-gates-sell-the-last-ticket/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=LastTicketTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=LastTicketTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **181 tests** in `core` (including OutboxSender 11, HttpSyncClient 7), **28** in `cli` (BoxOfficeCli 25, PracticeServer 3), **41** in `desktop` and **23** in `sync server` (DeviceLogin 9, GateToServer 3, LastTicket 5, RaceToTheServer 1, SyncApi 5), **273** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] Box Office sync server ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**Build the jars.** `../../mvnw package`.

**A fresh server.** Yesterday's `sync-server-db.mv.db` is in Day 119's folder, so starting the server from today's `starter` folder makes a new one. Flyway runs V1 and V2 (`Successfully applied 2 migrations`). If you start it where an old one is, Flyway just adds V2.

**Terminal 1: the server.** As on Day 119 (`pick-a-long-secret` is a placeholder: use your own):

macOS / Linux:

```
BOXOFFICE_ADMIN_KEY=pick-a-long-secret java -jar server/target/boxoffice-server.jar
```

Windows PowerShell:

```
$env:BOXOFFICE_ADMIN_KEY="pick-a-long-secret"
```

```
java -jar server\target\boxoffice-server.jar
```

Windows cmd:

```
set BOXOFFICE_ADMIN_KEY=pick-a-long-secret
```

```
java -jar server\target\boxoffice-server.jar
```

Wait for `Started SyncServerApplication`.

**Terminal 2: both gates.** One laptop plays both gates, with a database file each: `--db a.db` is gate A, `--db b.db` is gate B. Set the admin key in this terminal too, for `register`:

macOS / Linux:

```
export BOXOFFICE_ADMIN_KEY=pick-a-long-secret
```

Windows PowerShell: `$env:BOXOFFICE_ADMIN_KEY="pick-a-long-secret"`. Windows cmd: `set BOXOFFICE_ADMIN_KEY=pick-a-long-secret`. (On Windows, write `cli\target\boxoffice.jar` below.)

Set up and register both gates:

```
$ java -jar cli/target/boxoffice.jar --db a.db setup --gate A
Kilele Nights is ready in a.db as gate A

$ java -jar cli/target/boxoffice.jar --db a.db register --server http://127.0.0.1:8080
Gate A is registered with http://127.0.0.1:8080. sync logs in with it from now on.

$ java -jar cli/target/boxoffice.jar --db b.db setup --gate B
Kilele Nights is ready in b.db as gate B

$ java -jar cli/target/boxoffice.jar --db b.db register --server http://127.0.0.1:8080
Gate B is registered with http://127.0.0.1:8080. sync logs in with it from now on.
```

Early evening, Wi-Fi up: gate A sells 49 VIP places (one big group, to save typing) and syncs:

```
$ java -jar cli/target/boxoffice.jar --db a.db sell -q 49 VIP "Early birds"
Sold 49 tickets to Early birds for KES 245,000: A-VIP-0001 to A-VIP-0049

$ java -jar cli/target/boxoffice.jar --db a.db sync --server http://127.0.0.1:8080
Sent 1 sale. Nothing waiting.
```

22:10, Wi-Fi down. Gate B never heard about A's sales, so it thinks all 50 are free. Each gate sells "the last" VIP:

```
$ java -jar cli/target/boxoffice.jar --db a.db sell VIP Kamau
Sold A-VIP-0050 to Kamau for KES 5,000

$ java -jar cli/target/boxoffice.jar --db b.db sell VIP Wanjiku
Sold B-VIP-0001 to Wanjiku for KES 5,000
```

Wi-Fi back. A reaches the server first:

```
$ java -jar cli/target/boxoffice.jar --db a.db sync --server http://127.0.0.1:8080
Sent 1 sale. Nothing waiting.

$ java -jar cli/target/boxoffice.jar --db b.db sync --server http://127.0.0.1:8080
Sent 0 sales. Nothing waiting. 1 sale was oversold: run conflicts to see who.

$ java -jar cli/target/boxoffice.jar --db b.db conflicts
B-VIP-0001 (Wanjiku): Only 0 VIP places were left
Offer each buyer a refund or another ticket type. Their ticket still works at this gate.

$ java -jar cli/target/boxoffice.jar --db a.db conflicts
No conflicts.
```

"Nothing waiting": the oversold sale isn't stuck in the outbox. Sync B again and it isn't sent again (`Sent 0 sales. Nothing waiting.`). And it doesn't hold up later sales:

```
$ java -jar cli/target/boxoffice.jar --db b.db sell REG Otieno
Sold B-REG-0001 to Otieno for KES 1,500

$ java -jar cli/target/boxoffice.jar --db b.db sync --server http://127.0.0.1:8080
Sent 1 sale. Nothing waiting.
```

Stop the server with Ctrl+C. `a.db`, `b.db` and `sync-server-db.mv.db` are all in `.gitignore`. Delete them to play the night again.

## When you're stuck

- **`OutboxSenderTest` fails with `com.boxoffice.core.sync.SaleConflict: Only 0 VIP places were left`**: nothing catches `SaleConflict`, so it flies out of `sendWaiting`. Do step 5.
- **`aConflictCarriesTheServersReason` says `but was: com.boxoffice.core.sync.SyncRefused: The server said no: HTTP 409`**: the 409 check is missing, or it's below the `/ 100 != 2` check, which catches every non-2xx answer first. Put it above.
- **`theLastVipGoesToTheSaleThatReachesTheServerFirst` or `aGroupThatDoesNotFitIsRefusedWhole` says `expected: 409 but was: 201`**: the oversold check is missing. Do step 3.
- **`aRepeatGetsTheSameAnswerAsTheFirstTime` says `expected: 409 but was: 200`**: the server took the sale the first time, so the repeat is a `duplicate`. Do step 3. Keep the `saveSale(gate, key, sale, oversold)` line in it: it's what lets the server give a repeat its first answer.
- **`RaceToTheServerTest` says `Expecting actual: [201, 201] to contain exactly in any order: [201, 409]`**: both requests counted before either saved. Add ` for update` (step 4), with a space before `for`.
- **`twoGatesOfflineSellTheLastVipAndTheServerSettlesIt` says `but was: SyncReport[sent=1, waiting=0, problem=, conflicts=0]`**: the server took gate B's sale, so it never said 409. Do step 3.
- **The same test says `but was: SyncReport[sent=0, waiting=1, problem=The server said no: HTTP 409, conflicts=0]`**: the server is done; the gate isn't. Do steps 5 and 6.
- **`anOversoldSaleIsListedForStaff` says `Expected size: 1 but was: 0`, and `anOversoldSaleDoesNotHoldUpTheRest` says `but was: SyncReport[sent=2, waiting=0, problem=, conflicts=1]`**: the `catch (SaleConflict e)` has no `continue`, so the code after the `try` runs too: `markSynced` turns the conflict back into `accepted`, and `sent++` counts it as sent. Add `continue;` at the end of the catch.
- **`cli`, `desktop` and `sync server` are `SKIPPED`**: `core` failed first. Use the `-Dtest=...` command from step 1 for the server's tests, or finish steps 5 and 6.
- **`sync` says `Can't reach the server. Trying again...`**: add `--server http://127.0.0.1:8080`; without it, `sync` tries the practice server on 8099.
- **`sync` says `The server doesn't know this laptop. Run register first`**: that `--db` file was never registered, or the server's database was deleted since. Run `register` with the same `--db`.
- **`Web server failed to start. Port 8080 was already in use.`**: Day 119's server is still running somewhere. Stop it with Ctrl+C.
- **Still stuck?** Open `solution/server/src/main/java/com/boxoffice/server/Sales.java` (or `solution/core/src/main/java/com/boxoffice/core/sync/OutboxSender.java`, `HttpSyncClient.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say why the server decides, and why "first to reach the server" and not "earliest sold"
- [ ] Can say what `for update` locks, and why Day 118's lock can't help between gates
- [ ] Can say what idempotency means today: the same answer, not just saved once
- [ ] Can say why a 409 doesn't stop the outbox when a 401 does
- [ ] Can say why the oversold ticket still works at the gate
- [ ] All 273 tests pass, more than once
- [ ] Played the night with two gates and saw Wanjiku on gate B's `conflicts` list
- [ ] Committed your work to Git (not `a.db`, `b.db` or `sync-server-db.mv.db`)

## Next

The gates and the server agree on every ticket. Tomorrow fans stop queueing at the gate at all: a public ticket site, served by the same Spring Boot server, with Thymeleaf pages and htmx to update them without reloading.
