# Day 116: Queue now, sync later

> 20:05, the Wi-Fi has been down for forty minutes, and Achieng is the 212th sale since. The gate doesn't care. Every sale is waiting on the disk for the server.

Watch the video: *(not recorded yet)*

## The brief

From Day 119, the organiser has a **sync server**: one place on the internet that collects every gate's sales, so she can see the night's takings from her phone. The gate has to tell it about every sale. But the gate must **never** stop selling because the internet is down. A sale happens in the queue, now. Telling the server can wait.

So every sale leaves a note on the gate's own disk: "tell the server about this". That note sits in a table called the **outbox**, and it's saved **in the same transaction** as the sale (Day 111): both are saved, or neither. Later, when the Wi-Fi is back, `sync` sends the waiting notes to the server, oldest first, and ticks each one off.

Two rules keep this safe:

- **Nothing overtakes.** If one sale can't be sent, `sync` stops there. A later sale never reaches the server before an earlier one.
- **The server never saves a sale twice.** Every note gets its own random **idempotency key** when the sale is made, and keeps it for ever. If a sale did reach the server but the answer was lost, the gate sends it again, with the same key, and the server says "I've had this one".

The real server comes on Day 119. Today you practise against a pretend one on your own laptop:

```
$ java -jar cli/target/boxoffice.jar sync
HTTP 503. Trying again in 0.4 s (attempt 2 of 5)
HTTP 503. Trying again in 0.8 s (attempt 3 of 5)
Sent 3 sales. Nothing waiting.
```

## What you'll learn

- What the **outbox pattern** is, and why the note and the sale go in one transaction
- How **Jackson** turns a record into JSON and back
- What a **UUID** is, and why each sale gets one as its idempotency key
- Why sending oldest first and stopping at the first failure keeps the order
- The difference between "couldn't reach the server" (try later) and "the server said no"
- How the `Idempotency-Key` header makes yesterday's client allowed to send a POST again
- How to run a pretend server in one terminal and the gate in another

## New words

- **Sync**: making two places hold the same thing; here, getting the gate's sales onto the server.
- **Sync server**: the organiser's server that collects every gate's sales. You build the real one on Day 119.
- **Outbox**: a table of messages waiting to be sent, like the outbox of an email program.
- **Outbox pattern**: saving a change and its "tell the others" note in one transaction, then sending the notes later.
- **Payload**: the data a message carries. Here, the sale as JSON.
- **UUID** (universally unique identifier): a random 36-character id like `b1c4f3fe-ccc9-48d3-9954-a914cf917078`. Two of them are, in practice, never the same.
- **Idempotency key**: the label that lets the server spot a repeat. Today it's a UUID per sale.
- **Serialise**: turn an object into text (JSON here) so it can be saved or sent. **Deserialise** is the way back.
- **Batch**: a group of rows handled together. `sync` reads the outbox 50 at a time.
- **Practice server**: a pretend sync server that runs on your laptop until you stop it.
- **Port**: a number that picks one program on a computer, like `8099`. `http://127.0.0.1:8099` is "port 8099 on this laptop".

## What's already written

From Days 101–115: the gate, the SMS reminders and the Wi-Fi-proof HTTP client, working, with tests. New today, already done:

- `V5__add_outbox.sql`: the `outbox` table. Read it: it's short.
- `TicketStore`: `recordSale` now runs inside `inTransaction`, and both `recordSale` and `recordGroupSale` call `queueForSync(eventId, tickets)` inside their transaction. The outbox methods `waitingToSync`, `countWaitingToSync`, `markSynced`, `markSyncFailed` and `lastSyncError` are written. You write `queueForSync`.
- A new package, `com.boxoffice.core.sync`:
  - `SalePayload`: a sale the way the server sees it, with `toJson()` and `fromJson()`, done by Jackson.
  - `OutboxEntry`: one waiting sale: its id, key, payload and failed attempts.
  - `SyncClient`: an interface with one method, `push(entry)`. Tests pass a pretend one.
  - `HttpSyncClient`: the real one. It POSTs the payload to `/sync/sales` through `ResilientHttp`. You add one header.
  - `SyncRefused`: "the server answered, and the answer was no".
  - `SyncReport`: what one round of syncing did, with `describe()`.
  - `OutboxSender`: empties the outbox. You write `sendWaiting`.
