# Day 108: Sell a ticket without touching the mouse

> F2, type "Wanjiku", Enter. Next. The queue moves at the speed of the keyboard.

Watch the video: *(not recorded yet)*

## The brief

At 21:30 the queue at the Kilele Nights gate is forty people long. Every reach for the mouse costs a second or two, and the mouse pad is a folded flyer on a wobbly table. So the gate sells from the **keyboard alone**:

- **F1** picks Regular, **F2** picks VIP;
- type the buyer's name, and their phone number if they give one;
- **Enter** sells;
- **Esc** clears the form for the next person.

The Sell button stays greyed out until the form is ready, and a line under it says what's missing: "Type the buyer's name", "Phone should look like 0712 345 678".

The phone number is new. Buyers type it every way: `0712 345 678`, `+254 712-345-678`, `254712345678`. The gate keeps one way only, the way M-Pesa and SMS gateways want it: `254712345678`. Saving it means a new column in the database, so today you write your second Flyway migration. Every gate laptop that ran Day 107 must upgrade without losing a single sale.

```java
PhoneNumbers.normalise("0712 345 678")      // Optional[254712345678]
PhoneNumbers.normalise("+254 712-345-678")  // Optional[254712345678]
PhoneNumbers.normalise("0110 123 456")      // Optional[254110123456]
PhoneNumbers.normalise("020 222 2222")      // Optional.empty  (a landline)
```

## What you'll learn

- How to change a table that already has data in it, with a new migration
- Why you never edit a migration that has already run, and what Flyway says if you do
- How an **event filter** on the window's root sees every key press first
- How **bidirectional binding** keeps a text box and the model in step, both ways
- How to make a "ready" binding that switches a button on and off by itself
- Clean-up of phone numbers, with `Optional` for "it isn't one"

## New words

- **Keyboard-first**: designed so every job can be done without the mouse.
- **Function key**: the F1, F2... keys along the top. On many laptops you hold **Fn** to use them.
- **Event filter**: code that sees a key press (or click) *before* the field that has the focus does.
- **Consume** (an event): mark a key press as handled, so nothing else acts on it.
- **Focus**: which field the keys go to right now. It has a blue outline.
- **Bidirectional binding**: two properties linked both ways. Change either one and the other follows.
- **Default button**: the button that Enter presses. JavaFX draws it a little bolder.
- **Normalise**: turn the many ways people write something into one standard way.
- **Schema**: the shape of the database: which tables there are and what columns they have.
- **Checksum**: a number worked out from a file's contents. Change one character and the number changes.

## What's already written

From Days 101–107: everything up to the live sales table, working, with tests.

Changed today, already done:

- `Ticket` has a new `phone` field (`"254712345678"`, or `""` for none). The old constructor without a phone still works.
- `Gate.sell(type, buyer, phone)` cleans the phone with `PhoneNumbers.normalise`, and rejects one that isn't a Kenyan mobile. `TicketStore` saves and reads `buyer_phone`. So these only pass once your migration and `normalise` work.
- `dashboard.fxml` has a phone box, a `problem` label, a hint line, and `fx:id="root"` on the outer `BorderPane`.
- `DashboardController.java`: the key filter, the bindings and the Enter handlers. Complete. Read it.

New, for you to finish:

- `core/src/main/resources/db/migration/V2__add_buyer_phone.sql`: a comment and a TODO. You write the SQL.
- `PhoneNumbers.java`: you write `normalise`.
- `SaleForm.java` (in `desktop`): what's typed in the form. You write `findProblem()` and `pick()`.
- New tests: `PhoneNumbersTest` (7), `MigrationTest` (1), `SaleFormTest` (9), plus new ones in `GateTest`, `TicketStoreTest` and `DashboardModelTest`.

## What you'll use

**Adding a column.** SQLite's `alter table` adds a column to a table that already has rows. A `default` fills it in for the old rows:

```sql
alter table guests add column table_number integer not null default 0;
```

**Taking off the front of a string.**

