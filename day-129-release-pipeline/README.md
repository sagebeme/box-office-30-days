# Day 129: A release pipeline

> Thursday before the night. Otieno fixed the receipt date and added two features. He types `git tag v1.1.0` and goes to make chai. When he's back, there's an installer for every laptop at the gate.

Watch the video: *(not recorded yet)*

## The brief

Yesterday you built an installer on your own laptop. That works, but only for your own system: jpackage makes a `.deb` on Linux, an `.msi` on Windows and a `.dmg` on a Mac, and it can't make one for another system. Kilele Nights has a Windows laptop at gate A, an old MacBook at gate B and Ubuntu on the organiser's laptop. Nobody owns all three.

So today a computer does the release for you. **GitHub Actions** lends you a fresh Linux, Windows and Mac machine for each release. You push a **tag**, a name like `v1.1.0` on one commit, and the **release pipeline** does the rest:

1. On each of the three systems: check the tag matches the version in `pom.xml`, run every test, build that system's installer.
2. Write the **release notes** from the commit messages since the last release.
3. Write `SHA256SUMS`, a fingerprint of every installer, so people can check their download.
4. Put it all on a **GitHub Release**: a page with the notes and the three files to download.

The release notes come from a new CLI command you finish today. It reads commit subjects, works out the next version, and writes Markdown:

```
$ git log --format=%s v1.0.0..HEAD | java -jar cli/target/boxoffice.jar release-notes --last v1.0.0
## Box Office 1.1.0

### New
- the gate checks for updates

### Fixed
- receipts print the right date
- desktop: the total lagged one sale behind
```

It knew the next version is 1.1.0, not 1.0.1 or 2.0.0, from the commit messages alone. You write that part: reading a commit message, working out the next version, and writing the notes. Then you finish the workflow file.

## What you'll learn

- What a **release** is, and why "it works on my laptop" isn't one
- How **Semantic Versioning** says what a version number promises: 1.0.1, 1.1.0 or 2.0.0
- How **Conventional Commits** (`feat:`, `fix:`, `feat!:`) let a program pick the next version
- Why versions compare as numbers, not as text: 1.10.0 is newer than 1.9.0
- How a GitHub Actions **workflow** runs on a tag, and how a **matrix** runs one job on three systems
- Why the workflow is read-only, except for the one job that publishes
- What **code signing** is, why Windows and macOS warn about unsigned apps, and what it costs
- Why you test a workflow file on your laptop before GitHub runs it

## New words

- **Release**: one version of the app, built, named and published for people to install. Not "whatever is on my laptop today".
- **Version number**: a name for a release, like `1.1.0`.
- **Semantic Versioning** (SemVer): a rule for version numbers, `MAJOR.MINOR.PATCH`. A fix adds one to PATCH (1.0.0 to 1.0.1). Something new that breaks nothing adds one to MINOR (1.0.1 to 1.1.0). Something that breaks what worked before adds one to MAJOR (1.1.0 to 2.0.0).
- **Bump**: moving the version up. Bumping a bigger part sets the smaller ones back to 0: 1.4.2 bumped MINOR is 1.5.0.
- **Breaking change**: a change after which something that worked before stops working. Gates that suddenly need a venue key (Day 123) is one.
- **Commit subject**: the first line of a commit message.
- **Conventional Commits**: a style for commit subjects, `type(scope)!: description`. This repository uses it: `feat: Day 129 adds a release pipeline...`.
- **Type**: the word before the colon. `feat` is something new, `fix` a fix, `perf` faster, and `chore`, `docs`, `test`, `refactor`, `ci` are changes a user never notices.
- **Scope**: an optional part name in brackets, like `fix(desktop):`.
- **Release notes**: the list of what changed, for the people who install it.
- **Tag**: a name stuck on one commit, like `v1.1.0`. It never moves, so `v1.1.0` always means the same code.
- **CI** ("continuous integration"): a computer somewhere that builds and tests your code every time you push. This course's own CI runs `tools/verify.sh`.
- **GitHub Actions**: GitHub's CI. It runs **workflows**.
- **Workflow**: a YAML file in `.github/workflows/` that says when to run (`on:`) and what to run (`jobs:`).
- **YAML**: a text format where indentation is meaning. Two spaces too many or too few, and it means something else.
- **Job**: one part of a workflow, run on one fresh machine (a **runner**). A job is a list of **steps**.
- **Action**: a ready-made step, like `actions/checkout@v4` ("get the code").
- **Matrix**: "run this job once for each of these", here once per system.
- **Artifact**: a file one job saves so a later job can use it. The installers travel from the three build jobs to the release job this way.
- **GitHub Release**: a page on your repository for one tag, with notes and files to download.
- **Secret**: a value you give GitHub (a password, a certificate) that a workflow can use but nobody can read back, not even you.
- **Code signing**: stamping an installer with a certificate that says who made it, so Windows or macOS can check it wasn't changed and who to blame if it's bad.
- **Certificate**: a file a company (a **certificate authority**) sells you after checking who you are. On Windows it's often a `.pfx` file with a password.
- **SmartScreen**: Windows' check on downloaded apps. **Gatekeeper**: the Mac's.
- **Notarisation**: Apple's scan of a Mac app before it lets Gatekeeper open it without a warning.

