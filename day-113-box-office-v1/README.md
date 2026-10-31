# Day 113: Box Office v1

> 22:40, the queue is long, and the new gate volunteer types the next name before the last sale has saved. It vanishes. She types it again. And again.

Watch the video: *(not recorded yet)*

## The brief

The desktop app is finished: it sells, checks in, survives power cuts and prints. Today it becomes **version 1**, and the window title says so: `Box Office v1 · Kilele Nights`.

Before you call anything "version 1", you want proof that the screen works the way gate staff use it: keyboard only, fast, with a queue. Clicking through it by hand after every change takes ages, and you will forget a step. So today you add **UI tests**: tests that open the real window and drive it with a robot that types, like a person (or a scanner) would. Day 110's `CheckInScreenTest` already does this for the Check-in tab. Today's `SellScreenTest` does it for the whole sell flow.

The new tests catch two things the app gets wrong:

- **F5 should re-read `gate.db`.** The CLI can sell or import guests into the same `gate.db` while the window is open. The window doesn't know, so the numbers on the screen go stale. F5 should bring them up to date.
- **A name typed while a sale is saving gets wiped.** On a slow laptop, a sale takes a moment to save (Day 109). If staff start typing the next person's name in that moment, the form clears itself when the first sale lands, and the new name is gone.

You fix both, and the tests go green.

## What you'll learn

- What a UI test is, and why you write only a few of them
- How **TestFX** opens the real window in a test, and how its **robot** types
- How `lookup("#id")` finds a control on the screen
- Why UI tests must wait, and how `WaitForAsyncUtils` does it
- How **Monocle** runs all this with no screen, so CI can run it too
- Why the order of `if` checks matters in a key handler
- How to clear a form only when it still holds the sale that just finished

## New words

- **UI test**: a test that uses the app through its screen, the way a person does, instead of calling its methods.
- **Unit test**: a small test of one class or method, with no screen. Most of this course's tests are unit tests.
- **Robot** (in TestFX): the object that types keys and clicks in a test, as if someone were at the keyboard.
- **Headless**: running with no screen at all. The window exists, but only in memory.
- **Monocle**: a pretend screen for JavaFX, so a window can open in a headless test.
- **Flaky test**: a test that sometimes passes and sometimes fails with no change to the code, usually because it didn't wait long enough.
- **fx:id**: the name a control has in the FXML file, like `buyerField`. In a test you find it with `#buyerField`.
- **Stale**: out of date. The screen shows old numbers after something else changed the file.

## What's already written

From Days 101–112: the whole app, working, with tests. New today, already done:

- `BoxOfficeApp`: the window title is now `Box Office v1 · Kilele Nights`.
- `SellScreenTest.java` (in `desktop`): 8 UI tests of the sell screen. Read it all: it's today's brief, written as code.
- `SaleFormTest`: 2 new tests for `clearIfStill`.
- `DashboardModel.refresh()`: reads the gate again and updates everything on screen. It's been there since Day 106; today a key calls it.
- `DashboardController.sell()`: now remembers `buyer` and `phone` before it starts the sale, so it can compare later.

New, for you to finish: a TODO in `DashboardController.onKey` (F5), `SaleForm.clearIfStill`, and a TODO in `DashboardController.sell()` that uses it.

## What you'll use

**Few, slow, valuable.** A unit test runs in a few milliseconds. A UI test opens a window, types, and waits: about a second or more each. `CheckInScreenTest` and `SellScreenTest` together take around 40 seconds. So you don't test every rule through the screen: `GateTest` and `SaleFormTest` do that, fast. UI tests check the few things only the screen can show: the cursor starts in the right box, Enter sells, F2 picks VIP, the form empties for the next person. If a change breaks the keyboard flow, a UI test fails before anyone at a gate finds out.

**Opening the window in a test.** `@ExtendWith(ApplicationExtension.class)` starts JavaFX. The method marked `@Start` gets a `Stage` and shows the real dashboard in it, with a temporary `gate.db`. Every test method gets an `FxRobot`.

**The robot.** It types into whatever has the focus, the way a keyboard does:

```java
robot.write("Otieno").type(KeyCode.ENTER);   // type a name, press Enter
robot.type(KeyCode.F2);                      // press F2
robot.eraseText(6);                          // press Backspace 6 times
```

