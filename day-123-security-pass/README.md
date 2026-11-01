# Day 123: Security pass

> Friday, the day before Kilele Nights. Kamau's phone has rung with forty M-Pesa prompts in an hour, and he never ordered a ticket.

Watch the video: *(not recorded yet)*

## The brief

Since Day 121, the sync server is also a public ticket site, and since Day 122 it sends real M-Pesa prompts. Anyone on the internet can reach it now, not just the two gates. Most visitors are fans. A few are not:

- a script that places 500 orders a minute, so every place is "held" and real fans see "Sold out";
- someone who types Kamau's number into the order form again and again, so his phone rings all night;
- someone who tries admin keys one after another until one works;
- a stolen gate laptop that sends a "sale" with a 5,000-character name, a price of minus one shilling, or 1,000 tickets;
- a page on another site that puts our "Pay" button inside a frame and tricks a fan into clicking it;
- a stranger on the venue Wi-Fi who asks a gate's LAN feed (Day 117) for every buyer's name and phone number.

Today you close those doors, one at a time. None of it is new code for selling tickets. It's the work you do before you let the public in:

```
$ curl -i -X POST http://127.0.0.1:8080/events/kilele-2026/orders -H "HX-Request: true" -d type=REG -d quantity=1 -d buyer=Otieno -d "phone=0712 345 003"
HTTP/1.1 429
Retry-After: 29
HX-Retarget: #notice
HX-Reswap: innerHTML
Content-Type: text/html;charset=UTF-8

Too many orders from here. Wait 29 seconds and try again.
```

You write five pieces: the rate limiter's bucket, finding the real caller behind a proxy, checking what a gate sends, the security headers, and the venue key on the LAN feed.

## What you'll learn

- What a **rate limit** is, and how a **token bucket** allows a burst but not a flood
- Why `ConcurrentHashMap.compute` makes "take a token" safe when two requests arrive at once
- What **429 Too Many Requests** and `Retry-After` mean
- Why rate limits go per IP address for orders, but per **phone number** for M-Pesa prompts
- What `X-Forwarded-For` is, why anyone can fake it, and why the number of **trusted proxies** decides which address you believe
- What a **Content-Security-Policy** is, and why our pages can have a strict one
- What `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` and `Cache-Control: no-store` each stop
- Why the server checks everything a gate sends, even a gate with a good token
- Why a **control character** in a name is a problem
- Why the server refuses to start with a weak secret
- Why M-Pesa callbacks need three checks, since they aren't signed
- How the LAN feed asks for a shared **venue key**, and why it compares keys with `MessageDigest.isEqual`

## New words

- **Rate limit**: "at most this many requests in this much time". Ours: 10 orders a minute from one address.
- **Token bucket**: a way to count a rate limit. Each caller has a bucket of tokens. Each request takes one. The bucket fills back up slowly, one token at a time.
- **Burst**: several requests close together. A family ordering three times in ten seconds is a burst. The bucket allows it, up to its size.
- **429 Too Many Requests**: the HTTP answer for "slow down".
- **`Retry-After`**: a header with the 429 that says how many seconds to wait.
- **IP address**: the number that says where on the internet a request comes from, like `41.90.2.2`.
- **Proxy**: a server that stands in front of yours and passes requests on. Hosting platforms (Day 124) put one in front of every app.
- **`X-Forwarded-For`**: a header a proxy adds, saying which address it got the request from. A list, like `41.90.2.2, 10.0.0.5`.
- **Spoofing**: pretending to be someone else, here by writing a made-up address into `X-Forwarded-For`.
- **Response header**: a line the server sends before the page, like `Content-Type: text/html`. The browser reads it, the fan never sees it.
- **Content-Security-Policy (CSP)**: a header that tells the browser where scripts, styles and images may come from. Anything else, it refuses to run.
- **Inline script**: JavaScript written inside the page, in `<script>...</script>` or `onclick="..."`, instead of in its own file.
- **Clickjacking**: putting someone's page inside an invisible frame on yours, so a click meant for your page lands on theirs.
- **Control character**: an invisible character, like a newline or a NUL (character 0). `Character.isISOControl` says if a character is one.
- **Allowlist**: a list of the only things that are let in. Everything else is refused.
- **Venue key**: a secret both gates at one venue share. The LAN feed only answers a gate that sends it.
- **Timing attack**: guessing a secret by measuring how long a comparison takes. `equals` stops at the first wrong character, so a nearly right guess takes a tiny bit longer.

## What's already written

From Days 101–122: the gate app, the sync server, the ticket site and M-Pesa payments, working, with tests. New today, already done:

On the server:

- `Limits.java`: three `RateLimiter`s, built from `application.properties`: orders per minute (10), admin calls per minute (5), and M-Pesa prompts per phone per 10 minutes (3). Also the number of trusted proxies.
- `RateLimiter.java`: everything but the bucket itself. `Decision` has `allowed`, `retryAfter`, and `retryAfterSeconds()` (rounded up, never 0).
- `RateLimits.java`: a `HandlerInterceptor`, like Day 119's `DeviceLogin`. `WebConfig` puts it in front of `/events/*/orders` and `/admin/**`. A POST to place an order or any `/admin` call takes a token for the caller's address. No token: `429` with `Retry-After`. On the site it also sends `HX-Retarget: #notice` and `HX-Reswap: innerHTML`; on `/admin` it answers JSON, `{"error":"Too many tries. Wait 30 seconds"}`.
- `event.html` and `order.html`: an empty `<p id="notice">` for that message, and an `htmx-config` meta tag that tells htmx to show a 429 answer too, and not to add its own `<style>` tag (the CSP would block it).
- `Payments.requestPayment`: takes a token for the **phone number** before every prompt. None left: `Too many M-Pesa prompts to this number. Try again in N minutes`.
- `Orders`: a buyer name with a control character in it gets `Use letters, spaces and punctuation only`.
- `SaleChecks.java`: the first two checks of a gate's sale (the event id, at least one ticket). `Sales.receive` now calls `SaleChecks.problem(sale)` first and answers `400` with the problem.
- `SecurityHeaders.java`: a filter, with the policy written out in `POLICY`. You set the headers.
- `StartupChecks.java`: refuses to start when `BOXOFFICE_ADMIN_KEY` is set but shorter than 24 characters, or, in `sandbox` or `live` M-Pesa mode, when `MPESA_CALLBACK_SECRET` is shorter than 32 characters or has anything but letters, digits, `-` and `_`, or `MPESA_CALLBACK_BASE` doesn't start with `https://`.
- `MpesaSettings` has `callbackIps`, from `MPESA_CALLBACK_IPS`. `MpesaCallback` ignores a callback from any other address when the list isn't empty.
- `CallerIp.java`: the method you write, with its comment.
- Test settings: the test admin key became `test-admin-key-long-enough-for-day-123`, and the tests' limits are 10,000, so only `RateLimitTest` ever sees a 429.

On the gate:

- `TicketStore.venueKey()` and `setVenueKey(...)` (16 to 100 letters, digits, `-` or `_`).
- The CLI's `setup --venue-key ...`. `serve-feed` refuses to start without one: `Set a venue key first, the same on both gates: setup --venue-key ...`.
- `PeerPuller` sends `Authorization: Bearer <venue key>`, and turns a 401 into `The other gate has a different venue key. Set the same one on both: setup --venue-key ...`.

Tests: `RateLimiterTest` (6), `CallerIpTest` (6), `RateLimitTest` (6), `SecurityPassTest` (10), `CallbackAddressTest` (3), `StartupChecksTest` (6), 4 more in `LanSyncTest`, 2 more in `BoxOfficeCliTest`.

New, for you to finish: the bucket in `RateLimiter.java` (step 2), `CallerIp.of` (step 3), the rest of `SaleChecks.problem` (step 4), the headers in `SecurityHeaders.java` (step 5), and the venue key check in `GateFeedServer.java` (step 6). The TODOs are labelled with their step numbers.

## What you'll use

**A token bucket.** Picture a bucket that holds 10 tokens. Every order takes one. A token drips back in every 6 seconds (60 seconds / 10), and the bucket never holds more than 10. A family that orders 3 times in a minute never notices. A script that orders 500 times a minute empties the bucket in a second, then gets one order every 6 seconds and 429 for the rest. Each IP address has its own bucket.

The bucket only needs two numbers: how many tokens it had, and when. `refilled(bucket, now)` (already written) works out how many it has **now**: what it had, plus one for every `oneToken` since, up to full. So there's no timer anywhere. Your part, for one request:

```java
double tokens = bucket == null ? perPeriod : refilled(bucket, now);
if (tokens >= 1) {
    // allowed: take one
} else {
    // not allowed: how long until the next whole token?
    long waitNanos = (long) Math.ceil((1 - tokens) * oneToken.toNanos());
}
```

With 0.4 tokens and a token every 30 seconds, the wait is 0.6 × 30 = 18 seconds. `Math.ceil` rounds up, so the answer is never "wait 0".

