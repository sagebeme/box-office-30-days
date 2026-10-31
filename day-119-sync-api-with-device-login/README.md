# Day 119: Sync API with device login

> The organiser's laptop runs the server. Gate A logs in with its own token. A stranger on the venue Wi-Fi gets a 401.

Watch the video: *(not recorded yet)*

## The brief

Since Day 116 every sale waits in the outbox until a server takes it. Until today, that server was the practice one from the CLI (`practice-server`, port 8099), which takes anything from anyone. Today you build the real one: the **sync server**, a small web app the organiser runs. To practise, it runs on your own laptop, in its own terminal.

Anyone on the venue Wi-Fi can reach a server, so it has to know who's talking to it. Each gate laptop gets its own **token**, a long random secret, once, before the night:

1. The organiser starts the server with an **admin key** only they know.
2. Before the gates open, each laptop runs `register`. It sends the admin key and its gate id (`A`), and gets back a token. The laptop keeps the token in `gate.db`. The server keeps only a **hash** of it.
3. From then on, every `sync` sends `Authorization: Bearer <token>`. No token, or a wrong one: **401**. Gate B sending gate A's tickets: **403**.

```
$ java -jar cli/target/boxoffice.jar register --server http://127.0.0.1:8080
Gate A is registered with http://127.0.0.1:8080. sync logs in with it from now on.

$ java -jar cli/target/boxoffice.jar sync --server http://127.0.0.1:8080
Sent 2 sales. Nothing waiting.
```

The server is written with **Spring Boot**. Most of it is already done. You write the four pieces that make device login work.

## What you'll learn

- What **Spring Boot** does for you, and how it builds your objects with **constructor injection**
- What `@SpringBootApplication`, `@RestController`, `@Service` and `@Repository` mean
- What a **BOM** is, and why our pom imports Spring Boot's instead of making it the parent
- How `JdbcClient` runs plain SQL, and how Flyway runs the server's migrations at start-up
- How **H2** is a file on disk to practise, and a database in memory for the tests
- Why the server keeps a **SHA-256 hash** of each token, and why that's enough for a token but not for a password
- How a `HandlerInterceptor` checks every `/sync` request before the controller sees it
- What 401 and 403 mean, and which one to answer when
- How `MockMvcTester` tests a controller without a real port, and how a `RANDOM_PORT` test runs a real gate against the real server

## New words

- **Server**: a program that waits for requests from other programs and answers them. Here, the gates send it sales.
- **API**: the requests a server understands, and what it answers. Ours is `POST /admin/devices`, `POST /sync/sales` and `GET /health`.
- **Spring Boot**: a Java framework for servers. It starts a web server, creates your objects and wires them together, from a few annotations.
- **Bean**: an object Spring creates and looks after. Each of your `@RestController`, `@Service` and `@Repository` classes becomes one.
- **Constructor injection**: Spring calls your constructor and passes in the beans it asks for. `Sales(JdbcClient db, Clock clock)` gets a `JdbcClient` and a `Clock`.
- **Controller**: the class that turns an HTTP request into a method call, and the method's answer into JSON.
- **BOM** ("bill of materials"): a pom that lists one tested version for each of many libraries, so they all fit together.
- **H2**: a database written in Java. It can keep its data in a file or only in memory.
- **Profile**: a named set of settings. `test` is ours: it swaps the file database for one in memory.
- **Token**: a long random secret that proves who you are, like a key card.
- **Hash**: a fingerprint of some text. The same text always gives the same hash, but the hash can't be turned back into the text.
- **SHA-256**: the hash we use. It gives 32 bytes, written as 64 hex characters.
- **Hex**: writing bytes with the digits `0-9` and `a-f`, two characters per byte.
- **Interceptor**: code Spring runs before the controller, for every request to some addresses. It can stop the request there.
- **401 Unauthorized**: "I don't know who you are." **403 Forbidden**: "I know who you are, and you can't do that."
- **Admin key**: the organiser's secret, from an environment variable. Only it can register laptops.

## What's already written

From Days 101–118: the gate app, safe with many threads, working, with tests. New today, already done:

- `server/`: a new module, the sync server. The parent pom lists it, and `server/pom.xml` imports Spring Boot's BOM. Read both poms.
- `SyncServerApplication.java`: the `main`, and a `Clock` bean.
- `AdminController.java`: `POST /admin/devices`. Checks the `X-Admin-Key` header against `BOXOFFICE_ADMIN_KEY`, checks the gate id, and answers `201 {"gate":"A","token":"..."}`.
- `Devices.java`: the `devices` table. `register` makes a token with `Tokens.newToken()` and keeps `Tokens.hash(token)`; registering the same gate again replaces it. `gateFor(token)` finds which gate a token belongs to.
- `SyncController.java`: `POST /sync/sales` (201 `accepted`, 200 `duplicate`) and `GET /health`. The gate comes from the request with `@RequestAttribute(DeviceLogin.GATE)`.
- `Sales.java`: saves a sale and its tickets, once per idempotency key, in one `@Transactional` method.
- `WebConfig.java`: puts `DeviceLogin` in front of `/sync/**`. `Errors.java` and `ApiProblem.java` turn problems into `{"error":"..."}`.
- `application.properties`, `application-test.properties` and `db/server/V1__devices_and_sales.sql`.
- Tests: `DeviceLoginTest` (9), `SyncApiTest` (5), `GateToServerTest` (2).
- On the gate: the CLI's new `register` command, and `TicketStore.syncToken()` / `setSyncToken()`. `sync` passes the token to `HttpSyncClient`, which now turns a 401 into `The server doesn't know this laptop. Run register first`. New tests in `HttpSyncClientTest` (3) and `BoxOfficeCliTest` (3).

New, for you to finish: `Tokens.hash` and `DeviceLogin.preHandle` (server), the gate check in `Sales.receive` (server), and the `Authorization` header in `HttpSyncClient` (core).

## What you'll use

**Spring Boot in one paragraph.** `@SpringBootApplication` on `SyncServerApplication` tells Spring: "look in this package". It finds every class marked `@RestController`, `@Service`, `@Repository`, `@Component` or `@Configuration`, creates one of each, and calls each constructor with the beans it asks for. You never write `new Sales(...)`. It also sees what's on the classpath and sets it up: Tomcat (a web server) on port 8080 because of `spring-boot-starter-webmvc`, a database connection because of H2 and `spring-boot-starter-jdbc`, Flyway because of `spring-boot-starter-flyway`.

```java
@Service
class Sales {
    Sales(JdbcClient db, Clock clock) { ... }   // Spring passes both in
}
```

The labels are mostly for you, the reader: `@RestController` answers HTTP, `@Service` holds the rules, `@Repository` talks to the database.

**A BOM, imported.** Spring Boot is dozens of libraries, and they must be the same tested versions. Its BOM, `spring-boot-dependencies`, lists them all. Most projects make `spring-boot-starter-parent` their parent pom to get it, but our parent pom is our own (Day 101). So `server/pom.xml` *imports* the BOM instead, in `dependencyManagement`, with `<type>pom</type>` and `<scope>import</scope>`. Then `spring-boot-starter-webmvc` needs no version: the BOM says which.

**The Jackson change in the parent.** Our `core` reads and writes JSON with Jackson 2 (`com.fasterxml.jackson`, since Day 116). Spring Boot 4 uses Jackson 3 (`tools.jackson`) for its own JSON, and its BOM also names a Jackson 2 version, 2.21.7, for libraries that still use 2. The server's tests put `core` on the classpath, so `core`'s Jackson 2 must be the one Boot expects. The parent used to pin just `jackson-databind` 2.19.2. Now it imports `jackson-bom` 2.21.7, which sets `jackson-databind`, `jackson-core` and `jackson-annotations` together. (They don't even share a version number: `jackson-annotations` is just `2.21`. A BOM knows that, so you don't have to.) Jackson 2 and 3 live side by side because their package names are different.

**`JdbcClient`: plain SQL.** The same SQL you wrote for SQLite, without `PreparedStatement` and `ResultSet`:

```java
db.sql("select gate from devices where token_hash = ?")
        .param(Tokens.hash(token))
        .query(String.class)
        .optional();