**Finding a control.** `lookup("#message")` finds the control whose `fx:id` is `message`. `queryAs(Label.class)` hands it back as a `Label`, so you can read its text:

```java
robot.lookup("#message").queryAs(Label.class).getText()
```

**Waiting.** The robot's key presses go into the JavaFX event queue, and a sale saves on another thread. If the test checks too soon, it sees the old screen and fails, sometimes. That's a flaky test. Two ways to wait:

- `WaitForAsyncUtils.waitForFxEvents()`: wait until JavaFX has handled everything already in its queue.
- `WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> ...)`: wait until something is true, up to a time limit. `waitForTheSale` uses it to wait until the message isn't `Saving…` any more.

**Holding a sale while the robot types.** `SellScreenTest` gives the model its own worker that waits on a `CountDownLatch` before saving. In `theNextNameTypedWhileASaleSavesIsKept`, the latch starts at 1, so the sale waits. The robot deletes `Otieno` and types `Achieng`. Then `countDown()` lets the sale finish, and the test checks `Achieng` is still in the box. That's the 22:40 queue, in a test.

**No screen needed.** `starter/desktop/pom.xml` sets `glass.platform` to `Monocle` and `monocle.platform` to `Headless` for the tests. Nothing pops up on your screen, and the CI machine, which has no screen, runs them too.

**Order matters in `onKey`.** `onKey` checks `key.isFunctionKey()` and turns F1, F2... into a ticket type. F5 is a function key too. If the F5 check comes after that, F5 goes to `form.pick(5)`, which quietly does nothing, and the refresh never happens. The specific case goes first, the same rule as the `when` guard on Day 110.

**Clear only if nothing changed.** When a sale lands, compare what's in the form now with what was sold. The same: clear it. Different: someone is typing the next sale, so leave it alone. Compare strings with `equals`, never `==`.

## Steps

1. **Open the starter and run the tests.** `cd day-113-box-office-v1/starter`, then `../../mvnw test`. `core` (103) and `cli` (10) pass. In `desktop` you should see `Tests run: 41, Failures: 3` and `BUILD FAILURE`:
   - `SaleFormTest.clearsAfterTheSaleItWasFilledInFor`: `Expecting empty but was: "Otieno"`;
   - `SellScreenTest.fFiveShowsSalesMadeByAnotherProgram`: `expected: 1 but was: 0`;
   - `SellScreenTest.theNextNameTypedWhileASaleSavesIsKept`: `expected: "Achieng" but was: ""`.

   The desktop tests take about a minute, because the robot really types.

2. **Read the UI test.** Open `starter/desktop/src/test/java/com/boxoffice/desktop/SellScreenTest.java`. Find `@Start`, the helpers `waitForTheSale`, `text` and `rows`, then read the 8 tests. Each one is a few lines of "type this, check that". Nothing to write.

3. **F5 reads `gate.db` again.** Open `starter/desktop/src/main/java/com/boxoffice/desktop/DashboardController.java` and find `onKey`. Where the first TODO is, add a check for `KeyCode.F5` that calls `model.refresh()` and then `event.consume()`. It goes **before** the `isFunctionKey()` check, and the old `if` becomes `else if`.

   Run `../../mvnw test -Dtest=SellScreenTest -Dsurefire.failIfNoSpecifiedTests=false`. `fFiveShowsSalesMadeByAnotherProgram` passes now.

4. **Write `clearIfStill`.** Open `SaleForm.java` and find `clearIfStill(soldBuyer, soldPhone)`. Call `clear()` only when `buyer.get()` equals `soldBuyer` **and** `phone.get()` equals `soldPhone`. Otherwise do nothing.

   Run `SaleFormTest`: all 11 pass.

5. **Use it after a sale.** Back in `DashboardController.java`, find `sell()`. Inside the callback, replace `form.clear()` with `form.clearIfStill(buyer, phone)`. `buyer` and `phone` are what was in the form when the sale started. Leave the `form.clear()` for Escape in `onKey` as it is: Escape should always clear.

6. **Run all the tests.** `../../mvnw test`. You should see 154 tests in all and `BUILD SUCCESS`.