**`compute` does one key at a time.** Two requests from the same address arrive at the same moment, and the bucket has one token. If both read "1 token" and then both save "0 tokens", both got in. That's Day 118's race again. `ConcurrentHashMap.compute(key, (k, bucket) -> ...)` runs the lambda for one key at a time: the second request waits, then sees 0. The lambda returns the new `Bucket`, and `compute` saves it. Lambdas can't change a local variable, so the decision goes in a one-element array, `decision[0] = ...`, that the code after `compute` reads.

**429 and `Retry-After`.** `429 Too Many Requests` says "slow down". `Retry-After: 30` says "try again in 30 seconds". A polite script reads it and waits. A fan sees the message in the page's notice line: `RateLimits` sends `HX-Retarget: #notice` and `HX-Reswap: innerHTML`, which tell htmx "put this answer in `#notice`, not where the form is". The form stays as it was, with what the fan typed.

**Per phone, not per address, for prompts.** A stranger can make orders from many addresses (a phone on mobile data gets a new one often). But Kamau has one phone number. So `Payments` counts prompts per **phone**: 3 every 10 minutes, whoever asks. Placing an order sends the first prompt, and each "No prompt? Send it again" sends another. The fourth gets `Too many M-Pesa prompts to this number. Try again in 4 minutes`.

**Who is really calling? `X-Forwarded-For`.** On your laptop, `request.getRemoteAddr()` is the caller. On a real host (Day 124) there's a proxy in front of the server, so **every** request seems to come from the proxy, `10.0.0.5`. One bucket for the whole of Nairobi. The proxy helps: it adds the address it got the request from to the end of `X-Forwarded-For`.