```java
"+254712345678".startsWith("+254")   // true
"+254712345678".substring(4)         // "712345678"
"712345678".matches("[71]\\d{8}")    // true: a 7 or a 1, then exactly 8 digits
```

**A binding with several dependencies, and one built on it.** `SaleForm` already has these two lines. `findProblem` runs again whenever the type, buyer or phone changes, and `ready` is true when the problem is `""`:

```java
problem = Bindings.createStringBinding(this::findProblem, type, buyer, phone);
ready = problem.isEmpty();
```

**Both ways.** Typing changes `form.buyerProperty()`; `form.clear()` empties the box:

```java
buyerField.textProperty().bindBidirectional(form.buyerProperty());
```

**A filter on the root.** Every key press passes through the root first, whichever field has the focus:

```java
root.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
```

## Steps

1. **Open the starter and run the tests.** `cd day-108-sell-without-the-mouse/starter`, then `../../mvnw test`. You should see `Tests run: 73, Failures: 0, Errors: 29` in `core`, and `cli` and `desktop` `SKIPPED`. Most errors are `Couldn't record ticket VIP-0001`, caused by `table tickets has no column named buyer_phone`. The rest say `Not written yet: Day 108, step 3`.

   **Don't open the app yet.** If the app opens `gate.db` before you write the migration, Flyway notes the empty V2 as done, and you'll hit the checksum error in "When you're stuck".

2. **Write the migration.** Open `starter/core/src/main/resources/db/migration/V2__add_buyer_phone.sql`. Under the comments, write one line that adds a column called `buyer_phone` to `tickets`: `text`, `not null`, `default ''`. Leave `V1__create_tables.sql` alone: it has already run on every gate laptop, and Flyway would refuse to start if it changed.

   Run the tests. The `buyer_phone` errors are gone, and so is `MigrationTest`'s: it builds a database the way Day 107 left it (only V1), opens it with today's code, and checks the old ticket is still there with an empty phone.

3. **Clean up phone numbers.** Open `starter/core/src/main/java/com/boxoffice/core/PhoneNumbers.java`. Replace the `throw` with:
   - `null` gives `Optional.empty()`;
   - remove the spaces and dashes (`replace(" ", "")`, then the same for `"-"`);
   - take off the front: `"+254"`, else `"254"`, else `"0"`. Anything else gives `Optional.empty()`. Check `"+254"` before `"254"`;
   - what's left must match `"[71]\\d{8}"`. If not, `Optional.empty()`. If so, `Optional.of("254" + rest)`.

   Run the tests. `core` passes (73), `cli` passes (8), and now `desktop` runs: `SaleFormTest` shows `Tests run: 9, Failures: 7`.

4. **Read the keyboard code.** Open `DashboardController.java`. Find the three `bindBidirectional` lines, `sellButton.disableProperty().bind(form.ready().not())`, and `root.addEventFilter(...)`. Then read `onKey`: a function key calls `form.pick(...)` with its number, Esc calls `form.clear()`, and both `consume()` the key. Nothing to write here.

5. **Say what's missing.** Open `starter/desktop/src/main/java/com/boxoffice/desktop/SaleForm.java` and find `findProblem()`. Check these in order, and return the first that's a problem:
   - no type picked (`type.get() == null`): `"Pick a ticket type: " + keyHints()`;
   - a buyer that's `null` or blank: `"Type the buyer's name"`;
   - a phone that's typed (not `null`, not blank) but `PhoneNumbers.normalise` gives empty: `"Phone should look like 0712 345 678"`;
   - nothing wrong: `""`.

6. **Pick a type with F1, F2.** In `pick(int functionKey)`: if `functionKey` is between 1 and `types.size()`, `type.set(types.get(functionKey - 1))`. F1 is the first type, and lists count from 0. Any other key (F5 when there are two types) does nothing. Run `../../mvnw test`: everything passes.

