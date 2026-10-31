# Day 101: Project skeleton and CI

> Before the gate sells a single ticket, it needs a home: a project that builds the same way on every laptop.

Watch the video: *(not recorded yet)*

## The brief

Welcome to Part 2. For the next 30 days you build one app, **Box Office**: the program that runs on a laptop at the gate of a weekend event in Nairobi. There is a queue, a USB scanner, venue Wi-Fi that drops, and power that may go out. Our sample event is **Kilele Nights** at Ngong Racecourse, Saturday 14 November 2026, 18:00 to 04:00 the next morning. Regular tickets cost KES 1,500 (500 places) and VIP tickets KES 5,000 (50 places).

Everything runs on your own machine. No server, no cloud account.

Today is small on purpose. You set up the project the whole course builds on, learn how to run its tests, and write one tiny method: the line the gate screen shows when it opens.

```java
BoxOffice.welcome("Kilele Nights")      // "Box Office is open for Kilele Nights"
BoxOffice.welcome("  Kilele Nights  ")  // "Box Office is open for Kilele Nights"
BoxOffice.welcome("   ")                // throws IllegalArgumentException("An event needs a name")
```

## What you'll learn

- How every day of this course is laid out: `starter/` and `solution/`
- How to install Java 21 and check it works
- What the Maven wrapper is, and why you never install Maven yourself
- The difference between the parent `pom.xml` and a module's `pom.xml`
- How to run tests with JUnit and AssertJ, and read a failing test
- What CI is, and what this course's CI checks

## New words

- **JDK**: the Java Development Kit, the programs that compile and run Java (`javac` and `java`).
- **Maven**: a tool that downloads your libraries, compiles your code and runs your tests with one command.
- **Maven wrapper**: two small scripts (`mvnw` and `mvnw.cmd`) that download the right Maven for you the first time you run them.
- **pom.xml**: the file that tells Maven what the project is called, what it needs, and how to build it.
- **Module**: one part of a bigger project, with its own folder and its own `pom.xml`. Today there is one, called `core`.
- **Parent pom**: the `pom.xml` at the top, which lists the modules and picks one version of each library for all of them.
- **Dependency**: a library your code uses, such as JUnit.
- **AssertJ**: a library that makes test checks read like sentences: `assertThat(x).isEqualTo(y)`.
- **CI (continuous integration)**: a computer on the internet that builds and tests the project every time someone pushes code, so a broken change is caught early.

## What's already written

Every day folder has the same two parts:

```
day-101-project-skeleton/
├── README.md                 this file
├── starter/                  where you work: yesterday's finished code + today's TODOs
│   ├── pom.xml               the parent pom: lists the modules, pins library versions
│   └── core/                 the "core" module: the gate's rules
│       ├── pom.xml           the module pom: what core needs (JUnit, AssertJ)
│       └── src/
│           ├── main/java/com/boxoffice/core/
│           │   └── BoxOffice.java       the code you finish today
│           └── test/java/com/boxoffice/core/
│               └── BoxOfficeTest.java   today's tests, already written
└── solution/                 the same project, finished. Peek only when stuck.
```

The tests in `starter/` are already there, and they fail until you do the TODOs. When every test passes, you are done for the day. Tomorrow's `starter/` begins from today's `solution/`, so you never fall behind even if a day goes badly.

At the top of the repository there is also:

- `mvnw` and `mvnw.cmd`: the Maven wrapper (macOS/Linux and Windows).
- `.github/workflows/ci.yml`: the CI file.
- `tools/verify.sh`: the script CI runs.

## What you'll use

**The parent pom picks the versions.** In `starter/pom.xml`, `dependencyManagement` says which version of JUnit and AssertJ everyone uses:

```xml
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>${assertj.version}</version>
</dependency>
```

**The module pom asks for them, without a version.** `starter/core/pom.xml` just names them, with `<scope>test</scope>` so they are only used by tests, never shipped in the app.

**A JUnit test.** The `@Test` you met in Part 1. (The pom uses JUnit 6, which you write exactly like JUnit 5.)

```java
@Test
void welcomesTheEventByName() {
    assertThat(BoxOffice.welcome("Kilele Nights"))
            .isEqualTo("Box Office is open for Kilele Nights");
}
```

**Checking that something throws.** `assertThatThrownBy` runs the code and checks the exception and its message:

```java
assertThatThrownBy(() -> BoxOffice.welcome("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("An event needs a name");
```

**`isBlank()` and `strip()`.** `"   ".isBlank()` is `true`. `"  Kilele  ".strip()` is `"Kilele"`.

## Steps