- The CLI has two new commands: `practice-server` (`PracticeServerCommand.java` and `PracticeServer.java`) and `sync` (`SyncCommand.java`).

New, for you to finish: `TicketStore.queueForSync`, `OutboxSender.sendWaiting`, and the `Idempotency-Key` header in `HttpSyncClient.push`. New tests: `OutboxTest` (7), `OutboxSenderTest` (7), `HttpSyncClientTest` (3), `PracticeServerTest` (3) and two more in `BoxOfficeCliTest`.

## What you'll use

**Why the note goes in the sale's transaction.** Picture the other way: save the sale, then, in a second step, save the note. The power goes between the two. The sale is in `gate.db`, the note isn't, and the server never hears about that sale. Or send it to the server straight away, during the sale: then a sale needs the internet, and the queue stops when the Wi-Fi does. In the same transaction, the sale and its note are saved together or not at all, and the sending happens later, whenever it can.

**The outbox table.** From `V5__add_outbox.sql`:

| Column | What it holds |
|---|---|
| `id` | 1, 2, 3...: the order the sales were made in |
| `event_id` | `kilele-2026` |
| `idempotency_key` | a UUID, `unique`: no two notes share one |
| `payload` | the sale as JSON |
| `created_at` | when the sale was made |
| `attempts` | how many times sending has failed |
| `last_error` | why it failed last time |
| `sent_at` | empty until the server has it |

"Waiting" means `sent_at is null`. The table is never emptied: a sent note just gets its `sent_at`.

**The sale as JSON.** Jackson reads a record's components and writes them out by name:

```java
SalePayload.of("kilele-2026", tickets).toJson()
```

```json
{"eventId":"kilele-2026","tickets":[{"code":"REG-0001","type":"REG","buyer":"Otieno",
 "phone":"","priceCents":150000,"soldAt":"2026-11-14T18:00:00Z"}]}
```

Money goes as whole cents and times as UTC text, so nothing is rounded or moved to another time zone on the way. A group sale of four is **one** note with four tickets in it.

**A UUID per sale.** `UUID.randomUUID().toString()` gives a new random key. It's made once, when the sale is saved, and it's in the outbox row for good. Every time `sync` sends that sale, it sends the same key. That's the whole trick: a repeat looks like a repeat.

**A `PreparedStatement` with four `?`s.** The same as `recordSale` inserting a ticket since Day 104:

```java
try (PreparedStatement insert = connection.prepareStatement(
        "insert into outbox (event_id, idempotency_key, payload, created_at) values (?, ?, ?, ?)")) {
    insert.setString(1, eventId);
    // ... 2, 3 and 4 ...
    insert.executeUpdate();
}
```

Use the same `connection` as the sale. A second connection would be a second transaction, and the note would no longer be saved together with the sale. `attempts`, `last_error` and `sent_at` have defaults, so you don't set them.

**Oldest first, and stop at the first failure.** `sendWaiting` reads up to 50 waiting notes, oldest first, and pushes them one by one. When one fails, it records why (`markSyncFailed`) and **returns straight away**. If it carried on, sale 3 could reach the server before sale 2. The server would see the numbers out of order, and on Day 120, when the server decides who gets the last ticket, order matters. What's left stays on disk for the next `sync`, in a minute or tomorrow morning.

**Two ways to fail.** `push` can throw two things, and both stop the round:

