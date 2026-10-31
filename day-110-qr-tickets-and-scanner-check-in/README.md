# Day 110: QR tickets and scanner check-in

> Beep. Green. Next. Beep. Amber: "Already in at 21:04". Someone sent their ticket to a friend.

Watch the video: *(not recorded yet)*

## The brief

Selling is half of the gate's job. The other half is **letting people in**: checking that each ticket is real, and that it's used only once. At Kilele Nights, people show their ticket on a phone. A screenshot is easy to send to a friend, so the gate must catch the second person with the same ticket.

Today every sale gets a **QR code**, shown on the Sell tab so it can be sent to the buyer. And the gate gets a **Check-in** tab. The person on the door points a USB scanner at the phone; the scanner reads the QR and the screen turns:

- **green**: `Admitted: VIP-0001 · Wanjiku (VIP)`;
- **amber**: `Already in: VIP-0001 came in at 21:04`;
- **red**: `No such ticket: VIP-9999`.

**Ctrl+1** goes to the Sell tab and **Ctrl+2** to Check-in (on a Mac, **Cmd+1** and **Cmd+2**).

```java
gate.checkIn("VIP-0001")      // Admitted[ticket=Ticket[code=VIP-0001, ...]]
gate.checkIn(" vip-0001 \n")  // AlreadyIn[ticket=..., at=2026-11-14T18:04:00Z]
gate.checkIn("VIP-9999")      // NotFound[code=VIP-9999]
```

## What you'll learn

- How a QR code is made with **ZXing**, and why `core` hands back plain `boolean`s instead of an image
- Why a USB scanner needs no special code: to the computer, it's a keyboard
- How one SQL `UPDATE ... WHERE checked_in_at IS NULL` makes "only once" safe
- A third migration, with a column that's allowed to be empty
- A `switch` over a sealed interface with a `when` guard
- How **TestFX** tests type into a real window, the way a scanner does
- Why the scan box must `consume()` its Enter

## New words

- **QR code**: a square of black and white blocks that a camera or scanner reads back as text.
- **ZXing** ("zebra crossing"): a Java library that makes and reads QR codes and barcodes.
- **USB barcode scanner**: a handheld reader you plug in. Most work in **keyboard mode** (HID): they "type" what they read, then press Enter.
- **HID** (human interface device): the kind of USB device keyboards and mice are. Your computer needs no driver for one.
- **Suffix**: the key a scanner presses after the code. For us it must be Enter.
- **Check-in**: marking a ticket as "came through the gate".
- **`null` (in SQL)**: "no value". Here a ticket with `checked_in_at` null hasn't come in yet.
- **Guard** (`when`): an extra condition on a `case` in a switch.
- **TestFX**: a library whose robot clicks and types into a JavaFX window from a test.
- **`WritableImage`**: a JavaFX image you paint pixel by pixel.

## What's already written

From Days 101–109: everything up to the background saving, working, with tests.

New today, already done:

- The poms add **ZXing** (`com.google.zxing:core`, in `core`) and **TestFX** (`testfx-junit5`, tests in `desktop`).
- `QrCodes.encode(text)` (in `core`) gives a `boolean[][]` grid: `true` is a dark square. `QrCodesTest` (4 tests) reads it back with ZXing and checks it says the same thing.
- `QrImages.of(text, scale)` (in `desktop`) paints that grid into a `WritableImage`.
- `TicketStore`: `findTicket`, `markCheckedIn` and `checkedInAt`. Complete. Read `markCheckedIn`.
- `CheckInOutcome.java`: a sealed interface with three records, `Admitted`, `AlreadyIn` and `NotFound`. You write `describe()`.
- `CheckInState.java`: `WAITING`, `ADMITTED`, `ALREADY_IN`, `NOT_FOUND`, one CSS colour each.
- `DashboardModel`: `checkIn(scanned)` runs on the worker like a sale, and `lastSoldCode` for the QR. Complete.
- `dashboard.fxml`: two tabs. Sell has the old form plus the last ticket's QR. Check in has `scanField` and `checkInResult`.
- `DashboardController`: Ctrl+1 / Ctrl+2, the QR image, the colours. You write `onScan`.

New, for you to finish: `V3__add_checked_in_at.sql`, `CheckInOutcome.describe()`, `Gate.checkIn()`, and `DashboardController.onScan()`. New tests: `CheckInTest` (7), `QrCodesTest` (4), `CheckInScreenTest` (5).

