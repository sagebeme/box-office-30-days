# Box Office in 30 Days

Java, Part 2. One real piece of software, built over 30 days by people who already know Java: **Box Office**, an offline-first gate app for weekend events. It keeps selling when the Wi-Fi drops, never half-saves a sale when the power goes, checks tickets in with a USB scanner, and (later in the course) syncs to a server, takes M-Pesa and ships as an installer.

Every day ships with tests. When a day's tests pass, the day is done, and the app still runs.

- **Days 101–130**, after Part 1 (Days 1–100: [100-java-basics-advance](https://github.com/sagebeme/100-java-basics-advance))
- **One app**, tested every day
- **About 2–3 hours a day**
- **Runs on your own computer.** No cloud account and no web hosting. The only web phase is Days 119–124.

## The app, and the gate it has to survive

A Saturday night at Ngong Racecourse: **Kilele Nights**, 18:00 to 04:00. One laptop at the gate, a queue, a USB scanner, venue Wi-Fi that drops every few minutes, and power that may go out mid-sale. Regular tickets are KES 1,500 (500 places), VIP KES 5,000 (50 places).

Every requirement the gate has becomes a day:

| The gate needs to... | Days |
|---|---|
| Remember every sale on its own disk, with no server | 104 |
| Let the organiser's guest list in, and print the night's report | 105 |
| Be fast at the gate: keyboard only, and USB scanners that type like a keyboard | 108, 110 |
| Never freeze, even on a slow old laptop | 109 |
| Never half-save a sale when the power goes | 111 |
| Work offline and sync when the connection comes back | 115–117 |
| Let two gates agree, and let the server settle the last ticket | 117, 120 |
| Take M-Pesa from fans buying online | 122 |
| Install like real software and update itself | 128–130 |

## What you need

| | |
|---|---|
| **A JDK, version 21 or newer** | Temurin 21 from [adoptium.net](https://adoptium.net), or `sudo apt install openjdk-21-jdk`, `brew install --cask temurin@21`, `winget install EclipseAdoptium.Temurin.21.JDK`. Check with `java -version`. |
| **Maven** | Nothing to install. The Maven wrapper (`mvnw`, `mvnw.cmd`) downloads the right version the first time you run it. |
| **An editor** | IntelliJ IDEA Community, VS Code with the Java extensions, or Eclipse. Open a day's `starter/pom.xml` as a project. |
| **Git** | To keep your own daily commits. |
| **Optional** | [Scene Builder](https://gluonhq.com/products/scene-builder/) for dragging FXML layouts around (Day 106 on), a USB barcode scanner (Day 110). |

JavaFX comes from Maven like any other library. Unlike Part 1, there's no JavaFX SDK to download and no `--module-path` to type.

## Getting started

```bash
git clone https://github.com/sagebeme/box-office-30-days.git
```

```bash
cd box-office-30-days/day-101-project-skeleton/starter
```

```bash
../../mvnw test
```

On Windows, run `..\..\mvnw.cmd test` instead. The first run downloads Maven and the libraries, so give it a minute. Some tests fail: that's today's work. Then open [Day 101](day-101-project-skeleton/README.md) and follow it.

## How each day works

```
box-office-30-days/
├── mvnw, mvnw.cmd                   the Maven wrapper: no Maven install
├── day-104-storage-on-the-gate-laptop/
│   ├── README.md                    the brief, new words, steps, help when stuck
│   ├── starter/                     you work here
│   │   ├── pom.xml                  the parent pom: modules and library versions
│   │   └── core/                    the gate's rules and storage
│   │       └── src/test/...         today's tests, already written
│   └── solution/                    the same project, finished
├── tools/verify.sh                  checks every day, the way CI does
└── .github/workflows/ci.yml         CI: runs tools/verify.sh on every push
```

**`starter/` is yesterday's finished app plus today's TODOs.** So you can join at any day, and a bad day never leaves you behind: tomorrow starts from the solution, not from your copy.

**The routine:**

1. **Read the day's README to the end** before writing code. The "New words" and "When you're stuck" sections answer most questions before you have them.
2. **Run the tests first** and watch them fail. Each failure names the step it belongs to: `Not written yet: Day 104, step 5`.
3. **Do the steps in order.** Every TODO in the code says what goes there.
4. **Run the tests until they're green**: `../../mvnw test` from the day's `starter/` folder.
5. **Run the app** when the day has one, and commit your work.

To run just one test class:

```bash
../../mvnw test -Dtest=MoneyTest -Dsurefire.failIfNoSpecifiedTests=false
```

## Running the app

From a day's folder, once its tests pass (Day 105 on for the CLI, Day 106 on for the window):

```bash
../../mvnw package
```

```bash
java -jar cli/target/boxoffice.jar report
```

```bash
java -jar desktop/target/boxoffice-desktop.jar
```

Both read and write `gate.db` in the folder you run them from (`--db=other.db` picks another file). To try a starter before its tests pass, build it with `../../mvnw package -DskipTests`.

The app and the CLI run on a *practice clock* set to the night of the event, so the gate still sells whatever today's date is. A real gate would use the real clock: Day 103 explains why the clock is passed in.

## The course

Each line is the day's milestone, then the idea it teaches. Days 101–110 are ready; the rest are planned.

### Phase 1 · Days 101–105 · Foundations for real software

The project skeleton, the domain model and local storage, before any window appears.

- [Day 101](day-101-project-skeleton): Project skeleton and CI (Maven wrapper, modules, JUnit, AssertJ)
- [Day 102](day-102-tickets-and-sales): Tickets, events and sale outcomes (records, sealed interfaces, pattern-matching switch)
- [Day 103](day-103-money-and-time): Money and time done right (KES in cents, `java.time`, Africa/Nairobi)
- [Day 104](day-104-storage-on-the-gate-laptop): Storage on the gate laptop (embedded SQLite, Flyway migrations)
- [Day 105](day-105-guest-list-and-report): Guest-list import and sales report (picocli, CSV in, report out)

### Phase 2 · Days 106–113 · The desktop app

A JavaFX app gate staff can run all night without it freezing, losing a sale or needing a mouse.

- [Day 106](day-106-event-dashboard): The event dashboard (JavaFX via Maven, FXML, Scene Builder)
- [Day 107](day-107-live-sales-table): Live sales table (property binding, `TableView`)
- [Day 108](day-108-sell-without-the-mouse): Sell a ticket without touching the mouse (keyboard-first forms, validation)
- [Day 109](day-109-never-freeze-the-screen): Never freeze the screen (`Task`, the JavaFX application thread)
- [Day 110](day-110-qr-tickets-and-scanner-check-in): QR tickets and USB scanner check-in (QR generation, scanner input as keystrokes)
- Day 111: Power-cut proof (transactions, SQLite WAL, crash recovery)
- Day 112: Receipts and PDF exports (`PrinterJob`, PDF generation)
- Day 113: Box Office v1 (TestFX headless UI tests)

### Phase 3 · Days 114–118 · Concurrency and the network

- Day 114: Bulk SMS reminders (virtual threads, Africa's Talking sandbox)
- Day 115: A client that survives bad Wi-Fi (`HttpClient` timeouts, retries, backoff)
- Day 116: Queue now, sync later (the outbox pattern)
- Day 117: Two gates, no internet (LAN sync)
- Day 118: The night we oversold (race conditions, locks, atomics, many-thread tests)

### Phase 4 · Days 119–124 · Sync server and ticket site (the one web phase)

- Day 119: Sync API with device login (Spring Boot, tokens per gate laptop)
- Day 120: Two gates sell the last ticket (conflict resolution, idempotency)
- Day 121: Public ticket site (Thymeleaf + htmx)
- Day 122: Pay with M-Pesa (Daraja sandbox, STK push, callback)
- Day 123: Security pass (rate limits, secrets, input validation)
- Day 124: Server deployed (Docker, per-environment config, health checks)

### Phase 5 · Days 125–127 · AI in the app

- Day 125: Ask about tonight's sales (the Anthropic Java SDK, streaming)
- Day 126: An assistant that can look things up (tool use over Box Office data)
- Day 127: Evals (test the AI feature in JUnit)

### Phase 6 · Days 128–130 · Ship it

- Day 128: Installers small enough for a data bundle (`jpackage`, `jlink`, WiX on Windows)
- Day 129: A release pipeline (CI builds for Windows, Mac and Linux, GitHub Releases, code signing)
- Day 130: Box Office 1.0 (auto-update, release notes, then one feature of your own)

## Getting unstuck

1. Read the test that fails. Its name says what the gate should do (`aTicketWorksOnlyOnce`), and AssertJ prints what it expected next to what it got.
2. Check the day's **When you're stuck** section for the exact error message.
3. Read the TODO comment again, slowly: it usually names the method to call.
4. Still stuck? Open the same file in `solution/`, read it once, close it, and write your own version from memory.

## For contributors

Every day's starter and solution must share the same tests. Each solution must pass them, and each untouched starter must compile but fail them. `tools/verify.sh` checks all three (it needs bash: on Windows, use Git Bash or WSL):

```bash
tools/verify.sh
```

```bash
tools/verify.sh 104 105
```

CI runs the same script on every push and pull request.

The course pins Java 21, the LTS most machines already have; everything also runs on Java 25.
