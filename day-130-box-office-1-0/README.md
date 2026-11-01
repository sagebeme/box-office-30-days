# Day 130: Box Office 1.0

> Tuesday after the night. Wanjiku opens the gate laptop to put it away and sees "Box Office 1.1.0 is out". She downloads it, the checksum matches, and she installs it with a cup of tea. Not at 21:00 with a queue at the gate.

Watch the video: *(not recorded yet)*

## The brief

Yesterday's pipeline puts every release on GitHub. The laptops at the gate don't know it's there. Today the app learns three things:

1. **Its own version**, from the build. Until now `--version` said "Box Office 1.0" because someone typed it into the code. Now Maven writes the version from `pom.xml` into the app, so the two can't disagree.
2. **Whether a newer one is out.** The new `update` command asks GitHub for the latest release and compares versions, as numbers, the Day 129 way.
3. **How to fetch it safely.** `update --download FOLDER` downloads the installer for this system and checks it against the release's `SHA256SUMS`. A file that doesn't match is deleted. And it never downloads while the night is on.

It never installs anything. The person at the laptop does that, after the night.

```
$ java -jar cli/target/boxoffice.jar --version
Box Office 1.0.0

$ java -jar cli/target/boxoffice.jar update
Box Office 1.0.0: no releases published yet
```

That second answer is real: nobody has published a Box Office release on GitHub yet, so that's what you'll see too. You write the checksum code, the version check, the "is the night on?" rule and the last check before a download gets its name.

Today is also the last day of the course. After the steps, there's a list of what Box Office 1.0 now does, and ideas for **one feature of your own**.

## What you'll learn

- How Maven **resource filtering** puts the pom's version into the app, and why only one file is filtered
- How an app asks the **GitHub REST API** for the latest release, and what a 404 means there
- What a **SHA-256 checksum** proves, and what it doesn't
- How to fingerprint a 50 MB file in 64 KB pieces, without loading it all into memory
- Why a download goes to a `.part` file first, and only gets its real name after the check
- Why an app must never update itself during the event, and why that needs the **real** clock
- What auto-update can and can't promise: checksums catch a broken download, only signing catches a bad release

## New words

- **Resource**: a file that's packed into the jar beside the classes, like the SQL migrations or `version.properties`.
- **Resource filtering**: Maven replacing `${...}` placeholders in a resource with real values while it copies it, like `${project.version}` with `1.0.0`.
- **Properties file**: a text file of `name=value` lines. Java reads it with `java.util.Properties`.
- **Auto-update**: an app noticing that a newer version is out, and helping you get it.
- **REST API**: a web address a program asks for data, and gets JSON back. GitHub's is at `https://api.github.com`.
- **Latest release**: GitHub's newest published release. Drafts and **pre-releases** (marked "not ready yet") never count as latest.
- **Asset**: a file attached to a release: an installer, or `SHA256SUMS`.
- **Checksum**: a short fingerprint of a file's bytes. **SHA-256** gives 64 hex characters. Change one byte of the file, and the checksum is completely different.
- **`SHA256SUMS`**: the file Day 129's workflow writes with `sha256sum`: one line per installer, its checksum, two spaces, its name.
- **Redirect**: an HTTP answer (`302 Found`) that says "it's not here, it's over there". GitHub's download links redirect to where the files really are.
- **`.part` file**: a half-downloaded file, named so that nobody mistakes it for the finished one.
- **Clock**: Java's `java.time.Clock`, the thing that says what time it is (Day 103). The **practice clock** always says it's 19:00 on the night; the **real clock** says the real time.

## What's already written

From Days 101–129: Box Office with its installers and release pipeline, working, with tests. New today, already done:

- **The version.** `core/src/main/resources/com/boxoffice/core/version.properties` holds one line, `version=${project.version}`. `core/pom.xml` filters only that file. `BoxOffice.version()` reads it once, at start-up. `BoxOfficeCli.Version` makes the CLI's `--version` say `Box Office 1.0.0`.
- **Two clocks.** `BoxOfficeCli` has a `realClock` beside `clock`. `main` passes the practice clock and the real one; tests pass one fixed clock for both.
- A new package, `com.boxoffice.core.update`:
  - `Asset` (a name, a URL, a size) and `Release` (a version, notes, assets), with `asset(name)` and `installerFor(osName)`: `.msi` on Windows, `.dmg` on a Mac, `.deb` on Linux.
  - `ReleaseFeed`: an interface, "the newest release, if there is one". `GitHubReleases` is the real one.
  - `GitHubReleases`: asks `GET https://api.github.com/repos/{owner}/{name}/releases/latest` and reads the JSON.
  - `UpdateCheck`: a sealed interface with four answers, `NoReleases`, `UpToDate`, `Available` and `NothingForThisSystem`. You write the `UpdateChecker` that picks one.
  - `Checksums`: you write `sha256` and `parse`.
  - `Downloader`: fetches `SHA256SUMS` and the installer, following GitHub's redirect, into a `.part` file. You write the check at the end.
  - `UpdateException`: an update problem, with a message for the person at the gate.
- The CLI's new command, `update` (in `UpdateCommand.java`), with `--download FOLDER` and `--repo` (it's `sagebeme/box-office-30-days` if you leave it out). The environment variable `BOXOFFICE_RELEASES_URL` points it at another address than GitHub; the tests use it for a fake GitHub.
- `FakeGitHub` (in the `core` and `cli` tests): GitHub's releases API and download links, on your laptop, with a redirect like the real one. It can corrupt a file after writing its checksum.
- Tests: `BoxOfficeVersionTest` (1), `ChecksumsTest` (3), `GitHubReleasesTest` (6), `UpdateCheckerTest` (6), `DownloaderTest` (6) in `core`; `UpdateCommandTest` (8) in `cli`.

New, for you to finish: `Checksums.sha256` (step 2) and `Checksums.parse` (step 3), `UpdateChecker.check` (step 4) and `duringTheEvent` (step 5), and the checksum check in `Downloader.download` (step 6). The TODOs are labelled with their step numbers.

## What you'll use

**The version comes from the pom.** `version.properties`, as you write it:

```
version=${project.version}
```

and as Maven copies it into `core/target/classes`, and into the jar:

```
version=1.0.0
```