## What's already written

From Days 101–128: Box Office, installed like real software, with tests. New today, already done:

- A new package, `com.boxoffice.core.release`:
  - `Bump`: an enum, `NONE`, `PATCH`, `MINOR`, `MAJOR`, smallest first.
  - `ConventionalCommit`: a record (`type`, `scope`, `breaking`, `description`) and its pattern, `FORMAT`. You write `parse` and `bump`.
  - `SemVer`: a record (`major`, `minor`, `patch`) with `parse("v1.2.3")`, `compareTo` and `toString`. You write `bump` and `next`.
  - `ReleaseNotes`: `section(...)`, which writes one `### heading` and its lines, or nothing for an empty list. You write `render`.
- The CLI's new command, `release-notes` (in `ReleaseNotesCommand.java`): `--last v1.0.0`, an optional `--version v1.1.0` for the heading, and `--commits FILE`. Without `--commits`, it reads the subjects from its input, so `git log` can pipe into it.
- `starter/.github/workflows/release.yml`: the release workflow, nearly whole. You add two systems and one step.
- Tests: `ConventionalCommitTest` (5), `SemVerTest` (5), `ReleaseNotesTest` (2) in `core`; `ReleaseNotesCommandTest` (6) and `ReleaseWorkflowTest` (8) in `cli`.

New, for you to finish: `ConventionalCommit.parse` and `bump` (step 2), `SemVer.bump` and `next` (step 3), `ReleaseNotes.render` (step 4), and two pieces of `release.yml` (step 5). The TODOs are labelled with their step numbers.

## What you'll use

**What a version number promises.** People who install Box Office don't read your code. The number tells them what to expect:

| From 1.0.0 to | Means | Example commit |
|---|---|---|
| 1.0.1 (PATCH) | Fixed. Nothing new to learn. | `fix: receipts print the right date` |
| 1.1.0 (MINOR) | Something new. Everything you did before still works. | `feat: the gate checks for updates` |
| 2.0.0 (MAJOR) | Something you did before works differently. Read the notes. | `feat(sync)!: gates need a venue key` |

**One release, one bump: the biggest wins.** Otieno's release has two fixes and one feature. Fixes alone would be 1.0.1; the feature makes it 1.1.0, and 1.1.0 already says "fixed too". A breaking change in the pile would make it 2.0.0. Chores, docs and tests make no new version at all: `release-notes` says `Nothing to release since 1.0.0: no feat or fix commits`.

That's why `Bump` lists its values smallest first. Every enum can say which value comes later with `compareTo`:

```java
Bump.MINOR.compareTo(Bump.PATCH) > 0     // true: MINOR is listed after PATCH, so it's bigger
```

**Reading a commit subject.** `ConventionalCommit.FORMAT` is already written:

```java
([A-Za-z]+)(?:\(([^)\s]+)\))?(!)?: (.+)
```

Piece by piece: `([A-Za-z]+)` the type, group 1. `(?:\(([^)\s]+)\))?` an optional `(scope)`, the scope itself is group 2. `(!)?` an optional `!`, group 3. Then `: ` (a colon and a space) and `(.+)` the description, group 4. `(?: ...)` is brackets that group without counting as a group.

`Pattern.matcher(text)` gives a `Matcher`. `matches()` says if the **whole** text fits. After that, `group(2)` gives what group 2 caught, or `null` if that optional part wasn't there:

```java
Matcher match = FORMAT.matcher("fix(desktop): the total lagged");
match.matches();    // true
match.group(1);     // "fix"
match.group(2);     // "desktop"
match.group(3);     // null: no "!"
match.group(4);     // "the total lagged"
```

A subject that doesn't fit, like `Merge pull request #1 from sagebeme/feat/days-111-120` or `feat:no space`, isn't thrown away. It becomes type `other`, so a release never silently loses a commit. It just doesn't move the version.

`Feat:` with a capital F counts as `feat`: lower-case the type with `toLowerCase(Locale.ROOT)`. (`Locale.ROOT` means "no language's special rules". In Turkish, plain `toLowerCase()` turns `I` into a dotless `ı`.)

**Versions compare as numbers.** As text, `"1.10.0"` sorts before `"1.9.0"`, because `1` comes before `9`. As a version, 1.10.0 is newer. `SemVer` keeps three `int`s and compares major first, then minor, then patch, so it gets it right. Day 130's update check depends on this.

**Records don't change.** `SemVer` is a record, so `bump` can't change `minor`. It returns a new `SemVer`:

```java
case MINOR -> new SemVer(major, minor + 1, 0);
```

A `switch` over an enum with `->` cases must cover every value. Forget `NONE` and it doesn't compile.

**The workflow, from the top.** Open `starter/.github/workflows/release.yml` and read it beside this. It's YAML: indentation matters, and `#` starts a comment.

```yaml
on:
  push:
    tags: ["v*.*.*"]
```

It runs when a tag like `v1.1.0` is pushed, and at no other time. Pushing commits doesn't release anything.

```yaml
permissions:
  contents: read
```

Every job gets a token from GitHub to work on your repository. By default it's read-only. Only the `release` job says `contents: write`, because it's the only one that creates a release. If a library the build downloads were ever bad, it would get a token that can't change anything.

**Job 1, `installers`, runs three times.** The matrix lists one line per system:

```yaml
matrix:
  include:
    - os: ubuntu-latest
      type: deb
```

`runs-on: ${{ matrix.os }}` picks the machine. `${{ matrix.type }}` is filled in with `deb`, `msi` or `dmg`. `fail-fast: false` means a Mac failure doesn't cancel Windows, so you learn about both. On each machine, the steps:

- `actions/checkout@v4` gets the code, and `actions/setup-java@v4` installs Temurin 21, the course's Java. `@v4` pins a version, so the action can't change under you.
- **Check the tag matches the pom.** `./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout` prints the version in `pom.xml`. Tag `v1.1.0` with a pom that says `1.0.0`, and the job stops: `The tag is v1.1.0 but pom.xml says 1.0.0. Set the version first.` Otherwise the installer called 1.1.0 would say 1.0.0 inside.
- **Install WiX 3**, on Windows only (`if: runner.os == 'Windows'`). jpackage on JDK 21 needs the WiX Toolset 3 to make an `.msi`, as on Day 128. `choco` is a package installer for Windows that's already on GitHub's Windows machines.
- **Test, then build the installer**: `./mvnw -B package -Pinstaller -Dinstaller.type=${{ matrix.type }}`. That's Day 128's command. `package` runs every test first, so a release can't be built from code that fails its tests. `-B` is "batch mode": no colours or progress bars in the log.
- **Sign the Windows installer**, only if you've added a certificate (below).
- `actions/upload-artifact@v4` saves the installer as `installer-deb`, `installer-msi` or `installer-dmg`. `if-no-files-found: error` makes a build that made no installer fail loudly instead of publishing nothing.

