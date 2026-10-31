# Day 115: A client that survives bad Wi-Fi

> 19:20. The venue router restarts for the third time tonight. The gate laptop asks the server something, and waits. And waits.

Watch the video: *(not recorded yet)*

## The brief

Yesterday the gate talked to the internet for the first time. It assumed the internet would answer. At Ngong Racecourse it often doesn't: the Wi-Fi drops for ten seconds, the router restarts, a server is busy and says "come back later". From Day 116 on, the gate will send every sale to a server. So before that, it needs an HTTP client that copes:

- **A time limit on every request.** A server that never answers must not keep the gate waiting for ever.
- **Try again, and wait a little longer each time.** Half a second, then 1 s, then 2 s, then 4 s, with a little randomness, so twenty gate laptops don't all come back in the same millisecond.
- **Only repeat what's safe to repeat.** This is the important one. Asking "are you there?" twice does no harm. Sending "sell this ticket" twice might sell it twice.

Today you build that client, `ResilientHttp`, and yesterday's SMS reminders go through it. A new command, `ping`, shows every retry as it happens:

```
$ java -jar cli/target/boxoffice.jar ping http://127.0.0.1:9
Can't reach the server. Trying again in 0.5 s (attempt 2 of 5)
Can't reach the server. Trying again in 0.8 s (attempt 3 of 5)
Can't reach the server. Trying again in 1.3 s (attempt 4 of 5)
Can't reach the server. Trying again in 3.4 s (attempt 5 of 5)
Gave up after 5 attempts: Can't reach the server
```

## What you'll learn

- Why every network request needs a **timeout**, and the two kinds `HttpClient` has
- What **retrying** is, and when it makes things worse
- **Exponential backoff**: doubling the wait after each failure, up to a limit
- **Jitter**: why a bit of randomness in the wait helps when many laptops fail together
- Which requests are safe to send again: **GET** always, a **POST** only in three cases
- What the status codes 429, 500, 502, 503 and 504 mean, and what `Retry-After` asks for
- How an injected `Sleeper` lets a test of "wait 8 seconds" run in no time

## New words

- **Timeout**: the longest you'll wait for something before giving up on it.
- **Connect timeout**: how long to wait for the other computer to pick up at all.
- **Request timeout**: how long to wait for the answer once you've asked.
- **Retry**: sending the same request again after it failed.
- **Attempt**: one try. The first send is attempt 1; the first retry is attempt 2.
- **Backoff**: waiting before a retry, so a struggling server or router gets time to recover.
- **Exponential backoff**: doubling the wait each time: 0.5 s, 1 s, 2 s, 4 s...
- **Jitter**: a random part of the wait. Here, somewhere between half and all of the backoff.
- **GET** and **POST**: the two kinds of web request you'll meet most. GET asks for something; POST sends something to be saved or done.
- **Status code**: the number at the start of every web answer. 200 is "OK", 404 "not found", 500 "something broke".
- **Idempotent**: safe to do twice; the second time changes nothing. Reading a page is idempotent. Selling a ticket is not.
- **Idempotency key**: a unique label on a request, so the server can spot a repeat and not do the work twice. You'll use one tomorrow.
- **`Retry-After`**: a header in a server's answer that says "try again in this many seconds".
- **Bit shift** (`<<`): `x << n` doubles `x`, `n` times. `500 << 3` is 4000.
- **Overflow**: a number too big for its type. A `long` that overflows wraps round and goes negative.

## What's already written

From Days 101–114: Box Office v1 and the SMS reminders, working, with tests. New today, already done:

- A new package, `com.boxoffice.core.net`:
  - `RetryPolicy`: a record of how hard to try, `maxAttempts`, `firstDelay` and `maxDelay`, with the gate's settings in `GATE_WIFI` (5 attempts, 500 ms, 8 s). You write `delayBefore`.
  - `Sleeper`: waits. `Sleeper.REAL` calls `Thread.sleep`; tests pass one that only writes the wait down.
  - `NetworkException`: "no useful answer, even after every attempt we were allowed".
  - `ResilientHttp`: the client. `send` (the loop of attempts), `neverArrived`, `describe` and `retryAfter` are written. You write `withTimeout`, `repeatable` and `worthRetrying`.