`core/pom.xml` lists the resources twice. The first `<resource>` includes only `version.properties`, with `<filtering>true</filtering>`. The second takes everything else, with filtering off. Set the version to 1.1.0 (Day 129's `versions:set`), build, and `--version` says 1.1.0. Nobody types a version into the code again.

**Why only that one file.** Filtering rewrites anything that looks like `${...}` (or `@...@`) in a file as it's copied. The other resources in `core` are the gate's SQL migrations, `V1__create_tables.sql` to `V7__add_sync_result.sql`. Flyway (Day 104) keeps a checksum of every migration it has run, inside `gate.db`. If Maven ever changed one byte of an old migration, say a comment with `${...}` or two `@` signs in it, every gate's `gate.db` would remember a different checksum, and Flyway would refuse to start with "Validate failed: Migrations have failed validation". A gate that won't start on the night, because of a build setting. So SQL files are copied exactly as they are, always.

**Reading the version once.** `BoxOffice.version()` loads the file with `getResourceAsStream("version.properties")`, which finds it next to `BoxOffice.class` in the jar, and keeps it in a `static final` field. If the file is missing, it says `version.properties is missing: build with Maven`: running the code without a Maven build (some editors do) would otherwise give a `null` version.

**Asking GitHub.** `GitHubReleases.latest()` sends:

```
GET https://api.github.com/repos/sagebeme/box-office-30-days/releases/latest
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
```

That's what GitHub's documentation asks for. The answer is JSON: `tag_name` (`v1.1.0`), `body` (the release notes from Day 129) and `assets`, each with a `name`, a `size` and a `browser_download_url`. Two answers matter:

- **404**: there's no published release. That's not an error: it's `NoReleases`. (Drafts and pre-releases are never "latest", so a half-finished release never reaches a gate.)
- Anything else that isn't 200, like `403` when you've asked too often: `GitHub answered HTTP 403 about sagebeme/box-office-30-days`.

It's one read-only GET, with no key, through the gate's `ResilientHttp` from Day 115, so a slow connection gets retries. GitHub lets you ask 60 times an hour without logging in. Plenty for a laptop that checks once a day.

**Four answers.** `UpdateChecker.check()` returns an `UpdateCheck`. It's a sealed interface (Day 102), so the `switch` in `UpdateCommand` must handle all four:

| Answer | When | The CLI says |
|---|---|---|
| `NoReleases` | GitHub answered 404 | `Box Office 1.0.0: no releases published yet` |
| `UpToDate` | the latest is the same as yours, or older | `Box Office 1.0.0 is up to date` |
| `Available` | it's newer, with an installer for this system | `Box Office 1.1.0 is out (you have 1.0.0)`, then the notes |
| `NothingForThisSystem` | it's newer, with no installer for this system | `... but it has no installer for Linux` |

"Newer" means `compareTo` on two `SemVer`s, as numbers. As text, 1.10.0 would look older than 1.9.0, and a gate on 1.9.0 would never update. "The same or older" is `UpToDate`: you might be running a build of your own that's newer than anything published.

**A checksum is a fingerprint.** SHA-256 turns any file into 64 hex characters. The same bytes always give the same 64 characters; change one byte and nearly every character changes. So if the installer you downloaded gives the checksum the release published, you have exactly the file that was published: not half of it, not a corrupted copy from bad venue Wi-Fi, not a file someone swapped on the way.

**Fingerprinting 50 MB without loading 50 MB.** An installer is about 50 MB. `Files.readAllBytes` would hold all of it in memory at once. Instead, read it in 64 KB pieces and hand each piece to a `MessageDigest`, which keeps a running total:

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] buffer = new byte[64 * 1024];
int read;
while ((read = in.read(buffer)) != -1) {
    digest.update(buffer, 0, read);
}
return HexFormat.of().formatHex(digest.digest());
```

`in.read(buffer)` fills up to 64 KB and returns how many bytes it really read, or `-1` at the end of the file. The last piece is usually shorter, so `update(buffer, 0, read)` uses only the bytes read this time. `HexFormat.of().formatHex(...)` writes the 32 bytes of the result as 64 lower-case hex characters. `MessageDigest.getInstance` can throw `NoSuchAlgorithmException`, a checked exception; every Java has SHA-256, so turn it into an `IllegalStateException`.

**Reading `SHA256SUMS`.** Day 129's `sha256sum * > SHA256SUMS` writes lines like:

```
ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  boxoffice_1.1.0_amd64.deb
```

The checksum, two spaces, the name. (On Windows, some tools write a space and a `*` instead.) `Checksums.LINE` is already written: `([0-9a-fA-F]{64}) [ *](.+)`. `parse` turns the file into a `Map` of name to checksum, lower-cased so `BA78...` and `ba78...` compare equal, and skips any line that doesn't fit.

**Download to `.part`, check, then rename.** `Downloader.download` does it in this order:

1. No `SHA256SUMS` in the release: refuse (`Release 1.1.0 has no SHA256SUMS, so there's no way to check the download`). No checksum, no download.
2. Fetch `SHA256SUMS` and find the installer's line. Not listed: refuse.
3. Download the installer to `BoxOffice-1.1.0.msi.part`. GitHub's link answers `302` with the real address, so the `HttpClient` is built with `followRedirects(HttpClient.Redirect.NORMAL)`.
4. **Your step 6**: fingerprint the `.part` file. Different from `SHA256SUMS`: delete it and say `The download doesn't match its checksum, so it was deleted. Try again`. The same: only now rename it to `BoxOffice-1.1.0.msi`, with `Files.move(part, done, StandardCopyOption.REPLACE_EXISTING)`.

So a file with the installer's name is always a checked one. If the Wi-Fi drops halfway, or the check fails, there's never a `BoxOffice-1.1.0.msi` lying in the folder for someone to double-click later.