**Job 2, `release`, runs once, after all three** (`needs: installers`):

- `fetch-depth: 0` gets the whole history and every tag. By default, checkout gets only the newest commit, and there'd be no `git log` to write notes from.
- It builds the CLI and downloads the three installers into `installers/`.
- **Release notes.** `git describe --tags --abbrev=0 "$GITHUB_REF_NAME^"` finds the tag before this one. For your very first release there's none, so it's `v0.0.0` and the notes cover the whole history. Then `git log --format=%s` prints one subject per line, and pipes them into your `release-notes` command. `$GITHUB_REF_NAME` is GitHub's name for the tag that was pushed.
- **Checksums**: `sha256sum * > SHA256SUMS` in the `installers` folder. One line per installer: its SHA-256 and its name. You add this step.
- **Publish**: `gh release create "$GITHUB_REF_NAME" installers/* --title ... --notes-file notes.md`. `gh` is GitHub's own command-line tool, already on the machine.

**Where the workflow must live.** GitHub only reads workflows from `.github/workflows/` at the **top** of a repository. In this course repository, today's file is two folders down (`day-129-release-pipeline/solution/.github/`), so GitHub never runs it here. That's on purpose: nobody wants a release every time the course changes. To use it, copy it into your own copy of Box Office, at the top, next to `pom.xml`. The workflow runs `./mvnw`, so your repository needs `mvnw`, `mvnw.cmd` and the `.mvn/` folder at the top too (copy them from the top of this course repository).

**Test the workflow before GitHub runs it.** A workflow only runs on GitHub, after a tag is pushed: a slow and public place to find a typo. `ReleaseWorkflowTest` reads `release.yml` as plain text and checks what matters: the tag trigger, all three systems, Temurin 21, the version check, read-only permissions, the notes and checksums, signing only with a secret, and that every action names a version. It can't run the workflow. It catches the typos.

**Code signing: why it matters.** Download an unsigned `.msi` and double-click it, and Windows SmartScreen says "Windows protected your PC" with only a small "More info" link. On a Mac, Gatekeeper says the app "can't be opened because Apple cannot check it for malicious software". At 17:00 on the night, Wanjiku at gate A shouldn't have to decide whether to trust that.

Signing puts a certificate on the installer. Windows can then show who made it ("Verified publisher") and check that not one byte has changed since. It also protects people from a swapped file: someone who changes the installer can't sign it with your certificate. (Checksums, tomorrow, catch a broken download. Only signing proves who made the file.)

**Signing on Windows.** You need a code-signing certificate from a certificate authority. They check who you are, and they cost money every year. If you have one as a `.pfx` file, add two secrets in your repository on GitHub (Settings > Secrets and variables > Actions):

