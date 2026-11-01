# Day 124: Server deployed

> Thursday, two days to go. The organiser closes his laptop lid, and the ticket site keeps selling.

Watch the video: *(not recorded yet)*

## The brief

Since Day 119 the sync server runs on your laptop, in a terminal. Close the lid and the ticket site is gone. Today it moves to a real server that runs all night, the same way every time:

- **Docker** packs the server and the Java it needs into one **image**. Whatever machine runs the image runs exactly the same program.
- **Profiles** keep two sets of settings: `dev` for your laptop (the default) and `prod` for the real server.
- **Health checks** let whatever runs the server ask "are you alive?" and "can you take orders?", and act on the answer.
- The server refuses to start in production with **practice payments** or **no admin key**.

```
$ docker run --name boxoffice --env-file .env -p 8080:8080 -v boxoffice-data:/data boxoffice-server
...
$ curl -i http://127.0.0.1:8080/actuator/health/readiness
HTTP/1.1 200
Content-Type: application/vnd.spring-boot.actuator.v3+json

{"status":"UP"}
```

You write the production settings, the health settings, the two production checks, the payments health check and the end of the Dockerfile.

## What you'll learn

- What **Actuator** adds to a Spring Boot server, and why only `health` and `info` are open
- The difference between **liveness** and **readiness**, and why the database is in one but not the other
- How **profiles** keep laptop settings and real-server settings apart, in the same jar
- Why the real server's secrets come from **environment variables** and a `.env` file that's never committed
- How to write your own **health indicator**
- What a **Dockerfile**, an **image** and a **container** are
- Why the server runs as a user of its own, not as **root**
- What a **volume** is for: the H2 database survives a new container
- Why a real deploy has **HTTPS** in front of the server, and why that makes `trusted-proxies` 1
- What **graceful shutdown** does for a fan halfway through paying

## New words

- **Deploy**: put a program on the computer where it will really run, for real users.
- **Environment**: one place the program runs, with its own settings: your laptop (dev), the real server (prod).
- **Profile**: Spring's name for one environment's settings. `application-prod.properties` is read only when the `prod` profile is on.
- **Actuator**: a part of Spring Boot that adds `/actuator/...` addresses for checking on a running server.
- **Health check**: an address that answers `UP` or `DOWN`.
- **Liveness**: "is the program alive?" If not, restart it.
- **Readiness**: "can it take requests right now?" If not, send them somewhere else for now, and ask again soon.
- **Probe**: what a platform calls a health check it asks again and again, every few seconds.
- **Docker**: a tool that runs programs in **containers**, each with its own files, as if on its own small computer.
- **Image**: a frozen copy of everything a program needs: an operating system, Java, the jar. Built from a **Dockerfile**.
- **Container**: an image that's running. Stop it and start a new one from the same image, and it starts fresh.
- **Volume**: a folder that lives outside the container. A new container can use the same volume, so the data is still there.
- **root**: the user that can do anything on a Linux machine.
- **`.env` file**: a text file of `NAME=value` lines, the secrets for one environment. Git ignores it.
- **Reverse proxy**: a server that stands in front of yours, takes the fans' HTTPS requests, and passes them on (Day 123's "proxy").
- **HTTPS**: HTTP with encryption. Nobody between the fan and the server can read or change what's sent.
- **Graceful shutdown**: when told to stop, finish the requests already started, then stop.
- **Structured logs**: log lines written as JSON, one per line, so a log service can search them.

## What's already written

From Days 101–123: the gate app, the sync server, the ticket site and M-Pesa payments, made safe for the public, with tests. New today, already done:

- `server/pom.xml`: `spring-boot-starter-actuator`, and the `build-info` goal next to `repackage`, which writes the version and build time into the jar for `/actuator/info`.
- `application.properties`: `spring.profiles.default=dev`, `server.port=${PORT:8080}` (hosting platforms say which port in `PORT`), and graceful shutdown: `server.shutdown=graceful` with up to 20 seconds. The data's address moved out, into the profiles.
- `application-dev.properties`: the H2 file next to where you start the server, as before, and `management.endpoint.health.show-details=always`, so on your laptop the health check shows what's inside it.
- `application-prod.properties`: comments and a TODO.
- `StartupChecks`: now `problems(adminKey, mpesa, production)`. The constructor asks Spring's `Environment` whether the `prod` profile is on.
- `PaymentsHealthIndicator.java`: a `HealthIndicator` with a `count(status)` helper. You write `health()`.
- `Dockerfile`: the base image, the user, the folders and the jar, and `ENTRYPOINT` with `-XX:MaxRAMPercentage=75`. You write four lines in between.
- `.dockerignore`: lets only `server/target/boxoffice-server.jar` in.
- `compose.yaml`: runs the image with an optional `.env` and a named volume.
- Tests: `HealthTest` (4), `ProductionTest` (5), `DeploymentFilesTest` (5), and 2 more in `StartupChecksTest`.

