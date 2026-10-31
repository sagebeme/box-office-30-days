# Day 106: The event dashboard

> The person at the gate shouldn't have to type `report` to find out how many VIP tickets are left. They should just look.

Watch the video: *(not recorded yet)*

## The brief

Yesterday the gate got a command line. That's fine for you, but not for the person on duty at 21:00 with a queue in front of them and a phone torch in their face. They need a **window** that shows the night at a glance: which event, when it runs, the takings so far, and how many of each ticket are left.

Today you start the third module, `desktop`, with JavaFX. You met JavaFX in Part 1. Good news: there you downloaded the JavaFX SDK by hand and typed a long `--module-path` to run anything. Not any more. Here JavaFX is just a Maven dependency like JUnit or picocli, and Maven picks the right files for Windows, macOS or Linux by itself.

The window is already laid out. You write the part that decides **what it shows** (the model), then connect two labels and a list to it.

```
┌──────────────────────────────────────────────────────────────┐
│ Kilele Nights                                       Takings  │
│ Sat 14 Nov, 18:00 to Sun 15 Nov, 04:00            KES 5,000  │
├──────────────────────────────────────────────────────────────┤
│ Tickets                                                      │
│  Regular · KES 1,500 · 500 of 500 left                       │
│  VIP · KES 5,000 · 49 of 50 left                             │
└──────────────────────────────────────────────────────────────┘
```

## What you'll learn

- How to add JavaFX to a Maven project, with no SDK to install
- How an **FXML** file, its **controller** and `@FXML` fields fit together
- What `initialize()` is for, and when JavaFX calls it
- How a **controller factory** lets you hand the controller what it needs
- Why the screen and the model are kept apart, and how that lets you test a window without opening it
- How **properties** and **binding** keep a label up to date by themselves
- Why the jar starts from a tiny `Main` class instead of the `Application` class

## New words

- **JavaFX**: Java's library for windows, buttons, labels and lists.
- **FXML**: an XML file that describes what's in a window and where, instead of building it in Java code.
- **Controller**: the Java class that goes with an FXML file. It gets the window's parts and wires them up.
- **`@FXML`**: an annotation that says "fill this field in from the FXML file". The field's name matches an `fx:id` in the FXML.
- **`initialize()`**: a method in the controller that JavaFX calls once all the `@FXML` fields are filled in.
- **Controller factory**: a small function you give the FXML loader, so *you* make the controller (and can pass things into it).
- **Model**: a plain class that holds what the screen should show. It never touches a label or a button.
- **Property**: a value that tells anyone watching when it changes, like `StringProperty`.
- **Binding**: a link that keeps one property equal to another, automatically.
- **Observable list**: a list that tells anyone watching when items are added, removed or replaced.
- **CSS**: style rules (sizes, colours, padding) kept in their own file, the same idea as on a web page.
- **Scene Builder**: a free program for laying out FXML files by dragging things around.

## What's already written

From Days 101–105: `core` (the rules and SQLite storage) and `cli` (the command line), both working, with tests.

New today:

- The parent `pom.xml` lists a third module, `desktop`, and pins `javafx.version` (21.0.7).
- `desktop/pom.xml`: depends on `core`, `javafx-controls` and `javafx-fxml`, and builds `desktop/target/boxoffice-desktop.jar` with the shade plugin. Complete.
- `Main.java`: starts the app. Complete (see "What you'll use" for why it exists).
- `BoxOfficeApp.java`: opens the store, makes the model, loads the window. Complete.
- `dashboard.fxml`: the layout, with `fx:id`s `title`, `when`, `takings` and `types`. Complete.
- `box-office.css`: big, plain text you can read from a step back. Complete.
- `TypeSummary.java`: one line of the ticket list, `VIP · KES 5,000 · 49 of 50 left`. Complete.
- `DashboardModel.java`: the properties are there. You write `refresh()`.
- `DashboardController.java`: `title` is already bound. You bind the rest.
- `DashboardModelTest.java`: 7 tests that check the model with no window at all.

## What you'll use

**JavaFX from Maven.** In `desktop/pom.xml`, with no version (the parent pins it):