**Never during the night.** An installer replaces the app. Halfway through, the gate can't sell or scan. So `update --download` refuses from two hours before the doors open (`SET_UP_TIME`, so 16:00 for an 18:00 start: the laptops are being set up) until the event ends at 04:00:

```
The night is on: updates wait until Kilele Nights ends at Sun 15 Nov, 04:00
```

`duringTheEvent(event, now)` is true when `now` is at or after `startsAt` minus two hours, and before `endsAt`. "At or after" is `!now.isBefore(from)`: 16:00 exactly counts as the night; 04:00 exactly doesn't. Checking is still fine during the night: only the download waits. A new version can wait a night; a gate that won't start can't.

**Two clocks.** Since Day 103, the CLI runs on the practice clock: it always says it's 19:00 on 14 November 2026, so selling works whenever you practise. Ask the practice clock "is the night on?" and the answer is always yes, and `update --download` could never run. So `BoxOfficeCli` now has two: `clock`, the practice one, for selling, and `realClock`, `Clock.systemUTC()`, for "is the night on?". The tests pass one fixed clock for both, so `neverDuringTheNight` can say "it's 21:00 on the night" and `downloadsAndChecksTheInstallerForThisSystem` "it's Tuesday".

**It never runs the installer.** After a good download, it says `Saved installers/BoxOffice-1.1.0.msi (its checksum matches). Close Box Office, then run it to update` and stops. The person closes the app and runs the installer, after the night. An app that installs itself can break itself, at a moment it chose, with nobody watching. Day 128's upgrade id means the new `.msi` replaces the old one instead of installing beside it.

**What a checksum can't do.** `SHA256SUMS` comes from the same GitHub release as the installer. If someone took over the repository and published a bad release, they'd publish a matching `SHA256SUMS` too, and the check would pass. Checksums catch a broken or swapped **download**; they can't catch a bad **release**. Only code signing (Day 129) covers that: the bad release wouldn't carry your certificate's signature. Box Office's `.msi` is signed only if you add a certificate, and its `.dmg` isn't signed yet, so say this plainly to anyone you hand it to.

## Steps

1. **Open the starter and run the tests.** `cd day-130-box-office-1-0/starter`, then `../../mvnw test` (on Windows, `..\..\mvnw.cmd test`). You should see `Tests run: 274, Failures: 3, Errors: 10` in `core` and `BUILD FAILURE`; `cli`, `desktop` and `sync server` are `SKIPPED`:
   - `ChecksumsTest.fingerprintsAFile` and `oneByteChangesEverything`: `UnsupportedOperation Not written yet: Day 130, step 2`;
   - `ChecksumsTest.readsWhatSha256sumWrites`, and `DownloaderTest.downloadsAndChecksTheInstaller` and `followsGitHubsRedirect`: `UnsupportedOperation Not written yet: Day 130, step 3`;
   - three more `DownloaderTest` tests (`aFileThatDoesntMatchItsChecksumIsDeleted`, `anInstallerTheChecksumsDontListIsRefused`, `aMissingFileSaysSo`): `Expecting actual throwable to be an instance of: com.boxoffice.core.update.UpdateException but was: java.lang.UnsupportedOperationException: Not written yet: Day 130, step 3`;
   - four `UpdateCheckerTest` tests: `Not written yet: Day 130, step 4`, and `noUpdatesFromTwoHoursBeforeTheDoorsUntilTheEnd`: `Not written yet: Day 130, step 5`.

   `BoxOfficeVersionTest` and `GitHubReleasesTest` already pass: that code is written. On a busy or slow laptop, a test with a short time limit (like `PowerCutTest` or `GitHubReleasesTest`) can also say `Timeout` or `No answer in time`. Run the tests again when the laptop is quieter.