7. **Try both fixes for real.** `../../mvnw package`, then:
   - Run `java -jar desktop/target/boxoffice-desktop.jar`. The title bar says `Box Office v1 · Kilele Nights`. Leave it open.
   - In a second terminal, in the same `starter` folder, run `java -jar cli/target/boxoffice.jar sell REG "Kamau family" --quantity 4`. The window still shows the old numbers. Click on it and press **F5**: four more sold.
   - Close the window and start it again with `--slow-disk` at the end. Type `Otieno`, press Enter, and straight away type `Achieng` over it. When the first sale lands, `Achieng` stays in the box.

8. **Stretch (not required): write a UI test of your own.** In your own copy, add one test to `SellScreenTest`. Ideas: F2 then F1 sells a Regular ticket; Ctrl+2 then Ctrl+1 puts the cursor back in the name box; selling all 50 VIP tickets (with `gate.sellGroup` at the start of the test) shows `VIP is sold out` for the next one. Copy the shape of `fTwoSellsVip`. Your test isn't part of the course's tests, so keep it in your own Git, not in a pull request.

## Run it and test it

From the day's `starter` folder:

```
cd day-113-box-office-v1/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=SellScreenTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=SellScreenTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **103 tests** in `core`, **10** in `cli` and **41** in `desktop` (CheckInScreen 5, DashboardModel 17, SaleForm 11, SellScreen 8), **154** in all. `SellScreenTest` takes about 15 seconds and `CheckInScreenTest` about 25.

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

**Run the window:** `../../mvnw package`, then `java -jar desktop/target/boxoffice-desktop.jar` (Windows: `desktop\target\boxoffice-desktop.jar`). `--db=other.db` and `--slow-disk` work as before.

**The keys, all in one place:** F1 Regular, F2 VIP, Enter sells, Tab to the phone box, Escape clears, F5 reads `gate.db` again, Ctrl+1 Sell, Ctrl+2 Check-in, Ctrl+P print, Ctrl+S save PDF (Cmd instead of Ctrl on a Mac).

## When you're stuck

- **`fFiveShowsSalesMadeByAnotherProgram` says `expected: 1 but was: 0`**: F5 isn't handled, or its check is below `isFunctionKey()`, so F5 picks a fifth ticket type that doesn't exist. Move it to the top.
- **`clearsAfterTheSaleItWasFilledInFor` says `Expecting empty but was: "Otieno"`**: `clearIfStill` is still empty. Do step 4.
- **`theNextNameTypedWhileASaleSavesIsKept` says `expected: "Achieng" but was: ""`**: `sell()` still calls `form.clear()`. Do step 5.
- **`theFormEmptiesForTheNextPerson` says `Expecting empty but was: "Otieno"`**: you did step 5 but not step 4, so the form never clears at all.
- **`cannot find symbol` / `symbol: variable buyer`** in `DashboardController.java`, at the Escape line: you changed the `form.clear()` under `KeyCode.ESCAPE` too. There's no `buyer` there. Put it back: Escape always clears.
- **`The sale didn't finish in 5 seconds`**: the sale never got past `Saving…`. Check that `sell()` still calls `model.sell(...)`, and that you didn't block the JavaFX thread.
- **A window flashes up when you run the tests from your editor**: the editor may not use the Monocle settings from the pom. That's fine; `../../mvnw test` runs them headless.
- **A test passes on its own but fails in the full run, now and then**: that's a flaky test. Add `WaitForAsyncUtils.waitForFxEvents()` before you check the screen (this goes for your stretch test too).
- **F5 does nothing in the real window**: click on the window first, so it has the keyboard. On some laptops, F5 needs the Fn key too.
- **Still stuck?** Open `solution/desktop/src/main/java/com/boxoffice/desktop/DashboardController.java` (or `SaleForm.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say what a UI test is, and why there are only a few
- [ ] Can say what `write`, `type` and `lookup("#id")` do
- [ ] Can say why a UI test must wait, and what a flaky test is
- [ ] F5 shows sales made by the CLI while the window is open
- [ ] A name typed while a sale saves is kept
- [ ] All 154 tests pass, with no screen popping up
- [ ] The title bar says `Box Office v1 · Kilele Nights`
- [ ] (Stretch) Wrote one UI test of your own
- [ ] Committed your work to Git (not `gate.db`)

## Next

Version 1 runs one gate, on its own, with no internet. Tomorrow the gate reaches out for the first time: an SMS reminder to every buyer who left a phone number, hundreds of them, sent with virtual threads through the Africa's Talking sandbox.
