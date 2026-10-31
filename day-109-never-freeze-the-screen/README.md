# Day 109: Never freeze the screen

> At 23:00 the old gate laptop's disk is tired. Each sale takes two seconds to save, and for those two seconds the window is dead.

Watch the video: *(not recorded yet)*

## The brief

The gate laptop is borrowed, old, and running from a power bank since the last power cut. Saving a sale to `gate.db` can take a couple of seconds. Right now the window **waits** for the disk: it doesn't redraw, doesn't take key presses, can't even be moved. The person at the gate thinks it has crashed, presses Enter again, and maybe sells the same ticket twice.

Today you move the slow part **off the screen's thread**. The window stays alive while a sale saves: it says "Saving…", greys out the Sell button so nobody sells twice, and shows the new row when the disk is done.

You start by feeling the problem for yourself. The app has a practice switch, `--slow-disk`, that makes every sale take 2 seconds to save.

```
Click Sell (before)     Click Sell (after)
  window frozen 2 s       "Saving…", Sell greyed out, window still moves
  then the new row        2 s later: the new row, "Sold VIP-0001 to Wanjiku..."
```

## What you'll learn

- What the **JavaFX application thread** is, and the two rules that come with it
- Why waiting for the disk on that thread freezes the whole window
- How a **`Task`** does slow work on another thread and hands the result back
- What an **`Executor`** is, and why the gate uses exactly one worker thread
- How a `busy` property stops a second sale while the first is saving
- How tests run JavaFX with no screen at all, using **Monocle**

## New words

- **Thread**: one line of work inside a program. A program can have several running at once.
- **JavaFX application thread** (the FX thread): the one thread that draws the window and runs every click and key press.
- **Freeze**: the window stops answering because its thread is busy doing something else.
- **Background thread** (worker): a thread that does slow work so the FX thread doesn't have to.
- **`Task`**: a JavaFX class for one piece of background work. Its `call()` runs on the worker; its "succeeded" and "failed" handlers run back on the FX thread.
- **`Executor`**: something that runs tasks for you. `Executors.newSingleThreadExecutor` makes one with a single worker thread.
- **Daemon thread**: a thread that doesn't keep the program alive. When the window closes, it stops too.
- **Monocle**: a pretend screen for JavaFX, so tests can make windows with no monitor (on your laptop, or in CI).
- **Snapshot**: everything the screen needs, read in one go, so it can be carried from one thread to another.

## What's already written

From Days 101–108: everything up to the keyboard-first sell form, working, with tests.

Changed today, already done:

- `TicketStore.simulateSlowDisk(duration)` makes every `recordSale` wait that long. One new test in `TicketStoreTest`.
- `BoxOfficeApp`: `--slow-disk` turns that on (2 seconds). It also makes the **worker**: a single-thread executor whose thread is called `gate-worker` and is a daemon. It passes the worker to the model.
- `DashboardModel`: a new `busy` property, a `Snapshot` record (takings, types, sales), a `SaleResult` record (outcome + snapshot), and `snapshot()` and `show(snapshot)`. `sell` now takes an `afterwards` callback and returns nothing. It works, but it does everything on the FX thread. You fix that.
- `DashboardController`: the Sell button is disabled while the form isn't ready **or** the model is busy.
- `desktop/pom.xml`: Monocle as a test library, and the surefire settings that point JavaFX at it.
- `FxThread.java` (in the tests): runs a bit of a test on the FX thread and waits for it.
- `DashboardModelTest.java`: 12 tests, 3 of them new for today.

## What you'll use

**The two rules of the FX thread.**
1. Never make it wait: no slow disk, no network, no `Thread.sleep`.
2. Only it may change what's on screen: properties a label is bound to, lists a table shows.

**A `Task`.** `call()` runs on the worker. It may be slow; it must not touch the screen. It returns the result:

```java
Task<Integer> count = new Task<>() {
    @Override
    protected Integer call() {
        return store.countSold(eventId, "VIP");   // slow is fine here
    }
};
```

**Getting the result back.** `Task` runs these handlers on the FX thread, so changing the screen there is allowed:

```java
count.setOnSucceeded(done -> label.setText(count.getValue() + " VIP sold"));
count.setOnFailed(failed -> label.setText("Oops: " + count.getException().getMessage()));
```

**Starting it.** Hand it to the executor. This line returns at once; the work happens on the worker:

```java
worker.execute(count);
```

**Why one worker thread.** With one thread, sales are saved one at a time, in the order they were made. VIP-0001 is always saved before VIP-0002, and two sales can never fight over the same ticket number.

## Steps

1. **Open the starter and run the tests.** `cd day-109-never-freeze-the-screen/starter`, then `../../mvnw test`. `core` (74) and `cli` (8) pass. In `desktop` you should see `Tests run: 12, Failures: 3`. The three failures are today's tests: one says `Expecting value to be true but was false` (the model wasn't busy, because it had already finished), and one stops with `StoreException: Couldn't count VIP tickets`.

2. **Feel the freeze.** Build without the tests and run the app with the slow disk:

   ```
   ../../mvnw package -DskipTests
   java -jar desktop/target/boxoffice-desktop.jar --slow-disk
   ```

   Sell a ticket (Esc to get to the name box, F2, type a name, Enter). Straight away, try to drag the window by its title bar, and type in the name box. For 2 seconds nothing moves and nothing appears; then everything you typed turns up at once. You never see "Saving…", although the code sets it: the FX thread is stuck in the disk and can't redraw. Close the window.