```xml
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
</dependency>
```

**`fx:id` matches `@FXML`.** The FXML says `<Label fx:id="when" .../>`. The controller has a field with the same name, and the loader fills it in:

```java
@FXML
private Label when;
```

**A property the screen can watch but not change.** The model keeps a `ReadOnlyStringWrapper` for itself and hands out only the read-only side:

```java
private final ReadOnlyStringWrapper takings = new ReadOnlyStringWrapper("");

public ReadOnlyStringProperty takingsProperty() {
    return takings.getReadOnlyProperty();
}
```

The model calls `takings.set("KES 5,000")`. Anyone watching hears about it.

**Binding a label to it.** After this line the label always shows what the property holds. You never set the label's text again:

```java
title.textProperty().bind(model.titleProperty());
```

**Replacing what's in an observable list.** `setAll` swaps everything in one go, and a `ListView` showing that list redraws itself:

```java
types.setAll(List.of(summaryA, summaryB));
```

**Why `Main` and not `BoxOfficeApp`.** If the class named in a jar's manifest extends `Application`, Java checks for JavaFX on the module path and stops with `JavaFX runtime components are missing`, even though JavaFX is right there inside the jar. A plain `Main` that just calls `Application.launch(BoxOfficeApp.class, args)` skips that check. Open `Main.java`: it's three lines.

## Steps

1. **Open the starter and run the tests.** `cd day-106-event-dashboard/starter`, then `../../mvnw test`. The first run downloads JavaFX, so it needs internet. `core` and `cli` pass. In `desktop` you should see `Tests run: 7, Failures: 0, Errors: 5` and `BUILD FAILURE`, with `Not written yet: Day 106, step 4`.

2. **Look at the new module.** Open `starter/desktop/pom.xml` and find the two JavaFX dependencies and the shade plugin's `mainClass` (it says `Main`). No `--module-path` anywhere: Maven does it.

3. **Follow one label from FXML to screen.** Open `dashboard.fxml` and find `<Label fx:id="title" .../>`. Open `DashboardController.java` and find the `@FXML private Label title;` field and the line in `initialize()` that binds it. Then open `BoxOfficeApp.java` and read `loadDashboard`: `setControllerFactory` makes the controller with the model passed in. Without it, the loader would call an empty constructor and the controller would have no model.

4. **Write `DashboardModel.refresh()`.** Open `starter/desktop/src/main/java/com/boxoffice/desktop/DashboardModel.java`. `Event event = gate.event();` is there. Replace the `throw` with:
   - `title.set(...)` the event's name;
   - `when.set(...)` the start and the end, each through `NairobiTime.format(event.startsAt().toInstant())` (and the same for `endsAt()`), with `" to "` between them;
   - `takings.set(...)` with `gate.takings().format()`;
   - `types.setAll(...)` one `TypeSummary` per ticket type, in the event's order. `summarise(type)` below makes one, so a stream with `.map(this::summarise).toList()` makes them all.

   Run `../../mvnw test`. All 7 desktop tests should pass, with `BUILD SUCCESS`.

5. **Build and open the window.** `../../mvnw package`, then `java -jar desktop/target/boxoffice-desktop.jar`. A window called "Box Office" opens. You should see "Kilele Nights" at the top, but no times, no takings and an empty ticket list. That's right: the model has the values, but nothing on screen is bound to them yet. Close the window.

6. **Bind the rest.** Open `DashboardController.java`. In `initialize()`, under the `title` line:
   - bind `when`'s text to `model.whenProperty()`, the same way as `title`;
   - bind `takings`' text to `model.takingsProperty()`;
   - show the list: `types.setItems(model.types())`.

   **No test checks this file.** Tests check the model; this step you check with your eyes. Build and run again (step 5). Now you should see the times, `KES 0` under Takings, and the two ticket types with `500 of 500 left` and `50 of 50 left`.

7. **Sell one and look again.** Close the window. Sell a VIP ticket with yesterday's CLI on the same file: `java -jar cli/target/boxoffice.jar sell VIP "Wanjiku Kamau"`. Open the window again. Takings say `KES 5,000` and VIP says `49 of 50 left`. Both programs read the same `gate.db`.

