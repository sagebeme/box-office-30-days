# Day 105: Guest-list import and sales report

> At 17:30 the organiser hands you a USB stick with the guest list from Excel. At 04:00 she wants to know what the night took.

Watch the video: *(not recorded yet)*

## The brief

Two jobs come up at every event. Before the gate opens, the organiser's **guest list** has to go in: artists, sponsors and friends who get in free but still take a place. And when the night ends, someone wants the **sales report**: how many paid, how many guests, how many places are left, and how much money came in.

Today the gate gets a **command line**: one program you run from a terminal with a command word after it. It lives in a new module, `cli`, which uses everything in `core`. You build it into one runnable jar file that you can copy to the gate laptop.

The guest list comes from Excel, and Excel files bring surprises: an invisible mark at the very start, a header line, blank lines, a row with too many commas. The gate must cope with the harmless ones and refuse the bad ones with an error that names the line, so the organiser can fix it.

```
$ java -jar cli/target/boxoffice.jar sell VIP "Wanjiku Kamau"
Sold VIP-0001 to Wanjiku Kamau for KES 5,000

$ java -jar cli/target/boxoffice.jar import-guests guests.csv
Kamau Njoroge: Rejected: No ticket type called VVIP
Admitted 2 of 3 guests

$ java -jar cli/target/boxoffice.jar report
Kilele Nights · Sat 14 Nov, 18:00
Type        Paid  Guests   Left        Takings
Regular        0       1    499          KES 0
VIP            1       1     48      KES 5,000
Total          1       2    547      KES 5,000
```

## What you'll learn

- How to add a second Maven module that depends on the first
- How **picocli** turns a class into a command with options and parameters
- What **exit codes** are, and why scripts rely on them
- The gotchas of reading a CSV from Excel: the byte-order mark, the header, the column count, and errors that name the line
- How to lay out a text table with `String.format` widths
- How to build a **shaded jar** that runs with `java -jar`
- Why `print` needs a `flush`, and `println` often doesn't

## New words

- **CLI (command-line interface)**: a program you use by typing commands in a terminal instead of clicking.
- **Subcommand**: the word after the program name that says what to do, like `sell` or `report`.
- **Option**: a named setting that starts with dashes, like `--db other.db`.
- **Parameter**: a plain value in a fixed position, like `VIP` and `"Wanjiku Kamau"` after `sell`.
- **picocli**: a library that reads the command line for you and writes the `--help` text.
- **Exit code**: a number a program hands back when it ends; 0 means it worked, anything else means it didn't.
- **CSV**: "comma-separated values", a plain text file with one row per line and commas between columns.
- **Byte-order mark (BOM)**: an invisible character (`﻿`) that Excel puts at the very start of a CSV it saves as UTF-8.
- **Jar**: a zip file of compiled Java classes.
- **Shaded jar** (also called a fat jar): one jar with your code *and* every library inside, so `java -jar` needs nothing else.
- **Flush**: push text that is waiting in a buffer out to the screen or file now.

## What's already written

From Days 101–104: everything up to the SQLite store, working, with tests. A little grew today:

- `Gate` has a new `admitGuest(typeCode, name)`. It works like `sell` but the ticket costs `Money.ZERO`. A guest still takes a place. Two new tests in `GateTest` cover it.
- `SampleEvent.ensureIn(store)` puts Kilele Nights in the store if it isn't there yet.
- The parent `pom.xml` lists a second module, `cli`, and pins picocli and the shade plugin.

New in `core`:

- `Guest.java`: a record with `name` and `typeCode`. Complete.
- `GuestListException.java`: its message always starts `Line 3: ...`. Complete.
- `GuestListCsv.java`: the `HEADER`, the `BYTE_ORDER_MARK` and the reading loop are there. You write what happens to each line.
- `SalesReport.java`: `of(...)` (counting per type) and `totalTakings()` are complete. You finish `render()`.

New module `cli` (in `starter/cli/`):

- `pom.xml`: depends on `core` and picocli, and builds `cli/target/boxoffice.jar` with the shade plugin.
- `BoxOfficeCli.java`: the top command, the `--db` option (default `gate.db`) and `main`. Complete.
- `SetupCommand.java`, `SellCommand.java`, `ReportCommand.java`: complete.
- `ImportGuestsCommand.java`: reads the file and opens the store. You write the loop that lets each guest in.
- `BoxOfficeCliTest.java`: 7 tests that run the real commands against a temporary `gate.db`.

## What you'll use

**One module using another.** In `cli/pom.xml`, `core` is just another dependency. Maven builds `core` first because `cli` needs it:

```xml
<dependency>
    <groupId>com.boxoffice</groupId>
    <artifactId>core</artifactId>
</dependency>
```

**A picocli command.** The annotations describe the command; picocli fills the fields and calls `call()`. What `call()` returns becomes the exit code:

```java
@Command(name = "sell", description = "Sells one ticket.")
final class SellCommand implements Callable<Integer> {
    @Parameters(index = "0") String type;
    @Parameters(index = "1") String buyer;
    public Integer call() { ...; return outcome instanceof SaleOutcome.Sold ? 0 : 1; }
}
```

