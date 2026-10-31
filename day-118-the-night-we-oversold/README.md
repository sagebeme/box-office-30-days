# Day 118: The night we oversold

> 20:00, twenty tills, one VIP place left. Two of them sell it.

Watch the video: *(not recorded yet)*

## The brief

So far one person sells at a time. The window does its saving on one worker thread (Day 109), so sales happen one after another. But `Gate` itself doesn't know that. Anyone can call it from many threads at once, and as the gate grows (more tills on one laptop, a scanner on its own thread, the LAN feed from Day 117 answering in the background), someone will.

Picture Saturday at 20:00, the busiest minute of Kilele Nights. Twenty tills sell at once. One VIP place is left. Till 3 asks the gate "any VIP left?" and hears "yes, 1". Before till 3 saves, till 7 asks the same question, and also hears "yes, 1". Both sell. Both buyers get the code `VIP-0050`. That's how a gate **oversells**.

Today you make `Gate` and `TicketStore` safe when many threads use them at once:

- a **lock**, so "any left?", the numbering and the save happen as one step;
- **`synchronized`**, so two threads never share the one database connection in the middle of a transaction;
- a **`LongAdder`**, so the count of people let in never loses anyone.

The tests are already written. They start twenty threads on the same signal and make the race happen on every run, not just on a bad night.

```java
// 20 tills, 5 tries each, 100 tries for 50 VIP places:
// exactly 50 Sold, exactly 50 SoldOut, and 50 VIP tickets in gate.db.
```

## What you'll learn

- What a **race condition** is, and why "check, then act" is the classic one
- How a `ReentrantLock` makes several steps happen as one, and why `unlock()` goes in `finally`
- Why one database connection shared by threads can commit someone else's half-finished sale
- What `synchronized` does, and why it must be on **both** methods that share the connection
- Why `count++` loses counts, and how `LongAdder` fixes it
- Why check-in (Day 110) was already safe: one SQL statement checks and changes together
- How a test makes a race happen every time, with a `CountDownLatch` and a hook that waits at the worst moment

## New words

- **Thread**: one line of work inside a program. A program can run many at once, like many tills in one shop.
- **Race condition**: a bug where the answer depends on which thread gets there first. It works most of the time, and fails on the busiest night.
- **Check-then-act**: asking a question ("any left?"), then acting on the answer ("sell one"). Another thread can change the answer in between.
- **Lock**: something only one thread can hold at a time. The others wait until it's given back.
- **`ReentrantLock`**: Java's lock class. "Reentrant" means a thread that already holds it can take it again without blocking itself.
- **Critical section**: the code between taking a lock and giving it back. Only one thread is in it at a time.
- **`synchronized`**: a Java word you put on a method. Only one thread at a time can be inside any `synchronized` method of the same object.
- **Atomic**: done as one step that can't be split. You met the word on Day 111 for transactions; here it's for a single number.
- **`LongAdder`**: a counter many threads can add to at once without losing any, and fast while they do it.
- **`CountDownLatch`**: a gate for threads. They all wait at `await()` until someone calls `countDown()` enough times, then they all go together.
- **Hook**: a small piece of code a class runs at a chosen moment, so a test can step in. Here it's a pause between "any left?" and the save.
- **Flaky test**: a test that passes sometimes and fails sometimes with no change to the code (Day 113).
- **Deadlock** (here, a lock never given back): a thread waits for a lock forever, because whoever took it never gave it back.

## What's already written

From Days 101–117: the gate app, with sync, gate ids and the LAN feed, working, with tests. New today, already done:

- `RushTest.java` (in `core`): 5 tests. Twenty tills on twenty threads sell, sell groups, scan and count, all at once. Read it all: it's today's brief, written as code.
- `SharedConnectionTest.java`: 2 tests. Two threads, one connection, and two latches that make the timing the same on every run.
- `Gate`: a `saleLock` field (a `ReentrantLock`), a `turnstile` field, and a hook, `betweenCheckAndSave`, that runs after "any left?" and before the save. `checkIn` now calls `turnstile.admit()` when someone is let in, and `admittedHere()` says how many.
- `TicketStore`: `saveEvent`, `setGateId` and `update` are already `synchronized`. The hook `afterEachTicket` from Day 111 is used again.
- `Turnstile.java`: a counter of people let in through this laptop. It counts with a plain `int` for now.

