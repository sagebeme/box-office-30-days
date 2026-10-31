# Day 102: Tickets, events and sale outcomes

> Wanjiku asks for the last VIP ticket. Kamau asks right after her. The gate has to say yes to one and a clear no to the other.

Watch the video: *(not recorded yet)*

## The brief

The gate needs to know what it is selling. An **event** (Kilele Nights) sells **ticket types** (Regular, 500 places; VIP, 50 places), and every sale makes a **ticket** with a code like `VIP-0001` that gets printed and scanned.

A sale can go three ways, and the person at the gate must always know which one happened: it was **sold**, that type is **sold out**, or the gate **rejected** it with a reason (no name given, or a type that doesn't exist). Today you write the rules, and the line the gate screen shows for each outcome.

```java
Gate gate = new Gate(eventWithOneVipPlace);

gate.sell("VIP", "Wanjiku").describe()   // "Sold VIP-0001 to Wanjiku"
gate.sell("VIP", "Kamau").describe()     // "VIP is sold out"
gate.sell("VVIP", "Kamau").describe()    // "Rejected: No ticket type called VVIP"
gate.sell("REG", "  ").describe()        // "Rejected: A ticket needs a buyer's name"
gate.remaining("VIP")                    // 0
```

## What you'll learn

- How to model data with **records**
- How a **compact constructor** refuses bad values before an object exists
- How a **sealed interface** lists every kind of outcome, so none is forgotten
- How a **pattern-matching switch** handles each kind and pulls out its parts
- How **Optional** says "there might be nothing here" without `null`

## New words

- **Record**: a short way to write a class that just holds values; Java writes the constructor, getters, `equals`, `hashCode` and `toString` for you.
- **Compact constructor**: a constructor inside a record with no parameter list, where you check (or tidy) the values before they are stored.
- **Sealed interface**: an interface that names every class allowed to implement it, so Java knows the full list.
- **Pattern matching**: checking what kind of object you have and taking it apart in the same step.
- **Switch expression**: a `switch` that gives back a value, written with `->` arrows.
- **Optional**: a box that holds one value or is empty, used instead of returning `null`.
- **Immutable**: cannot be changed after it is made.

## What's already written

From Day 101: `BoxOffice.welcome`, finished, with its 3 tests.

New today, already complete:

- `Event.java`: a record with an id, a name and a list of ticket types. Its compact constructor makes the list immutable with `List.copyOf`, and `ticketType(code)` returns an `Optional<TicketType>`.
- `Ticket.java`: a record with `code`, `typeCode` and `buyer`.
- `SampleEvent.java`: `SampleEvent.kileleNights()`, the course's event with REG (500) and VIP (50).
- In `Gate.java`: the list of sold tickets, `ticketsSold()`, `soldOf(type)` (how many of a type were sold) and `nextCode(type)` (`VIP-0001`, `VIP-0002`, counted per type).
- In `SaleOutcome.java`: the sealed interface and its three records, `Sold`, `SoldOut` and `Rejected`.

You write: the checks in `TicketType`, `Gate.remaining`, `Gate.sell` and `SaleOutcome.describe`.

## What you'll use

**A compact constructor.** It runs before the fields are set. Throw here, and the bad object never exists:

```java
public record TicketType(String code, String name, int capacity) {
    public TicketType {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity can't be negative: " + capacity);
        }
    }
}
```

**A sealed interface with records inside.** `permits` is left out because all three live in the same file:

```java
public sealed interface SaleOutcome {
    record Sold(Ticket ticket) implements SaleOutcome { }
    record SoldOut(String typeCode) implements SaleOutcome { }
    record Rejected(String reason) implements SaleOutcome { }
}
```

**A pattern-matching switch.** Each `case` matches one record and names its parts. Because the interface is sealed, there is no `default`: if you forget a case, the code does not compile.

```java
return switch (this) {
    case Sold(Ticket ticket) -> "Sold " + ticket.code() + ...;
    case SoldOut(String typeCode) -> ...;
    case Rejected(String reason) -> ...;
};
```

**Optional.** `map` changes the value if there is one; `orElse` gives a fallback if the box is empty:

```java
Optional<TicketType> type = event.ticketType("VIP");
type.isEmpty()                                   // true if the event has no VIP
type.map(t -> t.capacity()).orElse(0)            // the capacity, or 0
```

## Steps

1. **Open the starter and run the tests.** `cd day-102-tickets-and-sales/starter`, then `../../mvnw test`. You should see `Tests run: 19, Failures: 2, Errors: 10` and `BUILD FAILURE`. The errors say `Not written yet: Day 102, step 4` and `step 5`.

2. **Check the ticket type's values.** Open `starter/core/src/main/java/com/boxoffice/core/TicketType.java`. In the compact constructor:
   - a `null` or blank `code` throws `new IllegalArgumentException("A ticket type needs a code")`;
   - a `capacity` below 0 throws `new IllegalArgumentException("Capacity can't be negative: " + capacity)`.

   Run `../../mvnw test -Dtest=TicketTypeTest -Dsurefire.failIfNoSpecifiedTests=false`. All 4 should pass.

3. **Count what is left.** Open `Gate.java` and find `remaining`. Look up the type with `event.ticketType(typeCode)`. If there is one, the answer is its `capacity()` minus `soldOf(typeCode)`. If there isn't, the answer is `0`. One line with `map(...).orElse(0)` does it. Do this before `sell`, because `sell` uses it.

4. **Sell a ticket.** In `Gate.sell`, check in this order and return as soon as one matches:
   - a `null` or blank `buyer`: `new SaleOutcome.Rejected("A ticket needs a buyer's name")`
   - the event doesn't sell that type (`event.ticketType(typeCode)` is empty): `new SaleOutcome.Rejected("No ticket type called " + typeCode)`
   - `remaining(typeCode) == 0`: `new SaleOutcome.SoldOut(typeCode)`
   - otherwise: make `new Ticket(nextCode(typeCode), typeCode, buyer.strip())`, add it to `sold`, and return `new SaleOutcome.Sold(ticket)`.

   `nextCode` counts what is already sold, so the first VIP is `VIP-0001`. Run `GateTest`: all 7 should pass.

5. **Describe the outcome.** Open `SaleOutcome.java`. In `describe()`, return a `switch (this)` with one case per record:
   - `Sold` gives `"Sold VIP-0001 to Wanjiku"` (the ticket's code and buyer)
   - `SoldOut` gives `"VIP is sold out"`
   - `Rejected` gives `"Rejected: "` plus the reason

6. **Run all the tests.** `../../mvnw test`. You should see 19 tests and `BUILD SUCCESS`.

## Run it and test it

From the day's `starter` folder:

```
cd day-102-tickets-and-sales/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=GateTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=GateTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **19 tests** (BoxOffice 3, Gate 7, SaleOutcome 3, SampleEvent 2, TicketType 4):

```
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## When you're stuck

- **`Expecting code to raise a throwable.`** in `TicketTypeTest`: the compact constructor doesn't throw yet. Check the messages match the test letter for letter.
- **`the switch expression does not cover all possible input values`**: a `case` is missing in `describe()`. You need all three: `Sold`, `SoldOut`, `Rejected`.
- **Every sale says `SoldOut`**: `remaining` still returns 0. Do step 3 first.
- **`countsDownWhatIsLeft` says `expected: 2 but was: 3`, or every ticket is `REG-0001`**: you made the ticket but forgot `sold.add(ticket)`, so the gate doesn't remember it.
- **`tidiesTheBuyersName` fails with `"  Otieno "`**: store `buyer.strip()`, not `buyer`.
- **`rejectsATypeTheEventDoesNotSell` says `SoldOut[typeCode=VVIP]`**: check for an unknown type before checking what is left.
- **`NoSuchElementException: No value present`**: you called `.get()` on an empty `Optional`. Check `isEmpty()` first, or use `map(...).orElse(...)`.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/Gate.java` (or `SaleOutcome.java`, `TicketType.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] `TicketType` refuses a blank code and a negative capacity
- [ ] `Gate.remaining` gives 0 for a type the event doesn't sell
- [ ] `Gate.sell` returns the right outcome for each of the four cases
- [ ] `describe()` uses a switch with no `default`
- [ ] All 19 tests pass
- [ ] Committed your work to Git

## Next

Tomorrow tickets get prices and times. You learn why money is never a `double`, and how the gate stops selling at 04:00 Nairobi time even on a laptop set to London.