2. **Fingerprint a file.** Open `starter/core/src/main/java/com/boxoffice/core/update/Checksums.java`. In `sha256`, replace the `throw`:
   - `try (InputStream in = Files.newInputStream(file)) { ... }`;
   - inside: `MessageDigest digest = MessageDigest.getInstance("SHA-256");`, a `byte[] buffer = new byte[64 * 1024];`, and the `while ((read = in.read(buffer)) != -1)` loop from "What you'll use", calling `digest.update(buffer, 0, read)`;
   - `return HexFormat.of().formatHex(digest.digest());`
   - `catch (IOException e)`: `throw new UncheckedIOException(e);`. `catch (NoSuchAlgorithmException e)`: `throw new IllegalStateException("Every Java has SHA-256", e);`.

   Run `../../mvnw test -Dtest=ChecksumsTest -Dsurefire.failIfNoSpecifiedTests=false`. `fingerprintsAFile` and `oneByteChangesEverything` pass. `fingerprintsAFile` checks the SHA-256 of `abc`, a value every SHA-256 in the world agrees on.

3. **Read `SHA256SUMS`.** In `parse`, replace the `throw`:
   - `Map<String, String> checksums = new HashMap<>();`
   - `for (String line : sums.lines().toList())`: `Matcher match = LINE.matcher(line.strip());`, and if `match.matches()`, `checksums.put(match.group(2).strip(), match.group(1).toLowerCase());`
   - `return checksums;`

   Run `ChecksumsTest`: all 3 pass. `DownloaderTest` now passes 3 of 6; the other three say `Not written yet: Day 130, step 6`.

4. **Is there a newer version?** Open `UpdateChecker.java`. In `check`, after the `NoReleases` check, replace the `throw`:
   - `Release release = latest.get();`
   - if `release.version().compareTo(current) <= 0`, return `new UpdateCheck.UpToDate(current)`;
   - otherwise `release.installerFor(osName)` gives an `Optional<Asset>`. Turn it into the answer:

     ```java
     return release.installerFor(osName)
             .<UpdateCheck>map(installer -> new UpdateCheck.Available(current, release, installer))
             .orElseGet(() -> new UpdateCheck.NothingForThisSystem(current, release));
     ```

     `.<UpdateCheck>map(...)` tells Java the `Optional` holds an `UpdateCheck`, not an `Available`. Without it, `orElseGet` can't give back a `NothingForThisSystem` and it doesn't compile. (An `if` with `isPresent()` and `get()` works just as well.)

5. **Is the night on?** In `duringTheEvent`, replace the `throw`:
   - `Instant from = event.startsAt().toInstant().minus(SET_UP_TIME);`
   - `Instant to = event.endsAt().toInstant();`
   - `return !now.isBefore(from) && now.isBefore(to);`

   Run `../../mvnw test -Dtest=UpdateCheckerTest -Dsurefire.failIfNoSpecifiedTests=false`. All 6 pass.

6. **Only a checked file gets the installer's name.** Open `Downloader.java`. Where the TODO marked `step 6` is, replace the `throw`:
   - if `!Checksums.sha256(part).equals(expected)`: `Files.delete(part);` then `throw new UpdateException("The download doesn't match its checksum, so it was deleted. Try again");`
   - otherwise `return Files.move(part, done, StandardCopyOption.REPLACE_EXISTING);`. `Files.move` returns where the file is now.

   Run `DownloaderTest`: all 6 pass, including `followsGitHubsRedirect`, which checks your download went to `/download/...` and then to `/files/...`, like GitHub's.

7. **Run all the tests.** `../../mvnw test`. You should see 542 tests in all and `BUILD SUCCESS`.

8. **Ask the real GitHub.** Follow "Run it and test it" below. It's one read-only request to GitHub, with no key.

9. **One feature of your own.** Pick one from "One feature of your own" below, or your own idea, and build it the way the course did: a test first, then the code, then a commit.

## Run it and test it

From the day's `starter` folder:

```
cd day-130-box-office-1-0/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=DownloaderTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=DownloaderTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **274 tests** in `core` (including BoxOfficeVersion 1, Checksums 3, Downloader 6, GitHubReleases 6, UpdateChecker 6), **78** in `cli` (including UpdateCommand 8; the live eval from Day 127 is skipped), **48** in `desktop` and **142** in `sync server`, **542** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] Box Office sync server ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**Build the jars.** `../../mvnw package -DskipTests`. (On Windows, write `cli\target\boxoffice.jar` below.)

**The version, from the build:**

```
$ java -jar cli/target/boxoffice.jar --version
Box Office 1.0.0
```

**Check for an update, for real.** This asks GitHub's API about `sagebeme/box-office-30-days`. It sends nothing about you or your sales, and needs no key:

```
$ java -jar cli/target/boxoffice.jar update
Box Office 1.0.0: no releases published yet
```

That's the true answer today: the course repository has published no releases. On slow Wi-Fi you may first see a retry line from Day 115, like `Couldn't connect in time. Trying again in 0.3 s (attempt 2 of 5)`. `update` uses `gate.db` to know the event's times, and makes one if there's none, like every other command.

If you've published releases from your own repository (Day 129), check that one instead:

```
java -jar cli/target/boxoffice.jar update --repo your-name/your-box-office
```

**What happens when there is a release.** You can't see this against the real GitHub yet, so here's what `UpdateCommandTest` sees from its fake GitHub, which publishes 1.1.0 with a `.deb`, `.msi`, `.dmg` and `SHA256SUMS`. On a Tuesday after the night:

```
Box Office 1.1.0 is out (you have 1.0.0)

## Box Office 1.1.0

### New
- the gate updates itself

Run `update --download <folder>` to fetch BoxOffice-1.1.0.msi
```