- `NetworkException` (Day 115): the server couldn't be reached, even after the retries. Try later.
- `SyncRefused`: the server answered, and it wasn't a 2xx. Sending it again as it is won't help.

**Why the header matters twice.** On the server's side, the `Idempotency-Key` lets it spot a repeat. On the gate's side, `ResilientHttp.repeatable` (Day 115) only lets a POST be sent again after a timeout or a 500 when it carries that header. Without it, one lost answer is the end of that round.

**The practice server.** `PracticeServer` keeps sales in memory, by key. A key it has seen before gets `{"result":"duplicate"}` and isn't saved again. A sale with no key gets `400`. With `--flaky 0.5`, it answers `503` to about half the requests, like a server at the end of bad Wi-Fi.

## Steps

1. **Open the starter and run the tests.** `cd day-116-queue-now-sync-later/starter`, then `../../mvnw test`. You should see `Tests run: 151, Failures: 7, Errors: 8` in `core`, and `BUILD FAILURE`. `cli` and `desktop` are `SKIPPED`. The failures:
   - `OutboxTest`: `Expected size: 1 but was: 0 in: []` and `expected: 1 but was: 0`: no note was left;
   - `OutboxSenderTest`: `Not written yet: Day 116, step 4`, and one `NoSuchElement`, because the outbox is empty;
   - `HttpSyncClientTest`: `Expecting actual: [null] to contain exactly (and in same order): ["6f1c2a"]`, and `SyncRefused The server said no: HTTP 500`.

   V5 is complete, so opening the app now is safe. But sales you make before step 3 leave no note, and will never be synced.

2. **Read the outbox and the payload.** Open `starter/core/src/main/resources/db/migration/V5__add_outbox.sql`, then `starter/core/src/main/java/com/boxoffice/core/sync/SalePayload.java`. In `TicketStore.java`, find where `recordSale` and `recordGroupSale` call `queueForSync`: inside `inTransaction(() -> { ... })`. Nothing to write.

3. **Leave a note with every sale.** In `TicketStore.java`, find `queueForSync`. Insert one row into `outbox`, as the TODO says: a `PreparedStatement` with four `?`s on `connection`, then `executeUpdate()`:
   - 1, `event_id`: `eventId`;
   - 2, `idempotency_key`: `UUID.randomUUID().toString()`;
   - 3, `payload`: `SalePayload.of(eventId, tickets).toJson()`;
   - 4, `created_at`: `tickets.getFirst().soldAt().toString()`.

   `UUID` is `java.util.UUID`, already imported. Run `../../mvnw test -Dtest=OutboxTest -Dsurefire.failIfNoSpecifiedTests=false`. All 7 pass. `aSaleThatFailsLeavesNoNote` proves the transaction: a sale that fails leaves no note behind.

4. **Send what's waiting, in order.** Open `OutboxSender.java` and find `sendWaiting`. Replace the `throw` with a `while (true)` loop, as the TODO says:
   - `List<OutboxEntry> batch = store.waitingToSync(BATCH);`. Empty? Everything's sent: `return new SyncReport(sent, 0, "");`
   - for each entry, `client.push(entry)` inside a `try`;
   - `catch (NetworkException | SyncRefused e)`: `store.markSyncFailed(entry.id(), e.getMessage())`, then `return new SyncReport(sent, store.countWaitingToSync(), e.getMessage());`
   - after a push that worked: `store.markSynced(entry.id(), clock.instant())` and `sent++`.

   The `|` in a `catch` catches either one. Run `OutboxSenderTest`: all 7 pass.

5. **Send the key.** Open `HttpSyncClient.java` and find `push`. Where the TODO is, add `.header("Idempotency-Key", entry.key())` to the builder, next to the `Content-Type` header. Run `HttpSyncClientTest`: all 3 pass.

6. **Run all the tests.** `../../mvnw test`. You should see 211 tests in all and `BUILD SUCCESS`.