## What you'll use

**Why `core` returns booleans.** `core` is the gate's rules. The CLI uses it, and a web page might one day. If `QrCodes` returned a JavaFX image, every one of them would need JavaFX. A `boolean[][]` needs nothing, and `desktop` turns it into pixels.

**"Only once" in one statement.** `markCheckedIn` runs this:

```sql
update tickets set checked_in_at = ?
 where event_id = ? and code = ? and checked_in_at is null
```

The first scan finds `checked_in_at` null, sets it, and changes 1 row: `true`. Every later scan finds it already set and changes 0 rows: `false`. The check and the change happen together, so two scans at the same moment can't both win. (Reading first, then writing in a second step, could let both in.)

**A switch with a guard.** The more specific case must come first, or Java refuses to compile:

```java
return switch (this) {
    case NotFound(String code) when code.isEmpty() -> "Nothing was scanned";
    case NotFound(String code) -> "No such ticket: " + code;
    // ... the other records
};
```

**A scanner is a keyboard.** It types `VIP-0001` very fast, then Enter. So check-in is a text box with an `onAction` handler, the same as Enter in the name box on Day 108.

**Stopping the Enter.** The Sell button is the window's default button: Enter anywhere presses it. `event.consume()` in the scan box's handler says "this Enter is used up".

## Steps

1. **Open the starter and run the tests.** `cd day-110-qr-tickets-and-scanner-check-in/starter`, then `../../mvnw test`. You should see `Tests run: 85, Failures: 0, Errors: 7` in `core`, all in `CheckInTest`, saying `Not written yet: Day 110, step 5` (and one `step 4`). `cli` and `desktop` are `SKIPPED`.

   As on Day 108, **don't open the app until step 3 is done**, or Flyway records an empty V3.

2. **Look at the QR code.** Open `starter/core/src/main/java/com/boxoffice/core/QrCodes.java` and see that nothing in it mentions JavaFX. Then open `starter/desktop/src/main/java/com/boxoffice/desktop/QrImages.java`: each `true` becomes a black square of `scale × scale` pixels. Nothing to write.

3. **Write the migration.** Open `starter/core/src/main/resources/db/migration/V3__add_checked_in_at.sql`. Add a column called `checked_in_at` to `tickets`, of type `text`. This time **no** `not null` and **no** `default`: null means "not in yet", and every old ticket starts that way.

4. **Describe each outcome.** Open `CheckInOutcome.java` and replace the `throw` with a `switch (this)`, like `SaleOutcome.describe` on Day 102:
   - `Admitted(Ticket ticket)`: `"Admitted: " + code + " · " + buyer + " (" + typeCode + ")"`, all from `ticket`;
   - `AlreadyIn(Ticket ticket, Instant at)`: `"Already in: " + code + " came in at " + NairobiTime.timeOfDay(at)`;
   - `NotFound` with an empty code (the guard): `"Nothing was scanned"`;
   - any other `NotFound`: `"No such ticket: " + code`.

5. **Check a ticket in.** Open `Gate.java` and find `checkIn`. Replace the `throw`:
   - tidy the code: `null` becomes `""`, otherwise `strip()` and `toUpperCase(Locale.ROOT)`. Scanners can add a space or a new line, and some send lower case;
   - `store.findTicket(event.id(), code)`. Empty: return `new CheckInOutcome.NotFound(code)`;
   - `store.markCheckedIn(event.id(), code, clock.instant())`. `true`: return `Admitted` with the ticket;
   - `false` means it was already in. Get the time with `store.checkedInAt(event.id(), code).orElseThrow()` and return `AlreadyIn`.

   Run `../../mvnw test`. `core` passes (85) and `cli` passes (8). In `desktop`, `CheckInScreenTest` now runs and fails, saying `but was: "Scan a ticket"`.

6. **Handle the scan.** Open `DashboardController.java` and find `onScan`. Write three lines:
   - `model.checkIn(scanField.getText());`
   - `scanField.clear();` so the box is empty for the next ticket;
   - `event.consume();` so this Enter doesn't also press Sell on the other tab.

   Run `../../mvnw test`. All 30 desktop tests pass. The 5 in `CheckInScreenTest` open the real window on Monocle's pretend screen and the robot types the code and presses Enter, the way a scanner does. Nothing pops up on your screen.