1. **Install Java 21 and check it.** You need a JDK, version 21 or newer. Pick one way:
   - Any system: download **Temurin 21** from [adoptium.net](https://adoptium.net) and run the installer.
   - Ubuntu/Debian: `sudo apt install openjdk-21-jdk`
   - macOS with Homebrew: `brew install --cask temurin@21`
   - Windows: `winget install EclipseAdoptium.Temurin.21.JDK`

   Open a **new** terminal and type `java -version`. You should see `21` (or higher) in the first line, for example `openjdk version "21.0.9"`. You do not need to install Maven: the wrapper does that.

   The wrapper uses `JAVA_HOME` if it is set, and the `java` on your PATH if not. If `java -version` says 21 but the build complains, check `JAVA_HOME` (`echo $JAVA_HOME` on macOS/Linux, `echo %JAVA_HOME%` in Windows cmd, `$env:JAVA_HOME` in PowerShell). It must point at the JDK 21 folder, or be empty.

2. **Open the starter and run the tests.** Open `day-101-project-skeleton/starter` in your editor (IntelliJ IDEA, VS Code, or any you like). Then, in a terminal:

   ```
   cd day-101-project-skeleton/starter
   ../../mvnw test
   ```

   The first run downloads Maven and the libraries, so it needs internet and takes a minute. Do it at home, not at the venue. You should see `BUILD FAILURE` and three failing tests, each saying `Not written yet: Day 101, step 4`. That is correct: the tests are waiting for you.

3. **Look around.** Open `starter/pom.xml` and find the `<modules>` list (just `core` for now) and the version numbers. Open `starter/core/pom.xml` and see that it names its parent and asks for JUnit and AssertJ. Open `BoxOfficeTest.java` and read the three tests. They are the brief, written as code.

4. **Write `BoxOffice.welcome`.** Open `starter/core/src/main/java/com/boxoffice/core/BoxOffice.java`. Replace the `throw new UnsupportedOperationException(...)` line with:
   - if `eventName` is `null` or blank, throw `new IllegalArgumentException("An event needs a name")`;
   - otherwise return `NAME + " is open for " + eventName.strip()`.

   Check for `null` first: calling `isBlank()` on `null` crashes with a `NullPointerException`.

5. **Run all the tests again.** `../../mvnw test`. You should see `Tests run: 3, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

## Run it and test it

From the day's `starter` folder:

```
cd day-101-project-skeleton/starter
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

To run one test class only:

```
../../mvnw test -Dtest=BoxOfficeTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=BoxOfficeTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

(On Windows PowerShell, keep the quotes: without them PowerShell splits the words at the dots.)

Success looks like this, with **3 tests**:

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**What CI does.** When this repository is pushed to GitHub, the file `.github/workflows/ci.yml` tells GitHub to start a fresh Linux machine, install Java 21 and run `tools/verify.sh`. For every day, that script checks three things: `starter/` and `solution/` have the same tests, the solution passes them, and the untouched starter compiles but fails them. You can run it yourself from the top of the repository (macOS/Linux, or Git Bash on Windows):

```
tools/verify.sh 101
```

It checks the course as it was given to you. Once you have finished a day, it will say your starter "passes its tests". That is fine: it means you did the work.

## When you're stuck

- **`The java/javac command does not exist in PATH nor is JAVA_HOME set, so mvnw cannot run.`**: Java is not installed, or the terminal was open before you installed it. Close the terminal, open a new one, and try `java -version`.
- **`The JAVA_HOME environment variable is not defined correctly`**: `JAVA_HOME` points at a folder with no JDK in it. Fix it to the JDK 21 folder, or remove it.
- **`release version 21 not supported`**: Maven found an older Java (often through `JAVA_HOME`). Point `JAVA_HOME` at JDK 21.
- **`../../mvnw: No such file or directory`**: you are not in `day-101-project-skeleton/starter`. Run `pwd` (or `cd` on Windows) to see where you are.
- **`Permission denied` running `../../mvnw`**: run `chmod +x ../../mvnw` once.
- **The first run hangs on "Downloading"**: it needs internet the first time. Later runs work offline.
- **`expected: "Box Office is open for Kilele Nights" but was: "Box Office is open for   Kilele Nights  "`**: you forgot `strip()`.
- **`NullPointerException` in `welcome`**: check for `null` before calling `isBlank()`.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/BoxOffice.java`, read it once, close it, then write your own from memory.

## Checklist

- [ ] `java -version` shows 21 or newer
- [ ] Ran `../../mvnw test` in `starter/` and saw it fail first
- [ ] Can say what the parent pom does and what the `core` pom does
- [ ] Wrote `BoxOffice.welcome`
- [ ] All 3 tests pass with `BUILD SUCCESS`
- [ ] Can say in one sentence what CI is
- [ ] Committed your work to Git

## Next

Tomorrow the gate learns to sell. You model events, ticket types and tickets with records, and find out what happens when Wanjiku asks for the last VIP ticket and Kamau asks right after her.
