# Day 128: Installers small enough for a data bundle

> 16:30, Ngong Racecourse. Otieno has a new laptop for gate B, no Java on it, and one bar of signal. He needs Box Office on it before 18:00.

Watch the video: *(not recorded yet)*

## The brief

Until now, every gate laptop needed Java installed, then `java -jar desktop/target/boxoffice-desktop.jar` typed into a terminal, from the right folder. That's fine for you. It's not fine for Otieno, at a racecourse, two hours before the gates open.

Today Box Office gets real installers, the kind you double-click:

- a `.deb` for Ubuntu and Debian;
- an `.msi` for Windows;
- a `.dmg` for a Mac.

Each one carries its own small Java inside, so the laptop needs nothing else. And each one is small enough to download on a phone's data bundle. The `.deb` is 49 MB:

```
$ ls -l desktop/target/installer/
-rw-r--r-- 1 wanjiku wanjiku 48884206 Nov 14 16:31 boxoffice_1.0.0_amd64.deb
```

The trick is leaving things out: a Java with only the parts Box Office uses, and only the one SQLite library this system needs, not all 24.

There's one more change. An installed app lives in a folder like `/opt/boxoffice`, which the gate staff can't write to, and which an update replaces. So `gate.db` can't sit next to the app any more. Installed, it goes in each system's folder for app data.

## What you'll learn

- What **jpackage** makes: an app image, and the installers built from it
- What **jlink** does, and how **jdeps** finds the modules your app really uses
- Why `jdk.crypto.ec` has to be added by hand
- Four jlink options that cut the runtime down to 56 MB
- How a **Maven profile** turns on extra build steps only when you ask, and how profiles can switch on by themselves for one OS
- Why the desktop jar went from 30 MB to 17 MB
- Where Windows, macOS and Linux each want an app to keep its data, and how to test all three from one laptop
- Why the main class is `Main`, not `BoxOfficeApp`
- Why the Windows upgrade id must never change
- Why each installer can only be built on its own system, and what "unsigned" means for the people installing it

## New words

- **Installer**: a file that puts an app on a computer: copies its files, adds it to the menu, and can remove it again.
- **App image**: the app's folder, ready to run as it is, with its own Java inside. Every installer is made from one.
- **`.deb`**: the installer format of Debian and Ubuntu. `.rpm` is the same idea for Fedora and Red Hat.
- **`.msi`**: the Windows Installer format.
- **`.dmg`**: a Mac disk image. You open it and drag the app to Applications.
- **jpackage**: a JDK tool that turns your jar into an app image or an installer.
- **Module**: a named part of the JDK, like `java.sql` (databases) or `java.desktop` (printing, images). Java 21 has about 70.
- **Runtime**: the part of Java that runs programs. A JDK also has the compiler and other tools you don't need to run an app.
- **jlink**: a JDK tool that builds a runtime from only the modules you name.
- **jdeps**: a JDK tool that reads a jar and lists the modules it uses.
- **Native library**: code compiled for one system and one kind of processor, like `libsqlitejdbc.so` for Linux on x86_64. Java can call it, but it only runs on the system it was made for.
- **Maven profile**: a named set of extra build settings, turned on with `-P` (like `-Pinstaller`), or by itself when a condition is true, like "this is Linux".
- **App-data folder**: the folder each system gives apps for their own files, inside the user's home.
- **Launcher**: the small program that starts the app, like `BoxOffice` or `BoxOffice.exe`. jpackage writes it.
- **Upgrade UUID**: a long id that tells Windows "these installers are all the same app", so a new version replaces the old one.
- **WiX**: the free toolset jpackage uses to build an `.msi`.
- **Code signing**: putting the maker's certificate on an installer. Without it, Windows and macOS warn people before they open it.

## What's already written

From Days 101–127: the gate app, the sync server and the sales assistant with its evals, working, with tests. New today, already done:

- `AppPaths.java` in `core`: `DATABASE` (`"gate.db"`), `installed()`, and `defaultDatabase()` with no arguments, which asks the real system and makes the folder. You write the two methods that do the thinking.
- `BoxOfficeApp.java`: opens `--db=...` if you give one, and `AppPaths.defaultDatabase()` if you don't.
- The root `pom.xml` pins three more plugins: `exec-maven-plugin` (to run jlink and jpackage), `maven-resources-plugin` and `maven-clean-plugin`.
- `desktop/pom.xml`: the shade step now has an id, `fat-jar`, and there's a new section of **profiles**:
  - `installer`: keeps one SQLite library in the jar, cleans last time's output, copies the jar alone into `target/installer-input`, then runs jlink and jpackage;
  - `deb`, `rpm`, `msi` and `dmg`: each type's own jpackage options, switched on by `-Dinstaller.type=...`;
  - `sqlite-linux-x64`, `sqlite-linux-arm64`, `sqlite-mac-arm64`, `sqlite-mac-x64` and `sqlite-windows-x64`: each switches on by itself on its own system and sets `sqlite.native` to that system's folder in sqlite-jdbc, like `Linux/x86_64`.
- Tests: `AppPathsTest` (8) in `core`, `InstallerProfileTest` (7) in `desktop`.

New, for you to finish: `dataFolder` (step 2) and `defaultDatabase` (step 3) in `AppPaths.java`, and in `desktop/pom.xml` the jlink modules and options (step 4), the SQLite filter (step 5) and the main class (step 6). The TODOs are labelled with their step numbers.

## What you'll use

**What jpackage makes.** You give it your jar, a Java runtime and a name. It writes an **app image**: a folder with a launcher, your jar and the runtime. On Linux:

```
BoxOffice/
  bin/BoxOffice                      the launcher
  lib/app/boxoffice-desktop.jar      your jar
  lib/app/BoxOffice.cfg              which jar and main class to start
  lib/runtime/                       Java, just for this app
```

With `--type deb` (or `msi`, `dmg`, `rpm`) it goes one step further and wraps that folder in an installer. Without a type, the course's profile makes just the app image (`app-image`), which needs no other tools: the quickest way to check it all works.

**A runtime of only what you use.** A whole JDK is about 300 MB. Box Office uses a few modules of it. jdeps reads the jar and lists them:

```
$ jdeps --print-module-deps --ignore-missing-deps target/boxoffice-desktop.jar
java.base,java.desktop,java.net.http,java.scripting,java.sql,jdk.httpserver,jdk.jfr,jdk.unsupported
```

`--ignore-missing-deps` lets it carry on past classes in the libraries that point at things Box Office never uses. Then jlink builds a runtime from that list:

```
jlink --add-modules java.base,java.desktop,...  --output target/runtime
```

jlink also adds the modules those modules need (`java.xml`, `java.logging` and a few more), so the runtime always works on its own.

**The one module jdeps can't see.** jdeps finds modules by the class names your code uses. HTTPS (syncing with the server, Day 124) needs elliptic-curve encryption from `jdk.crypto.ec`, but no class of yours names it: Java finds it while the connection is being set up. Leave it out and the app starts fine, then fails on the first HTTPS sync. So you add it to the list by hand.

**Four options that squeeze it.** Each jlink option is its own `<argument>` in the pom:

- `--strip-debug`: takes out the information debuggers use, like line numbers. A gate laptop never debugs Java.
- `--no-header-files`: no C header files, which are only for people writing native code.
- `--no-man-pages`: no Unix manual pages.
- `--compress=zip-6`: zips the modules inside the runtime. `6` is a middle setting between size and start-up speed.

The runtime comes out at 56 MB.

**One SQLite library, not 24.** sqlite-jdbc is the library that lets Java talk to SQLite (Day 104). SQLite itself is native code, so sqlite-jdbc carries a copy for every system it knows: Linux on x86_64, Linux on ARM, Windows, Mac, Android, FreeBSD and more, 24 libraries, about 24 MB. A `.deb` for a 64-bit Linux laptop needs exactly one of them. So the `installer` profile adds a **filter** to the shade step, with an `<include>` for each part of sqlite-jdbc to keep. Today it keeps every library:

```xml
<include>org/sqlite/native/**</include>
```

and you change it to keep only this system's:

```xml
<include>org/sqlite/native/${sqlite.native}/**</include>
```

`${sqlite.native}` comes from whichever `sqlite-...` profile switched on by itself. On a 64-bit Linux laptop, that's `sqlite-linux-x64`, so it's `Linux/x86_64`. `**` means "everything below this folder". The desktop jar goes from 30 MB to 17 MB.

**Profiles that switch on by themselves.** A profile can have an `<activation>`:

```xml
<activation>
    <os>
        <name>Linux</name>
        <arch>amd64</arch>
    </os>
</activation>
```

"Switch me on when Maven runs on Linux, on an amd64 (x86_64) processor." The `deb` profile switches on when the property `installer.type` is `deb`. You never type `-Psqlite-linux-x64` or `-Pdeb`; `-Pinstaller -Dinstaller.type=deb` is enough.

**Only the jar goes in.** jpackage copies everything in its `--input` folder into the app. `target/` is full of other things (classes, test reports, the 30 MB original jar), so the profile copies `boxoffice-desktop.jar` alone into `target/installer-input` and hands jpackage that.

**The main class.** On Day 106 you made `Main`, a tiny class whose `main` calls `Application.launch(BoxOfficeApp.class, args)`. When the class Java starts extends `Application`, Java checks that JavaFX is installed as modules, and refuses with "JavaFX runtime components are missing" when it's in a jar instead, as it is here. `Main` doesn't extend `Application`, so the check never runs. jpackage's launcher starts the class you give it with `--main-class` the same way, so it has to be `com.boxoffice.desktop.Main` too.

**Where the database goes.** Installed, the app's folder is `/opt/boxoffice` on Linux or `C:\Program Files\BoxOffice` on Windows: the gate staff can't write there, and an update replaces it. Each system has a folder in the user's home for an app's own files instead:

```
Windows   %LOCALAPPDATA%\BoxOffice           C:\Users\wanjiku\AppData\Local\BoxOffice
macOS     ~/Library/Application Support/BoxOffice
Linux     $XDG_DATA_HOME/boxoffice           or ~/.local/share/boxoffice
```

`%LOCALAPPDATA%` is Windows' environment variable for that folder. On Linux, `XDG_DATA_HOME` is the standard's variable for it; most laptops don't set it, and then it's `~/.local/share`. The standard also says to ignore it if it isn't a full path (one that starts with `/`). Lower case `boxoffice` on Linux is the Linux way.

**Testing three systems on one laptop.** If `dataFolder` read `System.getProperty("os.name")` itself, you could only ever test the system you're on. So it takes the system's name, the environment and the home folder as arguments, and `AppPathsTest` passes in Windows' answers on a Linux laptop:

```java
AppPaths.dataFolder("Windows 11", Map.of("LOCALAPPDATA", "C:\\Users\\wanjiku\\AppData\\Local"), "C:\\Users\\wanjiku")
```

It builds `String`s, not `Path`s: a `Path` on Linux can't hold a Windows path like `C:\Users`. Only the no-argument `defaultDatabase()` (already written) turns the answer into a `Path`, on the real system.

**Installed, or run from a jar?** The launcher jpackage writes sets the system property `jpackage.app-path` when it starts Java. `java -jar` doesn't. So `installed()` is just `System.getProperty("jpackage.app-path") != null`. Run with `java -jar`, Box Office keeps `gate.db` in the folder you start it from, as it has since Day 104.

**The Windows upgrade id.** The `msi` profile passes `--win-upgrade-uuid 7f3c2a64-5b0e-4f2d-9a51-6c1d8e0b4a77`. Windows uses it to know that 1.1 is the same app as 1.0, so installing 1.1 replaces 1.0. Change it, and Windows installs 1.1 **beside** 1.0, with two "BoxOffice" entries in the menu. It must stay the same in every version, for ever. (`InstallerProfileTest` checks it.)