```

**Flyway at start-up, in its own folder.** Spring Boot runs Flyway before the server takes requests. The server's migrations are in `db/server`, not `db/migration`, because the tests have the gate's code on the classpath too, and the gate's V1–V6 are in `db/migration`. `spring.flyway.locations=classpath:db/server` keeps them apart.

**H2, two ways.** `application.properties` says `jdbc:h2:file:./sync-server-db`: a file called `sync-server-db.mv.db`, made in the folder you start the server from. `application-test.properties` says `jdbc:h2:mem:sync-tests`: a database in memory, gone when the tests end. `@ActiveProfiles("test")` on a test class turns on the `test` profile, so its settings replace the ones with the same name.

**A hash, not the token.** If someone copies the `devices` table, they get hashes. A hash can't be turned back into the token, so they still can't log in. When a gate sends a token, the server hashes it and looks for that hash:

```java
byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
return HexFormat.of().formatHex(digest);
```

`getInstance` can throw `NoSuchAlgorithmException`. Every Java has SHA-256, so it never happens: catch it and throw an `IllegalStateException`.

**Why a plain hash is enough here, and not for passwords.** A person's password is short and guessable: `kilele2026`. An attacker with the hash tries a billion guesses a second until one matches. That's why passwords need a slow hash with a salt (bcrypt, Argon2). Our token is 32 random bytes from `SecureRandom`: there are more possible tokens than atoms on Earth, so there's nothing to guess. A fast hash is fine.

**The token is shown once.** `register` answers with the token. The gate saves it in `gate.db` and never prints it: it's a password. Registering the same gate again makes a new token, and the old one stops working, which is what you want if a laptop is stolen.

**The interceptor.** `WebConfig` puts `DeviceLogin` in front of every `/sync/**` address. Its `preHandle` runs before the controller. Return `true`: carry on to the controller. Return `false`: stop, the answer you wrote is the answer. `/admin` and `/health` aren't under `/sync`, so they need no token.

```
Authorization: Bearer 3qN0x...
```

`Bearer ` (with one space) means "whoever bears this token". The rest is the token.

**401 or 403.** No token, or one the server doesn't know: 401, "who are you?". Gate B, with B's good token, sending ticket `A-REG-0001`: 403, "I know you're B, and that isn't yours". Gate A's codes start with `A-` since Day 117, so the check is `code.startsWith(gate + "-")`.

**Testing without a port.** `@SpringBootTest` starts the whole app once for the test class. `@AutoConfigureMockMvc` adds a `MockMvcTester`, which hands requests straight to the controllers with no network:

```java
assertThat(mvc.get().uri("/health")).hasStatusOk();
```

`GateToServerTest` uses `webEnvironment = RANDOM_PORT` instead: Tomcat really starts, on a free port, and a real gate (`TicketStore`, `Gate`, `OutboxSender`, `HttpSyncClient` from `core`) syncs to it over HTTP.

## Steps

1. **Open the starter and run the tests.** `cd day-119-sync-api-with-device-login/starter`, then `../../mvnw test`. The first run downloads Spring Boot, so it needs internet and a few minutes. You should see `Tests run: 176, Failures: 1` in `core` and `BUILD FAILURE`: `HttpSyncClientTest.logsInWithTheDeviceToken` says `expected: "Bearer t0ken" but was: null`. That's step 6. `cli`, `desktop` and `sync server` are `SKIPPED`, because Maven stops at the first module that fails.

   To see the server's tests now, run only them (the other modules then run no tests, and pass):

   ```
   ../../mvnw test -Dtest="DeviceLoginTest,SyncApiTest,GateToServerTest" -Dsurefire.failIfNoSpecifiedTests=false
   ```

   On Windows: `..\..\mvnw.cmd test "-Dtest=DeviceLoginTest,SyncApiTest,GateToServerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`. You should see `Tests run: 16, Failures: 3, Errors: 10`, most saying `Not written yet: Day 119, step 3`. Use this command after each server step below.

2. **Read the server.** Start with `starter/pom.xml` (the new `<module>server</module>`, `spring-boot.version` and the Jackson BOM), then `starter/server/pom.xml`. Then, in `starter/server/src/main/java/com/boxoffice/server/`: `SyncServerApplication`, `AdminController`, `Devices`, `SyncController`, `WebConfig`. Then the two `.properties` files and `V1__devices_and_sales.sql`. Nothing to write. For each class, find what its constructor asks for: that's what Spring passes in.

3. **Hash a token.** Open `Tokens.java` and find `hash`. Replace the `throw` with a `try`: `MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))`, then `return HexFormat.of().formatHex(digest)`. Catch `NoSuchAlgorithmException e` and `throw new IllegalStateException("Every Java has SHA-256", e)`.

   Run the server tests. Now `Tests run: 16, Failures: 8, Errors: 1`. Most failures say `expected: 401 but was: 400` (or `expected: 201 but was: 400`): `DeviceLogin` lets everyone in but never says which gate, so the controller can't find its `gate`.

4. **Log the device in.** Open `DeviceLogin.java` and find `preHandle`. Replace `return true` with:
   - `String header = request.getHeader("Authorization");`
   - if `header` isn't `null` and `startsWith("Bearer ")`, the token is `header.substring("Bearer ".length()).strip()`, and `devices.gateFor(token)` gives an `Optional<String>`. Otherwise use `Optional.empty()`;
   - empty: set the status to `HttpServletResponse.SC_UNAUTHORIZED`, the content type to `application/json`, write `{"error":"Unknown device. Register this laptop first"}` (the TODO has the exact line), and `return false`;
   - found: `request.setAttribute(GATE, gate.get())` and `return true`.

   Run the server tests. Two still fail: `aGateCanOnlySendItsOwnTickets` (`expected: 403 but was: 201`) and `aGateSyncsItsSalesToTheServer`, which says `The server doesn't know this laptop. Run register first`, because the gate doesn't send its token yet (step 6).

5. **Only your own tickets.** Open `Sales.java` and find `receive`. Where the TODO is, loop over `sale.tickets()`. For any ticket whose `code()` is `null` or doesn't `startsWith(gate + "-")`, throw the `ApiProblem` in the TODO, with `HttpStatus.FORBIDDEN`. It comes before anything is saved, so a refused sale leaves nothing behind.

   Run the server tests: only `aGateSyncsItsSalesToTheServer` fails now.

6. **Send the token.** Open `starter/core/src/main/java/com/boxoffice/core/sync/HttpSyncClient.java` and find `push`. Where the TODO is: if `token` isn't empty, `request.header("Authorization", "Bearer " + token)`. With no token (the practice server), send no header at all.

7. **Run all the tests.** `../../mvnw test`. You should see 259 tests in all and `BUILD SUCCESS`. The server's tests start Spring itself, so `sync server` takes about half a minute.

8. **Run the server and sync a gate to it.** Follow "Run it and test it" below: start the server in one terminal, then set up, register, sell and sync from a second.

9. **Stretch (not required): be the organiser by hand.** With the server running, register a gate B with `curl`, the way `register` does it (see "Without the gate" below). The answer holds B's token: that's the only time anyone sees it. Then write one more test in `DeviceLoginTest` of your own, for example "a token sent without the word `Bearer` is refused". Copy the shape of `aMadeUpTokenIsRefused`. Your test isn't part of the course's tests, so keep it in your own Git.

## Run it and test it

From the day's `starter` folder:

```
cd day-119-sync-api-with-device-login/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=DeviceLoginTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=DeviceLoginTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **176 tests** in `core` (including HttpSyncClient 6), **26** in `cli` (BoxOfficeCli 23, PracticeServer 3), **41** in `desktop` and **16** in `sync server` (DeviceLogin 9, SyncApi 5, GateToServer 2), **259** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] Box Office sync server ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**Build the jars.** `../../mvnw package`. You get `cli/target/boxoffice.jar` and `server/target/boxoffice-server.jar`.

**Terminal 1: start the server.** Pick your own admin key, long and hard to guess. `pick-a-long-secret` below is a placeholder: don't use it for anything real, and never put a real key in a file that goes into Git.

macOS / Linux:

```
BOXOFFICE_ADMIN_KEY=pick-a-long-secret java -jar server/target/boxoffice-server.jar
```

Windows PowerShell:

```
$env:BOXOFFICE_ADMIN_KEY="pick-a-long-secret"
```

```
java -jar server\target\boxoffice-server.jar
```

Windows cmd:

```
set BOXOFFICE_ADMIN_KEY=pick-a-long-secret
```

```
java -jar server\target\boxoffice-server.jar
```

Wait for `Tomcat started on port 8080` and `Started SyncServerApplication in ... seconds` (about 15 seconds). Above them, Flyway says `Successfully applied 1 migration`. The server keeps running: leave this terminal alone. To use another port, add `--server.port=9090` at the end, and use `9090` in the commands below. `sync-server-db.mv.db` appears in the folder you started it from; it's in `.gitignore`. Stop the server with Ctrl+C.

**Terminal 2: be gate A.** Open a second terminal in the same `starter` folder. `register` needs the same admin key, and an environment variable set in terminal 1 isn't seen here, so set it again (macOS / Linux shown; on Windows set it as above and drop it from the front of the line):

```
$ java -jar cli/target/boxoffice.jar ping http://127.0.0.1:8080/health
http://127.0.0.1:8080/health answered HTTP 200

$ java -jar cli/target/boxoffice.jar setup --gate A
Kilele Nights is ready in gate.db as gate A

$ BOXOFFICE_ADMIN_KEY=pick-a-long-secret java -jar cli/target/boxoffice.jar register --server http://127.0.0.1:8080
Gate A is registered with http://127.0.0.1:8080. sync logs in with it from now on.

$ java -jar cli/target/boxoffice.jar sell VIP Wanjiku
Sold A-VIP-0001 to Wanjiku for KES 5,000

$ java -jar cli/target/boxoffice.jar sell REG "Kamau family" -q 4
Sold 4 tickets to Kamau family for KES 6,000: A-REG-0001 to A-REG-0004

$ java -jar cli/target/boxoffice.jar sync --server http://127.0.0.1:8080
Sent 2 sales. Nothing waiting.

$ java -jar cli/target/boxoffice.jar sync --server http://127.0.0.1:8080
Sent 0 sales. Nothing waiting.
```

(On Windows, write `cli\target\boxoffice.jar`.) Two sales, not five tickets: the family is one sale. `sync` still goes to the practice server (port 8099) unless you give it `--server`; `register`'s default is already `http://127.0.0.1:8080`.

**A laptop registered again.** Make a second gate database that also says it's gate A, and register it. Gate A now has a new token, so the first laptop's token stops working:

```
$ java -jar cli/target/boxoffice.jar --db new-laptop.db setup --gate A
Kilele Nights is ready in new-laptop.db as gate A

$ BOXOFFICE_ADMIN_KEY=pick-a-long-secret java -jar cli/target/boxoffice.jar --db new-laptop.db register --server http://127.0.0.1:8080
Gate A is registered with http://127.0.0.1:8080. sync logs in with it from now on.

$ java -jar cli/target/boxoffice.jar sell VIP Otieno
Sold A-VIP-0002 to Otieno for KES 5,000

$ java -jar cli/target/boxoffice.jar sync --server http://127.0.0.1:8080
Sent 0 sales. 1 still waiting: The server doesn't know this laptop. Run register first
```

That's what you'd do for a stolen laptop. Register `gate.db` again to make it work.

**Without the gate.** On macOS and Linux you can ask the server yourself with `curl`. (Windows has `curl.exe` too, but its quoting rules differ between PowerShell and cmd; there, `register` and `sync` do the same job.) A sale with no token:

```
curl -i -X POST http://127.0.0.1:8080/sync/sales -H "Content-Type: application/json" -d "{}"
```

It answers `HTTP/1.1 401` and `{"error":"Unknown device. Register this laptop first"}`: no token, no sync. Registering gate B, as the organiser:

```
curl -X POST http://127.0.0.1:8080/admin/devices -H "X-Admin-Key: pick-a-long-secret" -H "Content-Type: application/json" -d "{\"gate\":\"B\"}"
```

It answers `{"token":"...","gate":"B"}`, with 43 random characters where the dots are.

## When you're stuck

- **`Not written yet: Day 119, step 3`**: `Tokens.hash` still has its `throw`. Every test that registers a gate needs it.
- **`expected: "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad" but was: "ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0="`**: you wrote the bytes as Base64. Use `HexFormat.of().formatHex(digest)`: the hash must be 64 hex characters.
- **`expected: 401 but was: 400`**, and the log says `Missing request attribute 'gate' of type String`: `preHandle` still returns `true` without finding the gate. Do step 4.
- **`Request failed unexpectedly: ... java.util.NoSuchElementException: No value present`**, and `GateToServerTest` says `Gave up after 2 attempts: HTTP 500`: after writing the 401, `preHandle` didn't `return false`, so the controller ran anyway with no gate. Add the `return false`.
- **`aGateCanOnlySendItsOwnTickets` says `expected: 403 but was: 201`**: the gate check in `Sales.receive` is missing. Do step 5.
- **`expected: "Bearer t0ken" but was: null`**: `HttpSyncClient` doesn't send the header. Do step 6.
- **`expected: "Bearer t0ken" but was: "Bearert0ken"`**: you forgot the space after `Bearer`.
- **`aGateSyncsItsSalesToTheServer` says `problem=The server doesn't know this laptop. Run register first`**: the same: the gate doesn't send its token yet. Do step 6.
- **`cli`, `desktop` and `sync server` are `SKIPPED`**: an earlier module failed, so Maven stopped. Use the `-Dtest=...` command from step 1 to run the server's tests on their own.
- **`Web server failed to start. Port 8080 was already in use.`**: another server is running, maybe one you started earlier in another terminal. Stop it with Ctrl+C there, or start this one with `--server.port=9090`.
- **`register` says `Set BOXOFFICE_ADMIN_KEY to the key the sync server was started with`**: set the variable in *this* terminal too.
- **`register` says `The server said no: Wrong admin key`**: the key in this terminal isn't the one the server was started with. Check for a typo or a stray space.
- **`register` says `The server said no: Start the server with BOXOFFICE_ADMIN_KEY set to register gates`**: the server was started without `BOXOFFICE_ADMIN_KEY`, so it refuses to register anyone. Stop it, set the variable in that terminal, and start it again.
- **`register` says `Give this laptop a gate id first: setup --gate A`**: run `setup --gate A` first. The gate id is what the server registers.
- **`sync` says `Can't reach the server. Trying again in 0.3 s`, then `Gave up after 5 attempts: Can't reach the server`**: you left out `--server http://127.0.0.1:8080`, so it tried the practice server on 8099. Or the server isn't running: check terminal 1.
- **`sync` says `The server doesn't know this laptop. Run register first`**: this laptop never registered, or the same gate was registered again since (from another `--db`), or you deleted `sync-server-db.mv.db`. Run `register` again.
- **A `WARN` that H2 2.4.240 is `newer than the version Flyway has been verified with`**: harmless. Flyway and H2 work fine together here.
- **Still stuck?** Open `solution/server/src/main/java/com/boxoffice/server/DeviceLogin.java` (or `Tokens.java`, `Sales.java`, `solution/core/src/main/java/com/boxoffice/core/sync/HttpSyncClient.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say what Spring Boot does when it starts, and what constructor injection is
- [ ] Can say what a BOM is, and why ours is imported and not the parent
- [ ] Can say why the server keeps a hash, and why a plain SHA-256 is enough for a token but not a password
- [ ] Can say the difference between 401 and 403
- [ ] Wrote `Tokens.hash`, `DeviceLogin.preHandle`, the gate check and the `Authorization` header
- [ ] All 259 tests pass
- [ ] Started the server, registered gate A, and synced two sales to it
- [ ] Saw an old token stop working after registering again
- [ ] Committed your work to Git (not `gate.db`, `new-laptop.db` or `sync-server-db.mv.db`, and never your admin key)

## Next

Gates can sync to a real server, and only real gates. Tomorrow two gates, offline all evening, both sell the last VIP ticket. Both are sure they were right. The server has to decide, and tell the gate that lost.