7. **Sell offline, sync later.** Follow "Run it and test it" below. You'll need two terminals.

8. **Stretch (not required): send a repeat yourself.** With the practice server running, use `curl` to POST the same sale twice with the same key, and watch the server refuse to save it twice (see the end of "Run it and test it"). Then try it with no `Idempotency-Key` header at all.

## Run it and test it

From the day's `starter` folder:

```
cd day-116-queue-now-sync-later/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=OutboxSenderTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=OutboxSenderTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **151 tests** in `core` (including Outbox 7, OutboxSender 7, HttpSyncClient 3), **19** in `cli` (BoxOfficeCli 16, PracticeServer 3) and **41** in `desktop`, **211** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

**Sell with no server.** Build, then sell a few (on Windows, write `cli\target\boxoffice.jar`):

```
../../mvnw package -DskipTests
```

```
java -jar cli/target/boxoffice.jar setup
```

```
java -jar cli/target/boxoffice.jar sell REG Otieno
```

```
java -jar cli/target/boxoffice.jar sell VIP Wanjiku
```

```
java -jar cli/target/boxoffice.jar sell REG "Kamau family" --quantity 4
```

Selling works with no server at all. Now try to sync. Nothing is listening yet, so after about seven seconds of retries:

```
java -jar cli/target/boxoffice.jar sync
```

```
Can't reach the server. Trying again in 0.4 s (attempt 2 of 5)
Can't reach the server. Trying again in 0.8 s (attempt 3 of 5)
Can't reach the server. Trying again in 1.5 s (attempt 4 of 5)
Can't reach the server. Trying again in 3.1 s (attempt 5 of 5)
Sent 0 sales. 3 still waiting: Gave up after 5 attempts: Can't reach the server
```

Three sales, not six tickets: the Kamau family's four tickets are one sale.

**Start the practice server.** Open a **second terminal**, `cd` to the same `starter` folder, and run:

```
java -jar cli/target/boxoffice.jar practice-server --flaky 0.5
```

```
Practice sync server on http://127.0.0.1:8099 (Ctrl+C to stop)
```

It keeps running until you press Ctrl+C. `--flaky 0.5` makes it play dead about half the time; leave it out for a server that always answers. `--port 8100` picks another port.

**Sync.** Back in the first terminal:

```
java -jar cli/target/boxoffice.jar sync
```

```
HTTP 503. Trying again in 0.4 s (attempt 2 of 5)
HTTP 503. Trying again in 0.8 s (attempt 3 of 5)
HTTP 503. Trying again in 1.8 s (attempt 4 of 5)
HTTP 503. Trying again in 2.9 s (attempt 5 of 5)
HTTP 503. Trying again in 0.3 s (attempt 2 of 5)
HTTP 503. Trying again in 0.7 s (attempt 3 of 5)
HTTP 503. Trying again in 1.4 s (attempt 4 of 5)
HTTP 503. Trying again in 0.3 s (attempt 2 of 5)
Sent 3 sales. Nothing waiting.
```

Yours will be different: the server plays dead at random. If it plays dead five times in a row for one sale, `sync` says `still waiting` and stops; run it again. In the second terminal, the server shows what it got, in order:

```
Pretending to be down: answered 503
Pretending to be down: answered 503
Pretending to be down: answered 503
Pretending to be down: answered 503
Got REG-0001 for Otieno
Pretending to be down: answered 503
Pretending to be down: answered 503
Pretending to be down: answered 503
Got VIP-0001 for Wanjiku
Pretending to be down: answered 503
Got REG-0002, REG-0003, REG-0004, REG-0005 for Kamau family
```

Run `sync` again: `Sent 0 sales. Nothing waiting.` Sync is safe to run as often as you like. Stop the server with Ctrl+C when you're done.

The window sells through the same `TicketStore`, so sales made in `java -jar desktop/target/boxoffice-desktop.jar` wait in the outbox too. Sales made before today aren't in the outbox, because they were made before V5.

**Send a repeat yourself (step 8).** With the practice server running (no `--flaky`), make a file called `sale.json` in the `starter` folder, holding this one line:

```json
{"eventId":"kilele-2026","tickets":[{"code":"REG-0099","type":"REG","buyer":"Achieng","phone":"","priceCents":150000,"soldAt":"2026-11-14T18:00:00Z"}]}
```

Then send it twice with the same key. macOS / Linux:

```
curl -X POST -H "Idempotency-Key: practice-1" -d @sale.json http://127.0.0.1:8099/sync/sales
```

Windows (PowerShell or cmd: type `curl.exe`, because in PowerShell plain `curl` is a different command):

```
curl.exe -X POST -H "Idempotency-Key: practice-1" -d @sale.json http://127.0.0.1:8099/sync/sales
```

The first time it answers `{"result":"accepted"}`, the second time `{"result":"duplicate"}`, and the server says `Already had REG-0099: not saved twice`. Leave out the `-H "Idempotency-Key: practice-1"` and it answers `Idempotency-Key header missing`. Delete `sale.json` afterwards: it's practice, not part of the app.

## When you're stuck

- **`OutboxTest` says `Expected size: 1 but was: 0 in: []`** (or `expected: 1 but was: 0`): no note was saved. `queueForSync` is still empty, or `executeUpdate()` is missing.
- **`Couldn't record ticket REG-0002`, `Caused by: ... UNIQUE constraint failed: outbox.idempotency_key`**: every sale got the same key. Call `UUID.randomUUID()` inside `queueForSync`, so each sale gets a new one.
- **`Not written yet: Day 116, step 4`**: `sendWaiting` still has its `throw`.
- **`catchesUpWhenTheWifiComesBackWithTheSameKeys` fails with `NoSuchElementException`**: the outbox is empty, so the test found no entry. Do step 3 first.
- **The tests stop at `Running com.boxoffice.core.sync.OutboxSenderTest` and never finish**: after a failed push, the loop carried on instead of returning, so it reads the same failed sale again and again for ever. `return` the report in the `catch`. Press Ctrl+C to stop Maven.
- **`postsTheSaleAsJsonWithItsKey` says `Expecting actual: [null] to contain exactly (and in same order): ["6f1c2a"]`**: the server got no key. Do step 5.
- **`theKeyLetsItTryAgainAfterAServerError` says `SyncRefused The server said no: HTTP 500`**: the same: without the key, `ResilientHttp` doesn't dare send the POST again after a 500.
- **`sync` says `Sent 0 sales. 3 still waiting: Gave up after 5 attempts: Can't reach the server`**: the practice server isn't running, or is on another port. Start it in a second terminal, or pass `--server http://127.0.0.1:8100` to match its `--port`.
- **`practice-server` stops with `java.net.BindException: Address already in use`**: something already listens on 8099, often a practice server in another terminal you forgot. Stop it, or use `--port 8100` (and `sync --server http://127.0.0.1:8100`).
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/sync/OutboxSender.java` (or `TicketStore.java`, `HttpSyncClient.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say what the outbox pattern is, and why the note is in the sale's transaction
- [ ] Can say why each sale gets its own key, made once and kept
- [ ] Can say why `sync` stops at the first failure
- [ ] Can tell "couldn't reach the server" from "the server said no"
- [ ] Wrote `queueForSync`, `sendWaiting` and the `Idempotency-Key` header
- [ ] All 211 tests pass
- [ ] Sold with no server, then synced to the practice server in a second terminal
- [ ] Committed your work to Git (not `gate.db`)

## Next

One gate now keeps every sale safe until the server can have it. But Kilele Nights has two gates, and on a bad night there's no internet at all. Tomorrow the two laptops talk to each other over the venue router: each gets its own id, and each copies the other's sales, so a ticket sold at gate A is let in at gate B.