- `AfricasTalkingSms` now sends through `ResilientHttp`. Its new tests check that a busy gateway is tried again, and that a text is never sent twice after a server error.
- The CLI has a new command, `ping <url>` (in `PingCommand.java`). `remind` now prints its retries too.
- Test helper: `ScriptedServer.java`, a bad server on your laptop that answers each request with the next step of its script: "503", "hang for 2 seconds", "200"...

New, for you to finish: `RetryPolicy.delayBefore`, and three methods in `ResilientHttp`. New tests: `RetryPolicyTest` (4), `ResilientHttpTest` (12), two more in `AfricasTalkingSmsTest` and two more in `BoxOfficeCliTest`.

## What you'll use

**Two time limits.** `HttpClient` has a **connect timeout**, set once on the client (3 s in `forTheGate`), and a **request timeout**, set on each request. Without the second, a server that picks up and then says nothing keeps you waiting for ever. A request is **immutable**: you can't change it, so `withTimeout` makes a copy with the time limit added:

```java
HttpRequest.newBuilder(request, (name, value) -> true)   // copy it, keeping every header
        .timeout(timeout)
        .build();
```

The `(name, value) -> true` is a filter that says "keep every header".

**Backoff, doubled, then capped.** The wait before attempt 2 is `firstDelay`, before attempt 3 twice that, before attempt 4 four times that. In code, `firstDelay.toMillis() << (attempt - 2)`. With 500 ms: 500, 1000, 2000, 4000. Then `Math.min(..., maxDelay.toMillis())` stops it growing past 8 s.

Careful: a `long` holds 63 doublings, and Java takes a shift count modulo 64. Shift too far and the number wraps round to a negative wait, or comes back small. So when `attempt - 2` is 30 or more, just use `maxDelay`: by then you're far past it anyway.

**Jitter.** Twenty gate laptops lose the Wi-Fi at the same moment. With plain backoff, they all retry together at 0.5 s, all together again at 1.5 s, and hit the router at the same instant every time. With jitter each waits somewhere between half and all of the backoff, chosen at random, so they spread out:

```java
Duration.ofMillis(backoff / 2 + Math.round(roll * (backoff / 2.0)))
```

`roll` is a random number from 0 to 1. The tests pass a fixed `roll`, so the answer is always the same: `1.0` gives the whole backoff, `0.0` gives half.

**Which requests are safe to send again?** This is today's big idea. There are two ways a request can fail:

| What happened | Did the server see it? | Safe to send again? |
|---|---|---|
| Couldn't connect at all | No: it never arrived | Always |
| The server answered 429 or 503 | Yes, and it says it did nothing | Always |
| No answer in time, or the line dropped | **Maybe**: it may have done the work and only the answer got lost | Only if repeating can't do harm |
| The server answered 500, 502 or 504 | Yes, and it broke, maybe halfway | Only if repeating can't do harm |

"Repeating can't do harm" is `repeatable`. A **GET** only reads, so it's always repeatable (and `HEAD`, `PUT`, `DELETE` and `OPTIONS` are too, by the web's rules). A **POST** that sells a ticket is not, unless it carries an `Idempotency-Key` header: then the server can see "I've had this one before" and not sell it twice. Tomorrow every sale gets one.

`send` already uses these rules. Your two methods feed it:

- `repeatable(request)`: `true` when the method is in `REPEATABLE_METHODS`, or the request has an `Idempotency-Key` header. `request.headers().firstValue("Idempotency-Key")` gives an `Optional`; `.isPresent()` says if it's there.
- `worthRetrying(status, repeatable)`: a `switch` on the status. 429 and 503 always; 500, 502 and 504 only when `repeatable`; anything else, like 200 or 404, is the answer, and asking again won't change it.

**`Retry-After`.** A server that answers 429 "too many requests" or 503 "busy" may add `Retry-After: 2`. `send` then waits 2 s instead of its own backoff, but never longer than `maxDelay`. That's written already.