- `WINDOWS_CERTIFICATE`: the `.pfx` file, turned into text with base64. On Linux: `base64 -w0 cert.pfx`. On macOS: `base64 -i cert.pfx`. In PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("cert.pfx"))`.
- `WINDOWS_CERTIFICATE_PASSWORD`: its password.

The signing step only runs when `WINDOWS_CERTIFICATE` isn't empty, so without the secrets the workflow still works and the `.msi` is unsigned. The step writes the certificate to a file, signs with Microsoft's `signtool` (SHA-256, with a **timestamp** from DigiCert, so the signature stays good after the certificate expires), then deletes the file. Never commit the `.pfx` file, and never paste its base64 anywhere but the secret.

**Signing on macOS: not wired in.** It needs an Apple Developer ID: a paid Apple Developer Program membership, a "Developer ID Application" certificate, and notarisation, where you upload the app to Apple and wait for its scan. jpackage can sign: see its `--mac-sign`, `--mac-signing-key-user-name` and `--mac-package-identifier` options (`jpackage --help` lists them). Notarising is a separate step with Apple's `xcrun notarytool`. Until you do both, people on a Mac open the `.dmg` once with right-click > Open, or allow it in System Settings > Privacy & Security. Linux `.deb` files you download yourself are usually unsigned; the checksums are what people check.

**Honest note: this workflow hasn't run on GitHub yet.** The tests check its text, and each command in it works on its own. But nobody has pushed a tag with it. In particular, the WiX install through `choco`, and running the whole test suite on GitHub's Windows and macOS machines, aren't proven. Watch your first run in your repository's **Actions** tab, and read the log of any red step. That's normal for a new pipeline: the first run is the real test.

## Steps

1. **Open the starter and run the tests.** `cd day-129-release-pipeline/starter`, then `../../mvnw test` (on Windows, `..\..\mvnw.cmd test`). You should see `Tests run: 252, Failures: 0, Errors: 9` in `core` and `BUILD FAILURE`; `cli`, `desktop` and `sync server` are `SKIPPED`:
   - all 5 `ConventionalCommitTest` tests, both `ReleaseNotesTest` tests and `SemVerTest.theBiggestChangeDecidesTheNextVersion`: `UnsupportedOperation Not written yet: Day 129, step 2`;
   - `SemVerTest.aBumpResetsTheSmallerParts`: `UnsupportedOperation Not written yet: Day 129, step 3`.

   The `cli` tests don't run while `core` fails. When they do, `ReleaseWorkflowTest.buildsEachInstallerOnItsOwnSystem` fails until step 5, and some `ReleaseNotesCommandTest` tests fail until step 4.

2. **Read a commit subject.** Open `starter/core/src/main/java/com/boxoffice/core/release/ConventionalCommit.java`. In `parse`:
   - `String line = subject.strip();` and `Matcher match = FORMAT.matcher(line);`
   - if `!match.matches()`, return `new ConventionalCommit("other", "", false, line)`;
   - otherwise return a `ConventionalCommit` of `match.group(1).toLowerCase(Locale.ROOT)`, then `match.group(2) == null ? "" : match.group(2)`, then `match.group(3) != null`, then `match.group(4).strip()`.

   In `bump`: if `breaking`, return `Bump.MAJOR`, whatever the type. Otherwise a `switch` on `type`: `case "feat" -> Bump.MINOR;`, `case "fix", "perf" -> Bump.PATCH;`, `default -> Bump.NONE;`.

   Run `../../mvnw test -Dtest=ConventionalCommitTest -Dsurefire.failIfNoSpecifiedTests=false`. All 5 pass. The `SemVerTest` and `ReleaseNotesTest` errors now say `step 3` and `step 4`.

3. **Work out the next version.** Open `SemVer.java`. In `bump`, `return switch (bump) { ... };` with one case per value:
   - `NONE -> this`;
   - `PATCH -> new SemVer(major, minor, patch + 1)`;
   - `MINOR -> new SemVer(major, minor + 1, 0)`;
   - `MAJOR -> new SemVer(major + 1, 0, 0)`.

   In `next`: start with `Bump biggest = Bump.NONE;`. For each commit, if `commit.bump().compareTo(biggest) > 0`, it's bigger: keep it in `biggest`. After the loop, `return bump(biggest);`.

   Run `SemVerTest` the same way: all 5 pass.

4. **Write the notes.** Open `ReleaseNotes.java`. In `render`:
   - make three `List<String>`s: `breaking`, `added`, `fixed`;
   - for each commit, build the line: `"- " + (commit.scope().isEmpty() ? "" : commit.scope() + ": ") + commit.description()`;
   - `switch (commit.bump())`: `MAJOR` adds it to `breaking`, `MINOR` to `added`, `PATCH` to `fixed`, and `NONE -> { }` leaves it out;
   - then `StringBuilder out = new StringBuilder("## Box Office ").append(version).append('\n');`, then `section(out, "Breaking changes", breaking)`, `section(out, "New", added)`, `section(out, "Fixed", fixed)`, in that order, and `return out.toString();`.

   Run `../../mvnw test`. `core` passes now (252 tests). In `cli`, `ReleaseNotesCommandTest` passes (6); only `ReleaseWorkflowTest.buildsEachInstallerOnItsOwnSystem` fails, with `Expecting actual: "# Day 129: the release pipeline. ...` (the whole file) `to contain: "- os: windows-latest` and `type: msi"`.