(The installer named is the one for the system you're on: `.msi`, `.dmg` or `.deb`.) Then `update --download installers` fetches `SHA256SUMS`, follows the redirect to the installer, saves it as `installers/BoxOffice-1.1.0.msi.part`, checks it, renames it, and says:

```
Saved installers/BoxOffice-1.1.0.msi (its checksum matches). Close Box Office, then run it to update
```

When the fake swaps the installer's bytes after writing its checksum, like a broken or tampered download, the file is deleted and the command ends with exit code 1:

```
The download doesn't match its checksum, so it was deleted. Try again
```

And at 21:00 on the night, it doesn't download at all:

```
The night is on: updates wait until Kilele Nights ends at Sun 15 Nov, 04:00
```

**Checking a download by hand.** You can do what `Downloader` does yourself, in the folder with the installer and `SHA256SUMS`. Linux: `sha256sum -c SHA256SUMS --ignore-missing`. macOS: `shasum -a 256 -c SHA256SUMS --ignore-missing`. Windows PowerShell: `Get-FileHash BoxOffice-1.1.0.msi -Algorithm SHA256`, then compare it with the `.msi` line in `SHA256SUMS` (PowerShell writes it in capitals; that's the same checksum).

## When you're stuck

- **`Not written yet: Day 130, step N`**: that method still has its `throw`. `Downloader` calls `Checksums.parse` first, which is why its tests say `step 3` until you do step 3.
- **`fingerprintsAFile` says `expected: "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad" but was: "17fe03c5f83a1a6e08a786ab8f5382314b60281d39b4b3cb321bbd579dda98af"`**: you wrote `digest.update(buffer)`, which fingerprints the whole 64 KB buffer, zeros and all, not just the 3 bytes of `abc`. Use `digest.update(buffer, 0, read)`. The same mistake makes `downloadsAndChecksTheInstaller` say `The download doesn't match its checksum, so it was deleted. Try again`, about a file that was fine.
- **`readsWhatSha256sumWrites` says `but the following map entries had different values: ["BoxOffice-1.1.0.msi"="BA7816BF..."`**: lower-case the checksum, `match.group(1).toLowerCase()`, so a line in capitals still matches.
- **`theSameOrAnOlderVersionIsUpToDate` says `expected: UpToDate[current=1.0.0] but was: Available[...]`**: "the same version" is up to date too. Use `<= 0`, not `< 0`.
- **`aReleaseWithNothingForThisSystem` says `expected: NothingForThisSystem[...]`**: when `installerFor` is empty, the answer is `NothingForThisSystem`, not `UpToDate`.
- **`incompatible types: bad return type in lambda expression`** (when you compile): Java thinks the `Optional` holds an `Available`. Write `.<UpdateCheck>map(...)`, with the type in angle brackets, or use an `if`.
- **`noUpdatesFromTwoHoursBeforeTheDoorsUntilTheEnd` says `Expecting value to be true but was false`**: 16:00 exactly must count. `!now.isBefore(from)` is "at or after"; `now.isAfter(from)` isn't. If it says `Expecting value to be false but was true`, 04:00 is counting: use `now.isBefore(to)`.
- **`aFileThatDoesntMatchItsChecksumIsDeleted` says `Expecting path: ... not to exist`**: delete the `.part` file before throwing, and give the file its real name only after the check passes.
- **`downloadsAndChecksTheInstaller` says `Expecting value to be false but was true`**: the `.part` file is still there. Use `Files.move`, not `Files.copy`.
- **`update` says `Couldn't reach GitHub to check for updates: Gave up after 5 attempts: ...`**: the laptop isn't online, or GitHub is blocked on this network. The gate keeps working; try again later.
- **`update` says `GitHub answered HTTP 403 about sagebeme/box-office-30-days`**: you've asked more than 60 times this hour from this network. Wait an hour.
- **`update --repo` with a typo still says `no releases published yet`**: GitHub answers 404 for a repository that doesn't exist (or is private), the same as for one with no releases. Check the spelling: `owner/name`.
- **`update --repo sagebeme` says `A repository is owner/name, like sagebeme/box-office-30-days`**: give both parts, with a `/`.
- **`update --download` says `The night is on: ...`**: your laptop's real clock is between 16:00 on 14 November and 04:00 on 15 November, Nairobi time. That's the rule working.
- **`version.properties is missing: build with Maven`**: your editor ran the code without Maven copying the resources. Run `../../mvnw package -DskipTests` once, or run the tests with Maven.
- **`cli`, `desktop` and `sync server` are `SKIPPED`**: `core` failed first. Finish steps 2 to 6.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/update/Checksums.java` (or `UpdateChecker.java`, `Downloader.java`), read it once, close it, then write your own from memory.

## Box Office 1.0: what you built

Thirty days ago, Box Office was a parent pom and one test that said "Box Office is open". Today it's this:

- **It sells, offline.** Tickets, events and money as records, KES in cents, time in Nairobi (Days 101–103). Every sale saved in SQLite on the gate laptop, with Flyway migrations (104). A guest list from a CSV and a sales report (105).
- **It's quick at the gate.** A JavaFX window with a live sales table (106, 107), keyboard-only selling with Kenyan phone numbers (108), and a window that never freezes, even on a slow disk (109).
- **It checks people in.** A QR code per ticket, and a USB scanner that types like a keyboard (110).
- **It survives the venue.** A sale is never half-saved when the power goes (111). Receipts print, and reports export as PDF (112). Box Office v1, tested end to end (113).
- **It talks to the outside.** Reminder texts on virtual threads (114). An HTTP client that survives bad Wi-Fi (115). An outbox that queues every sale and syncs it later, in order, exactly once (116).
- **Two gates agree.** Over the LAN without internet (117), without overselling in a rush (118), with a sync server that logs each laptop in (119) and settles the last ticket (120).
- **Fans buy online.** A public ticket site (121) that takes M-Pesa (122), behind a security pass (123), in a Docker image ready to deploy (124).
- **Staff can ask it things.** "How many VIP are left?" answered by Claude from the night's numbers (125), with read-only lookups it can call (126), and evals that prove the answers stay careful (127).
- **It's real software.** Installers for Windows, macOS and Linux (128), built and published by a release pipeline (129), and an app that knows its version and finds the next one safely (130).

More than 500 tests, all passing, every day.

## One feature of your own

The course gave you the steps until now. This one is yours. Pick one feature, small enough for a day or two, and build it the way every day did: write the test first, watch it fail, make it pass, commit. Some ideas that fit Box Office, with where to start:

1. **A refund at the gate, with an audit trail.** Staff can refund a ticket, and the gate keeps who did it, when and why. Start with a `V8__add_refunds.sql` migration (a `refunds` table: ticket code, reason, staff name, time) and a `TicketStore.refund(...)` in one transaction, like Day 111's group sales. A refunded ticket must not check in: add a test in the style of `CheckInOutcome`'s tests. Then a button on the check-in screen. Gate B's `conflicts` list from Day 120 is a good first user.
2. **A second event night.** Kilele Nights is so good there's a Sunday too. Today `SampleEvent.ensureIn` puts one event in `gate.db`. Let `setup` take an event name and dates, and `sell` and `report` take `--event`. `TicketStore` already stores events by id; the work is in the CLI and in which event the window shows. Test that Saturday's tickets don't check in on Sunday.
3. **Swahili labels.** "Uza tiketi", "Tiketi imeingia". Move the window's words into `messages.properties` and `messages_sw.properties` (a Java `ResourceBundle`), and use `%key` in `dashboard.fxml`. Pick the language with a `--lang sw` option or the system's language. Start with the sell screen, and a test that loads the window in Swahili and finds the sell button by its Swahili text.
4. **SMS tickets with QR links.** Add a link to each reminder text from Day 114 (`ReminderSender.messages`), to a page on the ticket site from Day 121 that shows the ticket's QR code (`QrCodes`). Careful: whoever has the link has the ticket. Put a long random token in the link, never the ticket code alone, and keep the text under 160 characters. Test the message length and that the page refuses a wrong token.
5. **A "gate is offline" banner.** When sync hasn't worked for five minutes, the window shows a red line: "Offline since 21:14. Sales are safe and will sync later." Start in `DashboardModel` with a property for the last good sync (the `SyncReport` from Day 116 says when it worked), and test it with a fixed `Clock` like Day 103, before you touch the window.
6. **A model-graded eval.** Day 127's graders check answers with code. Some things code can't check well: "is this answer polite and clear for gate staff?". Add a grader in `Graders` that asks Claude to score an answer against a short rubric, and returns pass or fail with its reason. Run it only in the live run, like Day 127's `LiveEval`, and check it against the careful and lazy answers you already have: it must pass the careful ones and fail the lazy ones.
7. **The update check in the window.** Today only the CLI checks. When the window opens, and the night isn't on, ask `UpdateChecker` in a background `Task` (Day 109) and show "Box Office 1.1.0 is out" with the notes. Never block the window on GitHub. Test the model with a fake `ReleaseFeed`, which is why it's an interface.

Write your feature's own README in the same shape as the course's: the brief, new words, steps, when you're stuck. If you can explain it to someone at the gate, you understand it.

## Checklist

- [ ] Can say how `${project.version}` gets into the app, and why the SQL migrations are never filtered
- [ ] Can say what GitHub's 404 means for `releases/latest`, and why drafts never reach a gate
- [ ] Can say what a SHA-256 checksum proves, and what only signing can prove
- [ ] Can say why a download goes to `.part` first, and when it gets its real name
- [ ] Can say why the update waits for the night to end, and why that needs the real clock
- [ ] Wrote `sha256`, `parse`, `check`, `duringTheEvent` and the check in `download`
- [ ] All 542 tests pass
- [ ] Ran `update` against the real GitHub and read its answer
- [ ] Picked one feature of your own, and wrote its first test
- [ ] Committed your work to Git (not `gate.db`, not a downloaded `installers/` folder, and never a key or certificate)

## Next

That's the course. Thirty days ago you had a welcome test; today Box Office sells, scans, syncs, takes M-Pesa, answers questions, installs on three systems and finds its own updates, with every day tested. The next step is the one you choose: build your feature, put Box Office in your own repository with its release pipeline, tag `v1.1.0`, and watch the first real run. Then take it to a real gate. Asante, and see you at Kilele Nights.