3. **Read the new model code.** Open `starter/desktop/src/main/java/com/boxoffice/desktop/DashboardModel.java`. Find `busy`, the `Snapshot` and `SaleResult` records, `snapshot()` (reads the gate; safe on any thread) and `show(snapshot)` (changes the screen; FX thread only). Then find `sell(typeCode, buyer, phone, afterwards)`: it sets `busy` and `"Saving…"`, then does the rest right there. Also open `BoxOfficeApp.java` and find the `gate-worker` thread.

4. **Make the task.** In `sell(...)`, under the TODO (its part 1), make a `Task<SaleResult>` called `save`. In its `call()`, do `gate.sell(typeCode, buyer, phone)` and return `new SaleResult(outcome, snapshot())`. Nothing else: no `message.set`, no `show`. Those touch the screen.

5. **Handle success and failure.** (The TODO's parts 2 and 3.)
   - `save.setOnSucceeded(done -> { ... })`: get the result with `save.getValue()`, `show(result.snapshot())`, put `result.outcome().describe()` in `message`, set `busy` to `false`, then `afterwards.accept(result.outcome())`.
   - `save.setOnFailed(failed -> { ... })`: put `"Couldn't save the sale: " + save.getException().getMessage()` in `message`, and set `busy` to `false`. Without this, a failed save would leave the gate stuck on "Saving…" all night.

6. **Start it, and delete the old lines.** (Part 4.) Add `worker.execute(save);`. Then delete the old lines under the TODO, from `SaleOutcome outcome = gate.sell(...)` down to `afterwards.accept(outcome);`. Run `../../mvnw test`: all 21 desktop tests should pass.

7. **Run it again, slow.** `../../mvnw package`, then `java -jar desktop/target/boxoffice-desktop.jar --slow-disk`. Sell a ticket. This time you see `Saving…`, the Sell button greys out, and you can drag the window around while it saves. Two seconds later the row appears and the message says `Sold ...`. Press Enter again while it says "Saving…": nothing happens. That's `busy` doing its job.

## Run it and test it

From the day's `starter` folder:

```
cd day-109-never-freeze-the-screen/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=DashboardModelTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=DashboardModelTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **74 tests** in `core`, **8** in `cli` and **21** in `desktop` (DashboardModel 12, SaleForm 9), **103** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

**Run the window** (Windows: `desktop\target\boxoffice-desktop.jar`):

```
../../mvnw package
java -jar desktop/target/boxoffice-desktop.jar                # normal
java -jar desktop/target/boxoffice-desktop.jar --slow-disk    # every sale takes 2 seconds to save
```

**How the tests work with no screen.** `desktop/pom.xml` tells surefire to start JavaFX on Monocle (`glass.platform=Monocle`, `monocle.platform=Headless`, `prism.order=sw`), so no window ever appears and the tests also run in CI. `FxThread.run(...)` runs a bit of test code on the FX thread, the way a click would. Most tests use an executor that runs the task straight away (`Runnable::run`). Even then, `Task` hands its result to the FX thread a moment *later*, in a queue. That's why each test waits for `busy` to go `false` before it checks anything.

## When you're stuck

- **`sellingReturnsAtOnceAndSavesInTheBackground` says `Expecting value to be true but was false`**: the sale finished before `sell` returned, so it's still on the FX thread. Check you call `worker.execute(save)` and deleted the old lines.
- **`ignoresASecondSaleWhileTheFirstIsSaving` says `Expecting AtomicInteger(0) to have value: 1`**: the worker was never used. Same fix.
- **`aSaleThatCannotBeSavedSaysSoAndFreesTheGate` stops with `StoreException: Couldn't count VIP tickets`**: the test closed the database on purpose, so the save throws. Without a `Task`, that exception flies straight out of `sell` on the JavaFX thread and the test stops. Once the save runs in a `Task`, the exception goes to `setOnFailed`, which shows `Couldn't save the sale: ...` instead.
- **`expected: "Couldn't save the sale: ..."`** but it says `Saving…`: `setOnFailed` is missing, or doesn't set `message`.
- **The tests wait 5 seconds and fail with `Waited 5 seconds and it still wasn't true`**: `busy` never goes back to `false`. Set it in both handlers.
- **`IllegalStateException: Not on FX application thread`**, or the table sometimes not updating in the app: something in `call()` touched the screen. The tests may not catch this, because most of them run the task on the FX thread. Move `show(...)` and `message.set(...)` into `setOnSucceeded`.
- **The app freezes even after the fix**: you ran an old jar. Run `../../mvnw package` again, and check you passed `--slow-disk` to see the difference.
- **The window closes but the terminal doesn't come back**: a non-daemon thread is keeping Java alive. `BoxOfficeApp` makes the worker a daemon; check you didn't change it.
- **Still stuck?** Open `solution/desktop/src/main/java/com/boxoffice/desktop/DashboardModel.java`, read `sell` once, close it, then write your own from memory.

## Checklist

- [ ] Ran the starter with `--slow-disk` and felt the freeze
- [ ] Can state the two rules of the JavaFX thread
- [ ] Can say which parts of a `Task` run on the worker and which on the FX thread
- [ ] Can say why the gate uses one worker thread, not many
- [ ] The Sell button greys out while a sale saves
- [ ] All 103 tests pass
- [ ] Ran it again with `--slow-disk`: "Saving…" shows and the window still moves
- [ ] Committed your work to Git (not `gate.db`)

## Next

Tomorrow every ticket gets a QR code, and the gate gets a second job: checking people in. A USB scanner reads the code off a phone screen, and the gate makes sure each ticket gets in only once.