**Each installer on its own system.** jpackage only makes installers for the system it runs on: on Linux it makes `.deb` and `.rpm`, and `--type msi` says `Error: Invalid or unsupported type: [msi]`. The `.msi` is built on Windows, with **WiX 3** installed (JDK 21's jpackage works with WiX 3, not the newer 4 or 5), and the `.dmg` on a Mac. The SQLite library is picked the same way, from the system Maven runs on. Tomorrow, Day 129 builds all three on GitHub's Linux, Windows and Mac machines.

**Unsigned installers.** Windows and macOS like to know who made an installer. A real company buys a **code-signing certificate** and signs it. Ours aren't signed, so Windows shows a blue SmartScreen box ("Windows protected your PC": click "More info", then "Run anyway"), and macOS's Gatekeeper says the app "cannot be opened" the first time (right-click it, choose Open, then Open again). For a course and your own gates that's fine; tell the gate staff before they see it.

## Steps

1. **Open the starter and run the tests.** `cd day-128-installers/starter`, then `../../mvnw test` (Windows: `..\..\mvnw.cmd test`). You should see `Tests run: 240, Failures: 0, Errors: 7` in `core` and `BUILD FAILURE`; `cli`, `desktop` and `sync server` are `SKIPPED`:
   - five `AppPathsTest` tests (`windowsUsesLocalAppData`, `macUsesApplicationSupport` and three more): `UnsupportedOperation Not written yet: Day 128, step 2`;
   - `runFromAJarTheDatabaseStaysInTheCurrentFolder` and `installedTheDatabaseGoesInTheDataFolder`: `UnsupportedOperation Not written yet: Day 128, step 3`.

   Then read `starter/core/src/main/java/com/boxoffice/core/AppPaths.java` and `starter/desktop/pom.xml` from `Day 128: installers` to the end.

2. **Each system's folder.** In `AppPaths.dataFolder`, replace the `throw`. Lower-case the name first: `String os = osName.toLowerCase(Locale.ROOT);`. Then:
   - if `os.startsWith("windows")`: take `environment.get("LOCALAPPDATA")`; if it's `null` or blank, use `userHome + "\\AppData\\Local"` instead. Return that `+ "\\BoxOffice"`;
   - if `os.startsWith("mac")`: return `userHome + "/Library/Application Support/BoxOffice"`;
   - otherwise: take `environment.get("XDG_DATA_HOME")`; if it's `null` or doesn't start with `"/"`, use `userHome + "/.local/share"` instead. Return that `+ "/boxoffice"`.

   `"\\"` in Java is one backslash. Run `../../mvnw test -Dtest=AppPathsTest -Dsurefire.failIfNoSpecifiedTests=false`. The five step-2 tests pass; two still say `step 3`.

3. **The default database.** In `defaultDatabase(osName, environment, userHome, installed)`, replace the `throw`:
   - if not `installed`, return `DATABASE`;
   - otherwise, the separator is `"\\"` when the name starts with `windows` (lower-cased), and `"/"` otherwise. Return `dataFolder(osName, environment, userHome) + separator + DATABASE`.

   Run `AppPathsTest` again: all 8 pass. Now run `../../mvnw test` once: `core` and `cli` pass, and `desktop` fails in `InstallerProfileTest` (`Tests run: 7, Failures: 4`). The next three steps are in the pom.

4. **Ask jdeps, then tell jlink.** Build the jar and ask jdeps what it uses. From the `starter` folder:

   ```
   ../../mvnw package -DskipTests
   cd desktop
   jdeps --print-module-deps --ignore-missing-deps target/boxoffice-desktop.jar
   cd ..
   ```

   (Windows: `..\..\mvnw.cmd package -DskipTests`, then `target\boxoffice-desktop.jar`.) It takes a minute and prints:

   ```
   java.base,java.desktop,java.net.http,java.scripting,java.sql,jdk.httpserver,jdk.jfr,jdk.unsupported
   ```

   Open `desktop/pom.xml` and find the TODO marked `step 4`, in the `jlink` execution. Replace `<argument>java.base</argument>` with that list, with `jdk.crypto.ec` added, all in **one** argument, commas and no spaces:

   ```xml
   <argument>java.base,java.desktop,java.net.http,java.scripting,java.sql,jdk.crypto.ec,jdk.httpserver,jdk.jfr,jdk.unsupported</argument>
   ```

   Below it, four more arguments, each its own `<argument>`: `--strip-debug`, `--no-header-files`, `--no-man-pages` and `--compress=zip-6`. Leave `--output` and the folder after them. Delete the TODO comment.

   Run `../../mvnw test -Dtest=InstallerProfileTest -Dsurefire.failIfNoSpecifiedTests=false`. `theRuntimeHasWhatTheGateUses` and `theRuntimeIsStrippedAndSqueezed` pass now; two tests still fail.

5. **Keep one SQLite library.** Find the TODO marked `step 5`, in the shade filter. Change `<include>org/sqlite/native/**</include>` to `<include>org/sqlite/native/${sqlite.native}/**</include>`, and delete the TODO comment. Run `InstallerProfileTest`: `theShadedJarKeepsOnlyThatLibrary` passes.

6. **Start the right class.** Find the TODO marked `step 6`, above the `jpackage` execution. In its `<commandlineArgs>`, `--main-class com.boxoffice.desktop.BoxOfficeApp` is the wrong one. Change it to `--main-class com.boxoffice.desktop.Main` and delete the TODO comment. Run `InstallerProfileTest`: all 7 pass.

7. **Run all the tests.** `../../mvnw test`. You should see 491 tests in all and `BUILD SUCCESS`. The tests don't run jlink or jpackage (they take minutes); `InstallerProfileTest` reads the pom with **XPath**, a way of pointing at parts of an XML file, like `/project/profiles/profile[id='installer']`. It also checks that each `sqlite-...` profile's folder really exists in sqlite-jdbc's jar: a typo there would build an app with no database driver for its own system.

8. **Build the app image and look inside.** Follow "Run it and test it" below.

9. **Build the installer for your system.** A `.deb` on Ubuntu, an `.msi` on Windows (install WiX 3 first), or a `.dmg` on a Mac. "Run it and test it" has the commands.

10. **Stretch (not required): see `jdk.crypto.ec` matter.** In a copy of your project, take `jdk.crypto.ec` out of the list, build the app image, and run the launcher's `sync` against a server on HTTPS. Then put it back. Or: check how much each of the four jlink options saves, by building with one left out and running `du -sh desktop/target/runtime`.

## Run it and test it

From the day's `starter` folder:

```
cd day-128-installers/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=InstallerProfileTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=InstallerProfileTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **240 tests** in `core` (including AppPaths 8), **56** in `cli` (LiveEval 1 skipped), **48** in `desktop` (including InstallerProfile 7) and **147** in `sync server`, **491** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] Box Office sync server ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**Build the app image.** From the `starter` folder (not from `desktop`: the whole build has to run, so `desktop` gets today's `core`):

```
../../mvnw package -Pinstaller -DskipTests
..\..\mvnw.cmd package -Pinstaller -DskipTests
```

It takes a few minutes. Near the end you'll see `exec:3.5.0:exec (jlink)` and then `exec:3.5.0:exec (jpackage)`, and `BUILD SUCCESS`. Look at what came out (macOS / Linux; on Windows, look in File Explorer, or `dir`):

```
$ ls -l desktop/target/*.jar
-rw-rw-r-- 1 wanjiku wanjiku 16809402 Nov 14 16:12 desktop/target/boxoffice-desktop.jar
-rw-rw-r-- 1 wanjiku wanjiku    34708 Nov 14 16:08 desktop/target/desktop-1.0.0.jar
-rw-rw-r-- 1 wanjiku wanjiku 30339043 Nov 14 16:08 desktop/target/original-boxoffice-desktop.jar

$ du -sh desktop/target/runtime desktop/target/installer/BoxOffice
56M	desktop/target/runtime
73M	desktop/target/installer/BoxOffice

$ cat desktop/target/installer/BoxOffice/lib/app/BoxOffice.cfg
[Application]
app.classpath=$APPDIR/boxoffice-desktop.jar
app.mainclass=com.boxoffice.desktop.Main

[JavaOptions]
java-options=-Djpackage.app-version=1.0.0

$ cat desktop/target/runtime/release
JAVA_VERSION="21.0.9"
MODULES="java.base java.datatransfer java.xml java.prefs java.desktop java.logging java.net.http java.scripting java.transaction.xa java.sql jdk.crypto.ec jdk.httpserver jdk.jfr jdk.unsupported"
```

(Your names, dates and Java version will differ.) The jar is 17 MB now: the shade step kept the 30 MB one as `original-boxoffice-desktop.jar`. The app is 73 MB: 56 MB of Java and 17 MB of Box Office. The runtime has 14 modules: your 9, plus 5 that jlink added because those 9 need them.

A plain `../../mvnw package`, without `-Pinstaller`, makes the 30 MB jar with every SQLite library again: that one runs anywhere with `java -jar`.

**Start it.** The app image runs as it is, no Java needed:

- Linux: `desktop/target/installer/BoxOffice/bin/BoxOffice`
- Windows: `desktop\target\installer\BoxOffice\BoxOffice.exe`
- macOS: `open desktop/target/installer/BoxOffice.app`

The window opens as usual. Sell a ticket to Kamau and close it. Its `gate.db` isn't in the folder you started from: it's in the app-data folder, `~/.local/share/boxoffice/gate.db` on Linux, `%LOCALAPPDATA%\BoxOffice\gate.db` on Windows, `~/Library/Application Support/BoxOffice/gate.db` on a Mac. Start it again and Kamau's ticket is still there.

**Build the `.deb` (Ubuntu or Debian).**

```
../../mvnw package -Pinstaller -Dinstaller.type=deb -DskipTests
```

jpackage needs `dpkg-deb` and `fakeroot` for this, which Ubuntu has. Then look at it without installing it:

```
$ ls -l desktop/target/installer/
-rw-r--r-- 1 wanjiku wanjiku 48884206 Nov 14 16:31 boxoffice_1.0.0_amd64.deb

$ dpkg-deb --info desktop/target/installer/boxoffice_1.0.0_amd64.deb
 new Debian package, version 2.0.
 size 48884206 bytes: control archive=1194 bytes.
     541 bytes,    11 lines      control
     933 bytes,    38 lines   *  postinst             #!/bin/sh
     814 bytes,    31 lines   *  postrm               #!/bin/sh
     626 bytes,    35 lines   *  preinst              #!/bin/sh
     857 bytes,    39 lines   *  prerm                #!/bin/sh
 Package: boxoffice
 Version: 1.0.0
 Section: misc
 Maintainer: Box Office course <Unknown>
 Priority: optional
 Architecture: amd64
 Provides: boxoffice
 Description: Sells and checks in tickets at the gate
 Depends: libasound2t64, libatomic1, libbrotli1, libbz2-1.0, libc6, libfreetype6, libgcc-s1, libgif7, libglib2.0-0t64, libgraphite2-3, libharfbuzz0b, libjpeg-turbo8, liblcms2-2, libpcre2-8-0, libpng16-16t64, libstdc++6, libx11-6, libxau6, libxcb1, libxdmcp6, libxext6, libxi6, libxrender1, libxtst6, xdg-utils, zlib1g
 Installed-Size: 73534
```

49 MB to download, 73 MB once installed (`Installed-Size` is in kilobytes). The `Depends` line is the system libraries Java's window and sound code use; jpackage found them for you. Every Ubuntu desktop already has them. (Your list may differ a little on another Ubuntu version.) What's inside:

```
$ dpkg-deb --contents desktop/target/installer/boxoffice_1.0.0_amd64.deb | grep -v runtime/
drwxr-xr-x root/root         0 2026-11-14 16:29 ./
drwxr-xr-x root/root         0 2026-11-14 16:28 ./opt/
drwxr-xr-x root/root         0 2026-11-14 16:29 ./opt/boxoffice/
drwxr-xr-x root/root         0 2026-11-14 16:28 ./opt/boxoffice/bin/
-rwxr-xr-x root/root     21944 2026-11-14 16:28 ./opt/boxoffice/bin/BoxOffice
drwxr-xr-x root/root         0 2026-11-14 16:29 ./opt/boxoffice/lib/
-rw-r--r-- root/root      4955 2026-11-14 16:29 ./opt/boxoffice/lib/BoxOffice.png
drwxr-xr-x root/root         0 2026-11-14 16:28 ./opt/boxoffice/lib/app/
-rw-r--r-- root/root       156 2026-11-14 16:28 ./opt/boxoffice/lib/app/BoxOffice.cfg
-rw-r--r-- root/root  16809402 2026-11-14 16:28 ./opt/boxoffice/lib/app/boxoffice-desktop.jar
-rw-r--r-- root/root       211 2026-11-14 16:29 ./opt/boxoffice/lib/boxoffice-BoxOffice.desktop
-rwxr-xr-x root/root    214064 2026-11-14 16:28 ./opt/boxoffice/lib/libapplauncher.so
drwxr-xr-x root/root         0 2026-11-14 16:29 ./opt/boxoffice/share/
drwxr-xr-x root/root         0 2026-11-14 16:29 ./opt/boxoffice/share/doc/
-rw-r--r-- root/root       132 2026-11-14 16:29 ./opt/boxoffice/share/doc/copyright
```

It all goes to `/opt/boxoffice`. `boxoffice-BoxOffice.desktop` is the menu entry: the `postinst` script (it runs after installing) adds it to the menu with `xdg-desktop-menu`. That's what `--linux-shortcut` and `--linux-menu-group Office` in the `deb` profile asked for.

To install it on a gate laptop (it needs your password, because it writes to `/opt`):

```
sudo apt install ./desktop/target/installer/boxoffice_1.0.0_amd64.deb
```

Box Office is then in the menu, under Office, or runs as `/opt/boxoffice/bin/BoxOffice`. To remove it: `sudo apt remove boxoffice`. Removing the app leaves `~/.local/share/boxoffice/gate.db` where it is, so the night's sales survive an uninstall.

**Build the `.msi` (Windows).** Install the WiX Toolset **v3** (3.14) from wixtoolset.org and put its `bin` folder on the `PATH`. Then, in a new terminal:

```
..\..\mvnw.cmd package -Pinstaller "-Dinstaller.type=msi" -DskipTests
```

The installer lands in `desktop\target\installer\`. Double-click it: it asks where to install (`--win-dir-chooser`), installs for this user only (`--win-per-user-install`, no admin password), and adds a Start menu entry and a desktop shortcut. SmartScreen will warn you first, because it isn't signed.

**Build the `.dmg` (macOS).**

```
../../mvnw package -Pinstaller -Dinstaller.type=dmg -DskipTests
```

Open the `.dmg` in `desktop/target/installer/` and drag BoxOffice to Applications. The first time, right-click it and choose Open: Gatekeeper warns about an unsigned app.

`desktop/target/` is in `.gitignore`, so none of these files goes into Git. Installers are build output: you make them again from the code, you never commit them.

## When you're stuck

- **`Not written yet: Day 128, step 2`** (or `step 3`): that method still has its `throw`.
- **`windowsUsesLocalAppData` fails with `/` in the answer, or `macUsesApplicationSupport` gets `.../.local/share/boxoffice`**: the system check didn't match. Lower-case the name first (`"Windows 11"` doesn't start with `"windows"`, and `"Mac OS X"` doesn't start with `"mac"`), with `toLowerCase(Locale.ROOT)`.
- **`aRelativeXdgDataHomeIsIgnored` says `expected: "/home/wanjiku/.local/share/boxoffice" but was: "data/boxoffice"`**: a relative `XDG_DATA_HOME` must be ignored. Use it only when it starts with `"/"`.
- **`installedTheDatabaseGoesInTheDataFolder` fails on the Windows line**: the separator before `gate.db` must be `\` on Windows, `"\\"` in Java.
- **`theRuntimeHasWhatTheGateUses` says `but could not find the following element(s): ["jdk.crypto.ec"]`**: jdeps doesn't list it. Add it by hand. If it can't find several, check there are no spaces after the commas, and that the list is in the argument right after `--add-modules`.
- **`theRuntimeIsStrippedAndSqueezed` says `Expecting ListN: ["--add-modules", "java.base", "--output", "${project.build.directory}/runtime"] to contain: ["--strip-debug", "--no-header-files", "--no-man-pages", "--compress=zip-6"]`**: the four options are missing. Each is its own `<argument>`, spelled exactly like that.
- **`theShadedJarKeepsOnlyThatLibrary` says `could not find the following element(s): ["org/sqlite/native/${sqlite.native}/**"]`**: step 5 isn't done. If it fails on `noneMatch`, the old `org/sqlite/native/**` line is still there too: delete it.
- **`jpackageStartsTheMainThatDodgesTheJavaFxCheck` says `to contain: "--main-class com.boxoffice.desktop.Main"`**: step 6.
- **`theSqliteLibraryKeptForEachSystemReallyExists` fails**: a `sqlite-...` profile was changed, and its folder doesn't exist in sqlite-jdbc. Put it back as it was.
- **`cli`, `desktop` and `sync server` are `SKIPPED`**: `core` failed first. Finish steps 2 and 3.
- **`jdeps: command not found`** (or "not recognized" on Windows): use the one in your JDK, `"$JAVA_HOME/bin/jdeps"` (Windows: `& "$env:JAVA_HOME\bin\jdeps.exe"`).
- **`Could not find artifact com.boxoffice:box-office:pom:1.0.0`**: you ran Maven from the `desktop` folder. Go back up to `starter` and run it from there.
- **`Error: Invalid or unsupported type: [msi]`** (or `[dmg]`): jpackage only makes installers for the system it runs on. Build the `.msi` on Windows and the `.dmg` on a Mac, or leave it for Day 129.
- **The `.msi` build fails and the message mentions WiX**: WiX 3 isn't installed, or its `bin` folder isn't on the `PATH`, or you installed WiX 4 or 5. JDK 21 needs v3. Open a new terminal after changing the `PATH`.
- **The app image or installer starts, then says `JavaFX runtime components are missing`**: the main class is still `BoxOfficeApp`. Do step 6 and build again.
- **The installed app sync fails on `https://` with a handshake error**: `jdk.crypto.ec` is missing from the jlink list.
- **The installed app shows no sales from your `java -jar` nights**: that's right. Installed, it uses the app-data folder's `gate.db`, not the one in your project folder. Copy the file there (with the app closed) if you want those sales.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/AppPaths.java` or `solution/desktop/pom.xml`, read the part you need once, close it, then write your own from memory.

## Checklist

- [ ] Can say what jpackage makes, and the difference between an app image and an installer
- [ ] Can say what jlink and jdeps do, and why `jdk.crypto.ec` is added by hand
- [ ] Can say why the desktop jar went from 30 MB to 17 MB
- [ ] Can say where each system keeps app data, and why an installed app can't keep `gate.db` next to itself
- [ ] Can say why the main class is `Main`, and why the upgrade UUID never changes
- [ ] Can say why each installer is built on its own system, and what an unsigned installer means for the gate staff
- [ ] All 491 tests pass
- [ ] Built the app image and checked its size with `du`
- [ ] Built the installer for your own system
- [ ] Committed your work to Git (not `gate.db`, and not `desktop/target/` with its jars, runtime and installers)

## Next

Three installers, but each one needs its own system to build it, and you have one laptop. Tomorrow a release pipeline does it for you: Conventional Commits decide the next version number, a tag starts GitHub Actions on Linux, Windows and Mac machines at the same time, and the `.deb`, `.msi` and `.dmg` land on a GitHub Release with checksums.