**Checking what kind of outcome you got.** `instanceof` with a record type, as `SellCommand` does:

```java
if (outcome instanceof SaleOutcome.Sold) { ... }
```

**Reading CSV lines safely.** `split(",", -1)` keeps an empty last column, so `"Achieng,"` gives two columns, not one:

```java
"Achieng,VIP".split(",", -1)    // ["Achieng", "VIP"]
"Achieng,".split(",", -1)       // ["Achieng", ""]
"Achieng,".split(",")           // ["Achieng"]   (the empty one is dropped)
```

**Upper-casing with `Locale.ROOT`.** On a laptop set to Turkish, `"vip".toUpperCase()` gives `"VİP"` with a dotted capital İ. `toUpperCase(Locale.ROOT)` always gives `"VIP"`.

**Columns that line up.** In a format, `%-10s` means "at least 10 wide, on the left" and `%5s` means "at least 5 wide, on the right". `%n` is a new line. `render()` already has the row format:

```java
String row = "%-10s %5s %7s %6s %14s%n";
row.formatted("Regular", 2, 1, 497, "KES 3,000")
// "Regular        2       1    497      KES 3,000"
```

**print, println and flush.** Output is collected in a buffer and written out in chunks. The writer picocli gives you flushes on `println`, but not on `print`. `main` ends with `System.exit`, which doesn't wait for anything still in the buffer. That's why `ReportCommand` calls `out.flush()` after `out.print(...)`.

## Steps

1. **Open the starter and run the tests.** `cd day-105-guest-list-and-report/starter`, then `../../mvnw test`. You should see `Tests run: 59, Failures: 4, Errors: 8` for `core` and `BUILD FAILURE`. The reactor summary at the end says `Box Office CLI ... SKIPPED`: Maven doesn't test `cli` until `core` passes.

2. **Look at the new module.** Open `starter/cli/pom.xml` and find the `core` dependency and the shade plugin's `mainClass`. Open `BoxOfficeCli.java` and see the four subcommands and the `--db` option. Then read `SellCommand.java` from top to bottom: it's a whole command in 30 lines.

3. **Parse the guest list.** Open `starter/core/src/main/java/com/boxoffice/core/GuestListCsv.java`. Inside the `while` loop, replace the `throw` with, in this order:
   - on line 1, if the line starts with `BYTE_ORDER_MARK`, drop it (`line.substring(1)`);
   - if the line is blank, `continue`;
   - the first line that isn't blank must be the `HEADER`, ignoring case and spaces (`line.strip().equalsIgnoreCase(HEADER)`). If it isn't, throw `new GuestListException(lineNumber, "the first line should be " + HEADER)`. If it is, set `seenHeader = true` and `continue`;
   - every other line: `String[] columns = line.split(",", -1)`. Not exactly 2 columns: throw `new GuestListException(lineNumber, "expected 2 columns (" + HEADER + "), found " + columns.length)`;
   - strip both columns, and upper-case the type with `toUpperCase(Locale.ROOT)`;
   - an empty name throws with `"a guest needs a name"`; an empty type throws with `"a guest needs a ticket type"`;
   - otherwise `guests.add(new Guest(name, type))`.

   Run `../../mvnw test -Dtest=GuestListCsvTest -Dsurefire.failIfNoSpecifiedTests=false`. All 10 should pass.

4. **Render the report.** Open `SalesReport.java` and find `render()`. The title and the heading row are already added to `out`. Now:
   - keep three running totals, `paid`, `guests` and `left`, starting at 0;
   - for each `Line` in `lines`, append `row.formatted(line.typeName(), line.paid(), line.guests(), line.left(), line.takings().format())` and add its numbers to the totals;
   - append one last row: `"Total"`, the three totals, and `totalTakings().format()`;
   - `return out.toString()`.

   Run `SalesReportTest`: all 3 should pass. Now `core` is done, so the `cli` tests run too.

5. **Let the guests in.** Open `starter/cli/src/main/java/com/boxoffice/cli/ImportGuestsCommand.java`. Below `Gate gate = ...`, write a loop over `guests`. For each one, call `gate.admitGuest(guest.typeCode(), guest.name())`:
   - a `SaleOutcome.Sold` adds 1 to `admitted`;
   - anything else prints `guest.name() + ": " + outcome.describe()` to `err`.

   The lines after the loop already print `Admitted 2 of 3 guests` and pick the exit code.

6. **Run all the tests, then build and try the jar.** `../../mvnw test`. You should see 68 tests in all (60 in `core`, 8 in `cli`) and `BUILD SUCCESS`. Then follow "Run it and test it" below to build the jar and sell a ticket from your terminal.

## Run it and test it

From the day's `starter` folder:

```
cd day-105-guest-list-and-report/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=BoxOfficeCliTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=BoxOfficeCliTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **60 tests** in `core` (BoxOffice 3, Gate 13, GuestListCsv 10, Money 8, NairobiTime 4, SaleOutcome 3, SalesReport 3, SampleEvent 5, TicketStore 7, TicketType 4) and **8** in `cli`, **68** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] BUILD SUCCESS
```