New, for you to finish: the lock in two places in `Gate.java`, the word `synchronized` in two places in `TicketStore.java`, and `Turnstile.java`. None of them throws `Not written yet`: the code runs, it just isn't safe with many threads.

## What you'll use

**Check-then-act.** This is today's whole bug, in four lines of `Gate.issue`:

```java
if (remaining(typeCode) == 0) {             // 1. check: any left?
    return new SaleOutcome.SoldOut(typeCode);
}
Ticket ticket = new Ticket(nextCode(typeCode), ...);   // 2. number it: count + 1
store.recordSale(event.id(), ticket);       // 3. act: save it
```

With one thread, nothing happens between 1 and 3. With twenty, anything can. Two tills both pass step 1 with "1 left", both count 49 tickets in step 2 and both make `VIP-0050`.

**What saved us: the primary key.** On Day 104 the `tickets` table got `primary key (event_id, code)`: no two rows may have the same code. So when the second `VIP-0050` is saved, SQLite refuses it, and the till gets a `StoreException`. That's a crash for the person in the queue, but not an oversell. **Without that key, the gate would have saved both, and sold 51 of 50 VIP places.** A database rule is a good last line of defence. It's not a plan: the lock is the plan.

**A lock around the three steps.** Only one thread can hold `saleLock`. The others wait at `lock()` until it's given back:

```java
saleLock.lock();
try {
    // check, number, save
    return ...;
} finally {
    saleLock.unlock();
}
```

`finally` runs whether the code returns, returns early (`SoldOut`), or throws (the disk is full). If `unlock()` were just before the last `return`, the first `SoldOut` would keep the lock forever, and every other till would wait forever.

**Why the lock must be in both places.** `sell` goes through `issue`, and a family goes through `sellGroup`. Both count and both save. If only one of them takes the lock, a family and a single buyer can still race.

**One connection, many threads.** `TicketStore` opens **one** JDBC connection and everything uses it. A transaction (Day 111) belongs to the *connection*, not to the thread. So while thread 1 is in the middle of a family's sale, with auto-commit off, thread 2's insert lands in the same transaction. Then:

- if thread 1's sale fails and rolls back, thread 2's check-in is rolled back too: lost, with nobody told;
- if thread 2's code turns auto-commit back on, half of thread 1's family sale is committed. That's the half sale Day 111 was meant to stop.

`synchronized` fixes it. Only one thread at a time can be inside **any** `synchronized` method of the same `TicketStore`. So it must be on every method that uses the connection while another might be mid-transaction: `inTransaction` and `markCheckedIn` today (`saveEvent`, `setGateId` and `update` already have it). A `synchronized` method only keeps out other `synchronized` methods: one without the word walks straight in.

**Why check-in was already safe from double entry.** On Day 110 you made check-in one SQL statement:

```sql
update tickets set checked_in_at = ?
 where event_id = ? and code = ? and checked_in_at is null
```

The check ("not in yet?") and the change ("now it is") happen together inside the database, so two scanners can't both win. That's the same idea as the lock, done by SQLite. `twoScannersReadingOneTicketLetOnePersonIn` passes even before you start. Today's `synchronized` on `markCheckedIn` is for a different reason: so a check-in isn't swallowed by someone else's transaction.

**Why `count++` loses people.** `inside++` looks like one step, but it's three: read the number, add one, write it back. Two threads both read 41, both write 42, and one person is gone from the count. A `LongAdder` does the adding as one step that can't be split:

```java
private final LongAdder inside = new LongAdder();
inside.increment();    // add one, safely
inside.sum();          // read the total
```

(An `AtomicLong` works too. `LongAdder` stays fast when many threads add at the same moment, which is exactly a rush.)

**How the tests make a race happen every time.** A race that happens one time in a thousand makes a useless test: it passes on your laptop and fails on CI next month. So the tests set the timing up on purpose:

- `rush` in `RushTest` gives each job to a thread, and each thread waits at `go.await()`. Only when every job is waiting does `go.countDown()` let them all go at once.
- `gate.betweenCheckAndSave(...)` makes every sale sleep for 2 milliseconds between "any left?" and the save. That's an old, slow laptop, and it's exactly when another till sneaks in. Without the lock, the race happens on every run; with it, the pause is harmless, because nobody else can get in.
- `SharedConnectionTest` uses two latches. The group sale writes its first ticket, then calls `halfway.countDown()` and waits. The other thread writes, then calls `otherThreadDone.countDown()`. Only then does the group sale fail. Same order, every run.

