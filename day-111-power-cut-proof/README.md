# Day 111: Power-cut proof

> The Kamau family buys four tickets. Two are saved. Then the generator coughs and the laptop goes dark.

Watch the video: *(not recorded yet)*

## The brief

At Ngong Racecourse the power is not yours. A generator runs out of fuel, someone trips over the extension cable, or the laptop's old battery dies at 2%. The gate has to come back on with every sale **whole**: either a sale is fully there, or it isn't there at all. Never half.

Today the gate learns to sell to a **group**. A family buys four tickets as one sale, and pays once. That code is already written. What isn't safe yet is how it's saved: right now each ticket is saved the moment it's written. If the power goes after the second ticket, `gate.db` says "a sale of 4" but holds only 2 tickets. The family has paid for 4, and the gate can't tell what happened.

You fix that in two ways:

- **One transaction per sale.** All four tickets are saved together, or none are.
- **Safer settings for SQLite.** A write-ahead log, and "wait for the disk" on every save.

Then a test proves it: it starts a real second Java program, lets it write 2 of the 4 tickets, and kills it on the spot.

```
$ java -jar cli/target/boxoffice.jar sell REG "Kamau family" --quantity 4
Sold 4 tickets to Kamau family for KES 6,000: REG-0001 to REG-0004
```

## What you'll learn

- What a **transaction** is, and why a sale of four tickets needs one
- How `setAutoCommit(false)`, `commit()` and `rollback()` work in JDBC
- Why `setAutoCommit(true)` goes in a `finally` block
- What SQLite's **write-ahead log** (WAL) is, and why `gate.db-wal` and `gate.db-shm` appear next to `gate.db`
- The difference between a **crash** (the program dies) and a **power cut** (the whole laptop dies), and what `pragma synchronous = full` adds
- How SQLite cleans up after a crash the next time it opens
- How a test can start a second Java program and kill it mid-sale

## New words

- **Group sale**: one sale of several tickets to one buyer, such as a family of four.
- **Transaction**: a group of changes to the database that are saved all together or not at all.
- **Commit**: "save this transaction for good".
- **Rollback**: "undo everything this transaction did".
- **Auto-commit**: JDBC's normal mode, where every single statement is its own transaction and is saved at once.
- **Atomic**: all-or-nothing; it can't be left half done.
- **Pragma**: a special SQLite command that changes a setting, like `pragma journal_mode = wal`.
- **WAL (write-ahead log)**: SQLite writes new changes to a side file, `gate.db-wal`, first, and copies them into `gate.db` later.
- **Checkpoint**: the moment SQLite copies finished changes from `gate.db-wal` into `gate.db`.
- **Disk cache**: memory where your computer keeps writes for a moment before they really reach the disk. It is lost when the power goes.
- **Process**: one running program. Your tests run in one; today's test starts a second one.
- **Integrity check**: SQLite reading its own file to check nothing in it is damaged.

## What's already written

From Days 101–110: everything up to QR tickets and check-in, working, with tests. New today, already done:

- `V4__add_sales.sql`: a new `sales` table (one row per sale, with its `quantity` and `total_cents`), and a new column `tickets.sale_id` that says which sale a ticket belongs to. Read it: it's short.
- `Gate.sellGroup(typeCode, buyer, phone, quantity)`: checks the same things as `sell`, plus "is there room for all of them?", then numbers the tickets and calls `store.recordGroupSale`.
- `SaleOutcome.SoldGroup`: a new kind of outcome, with the tickets and the total.
- `TicketStore.recordGroupSale(...)`: inserts the `sales` row, then one ticket at a time, all inside `inTransaction(() -> { ... })`.
- `TicketStore`: `integrityCheck()`, `journalMode()`, `ticketsInSales()` and `ticketsPromisedBySales()`, which the tests use.
- The CLI's `sell` has a new option, `--quantity` (or `-q`).
- Test helpers: `CrashingGate.java` (a tiny gate program that stops mid-sale) and `GateProcess.java` (starts it as a separate program).

New, for you to finish, both in `TicketStore.java`: two pragmas in `open`, and `inTransaction`. New tests: `GroupSaleTest` (8), `PowerCutTest` (1), and one more in `BoxOfficeCliTest`.

## What you'll use

**Why half a sale is so bad.** The `sales` row says "4 tickets, KES 6,000". If only 2 tickets made it, the report and the money don't agree, 2 people get turned away, and nobody knows which 2. A missing sale is easy: the family just buys again. A half sale is a mess.

**Auto-commit, and turning it off.** Until today, every `insert` was saved the moment it ran. That's auto-commit. Turn it off and nothing is saved for good until you say so:

```java
connection.setAutoCommit(false);   // from here, changes wait
// ... insert, insert, insert ...
connection.commit();               // now they're all saved, together
```

If something throws halfway, `connection.rollback()` undoes every change since auto-commit went off.

**Why `finally`.** The same `connection` is used for everything, including single sales. If auto-commit stays off after a group sale, the next single sale is never committed, and it vanishes when the app closes. A `finally` block runs whether the work worked or threw, so it's the one place that always turns auto-commit back on.

**`SqlWork`, a lambda that may throw.** `inTransaction` takes the work as a lambda. `Runnable` can't throw `SQLException`, so `TicketStore` has its own small interface, `SqlWork`, with `void run() throws SQLException`. Inside `inTransaction`, `work.run()` does the inserts.

**The write-ahead log.** Without WAL, SQLite changes `gate.db` itself and keeps a copy of the old pages in case it has to undo. With WAL, it leaves `gate.db` alone and adds new changes to the end of `gate.db-wal`. Later, at a checkpoint, it copies them in. Two good things for the gate:

- A crash in the middle of a write can't leave `gate.db` itself half changed.
- The screen can read the sales while the worker thread is writing a new one.

`gate.db-shm` is a small index that helps find things in the log quickly. Both files are there while the app has the database open. When the app closes normally, SQLite folds the log into `gate.db` and deletes them. After a crash they stay, and SQLite uses them the next time it opens. **Never delete them while the app is running**, and copy all three files together if you back up `gate.db`.

**A crash is not a power cut.** These protect against different things:

| What happens | What's lost | What protects you |
|---|---|---|
| The program crashes, or is killed | Anything not yet committed | A transaction |
| The power goes | Also anything still in the disk cache, even if it was committed | `pragma synchronous = full` |

When a program commits, the computer often says "done" while the data is still in the disk cache. If the program dies, the computer still writes it out. If the power dies, it's gone. `synchronous = full` makes SQLite wait until the disk says the data is really on it. Each sale takes a few milliseconds longer. Nobody in the queue will notice.

**Cleaning up after a crash.** The next time `TicketStore.open` runs, SQLite looks at the log. A transaction that was never committed is thrown away, as if it never started. That's why the family's half sale simply disappears. `integrityCheck()` runs `pragma integrity_check`, and `"ok"` means the file is healthy.

**How the test pulls the plug.** `PowerCutTest` uses `GateProcess.start` to run `CrashingGate` as a separate Java program, with its own `gate.db` in a temporary folder. `CrashingGate` starts a sale of 4 VIP tickets. After the second ticket, it prints `MID_SALE` and waits. The test waits for that line, then calls `destroyForcibly()`: the operating system ends the program at once, with no clean-up. Then the test opens the same `gate.db` and checks:

```java
assertThat(store.integrityCheck()).isEqualTo("ok");
assertThat(store.ticketsInSales(SampleEvent.ID))
        .isEqualTo(store.ticketsPromisedBySales(SampleEvent.ID));
```

A test can't switch off your laptop's power. Killing the program is as close as a test can get; `synchronous = full` covers the rest.

## Steps

1. **Open the starter and run the tests.** `cd day-111-power-cut-proof/starter`, then `../../mvnw test`. You should see `Tests run: 94, Failures: 3` in `core`, and `BUILD FAILURE`. `cli` and `desktop` are `SKIPPED`. The three failures:
   - `theDatabaseUsesAWriteAheadLog`: `expected: "wal" but was: "delete"`;
   - `aSaleThatFailsHalfwaySavesNothing`: two tickets of a failed sale are still there;
   - `PowerCutTest`: `expected: 4 but was: 2`. The sale promised 4 tickets and 2 were saved. That's the half sale.

   There's no `Not written yet` message today: the code runs, it just isn't safe.

2. **Read the group sale.** Open `starter/core/src/main/resources/db/migration/V4__add_sales.sql`, then `Gate.sellGroup` in `Gate.java`, then `recordGroupSale` in `TicketStore.java`. See that every insert happens inside `inTransaction(() -> { ... })`. Then open `starter/core/src/test/java/com/boxoffice/core/CrashingGate.java` and find where it prints `MID_SALE`. Nothing to write.

3. **Turn on the write-ahead log, and wait for the disk.** In `TicketStore.java`, find `open`. Under `pragma foreign_keys = on` there are two TODOs. Replace each with one line that runs its pragma the same way, with `statement.execute(...)`:
   - `"pragma journal_mode = wal"`
   - `"pragma synchronous = full"`

   Run `../../mvnw test -Dtest=GroupSaleTest -Dsurefire.failIfNoSpecifiedTests=false`. `theDatabaseUsesAWriteAheadLog` passes now; `aSaleThatFailsHalfwaySavesNothing` still fails.

