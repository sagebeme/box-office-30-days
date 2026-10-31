# Day 103: Money and time done right

> At 04:00 the gate stops selling, and at the end of the night the takings must match the cash box to the last cent.

Watch the video: *(not recorded yet)*

## The brief

Tickets now have prices: Regular KES 1,500, VIP KES 5,000. Every ticket also remembers the moment it was sold. Two things go wrong in a lot of real apps here, and today you get both right.

**Money.** If you add up money with `double`, small errors creep in: in a `double`, `0.1 + 0.2` is `0.30000000000000004`. A till that is off by a cent every few hundred sales is a till nobody trusts. So the gate keeps money as a whole number of **cents** in a `long`. KES 1,500 is `150000` cents.

**Time.** The event ends at 04:00 *Nairobi time*. The laptop might be set to London, or to nothing in particular. The gate stores each moment as an `Instant` (one point in time, the same everywhere) and only turns it into Nairobi time when it shows it to a person. And so that tests give the same answer at 3pm and at 3am, the gate reads the time from a `Clock` that tests can fix in place.

```java
Money.ofKes(1_500).plus(Money.ofKes(5_000)).format()     // "KES 6,500"
Money.ofCents(150_050).format()                          // "KES 1,500.50"
NairobiTime.format(Instant.parse("2026-11-14T18:05:00Z"))  // "Sat 14 Nov, 21:05"

// a gate whose clock says 04:30 the next morning
gate.sell("REG", "Kamau")   // Rejected("Test Night ended at Sun 15 Nov, 04:00")
gate.takings()              // KES 8,000 after two Regular and one VIP
```

## What you'll learn

- Why money is a whole number of cents, never a `double`
- How `Math.addExact` and `Math.multiplyExact` catch numbers that are too big
- Why `Locale.ROOT` keeps "1,500" the same on every laptop
- The difference between an `Instant` and a `ZonedDateTime`
- How to show a moment in the `Africa/Nairobi` time zone
- How passing in a `Clock` makes time-based tests repeatable

## New words

- **Cents**: the hundredths of a shilling; KES 1 is 100 cents.
- **`long`**: a whole-number type big enough for about 92 million billion cents.
- **Overflow**: when a number gets too big for its type and silently wraps round to a wrong value.
- **Locale**: a setting for a language and country, which changes how numbers and dates are written (in German, 1,500 is written 1.500).
- **`Locale.ROOT`**: a neutral locale that writes numbers the same way everywhere.
- **`Instant`**: one exact point in time, with no time zone, like `2026-11-14T15:00:00Z` (the `Z` means UTC).
- **UTC**: the world's reference time; Nairobi is always three hours ahead of it.
- **Time zone**: a region's rules for its clocks, such as `Africa/Nairobi`.
- **`ZonedDateTime`**: a date and time on a particular region's clock.
- **`Clock`**: an object you ask "what time is it now?"; tests pass one that always gives the same answer.

## What's already written

From Days 101–102: `BoxOffice`, `Event`, `TicketType`, `Ticket`, `SaleOutcome`, `SampleEvent` and `Gate`, all working, with their tests. Some of them grew today:

- `Event` now has `startsAt` and `endsAt` (`ZonedDateTime`s) and refuses to end before it starts.
- `TicketType` now has a `price` and refuses a missing one.
- `Ticket` now has a `price` and `soldAt` (an `Instant`).
- `Gate` now takes a `Clock`: `new Gate(event, clock)`. `sell` reads `now` from it, and stamps each ticket with its price and time.
- `SaleOutcome.describe()` now ends with the price: `"Sold VIP-0001 to Wanjiku for KES 5,000"`.
- `SampleEvent.kileleNights()` now runs Sat 14 Nov 2026 18:00 to Sun 15 Nov 04:00, Nairobi time.

New files, partly written:

- `Money.java`: the record, `ZERO`, `ofKes`, `ofCents`, the check against negative amounts and `compareTo`. You write `plus`, `times` and `format`.
- `NairobiTime.java`: the `ZONE`, the `LABEL` pattern and `at(...)`. You write `format`.

## What you'll use

**Exact arithmetic.** `+` on two huge `long`s wraps round to a negative number without a word. These throw an `ArithmeticException` instead:

```java
Math.addExact(150_000L, 500_000L)      // 650000
Math.multiplyExact(150_000L, 3)        // 450000
```

**Making a new value, not changing the old one.** `Money` is a record, so it can't change. `plus` gives back a new `Money`:

```java
return new Money(Math.addExact(cents, other.cents));
```

**Formatting with commas.** `%,d` puts in thousands separators. `Locale.ROOT` makes them commas on every laptop. `%02d` pads to two digits, so 5 cents prints as `05`.

```java
String.format(Locale.ROOT, "%,d", 1_234_567L)   // "1,234,567"
"%02d".formatted(5)                             // "05"
```

**Whole shillings and leftover cents.** With whole numbers, `/` drops the remainder and `%` gives it:

```java
150_050 / 100    // 1500
150_050 % 100    // 50
```

**Instant to Nairobi clock to text.**

```java
ZonedDateTime inNairobi = instant.atZone(ZoneId.of("Africa/Nairobi"));
DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH).format(inNairobi)
```

**A fixed clock in tests.** This is how `GateTest` sets the time to 19:00 on the night:

```java
Clock clock = Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"), ZoneOffset.UTC);
new Gate(event, clock);
```

## Steps

1. **Open the starter and run the tests.** `cd day-103-money-and-time/starter`, then `../../mvnw test`. You should see `Tests run: 35, Failures: 0, Errors: 12` and `BUILD FAILURE`, with errors pointing at steps 3, 4 and 5.

2. **Read what changed.** Open `Event.java`, `TicketType.java`, `Ticket.java` and `Gate.java` and find the new fields listed above. In `Gate`, see how `sell` gets `now` from the clock and `end` from the event. Then open `GateTest.java` and find `clockAt(...)`: that is the fixed clock.

3. **Add and multiply money.** Open `starter/core/src/main/java/com/boxoffice/core/Money.java`.
   - `plus(other)`: return a new `Money` holding `Math.addExact(cents, other.cents)`.
   - `times(count)`: the negative check is already there. Return a new `Money` holding `Math.multiplyExact(cents, count)`.

4. **Format money.** In `format()`:
   - the shillings are `cents / 100`, the leftover is `cents % 100`;
   - format the shillings with `String.format(Locale.ROOT, "%,d", shillings)`;
   - no leftover gives `"KES 1,500"`; a leftover gives `"KES 1,500.50"`, always with two digits after the point (`%02d`).

   Run `../../mvnw test -Dtest=MoneyTest -Dsurefire.failIfNoSpecifiedTests=false`. All 8 should pass.

5. **Show a moment in Nairobi time.** Open `NairobiTime.java`. In `format(instant)`, put the instant on a Nairobi clock with `instant.atZone(ZONE)`, then format that with `LABEL`. One line. Run `NairobiTimeTest`: all 4 should pass, including the one that sets the laptop to New York.

6. **Stop selling when the event is over.** Back in `Gate.java`, in `sell`, find the TODO under `Instant end = ...`. If `now` is **not** before `end`, return `new SaleOutcome.Rejected(event.name() + " ended at " + NairobiTime.format(end))`. "Not before" means at exactly 04:00 the gate already says no.

7. **Add up the takings.** In `Gate.takings()`, start from `Money.ZERO` and, for every ticket in `sold`, add its `price()` with `plus`. Remember `plus` gives back a new value: `total = total.plus(...)`.

8. **Run all the tests.** `../../mvnw test`. You should see 35 tests and `BUILD SUCCESS`.

## Run it and test it

From the day's `starter` folder:

```
cd day-103-money-and-time/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=MoneyTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=MoneyTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **35 tests** (BoxOffice 3, Gate 10, Money 8, NairobiTime 4, SaleOutcome 3, SampleEvent 3, TicketType 4):

```
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## When you're stuck

- **`SaleOutcomeTest` fails with `Not written yet: Day 103, step 4`**: `describe()` prints the price with `Money.format`. Finish steps 3 and 4 first; then this test shows its real result.
- **`expected: "KES 1,500" but was: "KES 1.500"`**: you left out `Locale.ROOT`, and your laptop's language writes numbers the other way.
- **`expected: "KES 0.05" but was: "KES 0.5"`**: use `%02d` for the cents, not `%d`.
- **`expected: "KES 1,500" but was: "KES 1,500.00"`**: only show cents when `cents % 100` isn't 0.
- **`expected: "Sat 14 Nov, 21:05" but was: "Sat 14 Nov, 18:05"`**: you formatted the time in UTC. Use `instant.atZone(ZONE)` first.
- **`Unsupported field: DayOfWeek`**: you tried to format the `Instant` itself. An `Instant` has no day or hour until it's on a clock: call `atZone(ZONE)`.
- **`stopsSellingOnceTheEventIsOver` gets a `Sold`**: check the condition. You want to reject when `!now.isBefore(end)`.
- **`addsUpTheTakings` says `expected: KES 8,000 but was: KES 0`**: you called `total.plus(...)` but didn't store the result. `Money` never changes; write `total = total.plus(...)`.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/Money.java` (or `NairobiTime.java`, `Gate.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can explain why the gate never uses `double` for money
- [ ] `plus` and `times` use `Math.addExact` and `Math.multiplyExact`
- [ ] `format` uses `Locale.ROOT`
- [ ] `NairobiTime.format` gives Nairobi time whatever the laptop's time zone
- [ ] The gate refuses sales from 04:00 on
- [ ] All 35 tests pass
- [ ] Committed your work to Git

## Next

Right now, if the power goes out at 22:00, every sale since 18:00 is gone. Tomorrow the gate writes each sale to a database file on the laptop's disk, so a power cut can't lose anything.