5. **Finish the workflow.** Open `starter/.github/workflows/release.yml`. It's a hidden folder: in a file browser, show hidden files; in your editor, it's in the project tree.
   - Under the `ubuntu-latest` lines, where the TODO marked `step 5` is, add Windows and macOS in exactly the same shape, lined up with the `- os: ubuntu-latest` above:

     ```yaml
               - os: windows-latest
                 type: msi
               - os: macos-latest
                 type: dmg
     ```

   - In the `release` job, where the second TODO marked `step 5` is, between "Write the release notes" and "Publish the release", add the checksums step, lined up with the other `- name:` lines:

     ```yaml
           - name: Write the checksums
             working-directory: installers
             run: sha256sum * > SHA256SUMS
     ```

   Use spaces, never tabs: YAML doesn't allow tabs. Delete the two TODO comments.

6. **Run all the tests.** `../../mvnw test`. You should see 517 tests in all and `BUILD SUCCESS`.

7. **Write the notes for a pretend release.** Follow "Run it and test it" below: you make a throwaway Git repository with a few commits and a tag, and pipe its log into `release-notes`.

8. **Put the pipeline in your own repository (optional).** If you have your own copy of Box Office on GitHub, with `pom.xml` at the top, copy `solution/.github/workflows/release.yml` to `.github/workflows/release.yml` at the top of it, with `mvnw`, `mvnw.cmd` and `.mvn/`. Then follow "The release routine" below, and watch the run in the **Actions** tab. Never push a tag in the course repository itself.

9. **Stretch (not required): a test of your own.** In `ConventionalCommitTest`, check that `perf(sync)!: faster pull` is breaking and bumps `MAJOR`, or that `fix(two words): x` (a scope with a space) becomes `other`. Copy the shape of the tests above. Your test isn't part of the course's tests, so keep it in your own Git.

## Run it and test it

From the day's `starter` folder:

```
cd day-129-release-pipeline/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=ReleaseWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=ReleaseWorkflowTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **252 tests** in `core` (including ConventionalCommit 5, SemVer 5, ReleaseNotes 2), **70** in `cli` (including ReleaseNotesCommand 6, ReleaseWorkflow 8; the live eval from Day 127 is skipped), **48** in `desktop` and **147** in `sync server`, **517** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] Box Office sync server ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**Build the CLI.** `../../mvnw package -DskipTests`.

**A pretend release, in a throwaway repository.** Make a new folder **outside** the course repository, for example in your home folder, so nothing touches your real history. Then make a repository with a release `v1.0.0` and five commits after it. `--allow-empty` makes a commit with no files: only the message matters today.

macOS / Linux (set `BOXOFFICE` to your own `starter` folder):

```
BOXOFFICE=~/box-office-30-days/day-129-release-pipeline/starter
mkdir ~/release-practice && cd ~/release-practice
git init
git commit --allow-empty -m "feat: Box Office 1.0"
git tag v1.0.0
git commit --allow-empty -m "fix(desktop): the total lagged one sale behind"
git commit --allow-empty -m "docs: Day 129 README"
git commit --allow-empty -m "feat: the gate checks for updates"
git commit --allow-empty -m "chore: tidy the poms"
git commit --allow-empty -m "fix: receipts print the right date"
```

Windows PowerShell: the same `git` lines, with `$env:BOXOFFICE="C:\...\day-129-release-pipeline\starter"` first, `mkdir ~\release-practice` and `cd ~\release-practice`. (If Git asks who you are, it tells you the two `git config` lines to run.)

What happened since `v1.0.0`, newest first:

```
$ git log --format=%s v1.0.0..HEAD
fix: receipts print the right date
chore: tidy the poms
feat: the gate checks for updates
docs: Day 129 README
fix(desktop): the total lagged one sale behind
```

Pipe it into `release-notes`:

```
$ git log --format=%s v1.0.0..HEAD | java -jar $BOXOFFICE/cli/target/boxoffice.jar release-notes --last v1.0.0
## Box Office 1.1.0