The hooks are package-private (no `public`), so only tests in `com.boxoffice.core` can use them, and they do nothing unless a test sets them.

## Steps

1. **Open the starter and run the tests.** `cd day-118-the-night-we-oversold/starter`, then `../../mvnw test`. You should see `Tests run: 173, Failures: 3, Errors: 3` in `core` and `BUILD FAILURE`. `cli` and `desktop` are `SKIPPED`. The six:
   - `RushTest.twentyTillsNeverSellMoreThanFiftyVip`, `everyTicketSoldInARushHasItsOwnCode` and `groupSalesInARushStillAddUp`: a `StoreException` such as `Couldn't record ticket VIP-0001`, caused by `database in auto-commit mode` or `cannot commit - no transaction is active`. Threads are tripping over each other's transactions;
   - `RushTest.theTurnstileNeverLosesCount`: `expected: 1000000L but was: 951333L` (your number will be different, and different every run);
   - `SharedConnectionTest.anotherThreadsSaleNeverCommitsHalfAGroupSale`: `Expecting a throwable with cause being an instance of: java.lang.IllegalStateException but was an instance of: com.boxoffice.core.StoreException`;
   - `SharedConnectionTest.aCheckInIsNotLostWhenSomeoneElsesSaleRollsBack`: `Expecting Optional to contain: 2026-11-14T18:00:00Z but was empty.`

   `RushTest` takes a few seconds. There's no `Not written yet` today: the code runs, it just isn't safe.