But that header is just text, and the caller can send it too. A script sends `X-Forwarded-For: 196.201.214.200` (one of Safaricom's addresses), and our proxy adds the real one after it:

```
X-Forwarded-For: 196.201.214.200, 41.90.2.2      (what the script wrote, then what our proxy added)
remote address:  10.0.0.5                        (our proxy)
```

The only entries you can trust are the ones **your own** proxies wrote, and they're at the end. So you count from the end. Make one list, the header's entries and then the remote address:

```
[196.201.214.200, 41.90.2.2, 10.0.0.5]
```

With 1 trusted proxy, skip 1 from the end: the caller is `41.90.2.2`. Whatever the script writes at the start, it can't change that. In code: `chain.get(chain.size() - 1 - trustedProxies)`.

**Why the count matters.** Set `trusted-proxies` too low (0 behind a proxy) and every fan shares the proxy's bucket: ten orders a minute for the whole site. Set it too high (2 behind one proxy) and you skip past the real caller into what they wrote: anyone gets a fresh bucket by sending a new made-up address each time, and anyone can claim to be Safaricom. With no proxy at all (your laptop, `trusted-proxies` 0, the default), ignore the header completely. Day 124's production profile sets it to 1, because a real host has one proxy in front.

**Check everything a gate sends.** On Day 119, a gate with a good token was trusted. But a token can be stolen with the laptop, and code has bugs. A 5,000-character name used to reach the database, which refused it, and the gate got a `500`. Now `SaleChecks` refuses it first with a `400` that says what's wrong. It checks in order and stops at the first problem: how many tickets, then each ticket's code, type, buyer, phone, price and time. When it repeats something the gate sent (a bad code), it cuts it short with `shorten`, so a 5,000-character code doesn't come back in the answer and in the logs.

**Control characters.** A buyer called `Otieno` + newline + `PAID IN FULL` looks like two lines on a receipt, a CSV export or a log file. A NUL can cut a name short in some programs. `buyer.chars().anyMatch(Character::isISOControl)` finds them. Names with accents or spaces, like `Wanjikũ Mwangi`, are fine.

**Headers that tell the browser what to refuse.** `SecurityHeaders` is a filter: it runs for every request, before any controller, so no page can forget them.

- `Content-Security-Policy`: scripts, styles and images only from this server. Our pages have no inline scripts and no inline styles: htmx comes from `/webjars/...`, the styles from `/site.css`, and htmx's settings are in a `<meta>` tag. So the policy can say `script-src 'self'` and mean it. If a name like `<script>...` ever slipped past Thymeleaf's escaping, the browser still wouldn't run it.
- `X-Content-Type-Options: nosniff`: don't guess that a text answer is really a script.
- `X-Frame-Options: DENY`: nobody can put our pages in a frame, so no clickjacking.
- `Referrer-Policy: no-referrer`: an order's address, `/orders/JJED-SQQ2`, is its secret (Day 121). Without this, a click on a link from that page would send the address to the other site.
- `Cache-Control: no-store`, on `/orders/...` only: a fan's name, phone and tickets must not sit in a shared cache, like an office proxy's.

**Refuse to start with weak secrets.** `StartupChecks` runs when the server starts. An admin key of `admin` is guessed in seconds, so a key under 24 characters stops the server with `The server won't start:` and the reason. Better on Friday afternoon than all Saturday night. To make a good one:

```
openssl rand -base64 32                                              # macOS / Linux
[Convert]::ToBase64String((1..32 | % { Get-Random -Max 256 }))       # Windows PowerShell
```

**M-Pesa callbacks aren't signed.** Safaricom's callback has no signature, so anyone who knows the address can send a "paid". Trust comes from three checks together, and all of them must pass:

1. the long random secret at the end of the callback address (Day 122);
2. the caller's address is on `MPESA_CALLBACK_IPS`, Safaricom's list (worked out with `CallerIp`, so a faked header doesn't help). Safaricom changes the list: ask them for the current one before the night, never copy one from a blog;
3. the `CheckoutRequestID` matches an order we sent a prompt for, and the amount matches what we asked for (Day 122).

**The venue key on the LAN feed.** Day 117's feed has every buyer's name and phone, and anyone can join the venue Wi-Fi. So both gates now share a venue key, set with `setup --venue-key`. The puller sends `Authorization: Bearer <venue key>`. The feed checks it:

```java
String header = exchange.getRequestHeaders().getFirst("Authorization");
String sent = header != null && header.startsWith("Bearer ") ? header.substring("Bearer ".length()) : "";
```

Compare with `MessageDigest.isEqual(sent.getBytes(StandardCharsets.UTF_8), venueKey.getBytes(StandardCharsets.UTF_8))`, not `equals`. `isEqual` takes as long whatever the bytes are, so timing tells a stranger nothing. A wrong key gets `401 {"error":"Wrong venue key"}`. Read the request's body first (`exchange.getRequestBody().readAllBytes()`), so the connection is left clean for the next request.

## Steps

1. **Open the starter and run the tests.** `cd day-123-security-pass/starter`, then `../../mvnw test`. You should see `Tests run: 185, Failures: 2` in `core` and `BUILD FAILURE`; `cli`, `desktop` and `sync server` are `SKIPPED`:
   - `LanSyncTest.aStrangerOnTheWifiGets401`: `expected: 401 but was: 200` (step 6);
   - `LanSyncTest.aGateWithADifferentVenueKeyGetsNothing`: `Expecting code to raise a throwable.` (step 6).

   Steps 2 to 5 are on the server. To see the server's new tests now, run only them:

   ```
   ../../mvnw test -Dtest="RateLimiterTest,CallerIpTest,RateLimitTest,SecurityPassTest,CallbackAddressTest,StartupChecksTest" -Dsurefire.failIfNoSpecifiedTests=false
   ```

   On Windows: `..\..\mvnw.cmd test "-Dtest=RateLimiterTest,CallerIpTest,RateLimitTest,SecurityPassTest,CallbackAddressTest,StartupChecksTest" "-Dsurefire.failIfNoSpecifiedTests=false"`. You should see `Tests run: 37, Failures: 8, Errors: 20`. Most say `Not written yet: Day 123, step 2`: every order and every admin call goes through the rate limiter now, so nothing works until it does. The four `CallerIpTest` failures say `expected: "41.90.2.2" but was: "10.0.0.5"` (or `"10.0.0.7"`). `StartupChecksTest` already passes.

2. **Fill the bucket.** Open `starter/server/src/main/java/com/boxoffice/server/RateLimiter.java` and find the TODO marked `step 2`, inside `compute`. Replace the `throw`:
   - `double tokens = bucket == null ? perPeriod : refilled(bucket, now);` (a new key starts with a full bucket);
   - if `tokens >= 1`: `decision[0] = new Decision(true, Duration.ZERO);` and `return new Bucket(tokens - 1, now);`
   - otherwise: `long waitNanos = (long) Math.ceil((1 - tokens) * oneToken.toNanos());`, then `decision[0] = new Decision(false, Duration.ofNanos(waitNanos));` and `return new Bucket(tokens, now);` (the tokens stay as they are: asking again while you wait doesn't make the wait longer).

   Run the server tests again. You should see `Tests run: 37, Failures: 14`. `RateLimiterTest` (6) and `RateLimitTest` (6) pass. Still failing: the four in `CallerIpTest`, `CallbackAddressTest.aCallbackFromSafaricomCounts` (`expected: PAID but was: AWAITING_PAYMENT`: it can't see Safaricom's address behind the proxy yet), and nine in `SecurityPassTest`.

3. **Find the real caller.** Open `CallerIp.java` and find the TODO marked `step 3`:
   - if `trustedProxies <= 0`, or `forwardedFor` is `null` or blank: `return remoteAddress;`
   - otherwise make the chain: `new ArrayList<>(Arrays.stream(forwardedFor.split(",")).map(String::strip).filter(address -> !address.isEmpty()).toList())`, then `chain.add(remoteAddress);`
   - `int caller = chain.size() - 1 - trustedProxies;` and `return chain.get(Math.max(0, caller));` (`Math.max(0, ...)`: a chain shorter than you expected gives its first entry, not a crash).

   You need `new ArrayList<>(...)` because `toList()` gives a list you can't add to.

   Run the server tests: `Tests run: 37, Failures: 9`. `CallerIpTest` (6) and `CallbackAddressTest` (3) pass. The nine left are all in `SecurityPassTest`.

4. **Check what a gate sends.** Open `SaleChecks.java` and find the TODO marked `step 4`. Write the checks in the order the TODO lists them, each one `return Optional.of("...")` with the exact message:
   - `sale.tickets().size() > MOST_TICKETS`: `"A sale has at most " + MOST_TICKETS + " tickets"`;
   - then `for (SaleRequest.TicketRequest ticket : sale.tickets())`, and inside:
     - code `null` or not matching `"[A-Z0-9]{1,3}-[A-Z0-9]{1,10}-\\d{4,6}"`: `"A ticket code looks like A-REG-0001, not " + shorten(ticket.code())`;
     - type `null` or not matching `"[A-Z0-9]{1,10}"`: `"A ticket type is a short code like REG"`;
     - buyer `null`, blank, or longer than 200: `"A buyer's name is 1 to 200 characters"`;
     - `ticket.buyer().chars().anyMatch(Character::isISOControl)`: `"A buyer's name can't have control characters in it"`;
     - phone not `null`, not empty, and not matching `"254[17]\\d{8}"`: `"A phone number is empty or like 254712345678"`;
     - `priceCents` below 0 or over `10_000_000`: `"A ticket costs between KES 0 and KES 100,000"`;
     - `try { Instant.parse(ticket.soldAt() == null ? "" : ticket.soldAt()); } catch (DateTimeParseException e) { return Optional.of("soldAt is a time like 2026-11-14T18:00:00Z"); }`

   In a Java string, a regex's `\d` is written `"\\d"`. Leave `return Optional.empty();` at the end.

   Run the server tests: `Tests run: 37, Failures: 3`. The three left are the headers.

5. **Set the security headers.** Open `SecurityHeaders.java` and find the TODO marked `step 5`. Before `chain.doFilter(request, response)`:
   - `response.setHeader("Content-Security-Policy", POLICY);`
   - `response.setHeader("X-Content-Type-Options", "nosniff");`
   - `response.setHeader("X-Frame-Options", "DENY");`
   - `response.setHeader("Referrer-Policy", "no-referrer");`
   - and `if (request.getRequestURI().startsWith("/orders/"))`: `response.setHeader("Cache-Control", "no-store");`

   They must go **before** `chain.doFilter`: once the page has been written, its headers have already gone.

   Run the server tests: `Tests run: 37, Failures: 0` and `BUILD SUCCESS`.

6. **Ask for the venue key.** Open `starter/core/src/main/java/com/boxoffice/core/lan/GateFeedServer.java` and find `knowsTheKey`, with the TODO marked `step 6`. Replace `return true;`:
   - `String header = exchange.getRequestHeaders().getFirst("Authorization");`
   - `String sent = header != null && header.startsWith("Bearer ") ? header.substring("Bearer ".length()) : "";`
   - if `MessageDigest.isEqual(sent.getBytes(StandardCharsets.UTF_8), venueKey.getBytes(StandardCharsets.UTF_8))`: `return true;`
   - otherwise: `exchange.getRequestBody().readAllBytes();`, then `answer(exchange, 401, "{\"error\":\"Wrong venue key\"}");` and `return false;`

   Run `../../mvnw test -Dtest=LanSyncTest -Dsurefire.failIfNoSpecifiedTests=false`: all 13 pass.

7. **Run all the tests.** `../../mvnw test`. You should see 382 tests in all and `BUILD SUCCESS`.

8. **Knock on the doors yourself.** Follow "Run it and test it" below: the headers, a 429, a faked `X-Forwarded-For`, a too-short admin key, and a stranger at the LAN feed.

9. **Stretch (not required): a test of your own.** In `RateLimiterTest`, write a test that a bucket of 3 a minute, emptied, lets exactly one more request in after 20 seconds and not two. `TestClock` moves time forward without waiting. Your test isn't part of the course's tests, so keep it in your own Git.

## Run it and test it

From the day's `starter` folder:

```
cd day-123-security-pass/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=SecurityPassTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=SecurityPassTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **185 tests** in `core` (including LanSync 13), **30** in `cli` (BoxOfficeCli 27, PracticeServer 3), **41** in `desktop` and **126** in `sync server` (CallbackAddress 3, CallerIp 6, DarajaClient 12, DeviceLogin 9, GateToServer 3, HeldPlaces 5, LastTicket 5, MpesaCallback 5, Payments 12, PayOnTheSite 6, RaceToTheServer 1, RateLimiter 6, RateLimit 6, SecurityPass 10, StartupChecks 6, StkCallback 8, StkPassword 6, SyncApi 5, TicketSite 12), **382** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] Box Office sync server ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**Build the jars.** `../../mvnw package -DskipTests`.

**A short admin key won't start.** Try the old example key from Day 119:

```
BOXOFFICE_ADMIN_KEY=pick-a-long-secret java -jar server/target/boxoffice-server.jar
```

Among the lines it prints:

```
Caused by: java.lang.IllegalStateException: The server won't start:
  BOXOFFICE_ADMIN_KEY is too short: use at least 24 random characters (openssl rand -base64 32 makes some)
```

Make your own key with `openssl rand -base64 32` (macOS / Linux) or `[Convert]::ToBase64String((1..32 | % { Get-Random -Max 256 }))` (PowerShell), and use it below. `change-me-to-a-long-random-secret-123` is only a placeholder.

**Terminal 1: the server, with low limits.** Ten orders a minute is a lot to type, so start it with 2. Anything in `application.properties` can be changed with `--name=value` after the jar:

macOS / Linux:

```
BOXOFFICE_ADMIN_KEY=change-me-to-a-long-random-secret-123 java -jar server/target/boxoffice-server.jar --boxoffice.limits.orders-per-minute=2 --boxoffice.limits.admin-per-minute=2
```

Windows PowerShell:

```
$env:BOXOFFICE_ADMIN_KEY="change-me-to-a-long-random-secret-123"
```

```
java -jar server\target\boxoffice-server.jar --boxoffice.limits.orders-per-minute=2 --boxoffice.limits.admin-per-minute=2
```

Windows cmd: `set BOXOFFICE_ADMIN_KEY=change-me-to-a-long-random-secret-123`, then the same `java` line. Wait for `Started SyncServerApplication`. Flyway says `Successfully applied 4 migrations` the first time in this folder.

**Terminal 2: knock.** `curl` comes with macOS, Linux and Windows 10 and later. In PowerShell, type `curl.exe`, not `curl` (there, `curl` is another command). `-i` prints the headers.

The headers, on every page:

```
$ curl -i http://127.0.0.1:8080/events/kilele-2026
HTTP/1.1 200
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Content-Type: text/html;charset=UTF-8
...
```

Three orders in a row from your laptop. `-H "HX-Request: true"` makes curl look like htmx. The first two get `200`, the third:

```
$ curl -i -X POST http://127.0.0.1:8080/events/kilele-2026/orders -H "HX-Request: true" -d type=REG -d quantity=1 -d buyer=Otieno -d "phone=0712 345 003"
HTTP/1.1 429
Retry-After: 29
HX-Retarget: #notice
HX-Reswap: innerHTML
Content-Type: text/html;charset=UTF-8

Too many orders from here. Wait 29 seconds and try again.
```

Pretend to be someone else. It doesn't help: with `trusted-proxies` 0, the header is ignored:

```
$ curl -i -X POST http://127.0.0.1:8080/events/kilele-2026/orders -H "X-Forwarded-For: 41.90.2.99" -d type=REG -d quantity=1 -d buyer=Kamau -d "phone=0712 345 004"
HTTP/1.1 429
Retry-After: 14
...
Too many orders from here. Wait 14 seconds and try again.
```

Guess the admin key. Two wrong guesses get `401 {"error":"Wrong admin key"}`. The third gets a 429, even with the right key:

```
$ curl -i -X POST http://127.0.0.1:8080/admin/devices -H "Content-Type: application/json" -H "X-Admin-Key: guess-3" -d "{\"gate\":\"A\"}"
HTTP/1.1 429
Retry-After: 28
Content-Type: application/json

{"error":"Too many tries. Wait 28 seconds"}
```

Wait half a minute, then place an order without `HX-Request`, as a browser without JavaScript would. The answer sends you to the order's page, and that page is never cached:

```
$ curl -i -X POST http://127.0.0.1:8080/events/kilele-2026/orders -d type=REG -d quantity=1 -d buyer=Wanjiku -d "phone=0712 345 678"
HTTP/1.1 302
Location: http://127.0.0.1:8080/orders/JJED-SQQ2

$ curl -i http://127.0.0.1:8080/orders/JJED-SQQ2
HTTP/1.1 200
Content-Security-Policy: default-src 'self'; ...
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Cache-Control: no-store
...
```

Your reference will be different. In a browser, open that order's page and press "No prompt? Send it again" three times (stub mode: no real prompt is sent). Placing the order was the first prompt, so the third press shows `Too many M-Pesa prompts to this number. Try again in 4 minutes`.

Stop the server with Ctrl+C. `sync-server-db.mv.db` is in `.gitignore`.

**Sandbox settings that are too weak.** In `sandbox` or `live` mode, the callback secret and address are checked too. With a short secret and plain `http` (and made-up Daraja keys, so it gets as far as the check):

```
MPESA_MODE=sandbox MPESA_CONSUMER_KEY=x MPESA_CONSUMER_SECRET=x MPESA_PASSKEY=x MPESA_CALLBACK_BASE=http://x.example MPESA_CALLBACK_SECRET=short BOXOFFICE_ADMIN_KEY=change-me-to-a-long-random-secret-123 java -jar server/target/boxoffice-server.jar
```

```
Caused by: java.lang.IllegalStateException: The server won't start:
  MPESA_CALLBACK_SECRET is too short: use at least 32 random characters
  MPESA_CALLBACK_BASE must start with https://: Safaricom won't call plain http
```

Nothing is sent to Safaricom: it stops before that.

**A stranger at the LAN feed.** Two gates on one laptop, as on Day 120. Gate A has no venue key yet:

```
$ java -jar cli/target/boxoffice.jar --db a.db setup --gate A
Kilele Nights is ready in a.db as gate A

$ java -jar cli/target/boxoffice.jar --db a.db serve-feed
Set a venue key first, the same on both gates: setup --venue-key ...

$ java -jar cli/target/boxoffice.jar --db a.db setup --venue-key kilele
A venue key is at least 16 letters, digits, - or _

$ java -jar cli/target/boxoffice.jar --db a.db setup --gate A --venue-key ngong-racecourse-2026-gates
Kilele Nights is ready in a.db as gate A

$ java -jar cli/target/boxoffice.jar --db a.db sell VIP Wanjiku
Sold A-VIP-0001 to Wanjiku for KES 5,000

$ java -jar cli/target/boxoffice.jar --db a.db serve-feed
Sharing sales from a.db on port 8100 (Ctrl+C to stop)
On the other gate: pull --from http://<this laptop's IP address>:8100
```

Pick your own venue key; `ngong-racecourse-2026-gates` is an example. In a second terminal, be the stranger:

```
$ curl -i http://127.0.0.1:8100/feed/sales
HTTP/1.1 401 Unauthorized
Content-type: application/json
Content-length: 27

{"error":"Wrong venue key"}
```

Then gate B, with the same key, and a gate C with another:

```
$ java -jar cli/target/boxoffice.jar --db b.db setup --gate B --venue-key ngong-racecourse-2026-gates
Kilele Nights is ready in b.db as gate B

$ java -jar cli/target/boxoffice.jar --db b.db pull --from http://127.0.0.1:8100
Got 1 sale (1 ticket) from gate A

$ java -jar cli/target/boxoffice.jar --db c.db setup --gate C --venue-key someone-elses-venue-key
Kilele Nights is ready in c.db as gate C

$ java -jar cli/target/boxoffice.jar --db c.db pull --from http://127.0.0.1:8100
Couldn't pull from http://127.0.0.1:8100: The other gate has a different venue key. Set the same one on both: setup --venue-key ...
```

(On Windows, write `cli\target\boxoffice.jar`.) Stop the feed with Ctrl+C. `a.db`, `b.db` and `c.db` are in `.gitignore`.

## When you're stuck

- **`Not written yet: Day 123, step 2`**, in almost every server test: the bucket still has its `throw`. Every order and admin call goes through it. Do step 2.
- **`oneMoreThanTheLimitWaitsForTheNextToken` says `Expecting value to be false but was true`**: the bucket never runs dry. When you allow a request, return `new Bucket(tokens - 1, now)`, not `new Bucket(tokens, now)`.
- **`CallerIpTest` says `expected: "41.90.2.2" but was: "10.0.0.5"`**: `CallerIp.of` still returns `remoteAddress` every time. Do step 3.
- **`whatTheCallerMadeUpAtTheStartIsSkipped` says `but was: "196.201.214.200"`**: you took the header's first entry. That's the one the caller wrote. Count from the end of the chain.
- **`java.lang.UnsupportedOperationException` at `chain.add(remoteAddress)`**: `.toList()` gives a list you can't change. Wrap it: `new ArrayList<>(...)`.
- **`aShortChainGivesTheFirstEntry` fails with `Index -4 out of bounds for length 2`**: the chain is shorter than the number of proxies. Use `Math.max(0, caller)`.
- **`CallbackAddressTest.aCallbackFromSafaricomCounts` says `expected: PAID but was: AWAITING_PAYMENT`**: Safaricom's address is in `X-Forwarded-For`, behind one proxy, and the server can't see it yet. Do step 3.
- **`SecurityPassTest` says `expected: 400 but was: 201`** (a bad phone, a silly price, a time that isn't a time), **or `but was: 409`** (a thousand tickets): that check is missing in `SaleChecks`. Do step 4.
- **`aGiantNameFromAGateIs400Not500` or `aStrangeTicketCodeIsRefusedWithoutEchoingItAllBack` says `Request failed unexpectedly: ... DataIntegrityViolationException`**: the database refused it because nothing checked it first. Do step 4.
- **A `SecurityPassTest` says `expected:` one message `but was:` one that's nearly the same**: the tests compare the whole text, full stop and all. Copy each message from the TODO. For a ticket code, use `shorten(ticket.code())`, not the code itself.
- **`Expecting CaseInsensitiveHeaderNameSet: ["Content-Language", "Content-Type"] to contain: ["Content-Security-Policy"]`** (or `X-Content-Type-Options`, or `Cache-Control`): the headers aren't set. Do step 5, and set them **before** `chain.doFilter`.
- **`anOrderPageIsNeverCached` fails but the others pass**: `Cache-Control: no-store` is missing, or you checked `/order/` without the `s`.
- **`aStrangerOnTheWifiGets401` says `expected: 401 but was: 200`**, and `aGateWithADifferentVenueKeyGetsNothing` says `Expecting code to raise a throwable.`: `knowsTheKey` still returns `true`. Do step 6.
- **`cli`, `desktop` and `sync server` are `SKIPPED`**: `core` failed first. Use the `-Dtest=...` command from step 1 for the server's tests, or do step 6.
- **The server says `The server won't start: BOXOFFICE_ADMIN_KEY is too short`**: use a key of 24 characters or more. `openssl rand -base64 32` makes one.
- **`curl` in PowerShell asks for `Uri`, or prints a table**: type `curl.exe`.
- **Your third order gets `200`, not `429`**: you started the server without `--boxoffice.limits.orders-per-minute=2`, so the limit is 10. Or more than 30 seconds passed between orders, and a token dripped back.
- **`Web server failed to start. Port 8080 was already in use.`**: yesterday's server is still running somewhere. Stop it with Ctrl+C.
- **Still stuck?** Open `solution/server/src/main/java/com/boxoffice/server/RateLimiter.java` (or `CallerIp.java`, `SaleChecks.java`, `SecurityHeaders.java`, `solution/core/src/main/java/com/boxoffice/core/lan/GateFeedServer.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can explain a token bucket, and why a burst is fine and a flood isn't
- [ ] Can say why `compute` makes taking a token safe from two threads at once
- [ ] Can say why prompts are limited per phone, and orders per address
- [ ] Can explain how someone fakes `X-Forwarded-For`, and why you count trusted proxies from the end
- [ ] Can say what each security header stops, and why our CSP can be strict
- [ ] Can name the three checks that make an unsigned M-Pesa callback trustworthy
- [ ] Wrote the bucket, `CallerIp.of`, `SaleChecks`, the headers and the venue key check
- [ ] All 382 tests pass
- [ ] Saw a 429 with `Retry-After`, and a 401 from the LAN feed, with `curl -i`
- [ ] Committed your work to Git (not `a.db`, `b.db`, `c.db` or `sync-server-db.mv.db`, and never your admin key, callback secret or venue key)

## Next

The server is ready for the public, but it still runs on your laptop. Tomorrow it moves to a real server: a Docker image, settings for each environment, health checks that say when it's ready, and HTTPS in front, which is why `trusted-proxies` becomes 1.