8. **Optional: change the look.** Open `box-office.css` and change `.title`'s `-fx-font-size` to `40px`. Build and run. If you'd rather drag things around, install Scene Builder from [gluonhq.com/products/scene-builder](https://gluonhq.com/products/scene-builder/) and open `dashboard.fxml` in it. Keep the `fx:id`s as they are, or the controller can't find its fields.

## Run it and test it

From the day's `starter` folder:

```
cd day-106-event-dashboard/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=DashboardModelTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=DashboardModelTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **60 tests** in `core`, **8** in `cli` and **7** in `desktop`, **75** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

**Build and run the window.** `package` runs the tests, then builds both jars. The desktop jar is about 25 MB, because JavaFX is inside it. On Windows, write `desktop\target\boxoffice-desktop.jar`.

```
../../mvnw package
java -jar desktop/target/boxoffice-desktop.jar
java -jar desktop/target/boxoffice-desktop.jar --db=other.db
```

The window uses `gate.db` in the folder you run it from, the same file as the CLI. `--db=other.db` (with an `=`) picks another file.

You'll see this in the terminal when it starts. It's harmless:

```
WARNING: Unsupported JavaFX configuration: classes were loaded from 'unnamed module @17f93b8d'
```

JavaFX would like to be loaded as modules, and a shaded jar loads it as plain classes. Everything works the same. The number after `@` changes every time.

To build a jar while some tests still fail, skip them: `../../mvnw package -DskipTests`. Before step 4 the window won't open, though: it stops with `Exception in Application start method` and, further down, `Not written yet: Day 106, step 4`, because `loadDashboard` calls `refresh()`.

## When you're stuck

- **`Not written yet: Day 106, step 4`**: `refresh()` still has its `throw`. Do step 4.
- **`expected: "Sat 14 Nov, 18:00 to Sun 15 Nov, 04:00"`** but the end is missing or the spaces are wrong: put exactly `" to "` between the two times, and format `endsAt()` too.
- **`showsEachTicketTypeWithItsPriceAndWhatIsLeft` fails with the types in a different order**: keep the event's order (`event.ticketTypes()`); don't sort.
- **The window shows the title, but no times, no takings and no list**: step 6 isn't done, or you didn't rebuild after it. Run `../../mvnw package` again.
- **`JavaFX runtime components are missing, and are required to run this application`**: something is starting `BoxOfficeApp` directly. Start the jar with `java -jar`, which uses `Main`. In your editor, run `Main`, not `BoxOfficeApp`.
- **`LoadException` ... `dashboard.fxml`** with a `NullPointerException` on a label: a field name and an `fx:id` don't match (maybe after Scene Builder). They must be spelled the same.
- **`Error: Unable to access jarfile desktop/target/boxoffice-desktop.jar`**: run `../../mvnw package` first, from the `starter` folder. If the tests fail, no jar is built.
- **The CLI sale doesn't show up**: the window reads the database when it opens. Close it and open it again. (Tomorrow it updates after every sale it makes.)
- **Sales say 19:00 on 14 November, whatever today is?** Like the CLI, the app uses `SampleEvent.practiceClock()` from Day 105: a clock set to the night of the event, so practice sales aren't rejected for being after it ended.
- **Still stuck?** Open `solution/desktop/src/main/java/com/boxoffice/desktop/DashboardModel.java` (or `DashboardController.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say why JavaFX no longer needs a downloaded SDK
- [ ] Can point to an `fx:id` in the FXML and the `@FXML` field it fills
- [ ] Can explain what the controller factory in `loadDashboard` is for
- [ ] Wrote `DashboardModel.refresh()`
- [ ] All 75 tests pass
- [ ] Bound `when`, `takings` and the ticket list, and saw them in the window
- [ ] Can say why the jar starts from `Main`
- [ ] Committed your work to Git (not `gate.db`)

## Next

The dashboard shows totals, but not the sales themselves. Tomorrow you add a live table of every ticket sold, newest at the top, and a Sell button so the window can sell on its own.