2. **Read the tests and the hooks.** Open `starter/core/src/test/java/com/boxoffice/core/RushTest.java`. Find `rush` and the `CountDownLatch` called `go`, then the hook in `openTheGate`. Open `SharedConnectionTest.java` and follow the two latches, `halfway` and `otherThreadDone`. Then open `Gate.java`: find where `betweenCheckAndSave.run()` is called, and read `checkIn` (it's already safe; the comment says why). Nothing to write.

3. **Lock the sale.** In `Gate.java` there are two TODOs, each above a bare `{ ... }` block:
   - in `sellGroup`: replace the `{` with `saleLock.lock();` and `try {`, and after the block's closing `}` add `finally { saleLock.unlock(); }`. In short: `lock(); try { check, number, save } finally { unlock(); }`;
   - in `issue`: the same.

   Keep everything that was in the block inside the `try`: the check, `betweenCheckAndSave.run()`, the numbering and the save. Run `../../mvnw test -Dtest=RushTest -Dsurefire.failIfNoSpecifiedTests=false`. Four of the five pass now; `theTurnstileNeverLosesCount` still fails. `SharedConnectionTest` still fails too.

4. **One thread at a time on the connection.** In `TicketStore.java`, there are two TODOs:
   - `void inTransaction(SqlWork work)` becomes `synchronized void inTransaction(SqlWork work)`;
   - `public boolean markCheckedIn(...)` becomes `public synchronized boolean markCheckedIn(...)`.

   Run `../../mvnw test -Dtest=SharedConnectionTest -Dsurefire.failIfNoSpecifiedTests=false`: both pass.

5. **Count without losing anyone.** Open `Turnstile.java` and follow its three TODOs:
   - the field becomes `private final LongAdder inside = new LongAdder();` (the import is already there);
   - `admit()` calls `inside.increment()`;
   - `count()` returns `inside.sum()`.

   Run `RushTest` again: all 5 pass.

6. **Run all the tests.** `../../mvnw test`. You should see 237 tests in all and `BUILD SUCCESS`. Run it two or three more times: a race test that passes once might be lucky; one that passes every time, with the hooks forcing the race, isn't.

7. **Watch the oversell the lock stops.** In `Gate.java`, for a moment, put `//` in front of the two `saleLock.lock();` lines and the two `saleLock.unlock();` lines (leave `try` and `finally` as they are). Run `../../mvnw test -Dtest=RushTest -Dsurefire.failIfNoSpecifiedTests=false`. Three tests fail, and each error ends with `[SQLITE_CONSTRAINT_PRIMARYKEY] A PRIMARY KEY constraint failed`: two tills made the same code, and only the primary key stopped the second one. Take the `//` out again and run the tests once more before you commit.

8. **Stretch (not required): a race of your own.** On our laptop, twenty threads starting on one latch collided even with the pause set to `Thread.sleep(0)`. The 2 ms pause is there so the race also happens on a fast laptop and on CI, every run. Try it: change the `2` to `0` in your copy, take the lock out as in step 7, and run `RushTest` five times. Then write one more test in `RushTest` of your own, for example "two tills selling the last REG place to a family of 2 and a single buyer". Put everything back when you're done; your test isn't part of the course's tests, so keep it in your own Git.

## Run it and test it

From the day's `starter` folder:

```
cd day-118-the-night-we-oversold/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=RushTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=RushTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **173 tests** in `core` (including Rush 5, SharedConnection 2, CheckIn 7, GroupSale 8), **23** in `cli` and **41** in `desktop`, **237** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

**The app** works exactly as yesterday: `../../mvnw package`, then `java -jar cli/target/boxoffice.jar report`, or `java -jar desktop/target/boxoffice-desktop.jar` (on Windows, `cli\target\boxoffice.jar` and `desktop\target\boxoffice-desktop.jar`). Today's change is inside: it shows when many threads sell at once, which is what the tests do.

**One lock, one program.** `saleLock` belongs to one `Gate` object in one running program. It does nothing for the CLI and the window selling from the same `gate.db` at the same moment, and nothing for two laptops. Between programs, the primary key is still the last line of defence. Between laptops, the sync server will decide (Day 120).

## When you're stuck

- **`RushTest` fails with `Couldn't record ticket VIP-0002` caused by `[SQLITE_CONSTRAINT_PRIMARYKEY] A PRIMARY KEY constraint failed (UNIQUE constraint failed: tickets.event_id, tickets.code)`**: you did step 4 but not step 3. Two tills made the same code, and the primary key refused the second one. Without that key, this would be an oversell. Do step 3.
- **Only `groupSalesInARushStillAddUp` fails, with `Couldn't record the sale to Family` and the same `PRIMARY KEY` message**: the lock is in `issue` but not in `sellGroup`. Both need it.
- **`RushTest` fails with `database in auto-commit mode`** (or `cannot commit - no transaction is active`): two threads used the connection's transaction at once. If you've done step 3 and still see it in `SharedConnectionTest`, do step 4.
- **`aCheckInIsNotLostWhenSomeoneElsesSaleRollsBack` says `Expecting Optional to contain: 2026-11-14T18:00:00Z but was empty.`** but `anotherThreadsSaleNeverCommitsHalfAGroupSale` passes: `synchronized` is on `inTransaction` but not on `markCheckedIn`. The check-in landed in the group sale's transaction and was rolled back with it.
- **Both `SharedConnectionTest` tests fail even though `markCheckedIn` is `synchronized`**: `inTransaction` needs it too. `synchronized` only keeps out other `synchronized` methods on the same object.
- **`theTurnstileNeverLosesCount` says `expected: 1000000L but was: 724708L`** (or any number below a million): `Turnstile` still uses `int` and `++`. Do step 5. Making the field `volatile` isn't enough: `volatile` makes every thread see the latest value, but `++` is still three steps. One run of ours with `volatile int` even passed by luck. Use `LongAdder`.
- **The tests stop at `Running com.boxoffice.core.RushTest` and never finish**: a lock was taken and never given back. Usually `unlock()` is at the end of the method instead of in `finally`, so the first `SoldOut` (an early `return`) keeps the lock, and every other thread waits forever. Press Ctrl+C and put `unlock()` in `finally`.
- **The same `PRIMARY KEY` errors, even though both methods call `lock()`**: check you're using the `saleLock` field at the top of `Gate`, not a `ReentrantLock saleLock = new ReentrantLock();` written inside the method. A lock made inside the method is a new lock on every call, so every till gets its own and it stops nobody.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/Gate.java` (or `TicketStore.java`, `Turnstile.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say what a race condition is, with the "1 VIP left" story
- [ ] Can say why `unlock()` goes in `finally`
- [ ] Locked both `issue` and `sellGroup`
- [ ] Can say why a shared connection needs `synchronized`, and why on both methods
- [ ] Can say why `count++` loses counts and what `LongAdder` does
- [ ] Can say why check-in was already safe from double entry
- [ ] Can explain how the latch and the hook make the race happen every run
- [ ] All 237 tests pass, more than once
- [ ] Committed your work to Git (not `gate.db`)

## Next

One laptop is now safe with many threads. Tomorrow the gates get a real server to sync to: a Spring Boot app on your own laptop, where each gate laptop logs in with its own token, and nobody else can send it sales.
