# Day 107: Live sales table

> "Did my ticket go through?" The answer should be right there on the screen, at the top, before the buyer finishes asking.

Watch the video: *(not recorded yet)*

## The brief

Yesterday's dashboard shows totals. Today it shows **every sale**, in a table, newest at the top: ticket code, type, buyer, price and time. When Wanjiku asks whether her VIP ticket went through, the person at the gate glances at the first row and says yes.

The window also learns to **sell**. A type picker, a name box and a Sell button are already on the left. You write the model's side: fill the table, keep a "2 sold" count that updates by itself, and make a sale.

```
Tickets                       │ Sales, newest first
 Regular · KES 1,500 · 499... │ Ticket    Type     Buyer     Price      Time
 VIP · KES 5,000 · 49 of 50.. │ VIP-0001  VIP      Wanjiku   KES 5,000  19:02
Sell a ticket                 │ REG-0001  Regular  Otieno    KES 1,500  19:01
 [ VIP          v]            │
 [ Buyer's name   ]           │
 [      Sell      ]           │
 Sold VIP-0001 to Wanjiku ... │
```

The window and yesterday's CLI share one `gate.db`. A sale made from the terminal shows up in the table the next time the window reads the file.

## What you'll learn

- How a `TableView` and its columns show a list of rows
- Why `PropertyValueFactory` shows empty cells for a record, and what to use instead
- How an `ObservableList` makes the table redraw by itself
- How `Bindings.createStringBinding` makes a value that works itself out again when something changes
- How `List.reversed()` turns a list around without copying it by hand
- Why the model keeps the screen's text, and the screen just shows it

## New words

- **`TableView`**: a JavaFX table with a header row and one row per item in a list.
- **`TableColumn`**: one column of a table. You tell it which part of the row to show.
- **Cell value factory**: the small function a column calls for each row to get the value for its cell.
- **`ObservableList`**: a list that tells anyone watching when it changes. A table showing it redraws itself.
- **Binding** (again): a value linked to others. `createStringBinding` makes one from your own code.
- **Dependency** (of a binding): what the binding watches. When it changes, the binding works itself out again.
- **Newest first**: the latest item at the top, the way a phone shows messages.

## What's already written

From Days 101–106: `core`, `cli` and the dashboard, working, with tests.

New today, already done:

- `NairobiTime.timeOfDay(instant)` gives just the time, `"21:05"`. One new test in `NairobiTimeTest`.
- `SaleRow.java`: a record for one row of the table, with every value already turned into text. Complete.
- `dashboard.fxml`: the sell form on the left (`typeChoice`, `buyerField`, `sellButton`, `message`), the `salesTable` with five columns in the middle, and a `soldCount` label under the title.
- `DashboardController.java`: binds the new labels, fills the type picker, wires the Sell button, and sets up the five columns. Complete. Read it.
- `DashboardModel.java`: the `sales` list, the `message` property, `row(ticket)` that makes a `SaleRow`, and `ticketTypes()`. You write three bits.
- `DashboardModelTest.java`: 14 tests (7 from yesterday, 7 new).

## What you'll use

**A column for a record, with a lambda.** `new PropertyValueFactory<>("code")` looks for a method called `getCode()` or `codeProperty()`. A record only has `code()`, so the column would be empty, with no error. The controller says it straight instead:

```java
codeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().code()));
```

`cell.getValue()` is the `SaleRow` for that row. The controller does this for all five columns with a small helper, `show(column, SaleRow::code)`.

**A binding that works itself out.** The first argument is the code that makes the text. The rest are what to watch. Whenever `sales` changes, the code runs again:

```java
StringBinding count = Bindings.createStringBinding(() -> "Seats: " + seats.size(), seats);
```

**Turning a list around.** Java 21 lists have `reversed()`. It gives a view of the same list, last item first:

```java
List.of("REG-0001", "REG-0002", "VIP-0001").reversed()   // [VIP-0001, REG-0002, REG-0001]
```

**Checking the kind of outcome.** The same `instanceof` you used in the CLI on Day 105:

```java
if (outcome instanceof SaleOutcome.Sold) { ... }
```

## Steps

1. **Open the starter and run the tests.** `cd day-107-live-sales-table/starter`, then `../../mvnw test`. `core` (61) and `cli` (8) pass. In `desktop` you should see `Tests run: 14, Failures: 2, Errors: 4`, some with `Not written yet: Day 107, step 4` and one with `expected: "0 sold" but was: ""`.

2. **Read how the table is set up.** Open `dashboard.fxml` and find the `TableView` with `fx:id="salesTable"` and its five `TableColumn`s. Then open `DashboardController.java`: at the end of `initialize()`, `salesTable.setItems(model.sales())` hands the table the model's list, and the five `show(...)` lines tell each column what to show. Read the comment above `show`: it's why we don't use `PropertyValueFactory`. Nothing to write here.