New, for you to finish: the production settings (step 2), the health settings (step 3), the production checks (step 4), `PaymentsHealthIndicator.health()` (step 5) and the Dockerfile (step 6). The TODOs are labelled with their step numbers.

## What you'll use

**Actuator.** Add `spring-boot-starter-actuator` and the server gets `/actuator/health` and `/actuator/info`, and many more: `/actuator/env` lists every setting, `/actuator/beans` every object Spring made. Those are handy on your laptop and dangerous on the internet: `env` would show the admin key to anyone. So only two are open:

```
management.endpoints.web.exposure.include=health,info
```

`/actuator/info` says which version is running (from `build-info`): `{"build":{"version":"1.0.0",...}}`. Useful when you're not sure the new version really went out.

**Liveness and readiness.** A platform that runs your server asks two different questions, and does different things with the answers:

- **Liveness** (`/actuator/health/liveness`): "are you alive?" If the answer is no, or no answer comes, the platform **restarts** the program. That's the fix for a program that's stuck.
- **Readiness** (`/actuator/health/readiness`): "can you take requests?" If no, the platform **stops sending fans** to it for now, and asks again in a few seconds. That's the answer while the server is still starting, or while the database is away.

The database goes in readiness, never in liveness:

```
management.endpoint.health.group.readiness.include=readinessState,db
```

If the database is down for a minute, restarting the server won't bring it back. It would only throw away a server that's fine and start one that's just as stuck. "Not ready" is the honest answer: wait. `management.endpoint.health.probes.enabled=true` adds the two addresses.

**Details only on your laptop.** `/actuator/health` can show what's inside: the database, disk space, payments. On the internet, that tells a stranger what you run. So `application.properties` says `show-details=never` (`{"status":"UP"}` and nothing more) and `application-dev.properties` says `always`. Profile files win over `application.properties`.

**Profiles.** One jar, two sets of settings. `application.properties` holds what every environment shares. `application-dev.properties` is read when the `dev` profile is on (the default, from `spring.profiles.default=dev`). `application-prod.properties` is read when `SPRING_PROFILES_ACTIVE=prod`. The Docker image sets that. Production differs in three ways:

- the data lives in `${BOXOFFICE_DATA_DIR:/data}`, the volume, not next to wherever someone started the program;
- `boxoffice.trusted-proxies` is 1 (more on that below);
- `logging.structured.format.console=ecs`: each log line is one JSON object, so a log service can search "every ERROR from the payments logger".

**Secrets from the environment, never from a file in Git.** Today's settings files go into Git, and so does the image's recipe. The admin key and the M-Pesa keys don't. They come from environment variables, as on Day 114, and `application.properties` only says where to find them: `boxoffice.admin-key=${BOXOFFICE_ADMIN_KEY:}`. On a server, you keep them in a `.env` file:

```
BOXOFFICE_ADMIN_KEY=...
MPESA_MODE=sandbox
MPESA_CONSUMER_KEY=...
```

`docker run --env-file .env` (or `compose.yaml`) hands them to the container when it starts. The root `.gitignore` ignores `.env`, and `.dockerignore` keeps it out of the image too. An image is easy to copy and share; a secret baked into it is a secret given away.

**Refuse to start, in production.** On Day 122, stub mode put "Pretend to pay" buttons on the order page. On the real site, they'd be free tickets for anyone. And with no admin key, no gate can register on the night. Both are fine on your laptop and wrong on the real server, so `StartupChecks` refuses them only when `production` is true:

```
MPESA_MODE is stub: no practice payments in production. Set it to sandbox or live
Set BOXOFFICE_ADMIN_KEY: without it, no gate can register
```

**Your own health indicator.** Spring Boot builds `/actuator/health` from every `HealthIndicator` it finds. `db` and `diskSpace` are its own. A class called `PaymentsHealthIndicator` shows up as `payments`. It's always `UP`: a flagged order is a job for a person, not a reason to stop selling. But whoever watches the server wants three numbers at a glance:

```java
return Health.up()
        .withDetail("mode", mpesa.mode())
        .withDetail("awaitingPayment", count("AWAITING_PAYMENT"))
        .withDetail("flaggedOrders", count("FLAGGED"))
        .build();
```

`Health.up()` starts a **builder**: each `.withDetail` adds one thing, and `.build()` makes the `Health`.

**A Dockerfile, line by line.** Each line is one instruction, run in order when you build the image:

- `FROM eclipse-temurin:21-jre-noble`: start from Ubuntu 24.04 ("noble") with a Java 21 **runtime**. A JRE, not a JDK: nothing is compiled in here, so the compiler would only make the image bigger.
- `RUN useradd ... boxoffice`: make a user called `boxoffice`, uid 10001, and a `/data` folder it owns.
- `COPY server/target/boxoffice-server.jar app.jar`: put the jar in. You build it first, with Maven, on your own machine.
- `USER boxoffice`: from here on, run as that user. If someone ever breaks into the server program, they're `boxoffice`, who can write to `/data` and nothing else, not root.
- `ENV SPRING_PROFILES_ACTIVE=prod BOXOFFICE_DATA_DIR=/data`: settings that aren't secret. Never put a key here.
- `VOLUME /data`: this folder is data, not part of the program.
- `EXPOSE 8080`: the port the server listens on.
- `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]`: what runs when the container starts. `MaxRAMPercentage=75` lets Java use up to 75% of the container's memory, and leaves the rest for everything else.

**`.dockerignore` lets only the jar in.** `docker build .` sends the whole folder to Docker first. Ours is an allowlist: `*` (nothing), then `!server/target/boxoffice-server.jar` (except this). A `.env`, a `gate.db` or a key you left lying in the folder can't end up in the image by mistake.

**A volume keeps the data.** A container's own files go when the container goes. Every new version of the server is a new container, so without a volume every sale, order and registered gate would vanish with each update. `-v boxoffice-data:/data` puts `/data` in a **named volume** that lives outside any container. Stop the container, remove it, start a new one with the same volume: the H2 database is still there.

**HTTPS in front.** Fans type their names and phone numbers, and gates send tokens. Over plain HTTP, anyone on the same Wi-Fi can read them. Safaricom won't even call a plain `http` callback (Day 123). But the server itself speaks plain HTTP on 8080. The HTTPS part is done in front of it: a reverse proxy like Caddy or nginx on your own machine, or the platform's own (Render, Railway, Fly.io and the rest give every app one). The proxy takes the HTTPS request, then passes it to the server, adding the caller's address to `X-Forwarded-For`. That's **one** proxy of ours, which is why production says `trusted-proxies` 1 (Day 123). On your laptop there's none, so `compose.yaml` sets it back to 0.

**Graceful shutdown.** A new version means stopping the old container. Without `server.shutdown=graceful`, a fan halfway through placing an order gets cut off. With it, the server stops taking new requests and finishes the ones it has, for up to 20 seconds. `docker stop` gives it 10 seconds before it forces the stop.

## Steps