4. **Make a sale all-or-nothing.** Find `inTransaction`, near the bottom. Right now it just calls `work.run()`. Replace that line, following the four points in the TODO:
   - `connection.setAutoCommit(false)` first;
   - in a `try`: `work.run()`, then `connection.commit()`;
   - `catch (SQLException | RuntimeException e)`: `connection.rollback()`, then `throw e`, so the caller still hears that it failed;
   - `finally`: `connection.setAutoCommit(true)`.

   Run `GroupSaleTest` again: all 7 pass. Then run `PowerCutTest` the same way. It takes a few seconds, because it starts a second Java program.

5. **Run all the tests.** `../../mvnw test`. You should see 133 tests in all and `BUILD SUCCESS`.

6. **Sell a group from the terminal.** Follow "Run it and test it" below. Then open the window (`java -jar desktop/target/boxoffice-desktop.jar`) and, while it's open, look in the `starter` folder: `gate.db-wal` and `gate.db-shm` are next to `gate.db`. Close the window and look again: they're gone, folded into `gate.db`.

## Run it and test it

From the day's `starter` folder:

```
cd day-111-power-cut-proof/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=PowerCutTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=PowerCutTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **94 tests** in `core` (including GroupSale 8, PowerCut 1, TicketStore 9), **9** in `cli` and **30** in `desktop`, **133** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

**Sell a group.** `../../mvnw package`, then (on Windows, write `cli\target\boxoffice.jar`):

```
$ java -jar cli/target/boxoffice.jar sell REG "Kamau family" --quantity 4
Sold 4 tickets to Kamau family for KES 6,000: REG-0001 to REG-0004

$ java -jar cli/target/boxoffice.jar sell VIP "Big group" --quantity 60
Rejected: Only 50 VIP left

$ java -jar cli/target/boxoffice.jar report
Kilele Nights · Sat 14 Nov, 18:00
Type        Paid  Guests   Left        Takings
Regular        4       0    496      KES 6,000
VIP            0       0     50          KES 0
Total          4       0    546      KES 6,000
```

`-q 4` is the short way to write `--quantity 4`. Without it, `sell` sells one ticket, as before. A group that doesn't fit is refused whole: the gate never sells 50 of the 60 and leaves the rest of the group outside.

The first time you open an old `gate.db` today, Flyway adds V4 to it. `gate.db-wal` and `gate.db-shm` are in `.gitignore`, like `gate.db`.

## When you're stuck

- **`expected: "wal" but was: "delete"`**: the journal-mode pragma isn't there, or it's in a comment. `delete` is SQLite's old default mode.
- **`aSaleThatFailsHalfwaySavesNothing` says `Expecting actual: ["REG-0001", "REG-0002", "REG-0003"] to contain exactly (and in same order): ["REG-0001"]`**: the inserts are still auto-committed. Do step 4.
- **`PowerCutTest` says `expected: 4 but was: 2`**: the same thing: 2 of the 4 tickets were saved before the program was killed. Do step 4.
- **`aSaleThatFailsHalfwaySavesNothing` says `Expecting code to raise a throwable.`**: after `rollback()`, `throw e` again. If you swallow the error, the gate thinks the sale worked.
- **`aSingleSaleAfterAGroupSaleIsStillSaved` fails**: you forgot the `finally { connection.setAutoCommit(true); }`. Without it, the connection stays in "wait for commit" mode after a group sale, so the next single sale is never committed, and it's gone when the app closes.
- **`The gate program ended without printing MID_SALE`**: the second program stopped before it wrote the second ticket, so the test never got its moment to pull the plug. Today's code doesn't change `CrashingGate` or `Gate`: if you edited either by accident, compare it with the same file in `solution/`.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/TicketStore.java`, read `open` and `inTransaction` once, close it, then write your own from memory.

## Checklist

- [ ] Can say in one sentence what a transaction is
- [ ] Can say why half a sale is worse than no sale
- [ ] Turned on WAL and `synchronous = full`
- [ ] Wrote `inTransaction` with commit, rollback and `finally`
- [ ] Can explain the difference between a crash and a power cut
- [ ] Can explain how `PowerCutTest` kills a real program in the middle of a sale
- [ ] All 133 tests pass
- [ ] Sold a group of 4 from the terminal
- [ ] Committed your work to Git (not `gate.db`, `gate.db-wal` or `gate.db-shm`)

## Next

Every sale is now safe on the disk. Tomorrow the buyer gets something to hold: a receipt from the till printer, and a PDF of the receipt or the night's report that the organiser can email or print anywhere.