**Build the jar.** `package` runs the tests first, then builds the jar (about 18 MB, because SQLite, Flyway and picocli are inside):

```
../../mvnw package               # macOS / Linux
..\..\mvnw.cmd package           # Windows
```

**Make a guest list.** Save this as `guests.csv` in the `starter` folder:

```
name,type
Achieng Atieno,VIP
Otieno Odhiambo,REG
Kamau Njoroge,VVIP
```

**Run the gate.** On Windows, write `cli\target\boxoffice.jar` instead of `cli/target/boxoffice.jar`.

```
$ java -jar cli/target/boxoffice.jar setup
Kilele Nights is ready in gate.db

$ java -jar cli/target/boxoffice.jar sell VIP "Wanjiku Kamau"
Sold VIP-0001 to Wanjiku Kamau for KES 5,000

$ java -jar cli/target/boxoffice.jar import-guests guests.csv
Kamau Njoroge: Rejected: No ticket type called VVIP
Admitted 2 of 3 guests

$ java -jar cli/target/boxoffice.jar report
Kilele Nights · Sat 14 Nov, 18:00
Type        Paid  Guests   Left        Takings
Regular        0       1    499          KES 0
VIP            1       1     48      KES 5,000
Total          1       2    547      KES 5,000
```

`java -jar cli/target/boxoffice.jar --help` lists the commands. `--db other.db` (before the command) uses a different database file.

**See the exit code** right after a command: `echo $?` on macOS/Linux, `echo %ERRORLEVEL%` in Windows cmd, `$LASTEXITCODE` in PowerShell. A sale gives 0. `sell VVIP Kamau` gives 1. The import above gives 1, because one guest didn't get in. A missing parameter gives 2 (picocli's code for "you typed it wrong").

The database file `gate.db` appears in the folder you run the commands from. Run them again tomorrow and the sales are still there. It's listed in `.gitignore`, so it never ends up in Git. Delete it to start the night again.

## When you're stuck

- **`Box Office CLI ... SKIPPED`**: `core` still has failing tests. Finish steps 3 and 4; the `cli` tests run after that.
- **`GuestListException: Line 1: the first line should be name,type`** in `copesWithTheMarkExcelPutsAtTheStart`, on a file that clearly has the header: the invisible byte-order mark is still there, and `strip()` doesn't remove it. Drop it on line 1 before anything else.
- **`Line 2: expected 2 columns (name,type), found 1`** for `Achieng,`: use `line.split(",", -1)`. Without the `-1`, Java drops the empty last column.
- **`skipsBlankLines` fails with `Line 2: expected 2 columns`**: skip blank lines before you split.
- **`expected: Guest[name=Achieng, typeCode=VIP] but was: Guest[name=  Achieng , typeCode=  vip ]`**: strip both columns and upper-case the type.
- **The report's columns don't line up**: use the `row` format for every row, including `Total`, and pass `takings().format()`, not the `Money` itself.
- **`importsAGuestList` says `expected: 0 but was: 1`, and the output is `Admitted 0 of 2 guests`**: the loop in `ImportGuestsCommand` is missing, or doesn't add to `admitted`.
- **`Error: Unable to access jarfile cli/target/boxoffice.jar`**: run `../../mvnw package` first, from the `starter` folder. If the tests fail, no jar is built.
- **Why do sales from the jar say 19:00 on 14 November, whatever today is?** `main` uses `SampleEvent.practiceClock()`, a clock set to the night of the event that ticks on from when you start it. Without it, every sale after 15 November 2026 would be `Rejected: Kilele Nights ended at Sun 15 Nov, 04:00`: the end-of-event rule from Day 103 doing its job. A real gate would pass `Clock.systemUTC()`.
- **`There's no file called guests.csv`**: the file isn't in the folder you're running from. Check the name and where you saved it.
- **The report's first line shows a strange character instead of `·`** on Windows: your terminal isn't using UTF-8. Try Windows Terminal, or run `chcp 65001` first in an old console. The tests don't care; it's only the screen.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/GuestListCsv.java` (or `SalesReport.java`, `solution/cli/src/main/java/com/boxoffice/cli/ImportGuestsCommand.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can explain why `cli` is its own module that depends on `core`
- [ ] The guest list copes with Excel's byte-order mark, blank lines and a capitalised header
- [ ] A bad line stops the import with an error that names the line
- [ ] The report's columns line up, with a `Total` row
- [ ] All 68 tests pass
- [ ] Built `cli/target/boxoffice.jar` and sold a ticket from the terminal
- [ ] Can say what exit codes 0, 1 and 2 mean here
- [ ] Committed your work to Git (not `gate.db` or `guests.csv` with real names)

## Next

Typing commands is fine for you, but not for the person at the gate with a queue in front of them. Tomorrow the gate gets a window: an event dashboard that shows what's left and the takings at a glance.