7. **Sell with the keyboard only.** `../../mvnw package`, then `java -jar desktop/target/boxoffice-desktop.jar`. Keep your hand off the mouse:
   - When the window opens, the cursor is already in the name box: `initialize()` asks for that with `Platform.runLater(buyerField::requestFocus)`, once the window is on screen. No mouse needed from the first key press.
   - Press **F2**. The picker says `F2  VIP · KES 5,000`. The cursor stays where it is.
   - Type `Wanjiku`. Press **Tab**, type `0712 345 678`, press **Enter**. A new row appears; the name and phone boxes empty, and the cursor goes back to the name.
   - Type `Otieno`, then Tab and `12345`. The button greys out and the line under it says `Phone should look like 0712 345 678`. Press **Esc**: the form empties.

## Run it and test it

From the day's `starter` folder:

```
cd day-108-sell-without-the-mouse/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=PhoneNumbersTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=PhoneNumbersTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **73 tests** in `core` (including Gate 16, Migration 1, PhoneNumbers 7, TicketStore 8), **8** in `cli` and **24** in `desktop` (DashboardModel 15, SaleForm 9), **105** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

**Run the window:** `../../mvnw package`, then `java -jar desktop/target/boxoffice-desktop.jar` (Windows: `desktop\target\boxoffice-desktop.jar`). `--db=other.db` picks another file.

**Watch an old database upgrade.** Copy a `gate.db` from yesterday's folder (`day-107-live-sales-table/starter/gate.db`, if you sold some there) into today's `starter` folder and open the app. Yesterday's sales are all in the table. Flyway saw that V1 had run and V2 hadn't, and ran just V2.

## When you're stuck

- **`table tickets has no column named buyer_phone`** (or `no such column: buyer_phone`): the V2 file still has only comments. Do step 2.
- **The app won't start: `FlywayValidateException: Validate failed: Migrations have failed validation`**, then `Migration checksum mismatch for migration version 2`: the app opened `gate.db` while V2 was still empty (or you changed V2 after it ran), so Flyway remembered the old file's checksum. On a real gate you'd never edit a migration that ran; you'd add a V3. Here `gate.db` only holds practice sales, so delete it (and `gate.db-wal`, `gate.db-shm` if they're there) and start the app again. The tests never hit this: each one uses a fresh database in a `@TempDir`.
- **`Not written yet: Day 108, step 3`**: `PhoneNumbers.normalise` still has its `throw`.
- **`"+254712345678"` gives `Optional.empty`**: you checked `"254"` before `"+254"`, or forgot that `"+"` isn't a digit. Check `"+254"` first.
- **`"0712-345-678"` gives empty**: remove the dashes as well as the spaces.
- **`SaleFormTest` says `expected: "Pick a ticket type: F1 Regular, F2 VIP" but was: ""`**, and the output shows `WARNING: Exception while evaluating binding` with `Not written yet: Day 108, step 5`: a binding catches exceptions and gives `""` instead, so the test sees an empty problem. Do step 5.
- **`functionKeysPickTheTypeInOrder` gets the wrong type**: `types.get(functionKey)` is one too far. Use `functionKey - 1`.
- **`aFunctionKeyWithNoTypeDoesNothing` throws `IndexOutOfBoundsException`**: check the number is between 1 and `types.size()` before calling `get`.
- **F1 opens your system's help, or changes the screen brightness**: hold **Fn** while pressing it, or switch your laptop's "function keys" setting.
- **Enter does nothing**: the form isn't ready. Read the line under the Sell button.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/PhoneNumbers.java` (or `solution/desktop/src/main/java/com/boxoffice/desktop/SaleForm.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Wrote the V2 migration, and can say why you never change V1
- [ ] Can say what Flyway's "checksum mismatch" means and how to get past it on a practice laptop
- [ ] `normalise` turns `0712 345 678`, `+254 712-345-678` and `254712345678` into `254712345678`
- [ ] The Sell button is greyed out until the form is ready
- [ ] All 105 tests pass
- [ ] Sold two tickets with F1/F2, Tab, Enter and Esc, and no mouse
- [ ] Committed your work to Git (not `gate.db`)

## Next

Tomorrow the gate laptop's disk gets slow, the way an old one does at 23:00, and the whole window freezes while a sale saves. You'll make the saving happen in the background, so the screen never stops answering.