### New
- the gate checks for updates

### Fixed
- receipts print the right date
- desktop: the total lagged one sale behind
```

(In PowerShell, write `$env:BOXOFFICE\cli\target\boxoffice.jar`.) The chore and the docs commit are left out: nobody at the gate notices them. Now add a breaking change and run it again:

```
$ git commit --allow-empty -m "feat(sync)!: gates need a venue key"
$ git log --format=%s v1.0.0..HEAD | java -jar $BOXOFFICE/cli/target/boxoffice.jar release-notes --last v1.0.0
## Box Office 2.0.0

### Breaking changes
- sync: gates need a venue key

### New
- the gate checks for updates

### Fixed
- receipts print the right date
- desktop: the total lagged one sale behind
```

The `!` made it 2.0.0. Two more, with a file of subjects instead of a pipe. (`printf` is macOS / Linux; in PowerShell, `Set-Content subjects.txt "chore: tidy","docs: more words"` makes the same file.)

```
$ printf 'chore: tidy\ndocs: more words\n' > subjects.txt
$ java -jar $BOXOFFICE/cli/target/boxoffice.jar release-notes --last v1.0.0 --commits subjects.txt
Nothing to release since 1.0.0: no feat or fix commits

$ java -jar $BOXOFFICE/cli/target/boxoffice.jar release-notes --last latest --commits subjects.txt
Not a version like 1.2.3: latest
```

Both end with exit code 1, so a workflow step that runs them stops there. Delete `~/release-practice` when you're done.

**The release routine.** In your own Box Office repository (never the course one), with everything committed:

1. **Write the notes and read them.** `git log --format=%s v1.0.0..HEAD | java -jar cli/target/boxoffice.jar release-notes --last v1.0.0`. The heading says the next version, say 1.1.0.
2. **Set the version** in every pom at once:

   ```
   ../../mvnw versions:set -DnewVersion=1.1.0 -DgenerateBackupPoms=false     # macOS / Linux
   ..\..\mvnw.cmd versions:set -DnewVersion=1.1.0 -DgenerateBackupPoms=false  # Windows
   ```

   (In your own repository, with `mvnw` at the top, it's `./mvnw` or `.\mvnw.cmd`.) Tried on a copy of today's solution, it updates all five poms: the parent's `<version>`, and the `<parent>` version in `core`, `cli`, `desktop` and `server`:

   ```
   [INFO] Processing com.boxoffice:cli
   [INFO]     Updating parent com.boxoffice:box-office
   [INFO]         from version 1.0.0 to 1.1.0
   ...
   [INFO] BUILD SUCCESS
   ```

   `-DgenerateBackupPoms=false` stops it leaving a `pom.xml.versionsBackup` beside each pom.
3. **Run the tests.** They should all still pass. No test types the version in: the parent `pom.xml` hands `${project.version}` to the tests as the `boxoffice.version` system property (look for `systemPropertyVariables`), and `HealthTest` checks against that. The workflow runs every test, so a release with a red test never gets built.
4. **Commit**: `git commit -am "chore: release 1.1.0"`.
5. **Tag and push the tag**: `git tag v1.1.0`, then `git push origin v1.1.0`. Pushing the tag is what starts the workflow. A tag is for ever: if the run fails, fix it, set version 1.1.1, and tag that.

## When you're stuck

- **`Not written yet: Day 129, step 2`** (or `step 3`, `step 4`): that method still has its `throw`. Many tests use `parse`, so step 2's message shows up in `SemVerTest` and `ReleaseNotesTest` too.
- **`theTypeDecidesTheBump` says `expected: MINOR but was: NONE`**: `Feat: x` wasn't read as `feat`. Lower-case the type with `toLowerCase(Locale.ROOT)`.
- **`readsTheTypeAndTheDescription` says `expected: ConventionalCommit[type=feat, scope=, ...]` but was `ConventionalCommit[type=feat, scope=null, ...]`**: `group(2)` is `null` when there's no scope. Use `""` instead.
- **`anExclamationMarkMeansItBreaksSomething` says `Expecting value to be true but was false`**: `breaking` is `match.group(3) != null`. Or it's `true` but `bump` isn't `MAJOR`: check `breaking` before the `switch` on the type.
- **`anythingElseIsKeptAsOther` fails with `IllegalState No match found`**: you called `group(...)` without checking `matches()` first. Return the `other` commit when it doesn't match.
- **`aBumpResetsTheSmallerParts` says `Expecting actual's toString() to return: "1.5.0" but was: "1.5.2"`**: a MINOR bump must set patch to 0. For MAJOR, both minor and patch.
- **`theBiggestChangeDecidesTheNextVersion` says `to return: "1.1.0"` but was `"1.0.1"`**: `next` used the first or the last commit's bump, not the biggest. Compare each with `compareTo`.
- **`groupsWhatUsersNotice` shows the sections in another order, or `### Fixed` before `### New`**: call `section(...)` for "Breaking changes", "New", "Fixed", in that order.
- **`emptySectionsAreLeftOut` shows an empty `### New`**: you wrote the headings yourself. `section(...)` already writes nothing for an empty list.
- **`buildsEachInstallerOnItsOwnSystem` prints the whole file and says `to contain: "- os: windows-latest`**: the two systems are missing, or their indentation differs from the `ubuntu-latest` lines. `type:` is two spaces further in than `- os:`.
- **`publishesNotesAndChecksums` says `to contain: "sha256sum * > SHA256SUMS"`**: the checksums step is missing, or spelt differently.
- **`release-notes` waits and prints nothing**: without `--commits` it reads from its input. Pipe `git log` into it, or give it `--commits FILE`. Press Ctrl+C to stop it.
- **`fatal: ambiguous argument 'v1.0.0..HEAD': unknown revision or path not in the working tree.`**: there's no tag `v1.0.0` in this repository. `git tag` lists the tags.
- **On GitHub, the run stops at "Check the tag matches the version in pom.xml"**: you tagged before setting the version. Delete the tag (`git tag -d v1.1.0` and `git push origin :refs/tags/v1.1.0`), set the version, commit, and tag again.
- **On GitHub, the Windows job fails at "Install WiX 3" or "Test, then build the installer"**: read that step's log in the Actions tab. This is one of the parts that hasn't been proven on GitHub yet. Say what you saw when you ask for help.
- **The workflow never starts**: it isn't at `.github/workflows/release.yml` at the top of the repository, or you pushed commits but not the tag (`git push origin v1.1.0`).
- **`cli`, `desktop` and `sync server` are `SKIPPED`**: `core` failed first. Finish steps 2 to 4.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/release/ConventionalCommit.java` (or `SemVer.java`, `ReleaseNotes.java`, `solution/.github/workflows/release.yml`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say what 1.0.1, 1.1.0 and 2.0.0 each promise, coming from 1.0.0
- [ ] Can say why the biggest bump in a release wins, and why chores make no release
- [ ] Can say why 1.10.0 is newer than 1.9.0, and how `SemVer` gets it right
- [ ] Can say why each installer is built on its own system, and why only the publishing job may write
- [ ] Can say what code signing protects against, and why macOS signing isn't wired in yet
- [ ] All 517 tests pass
- [ ] Wrote notes for a pretend release, and saw a `!` make it 2.0.0
- [ ] Committed your work to Git (not `gate.db`, and never a `.pfx` certificate or its password)

## Next

A release now sits on GitHub, with notes and checksums, but the laptops at the gate don't know it's there. Tomorrow is the last day: Box Office 1.0. The app learns its own version from the build, asks GitHub whether a newer one is out, downloads it only if it matches its checksum, and never during the night. Then you pick one feature of your own.
