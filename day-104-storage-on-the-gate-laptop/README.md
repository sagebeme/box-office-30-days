# Day 104: Storage on the gate laptop

> A power cut at 22:00 must not lose the sales from 21:00.

Watch the video: *(not recorded yet)*

## The brief

Until now the gate kept its sales in a `List` in memory. Close the app, or let the laptop battery die, and every sale is gone. At a real gate that means arguments: Otieno paid, but the gate has no record of his ticket.

Today the gate writes every sale to a **database file** on the laptop's own disk, the moment it happens. We use **SQLite**, which is a whole database in one file, with no server to install and no internet needed. Open the file again tomorrow and the sales are still there.

Most of the database code is written for you. You write three methods on `TicketStore`: one to save a sale, one to read the sales back, and one to count them.

```java
try (TicketStore store = TicketStore.open(Path.of("gate.db"))) {
    store.saveEvent(SampleEvent.kileleNights());
    store.recordSale("kilele-2026", new Ticket("REG-0001", "REG", "Ng'ang'a", Money.ofKes(1_500), ninePm));
}
// ... power cut, laptop restarts ...
try (TicketStore store = TicketStore.open(Path.of("gate.db"))) {
    store.tickets("kilele-2026")          // [Ticket[code=REG-0001, ..., buyer=Ng'ang'a, ...]]
    store.countSold("kilele-2026", "REG") // 1
}
```

## What you'll learn

- What an embedded database is, and why it suits a gate laptop
- How to talk to a database from Java with **JDBC**
- Why you always use a `PreparedStatement` with `?`, never glue values into the SQL yourself
- How **try-with-resources** closes connections and statements for you
- How **Flyway migrations** create and change tables safely, and why you never edit one that has run
- How JUnit's `@TempDir` gives each test a fresh, empty folder

## New words

- **Database**: a program (or here, a file) that stores data in tables and lets you search it.
- **Table, row, column**: a table is like a sheet in a spreadsheet; each row is one record (one ticket) and each column is one field (the buyer).
- **SQL**: the language you use to ask a database for things: `insert`, `select`, `count`.
- **SQLite**: a small database that lives in a single file inside your app; there is no separate server.
- **JDBC**: Java's standard way of talking to any database.
- **Connection**: an open line to the database; the app opens one and sends SQL down it.
- **`PreparedStatement`**: SQL with `?` placeholders that you fill in with values, safely.
- **`ResultSet`**: the rows that come back from a `select`, read one at a time with `next()`.
- **SQL injection**: when text typed by a person ends up being run as SQL, by accident or on purpose.
- **try-with-resources**: `try (...) { }`, which closes whatever you open in the brackets, even if something goes wrong.
- **Migration**: a numbered SQL file that changes the tables; each runs once, in order.
- **Flyway**: the library that runs the migrations and remembers which ones it has already run.
- **Checked exception**: an exception Java makes you catch or declare, like `SQLException`.

## What's already written

From Days 101–103: everything up to money and time, working, with tests.

Changed today, already done:

- `Gate` now takes a store instead of an event: `new Gate(store, "kilele-2026", clock)`. It looks the event up in the store, calls `store.recordSale` on every sale, and uses `store.countSold` and `store.tickets` instead of its old list. So `GateTest` only passes once your three methods work.
- The poms add three libraries: `sqlite-jdbc` (the database), `flyway-core` (migrations) and `slf4j-nop` (keeps Flyway's log lines quiet).

New files:

- `core/src/main/resources/db/migration/V1__create_tables.sql`: the three tables, `events`, `ticket_types` and `tickets`. Complete.
- `StoreException.java`: a plain (unchecked) exception that wraps `SQLException`. Complete.
- `TicketStore.java`: `open`, `saveEvent`, `findEvent` and `close` are complete. You write `recordSale`, `tickets` and `countSold`.
- `TicketStoreTest.java`: 7 tests, including the Ng'ang'a apostrophe test.

## What you'll use

**A prepared statement.** Write `?` where each value goes, then fill them in by number, starting at 1:

```java
try (PreparedStatement insert = connection.prepareStatement(
        "insert into events (id, name, starts_at, ends_at) values (?, ?, ?, ?)")) {
    insert.setString(1, event.id());
    insert.setString(2, event.name());
    ...
    insert.executeUpdate();
}
```

**Why not glue the text in?** Try it with the name Ng'ang'a:

```java
"insert into tickets (buyer) values ('" + buyer + "')"
// becomes: values ('Ng'ang'a')   -> [SQLITE_ERROR] ... (near "ang": syntax error)
```

The `'` in the name ends the SQL text early. With `?`, the driver sends the name separately, so any name is safe. The same trick stops someone typing SQL into a name field on purpose.

**Reading rows back.** `executeQuery()` gives a `ResultSet`. Call `next()` to move to each row, and read columns by name:

```java
try (ResultSet row = select.executeQuery()) {
    while (row.next()) {
        String code = row.getString("code");
        long cents = row.getLong("price_cents");
    }
}
```

**try-with-resources.** Everything opened in `try ( ... )` is closed at the end, in reverse order, even if an exception is thrown. A gate that forgets to close things runs out of them by midnight.

**Turning a checked exception into an unchecked one.** JDBC throws `SQLException`. The store wraps it so callers aren't forced to catch it:

```java
} catch (SQLException e) {
    throw new StoreException("Couldn't record ticket " + ticket.code(), e);
}
```

**A migration.** Flyway finds files named `V<number>__<description>.sql` in `db/migration` and runs each new one once, in order. It writes down what it ran in its own table, `flyway_schema_history`. Never edit a migration after it has run on any gate: Flyway notices the file changed and refuses to start. To change the tables later, add `V2__...sql`.

**A fresh folder per test.** JUnit fills this field with a new empty folder for each test and deletes it afterwards, so tests never share a database:

```java
@TempDir
Path folder;
```

## Steps

1. **Open the starter and run the tests.** `cd day-104-storage-on-the-gate-laptop/starter`, then `../../mvnw test`. The first run downloads SQLite and Flyway, so it needs internet. You should see `Tests run: 43, Failures: 0, Errors: 15` and `BUILD FAILURE`, with errors pointing at steps 4, 5 and 6.

2. **Read the migration.** Open `starter/core/src/main/resources/db/migration/V1__create_tables.sql`. Find the `tickets` table and its six columns: `event_id`, `code`, `type_code`, `buyer`, `price_cents`, `sold_at`. Note that money is stored as whole cents and times as text like `2026-11-14T18:00:00Z`.

3. **Read the store.** Open `starter/core/src/main/java/com/boxoffice/core/TicketStore.java`. Read `saveEvent` (how to insert) and `findEvent` (how to read rows back). Your three methods follow the same patterns.

4. **Record a sale.** In `recordSale`:
   - inside `try ( ... )`, prepare `insert into tickets (event_id, code, type_code, buyer, price_cents, sold_at) values (?, ?, ?, ?, ?, ?)`;
   - fill the six `?` in that order: `setString` for text, `setLong` for `ticket.price().cents()`, and `setString` with `ticket.soldAt().toString()` for the time;
   - call `executeUpdate()`;
   - catch `SQLException` and throw `new StoreException("Couldn't record ticket " + ticket.code(), e)`.

5. **Read the tickets back.** In `tickets(eventId)`:
   - prepare `select code, type_code, buyer, price_cents, sold_at from tickets where event_id = ? order by rowid` (`rowid` is the order the rows went in, so the oldest sale comes first);
   - set the one `?` to `eventId`, run `executeQuery()`, and open the `ResultSet` in its own `try ( ... )`;
   - for each row, add a `new Ticket(...)` to a list. Use `Money.ofCents(row.getLong("price_cents"))` for the price and `Instant.parse(row.getString("sold_at"))` for the time;
   - return the list, and catch `SQLException` as `new StoreException("Couldn't read the tickets for " + eventId, e)`.

6. **Count sold tickets.** In `countSold(eventId, typeCode)`:
   - prepare `select count(*) from tickets where event_id = ? and type_code = ?` and fill both `?`;
   - the answer is one row with one column: call `row.next()`, then return `row.getInt(1)`;
   - catch `SQLException` as `new StoreException("Couldn't count " + typeCode + " tickets", e)`.

7. **Run all the tests.** `../../mvnw test`. You should see 43 tests and `BUILD SUCCESS`. `GateTest.keepsItsSalesInTheStore` passing means a second gate on the same file sees the first gate's sale: the power-cut problem is solved.

## Run it and test it

From the day's `starter` folder:

```
cd day-104-storage-on-the-gate-laptop/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=TicketStoreTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=TicketStoreTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **43 tests** (BoxOffice 3, Gate 11, Money 8, NairobiTime 4, SaleOutcome 3, SampleEvent 3, TicketStore 7, TicketType 4):

```
[INFO] Tests run: 43, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The tests make their `gate.db` files inside `@TempDir` folders, so nothing is left behind in your project.

## When you're stuck

- **`GateTest` fails with `Not written yet: Day 104, step 5` or `step 6`**: the gate now uses your store methods. Finish steps 4 to 6 and those tests pass too.
- **`StoreException: Couldn't record ticket ...`**: the real reason is in the `Caused by:` line just below it in the output. Read that line.
- **`[SQLITE_ERROR] SQL error or missing database (no such column: byer)`**: a column name is spelled wrong. Copy them from `V1__create_tables.sql`.
- **`[SQLITE_ERROR] ... (near "ang": syntax error)`**: you glued the buyer's name into the SQL. Use `?` and `setString`.
- **`column 0 out of bounds [1,1]`**: JDBC counts from 1, not 0. Use `setString(1, ...)` and `getInt(1)`.
- **`no such column: 'price_cents'`** when reading: that column is missing from your `select` list.
- **`tickets` comes back empty**: did you call `executeUpdate()` in `recordSale`? Without it nothing is written.
- **`readsTicketsBackInTheOrderTheyWereSold` has them the wrong way round**: add `order by rowid`.
- **`DateTimeParseException`**: store the time with `ticket.soldAt().toString()` and read it with `Instant.parse(...)`, so the two match.
- **`Validate failed: Migrations have failed validation`** (when you run the app on a real `gate.db` later): someone edited `V1__create_tables.sql` after it ran. Put the file back, and add a `V2` file for the change.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/TicketStore.java`, read it once, close it, then write your own from memory.

## Checklist

- [ ] Can explain why the gate uses SQLite and not a database server
- [ ] Every SQL value goes in through a `?`, never glued in
- [ ] Every `PreparedStatement` and `ResultSet` is opened in `try ( ... )`
- [ ] Can say why you never edit `V1__create_tables.sql` once it has run
- [ ] The Ng'ang'a test passes
- [ ] All 43 tests pass
- [ ] Committed your work to Git

## Next

Tomorrow the gate gets a command line. You add a second module, import the organiser's guest list from an Excel CSV, and print the end-of-night sales report from one runnable jar.