7. **Try it for real.** `../../mvnw package`, then `java -jar desktop/target/boxoffice-desktop.jar`.
   - Press F2 (the cursor starts in the name box), type `Wanjiku`, Enter. Under the form, "Last ticket" shows a QR code and `VIP-0001`.
   - Open your phone's camera and point it at the QR on your screen. It should read `VIP-0001`.
   - Press **Ctrl+2**. The cursor is in the scan box. Type `VIP-0001` and press Enter: green. Again: amber, `Already in`. Type `VIP-9999`: red.
   - **If you have a USB scanner**: VIP-0001 is already in, so press Ctrl+1 and sell a second ticket, VIP-0002. Take a photo of its QR with your phone (the QR is on the Sell tab, but the scanner types into the Check-in tab). Plug the scanner in, press Ctrl+2, and scan the photo on your phone, just like a buyer at the gate. It should beep and go green.

## Run it and test it

From the day's `starter` folder:

```
cd day-110-qr-tickets-and-scanner-check-in/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=CheckInTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=CheckInTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **85 tests** in `core` (including CheckIn 7, QrCodes 4), **8** in `cli` and **30** in `desktop` (CheckInScreen 5, DashboardModel 16, SaleForm 9), **123** in all. `CheckInScreenTest` takes about 20 seconds: the robot really types.

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

**Run the window:** `../../mvnw package`, then `java -jar desktop/target/boxoffice-desktop.jar` (Windows: `desktop\target\boxoffice-desktop.jar`). `--db=other.db` and `--slow-disk` work as before.

**Setting up a USB scanner.** It must be a **2D** scanner (sometimes called an "imager"). Older laser ones only read the striped barcodes. Then:

1. Open a plain text editor and scan the QR. You should see `VIP-0001` and the cursor jump to a new line.
2. **No new line?** The scanner's suffix isn't Enter. Its manual has a page of setup barcodes: scan the one for "Add CR suffix" or "Enter".
3. **Strange characters, like `VIP?0001` or `vip-à)))`**? The scanner thinks the keyboard layout is different from your laptop's (US against UK, or French). Scan the manual's barcode for your layout, or set the laptop's keyboard to US while you scan.

## When you're stuck

- **`Not written yet: Day 110, step 4`** or **`step 5`**: that method still has its `throw`.
- **`this case label is dominated by a preceding case label`**: the plain `NotFound` case is above the one with `when`. Swap them.
- **`Couldn't check in ticket VIP-0001`** with `no such column: checked_in_at`: the V3 file has only comments. Do step 3.
- **The app won't start: `Migration checksum mismatch for migration version 3`**: the app opened `gate.db` before V3 had its SQL, the same as Day 108. Delete `gate.db` (and `gate.db-wal`, `gate.db-shm`) and start again.
- **`aTicketWorksOnlyOnce` says Admitted twice**: `markCheckedIn`'s answer isn't used. Return `Admitted` only when it's `true`.
- **`tidiesWhatTheScannerSends` says `NotFound[code=  vip-0001 ...]`**: strip and upper-case the code before looking it up.
- **`CheckInScreenTest` says `expected: "Admitted: VIP-0001 · Wanjiku (VIP)" but was: "Scan a ticket"`**: `onScan` is empty. Do step 6.
- **`theScanBoxEmptiesItselfForTheNextTicket` fails**: clear `scanField` after `checkIn`.
- **Ctrl+2 does nothing on a Mac**: use Cmd+2. The code asks for the "shortcut" key, which is Cmd on a Mac and Ctrl elsewhere.
- **No QR appears**: it shows the last ticket sold *in this window*, after the sale is saved. Sell one.
- **The scanner beeps but nothing happens in the window**: the cursor isn't in the scan box. Press Ctrl+2.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/Gate.java` (or `CheckInOutcome.java`, `solution/desktop/src/main/java/com/boxoffice/desktop/DashboardController.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say why `QrCodes` returns `boolean[][]`, not a JavaFX image
- [ ] Can say why a USB scanner needs no special code
- [ ] Wrote V3, with a column that can be null
- [ ] Can explain how `... and checked_in_at is null` makes "only once" safe
- [ ] Green, amber and red all show in the window
- [ ] All 123 tests pass
- [ ] Read the QR with a phone camera (and a USB scanner, if you have one)
- [ ] Committed your work to Git (not `gate.db`)

## Next

The gate sells and checks people in from one laptop. Tomorrow the power goes out in the middle of a sale, and you make sure the gate comes back with every sale whole: transactions, SQLite's WAL mode, and recovery after a crash.