1. **Open the starter and run the tests.** `cd day-124-server-deployed/starter`, then `../../mvnw test`. `core`, `cli` and `desktop` pass. In `sync server` you should see `Tests run: 142, Failures: 10` and `BUILD FAILURE`:
   - `DeploymentFilesTest.theServerDoesntRunAsRoot`: `Expecting actual not to be empty` (there's no `USER` line yet, step 6), and `itRunsTheProdProfileWithItsDataInAVolume` (step 6);
   - `HealthTest.infoSaysWhichVersionIsRunning`: `[HTTP status code] expected: 200 but was: 404` (step 3);
   - `HealthTest.livenessAndReadinessAreSeparate`: `Expecting: "{"status":"UP"}" To match JSON path: "$.components.db.status"` (step 3);
   - `HealthTest.healthSaysUpAndWhatsInside`: `Not written yet: Day 124, step 5`;
   - `ProductionTest.withGoodSettingsItStartsAndKeepsItsDataInTheDataFolder`: `Expecting path: /tmp/junit-.../sync-server-db.mv.db to exist` (step 2);
   - `ProductionTest.practicePaymentsStopTheServerStarting`: `Expecting code to raise a throwable.` (step 4), and `strangersSeeUpOrDownAndNothingMore`: `{"timestamp":...,"status":500,...}` (step 5);
   - `StartupChecksTest.productionRefusesPracticePayments` and `productionNeedsAnAdminKey`: `Expecting actual: [] to contain exactly (and in same order): [...]` (step 4).

   To run only today's server tests:

   ```
   ../../mvnw test -Dtest="HealthTest,ProductionTest,DeploymentFilesTest,StartupChecksTest" -Dsurefire.failIfNoSpecifiedTests=false
   ```

   On Windows: `..\..\mvnw.cmd test "-Dtest=HealthTest,ProductionTest,DeploymentFilesTest,StartupChecksTest" "-Dsurefire.failIfNoSpecifiedTests=false"`. You should see `Tests run: 22, Failures: 10`.

2. **Production settings.** Open `starter/server/src/main/resources/application-prod.properties`. Below the TODO marked `step 2`, add three lines:

   ```
   spring.datasource.url=jdbc:h2:file:${BOXOFFICE_DATA_DIR:/data}/sync-server-db
   boxoffice.trusted-proxies=${BOXOFFICE_TRUSTED_PROXIES:1}
   logging.structured.format.console=ecs
   ```

   `${BOXOFFICE_DATA_DIR:/data}` means "the environment variable, or `/data` if there isn't one". The test sets it to a temporary folder.

   Run today's server tests: `Tests run: 22, Failures: 9`. `withGoodSettingsItStartsAndKeepsItsDataInTheDataFolder` passes: the database is in the data folder.

3. **Health settings.** Open `application.properties` and find the TODO marked `step 3`. Add the four `management.*` lines it lists:

   ```
   management.endpoints.web.exposure.include=health,info
   management.endpoint.health.probes.enabled=true
   management.endpoint.health.group.readiness.include=readinessState,db
   management.endpoint.health.show-details=never
   ```

   Run the tests again: `Tests run: 22, Failures: 7`. `infoSaysWhichVersionIsRunning` and `livenessAndReadinessAreSeparate` pass. (`nothingElseUnderActuatorIsOpen` passed from the start: without any setting, Actuator opens only `health`. Today's line keeps it that way while opening `info` too.)

4. **Refuse stub mode and a missing key, in production.** Open `StartupChecks.java` and find the TODO marked `step 4`, at the start of `problems`. Write `if (production) { ... }`, and inside it:
   - `if (mpesa.stub())`: `problems.add("MPESA_MODE is stub: no practice payments in production. Set it to sandbox or live");`
   - `if (adminKey.isBlank())`: `problems.add("Set BOXOFFICE_ADMIN_KEY: without it, no gate can register");`

   Copy the messages exactly. `StartupChecksTest` (8) passes, and `practicePaymentsStopTheServerStarting`.

5. **The payments health check.** Open `PaymentsHealthIndicator.java` and find the TODO marked `step 5`. Replace the `throw` with `return Health.up()`, then `.withDetail("mode", mpesa.mode())`, `.withDetail("awaitingPayment", count("AWAITING_PAYMENT"))`, `.withDetail("flaggedOrders", count("FLAGGED"))`, then `.build();`.

   `HealthTest` (4) and `ProductionTest` (5) pass. Only `DeploymentFilesTest` still fails.

6. **Finish the Dockerfile.** Open `starter/Dockerfile` (at the top of the starter, next to `pom.xml`). Below the TODO marked `step 6`, and above `ENTRYPOINT`, add:

   ```
   USER boxoffice
   ENV SPRING_PROFILES_ACTIVE=prod \
       BOXOFFICE_DATA_DIR=/data
   VOLUME /data
   EXPOSE 8080
   ```

   The `\` at the end of a line means "this instruction carries on on the next line". `USER` must come before `ENTRYPOINT`: the user is switched for what runs after it.

   Run today's server tests: `Tests run: 22, Failures: 0` and `BUILD SUCCESS`.

7. **Run all the tests.** `../../mvnw test`. You should see 398 tests in all and `BUILD SUCCESS`.

8. **Run it the way a real server does.** Follow "Run it and test it" below: the dev profile on your laptop, then the Docker image, a new container on the same volume, and Docker Compose.

9. **Stretch (not required): put it online.** Most hosting platforms (Render, Railway, Fly.io) can build this Dockerfile from your Git repository. Give it a disk at `/data`, set the secrets in its settings page (never in the repository), and point its health check at `/actuator/health/readiness`. Use `MPESA_MODE=sandbox` and Safaricom's sandbox keys, and `MPESA_CALLBACK_BASE` set to the `https://` address the platform gives you. A free plan may stop the server when nobody visits, and lose the disk: fine to practise, not for the night.

## Run it and test it

From the day's `starter` folder:

```
cd day-124-server-deployed/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=HealthTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=HealthTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **185 tests** in `core`, **30** in `cli`, **41** in `desktop` and **142** in `sync server` (including DeploymentFiles 5, Health 4, Production 5, StartupChecks 8), **398** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] Box Office sync server ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**Build the jars.** `../../mvnw package -DskipTests`.

**On your laptop: the dev profile.** Nothing to set but the admin key (`change-me-to-a-long-random-secret-123` is a placeholder: make your own with `openssl rand -base64 32`, or in PowerShell `[Convert]::ToBase64String((1..32 | % { Get-Random -Max 256 }))`):

macOS / Linux:

```
BOXOFFICE_ADMIN_KEY=change-me-to-a-long-random-secret-123 java -jar server/target/boxoffice-server.jar
```

Windows PowerShell:

```
$env:BOXOFFICE_ADMIN_KEY="change-me-to-a-long-random-secret-123"
```

```
java -jar server\target\boxoffice-server.jar
```

It says `No active profile set, falling back to 1 default profile: "dev"`, then `Started SyncServerApplication`. In a second terminal (`curl.exe` in PowerShell), the health check shows what's inside, because dev says `show-details=always`:

```
$ curl http://127.0.0.1:8080/actuator/health
{"components":{"db":{"details":{"database":"H2","validationQuery":"isValid()"},"status":"UP"},"diskSpace":{...,"status":"UP"},"livenessState":{"status":"UP"},"payments":{"details":{"mode":"stub","awaitingPayment":0,"flaggedOrders":0},"status":"UP"},"ping":{"status":"UP"},"readinessState":{"status":"UP"},"ssl":{...,"status":"UP"}},"groups":["liveness","readiness"],"status":"UP"}

$ curl http://127.0.0.1:8080/actuator/health/readiness
{"components":{"db":{"details":{"database":"H2","validationQuery":"isValid()"},"status":"UP"},"readinessState":{"status":"UP"}},"status":"UP"}
```

Place an order and look again: `"payments":{"details":{"mode":"stub","awaitingPayment":1,"flaggedOrders":0}`. Stop the server with Ctrl+C.

**The prod profile refuses stub mode.** With no M-Pesa settings and no key:

```
SPRING_PROFILES_ACTIVE=prod BOXOFFICE_DATA_DIR=./prod-data java -jar server/target/boxoffice-server.jar
```

It stops at once. Among the JSON log lines, the message says:

```
The server won't start:
  MPESA_MODE is stub: no practice payments in production. Set it to sandbox or live
  Set BOXOFFICE_ADMIN_KEY: without it, no gate can register
```

The log lines are JSON now (`{"@timestamp":...,"log":{"level":"ERROR",...},"message":...}`): that's `logging.structured.format.console=ecs`.

**Docker.** Install Docker Desktop (macOS, Windows) or Docker Engine (Linux), and check `docker version` works. The commands are the same on every system. Build the image from the starter folder, after `package`:

```
docker build -t boxoffice-server .
```

```
$ docker images boxoffice-server
REPOSITORY         TAG       IMAGE ID       CREATED          SIZE
boxoffice-server   latest    9b6cbd4da02e   11 seconds ago   338MB
```

About 338 MB: Ubuntu, Java 21 and our 50 MB jar. Run it with no settings at all, and it refuses, as it should:

```
docker run --rm boxoffice-server
```

The last log line ends with `The server won't start:\n  MPESA_MODE is stub: no practice payments in production...`.

**A `.env` for the container.** Make a file called `.env` in the starter folder. For practice, use sandbox mode with made-up values: the server starts, and nothing is sent to Safaricom until a fan places an order (don't, with these):

```
BOXOFFICE_ADMIN_KEY=change-me-to-a-long-random-secret-123
MPESA_MODE=sandbox
MPESA_CONSUMER_KEY=your-sandbox-consumer-key
MPESA_CONSUMER_SECRET=your-sandbox-consumer-secret
MPESA_PASSKEY=your-sandbox-passkey
MPESA_CALLBACK_BASE=https://tickets.example.co.ke
MPESA_CALLBACK_SECRET=change-me-to-32-or-more-random-characters
```

Pick your own admin key and callback secret. Check Git won't take the file: `git status` must not list `.env`.

Run the container, with the `.env`, port 8080, and a volume called `boxoffice-data` for `/data`:

```
docker run -d --name boxoffice --env-file .env -p 8080:8080 -v boxoffice-data:/data boxoffice-server
```

`-d` runs it in the background. `docker logs boxoffice` shows its log; wait for `Started SyncServerApplication`. Then:

```
$ curl -i http://127.0.0.1:8080/actuator/health
HTTP/1.1 200
Content-Type: application/vnd.spring-boot.actuator.v3+json

{"groups":["liveness","readiness"],"status":"UP"}

$ curl -i http://127.0.0.1:8080/actuator/health/liveness
HTTP/1.1 200

{"status":"UP"}

$ curl -i http://127.0.0.1:8080/actuator/health/readiness
HTTP/1.1 200

{"status":"UP"}

$ curl http://127.0.0.1:8080/actuator/info
{"build":{"artifact":"server","name":"Box Office sync server","time":"2026-10-31T22:30:14.265Z","version":"1.0.0","group":"com.boxoffice"}}

$ curl -i http://127.0.0.1:8080/actuator/env
HTTP/1.1 404
```

No details for strangers (`show-details=never` in prod), and `/actuator/env` doesn't exist. Who is it running as, and where's the data?

```
$ docker exec boxoffice id
uid=10001(boxoffice) gid=999(boxoffice) groups=999(boxoffice)

$ docker exec boxoffice ls -la /data
-rw-r--r-- 1 boxoffice boxoffice 40960 Oct 31 22:35 sync-server-db.mv.db
```

**The data outlives the container.** Register gate A against the container and sync a sale, as on Day 119 (set `BOXOFFICE_ADMIN_KEY` in this terminal too):

```
$ java -jar cli/target/boxoffice.jar setup --gate A --venue-key ngong-racecourse-2026-gates
Kilele Nights is ready in gate.db as gate A

$ java -jar cli/target/boxoffice.jar register --server http://127.0.0.1:8080
Gate A is registered with http://127.0.0.1:8080. sync logs in with it from now on.

$ java -jar cli/target/boxoffice.jar sell VIP Wanjiku
Sold A-VIP-0001 to Wanjiku for KES 5,000

$ java -jar cli/target/boxoffice.jar sync --server http://127.0.0.1:8080
Sent 1 sale. Nothing waiting.
```

Now throw the container away, as an update would, and start a new one on the same volume:

```
docker stop boxoffice
docker rm boxoffice
docker run -d --name boxoffice --env-file .env -p 8080:8080 -v boxoffice-data:/data boxoffice-server
```

`docker logs boxoffice` from the old container showed `Commencing graceful shutdown. Waiting for active requests to complete`, then `Graceful shutdown complete`. When the new one is ready, gate A's token still works: the server remembers the gate, because its database is on the volume.

```
$ java -jar cli/target/boxoffice.jar sell REG Kamau
Sold A-REG-0001 to Kamau for KES 1,500

$ java -jar cli/target/boxoffice.jar sync --server http://127.0.0.1:8080
Sent 1 sale. Nothing waiting.
```

Without `-v boxoffice-data:/data`, the new container would have an empty database, and `sync` would say `The server doesn't know this laptop. Run register first`.

**Docker Compose.** `compose.yaml` writes all of that down, so nobody has to remember the flags. Stop the container first (`docker stop boxoffice`), then:

```
docker compose up --build
```

It builds the image, makes a volume called `<folder>_server-data`, reads `.env` if there is one, and starts the server. It sets `BOXOFFICE_TRUSTED_PROXIES` to 0, because on your laptop nothing stands in front. Ctrl+C stops it. `docker compose down` removes its container; `docker compose down -v` removes its volume too, and the data with it.

**Tidy up.** `docker rm -f boxoffice`, `docker volume rm boxoffice-data`, `docker rmi boxoffice-server`. Then `docker ps -a` and `docker images` show nothing of yours. Delete `gate.db` if you like. Never `docker push` this image anywhere public: it's your server.

## When you're stuck

- **`withGoodSettingsItStartsAndKeepsItsDataInTheDataFolder` says `Expecting path: /tmp/junit-.../sync-server-db.mv.db to exist`**: the prod profile has no `spring.datasource.url`, so the data isn't in the data folder. Do step 2, and check the file is called `application-prod.properties`, in `src/main/resources`.
- **`infoSaysWhichVersionIsRunning` says `expected: 200 but was: 404`**: `info` isn't exposed. Do step 3, with `health,info`, no spaces.
- **`livenessAndReadinessAreSeparate` says `To match JSON path: "$.components.db.status"`**: readiness doesn't include the database. Add `management.endpoint.health.group.readiness.include=readinessState,db`.
- **`nothingElseUnderActuatorIsOpen` fails with a `200` for `/actuator/env`**: you exposed `*`. Expose only `health,info`.
- **`strangersSeeUpOrDownAndNothingMore` says the body contains `components`**: `show-details` isn't `never` in `application.properties`.
- **`StartupChecksTest` says `Expecting actual: [] to contain exactly (and in same order): ["MPESA_MODE is stub: ..."]`**, or `practicePaymentsStopTheServerStarting` says `Expecting code to raise a throwable.`: the production checks are missing. Do step 4.
- **`noAdminKeyAtAllIsAllowedOnYourLaptop` or `aShortAdminKeyIsNot` fails after step 4, with the stub message in the list**: your checks run when `production` is false too. Put them inside `if (production)`.
- **`healthSaysUpAndWhatsInside` says `Not written yet: Day 124, step 5`**, or `strangersSeeUpOrDownAndNothingMore` gets `"status":500`: `PaymentsHealthIndicator.health()` still throws. Do step 5.
- **`theServerDoesntRunAsRoot` says `Expecting actual not to be empty`**: there's no `USER` line. Do step 6. If it says the index isn't less than `ENTRYPOINT`'s, put `USER` above `ENTRYPOINT`.
- **`itRunsTheProdProfileWithItsDataInAVolume` says the Dockerfile doesn't contain `SPRING_PROFILES_ACTIVE=prod`** (or `VOLUME /data`, `EXPOSE 8080`): one of step 6's lines is missing or spelled differently.
- **`noSecretIsBakedIntoTheImage` fails**: you put a key or `.env` in the Dockerfile. Take it out: secrets come from `--env-file` when the container starts.
- **`docker build` says `"/server/target/boxoffice-server.jar": not found`**: build the jar first, `../../mvnw package -DskipTests`, and run `docker build` from the starter folder, where the `Dockerfile` is.
- **`docker` says `Cannot connect to the Docker daemon`** (or on Windows, `error during connect`): Docker isn't running. Start Docker Desktop and wait until it says it's running.
- **`docker run` says `Bind for 0.0.0.0:8080 failed: port is already allocated`**: another server is on 8080: a `java -jar` in another terminal, or the container from before. Stop it, or `docker rm -f boxoffice`.
- **`docker run` says `Conflict. The container name "/boxoffice" is already in use`**: the old one is still there. `docker rm -f boxoffice` first.
- **The container stops at once, and the log says `Set MPESA_CONSUMER_KEY to use M-Pesa (or MPESA_MODE=stub to practise)`**: sandbox mode needs all three Daraja values in `.env`. Or `--env-file .env` is missing from the command.
- **After a new container, `sync` says `The server doesn't know this laptop. Run register first`**: the new container has no volume, or a different one. Use the same `-v boxoffice-data:/data` every time.
- **Still stuck?** Open `solution/server/src/main/resources/application-prod.properties` (or `application.properties`, `StartupChecks.java`, `PaymentsHealthIndicator.java`, `solution/Dockerfile`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say the difference between liveness and readiness, and why the database is only in readiness
- [ ] Can say why only `health` and `info` are open, and why details are hidden in prod
- [ ] Can say what a profile is, and what's different in prod
- [ ] Can say why secrets come from the environment and a `.env` that's never committed, and never from the Dockerfile
- [ ] Can say what a volume is for
- [ ] Can say why a real deploy has HTTPS in front, and why that makes `trusted-proxies` 1
- [ ] All 398 tests pass
- [ ] Built the image, ran it as `boxoffice`, saw readiness say `UP`, and synced a sale that survived a new container
- [ ] Committed your work to Git (not `.env`, `gate.db`, `sync-server-db.mv.db` or a `prod-data` folder, and never your admin key or M-Pesa keys)

## Next

The server runs like a real one, and says when it's ready. Tomorrow the organiser stops counting by hand: at 23:00 he asks "how many VIP tickets did gate B sell since 21:00?", in plain words, and Claude answers from tonight's sales.