3. **Fill the table and the count.** Open `starter/desktop/src/main/java/com/boxoffice/desktop/DashboardModel.java`.
   - In the constructor, change the `soldCount` line so the text is `sales.size() + " sold"`. Keep `sales` as the second argument: that's what it watches.
   - At the end of `refresh()`, `sales.setAll(...)` with one `row(ticket)` for each ticket in `gate.ticketsSold()`, newest first. `ticketsSold()` is oldest first, so call `.reversed()` on it, then `.stream().map(this::row).toList()`.

   Run the tests. `refreshShowsSalesMadeByAnotherProgramOnTheSameFile` passes now. Five tests still stop at `Not written yet: Day 107, step 4`, because they sell, and selling is next.

4. **Sell.** In `sell(typeCode, buyer)`, replace the `throw`:
   - `SaleOutcome outcome = gate.sell(typeCode, buyer);`
   - put `outcome.describe()` in `message`;
   - if it sold (`instanceof SaleOutcome.Sold`), call `refresh()`, so the table, the takings and "what's left" all catch up;
   - return the outcome.

   A refused sale (no name, sold out) only changes the message. Run `../../mvnw test`: all 14 desktop tests should pass.

5. **Sell from the window.** `../../mvnw package`, then `java -jar desktop/target/boxoffice-desktop.jar`. Pick VIP, type `Wanjiku`, click Sell. You should see `Sold VIP-0001 to Wanjiku for KES 5,000` under the button, a new row at the top of the table, `1 sold` under the title, and `KES 5,000` in Takings. Click Sell with the name box empty: the message says `Rejected: A ticket needs a buyer's name` and the table doesn't change.

6. **Sell from the terminal at the same time.** Leave the window open. In a second terminal, in the same `starter` folder, run `java -jar cli/target/boxoffice.jar sell REG Otieno`. The window doesn't know yet: it only reads `gate.db` when it opens and after its own sales. Sell one more in the window. Now both new sales are in the table, Otieno's under the window's newest one. Both programs number tickets from what's in the file, so they never hand out the same code. (Otieno's time may look earlier than the sale above it: each program's practice clock starts at 19:00 when you start that program.)

## Run it and test it

From the day's `starter` folder:

```
cd day-107-live-sales-table/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=DashboardModelTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=DashboardModelTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **61 tests** in `core`, **8** in `cli` and **14** in `desktop`, **83** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

**Run the window** (on Windows, `desktop\target\boxoffice-desktop.jar`):

```
../../mvnw package
java -jar desktop/target/boxoffice-desktop.jar
```

The `WARNING: Unsupported JavaFX configuration` line at startup is harmless, as on Day 106. Sales are stamped around 19:00 on 14 November because the app uses the practice clock. Delete `gate.db` to start the night again.

## When you're stuck

- **`Not written yet: Day 107, step 4`**: `sell` still has its `throw`. Do step 4.
- **`expected: "0 sold" but was: ""`**: the `soldCount` binding still returns `""`. Make it `sales.size() + " sold"`.
- **The count says `0 sold` and never changes, though the table fills up**: the second argument to `createStringBinding` must be `sales`. Without it, the binding doesn't know when to work itself out again.
- **`sellingPutsTheNewSaleAtTheTopOfTheTable` fails with Otieno first**: the rows are oldest first. Call `.reversed()` before `.stream()`.
- **`refreshShowsSalesMadeByAnotherProgramOnTheSameFile` says `Expecting actual: [] to contain exactly ["Kamau"]`**: `refresh()` doesn't fill `sales` yet (step 3).
- **`sellingUpdatesWhatIsLeftAndTheTakings` fails: still `50 of 50 left`**: call `refresh()` after a sale that sold.
- **The table has rows but every cell is empty**: you swapped a `show(...)` line for `PropertyValueFactory`. Records don't have `getCode()`; put the lambda back.
- **Clicking Sell does nothing and the terminal shows a long error**: the starter's `sell` still throws. Finish step 4 and rebuild.
- **The CLI sale doesn't appear in the window**: that's expected until the window reads the file again. Sell one in the window, or close and reopen it.
- **Still stuck?** Open `solution/desktop/src/main/java/com/boxoffice/desktop/DashboardModel.java`, read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say why `PropertyValueFactory` shows empty cells for a record
- [ ] The table shows every sale, newest at the top
- [ ] `0 sold` turns into `1 sold` by itself, with no code that sets it
- [ ] A refused sale changes the message and nothing else
- [ ] All 83 tests pass
- [ ] Sold a ticket from the window, and one from the CLI on the same `gate.db`
- [ ] Committed your work to Git (not `gate.db`)

## Next

The queue doesn't wait while someone hunts for the mouse. Tomorrow the gate sells from the keyboard alone: F1 or F2 for the type, type the name, Enter. And it learns to take the buyer's phone number, which means changing the database for the first time since Day 104.