**Tests that don't really wait.** `ResilientHttp` doesn't call `Thread.sleep` itself. It calls `sleeper.sleep(wait)`, and the `Sleeper` is passed in. The tests pass `waits::add`, which writes the wait into a list and returns at once. So "wait 4 s and try again" takes no time, and the test can check the list: `containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1))`. The same trick as the fixed `Clock` you've used since Day 103.

## Steps

1. **Open the starter and run the tests.** `cd day-115-surviving-bad-wifi/starter`, then `../../mvnw test`. You should see `Tests run: 134, Failures: 3, Errors: 19` in `core`, and `BUILD FAILURE`. `cli` and `desktop` are `SKIPPED`. The failures say `Not written yet: Day 115, step 3` (in `RetryPolicyTest`) or `step 5` (in `ResilientHttpTest` and `AfricasTalkingSmsTest`). Three of them say `Expecting actual throwable to be an instance of: com.boxoffice.core.net.NetworkException but was: java.lang.UnsupportedOperationException: Not written yet: Day 115, step 5`: the same thing, said differently.

2. **Read the loop.** Open `starter/core/src/main/java/com/boxoffice/core/net/ResilientHttp.java` and read `send` from top to bottom. Find where it asks `policy.delayBefore`, where it calls `worthRetrying`, and the line that throws `it isn't safe to send again`. Then open `starter/core/src/test/java/com/boxoffice/core/net/ScriptedServer.java` and see how a test scripts a bad server. Nothing to write.

3. **Wait a little longer each time.** Open `RetryPolicy.java` and find `delayBefore(attempt, roll)`. Replace the `throw`, following the TODO:
   - `int doublings = attempt - 2;` and `long max = maxDelay.toMillis();`
   - `long backoff`: `max` when `doublings >= 30`, otherwise `Math.min(max, firstDelay.toMillis() << doublings)`;
   - return `Duration.ofMillis(backoff / 2 + Math.round(roll * (backoff / 2.0)))`.

   Run `../../mvnw test -Dtest=RetryPolicyTest -Dsurefire.failIfNoSpecifiedTests=false`. All 4 pass.

4. **Put a time limit on every request.** In `ResilientHttp.java`, find `withTimeout`. It returns the request as it is. Replace that with `HttpRequest.newBuilder(request, (name, value) -> true).timeout(timeout).build()`.

   The tests still say `step 5`, because `send` needs step 5 too. Carry on.

5. **Decide what's safe to repeat.** Still in `ResilientHttp.java`, replace the two `throw`s:
   - `repeatable`: return `REPEATABLE_METHODS.contains(request.method()) || request.headers().firstValue("Idempotency-Key").isPresent()`;
   - `worthRetrying`: `return switch (status) { ... };` with `case 429, 503 -> true;`, `case 500, 502, 504 -> repeatable;` and `default -> false;`.

   Run `ResilientHttpTest`: all 12 pass, in a few seconds. Then `AfricasTalkingSmsTest`: all 7 pass.

6. **Run all the tests.** `../../mvnw test`. You should see 189 tests in all and `BUILD SUCCESS`.

7. **Watch the backoff.** Follow "Run it and test it" below: ping a server that isn't there, then one that comes back halfway through.

8. **Stretch (not required): a gate that gives up sooner.** In your own copy, add a `--attempts` option to `PingCommand` that builds a `ResilientHttp` with `new RetryPolicy(attempts, Duration.ofMillis(500), Duration.ofSeconds(8))` instead of `cli.http`. Try `--attempts 1` and `--attempts 8`. What's the longest `ping` can now take?

## Run it and test it

From the day's `starter` folder:

```
cd day-115-surviving-bad-wifi/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=ResilientHttpTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=ResilientHttpTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **134 tests** in `core` (including ResilientHttp 12, RetryPolicy 4, AfricasTalkingSms 7), **14** in `cli` and **41** in `desktop`, **189** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

**Ping a server that isn't there.** Build, then ping port 9, where nothing on your laptop listens (on Windows, write `cli\target\boxoffice.jar`):

```
../../mvnw package -DskipTests
```

```
java -jar cli/target/boxoffice.jar ping http://127.0.0.1:9
```

```
Can't reach the server. Trying again in 0.5 s (attempt 2 of 5)
Can't reach the server. Trying again in 0.8 s (attempt 3 of 5)
Can't reach the server. Trying again in 1.3 s (attempt 4 of 5)
Can't reach the server. Trying again in 3.4 s (attempt 5 of 5)
Gave up after 5 attempts: Can't reach the server
```

It takes several seconds, on purpose. Your waits will be different every time: that's the jitter. Each one is between half and all of 0.5 s, 1 s, 2 s and 4 s.

**Ping a server that comes back.** Java comes with a tiny web server, `jwebserver`. Open a second terminal, in an empty folder, and have it ready to start:

```
jwebserver -p 8000
```

In the first terminal, start the ping **first**, then press Enter in the second terminal while the retries are still going:

```
java -jar cli/target/boxoffice.jar ping http://127.0.0.1:8000/
```

```
Can't reach the server. Trying again in 0.3 s (attempt 2 of 5)
Can't reach the server. Trying again in 0.8 s (attempt 3 of 5)
Can't reach the server. Trying again in 1.4 s (attempt 4 of 5)
http://127.0.0.1:8000/ answered HTTP 200
```

That's venue Wi-Fi coming back after a router restart. Stop `jwebserver` with Ctrl+C.

`ping not-a-url` says `That isn't a web address: not-a-url`. `ping --help` shows how to use it.

## When you're stuck

- **`Not written yet: Day 115, step 3`** or **`step 5`**: that method still has its `throw`. After step 4 the tests still say `step 5`; that's expected.
- **`doublesTheWaitAfterEachFailure` says `expected: 0.5S but was: 1S`**: you doubled once too often. The wait before attempt 2 is `firstDelay` itself: shift by `attempt - 2`, not `attempt - 1`.
- **`neverWaitsLongerThanTheLongestWait` says `expected: 8S but was: 1M4S`**: the `Math.min` with `maxDelay` is missing. (`1M4S` is how a `Duration` writes 1 minute 4 seconds.)
- **`jitterWaitsBetweenHalfAndAllOfTheBackoff` says `expected: 1S but was: 2S`**: there's no jitter yet. Return half the backoff plus `roll` times the other half.
- **`stopsWaitingForAServerThatHangs` says `expected: 2 but was: 1`**, and **`neverResendsAPostThatMayHaveArrived` says `Expecting code to raise a throwable.`**: `withTimeout` still returns the request without a time limit, so the client waited out the whole 2-second hang. Do step 4.
- **`neverResendsAPostAfterAServerError` says `expected: 500 but was: 200`**: a POST with no key was sent again after a 500. In `worthRetrying`, 500, 502 and 504 give `repeatable`, not `true`. (`AfricasTalkingSmsTest.doesNotSendATextTwiceAfterAServerError` fails for the same reason.)
- **`resendsAPostThatCarriesAnIdempotencyKey` says `expected: 200 but was: 500`**: `repeatable` ignores the `Idempotency-Key` header.
- **`waitsAsLongAsTheServerAsks` fails now and then, with `Expecting actual: [0.5S] to contain exactly (and in same order): [2S]`**, and passes when you run it again: on a busy laptop, the test's first answer took longer than its 1 s time limit, so it counted as "no answer in time" instead of a 429. Close other heavy programs and run it again. If it fails every time, check that you didn't change `send`.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/net/ResilientHttp.java` (or `RetryPolicy.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say why every request needs a time limit
- [ ] Can explain exponential backoff, and why it's capped
- [ ] Can explain what jitter is for, with twenty laptops in mind
- [ ] Can say which failures make a POST safe to send again, and which don't
- [ ] Wrote `delayBefore`, `withTimeout`, `repeatable` and `worthRetrying`
- [ ] All 189 tests pass, in seconds, with no real waiting
- [ ] Watched `ping` back off, and catch a server that came back
- [ ] Committed your work to Git (not `gate.db`)

## Next

The client survives bad Wi-Fi, but only for one request at a time. Tomorrow the gate keeps selling while the Wi-Fi is down for an hour: every sale waits in an **outbox** on the laptop's own disk, in the same transaction as the sale, and goes to the server later, in order, each with its own idempotency key.
