# Day 114: Bulk SMS reminders

> 15:00 on the day. Three hundred buyers left a phone number. Wanjiku's text should not have to wait for the 299 before it.

Watch the video: *(not recorded yet)*

## The brief

On the afternoon of Kilele Nights, the organiser wants every buyer who left a phone number to get a text: the time, their ticket codes, and "show the QR code at the gate". A family that shares one phone gets **one** text listing all its tickets.

Sending one text takes about half a second, and nearly all of that is waiting for the network. Send 500 texts one after another and the organiser waits four minutes. Send them all at the same time and it's a few seconds. But not *all* at once: an SMS gateway blocks you if you flood it. So today the gate sends many texts at once, with a limit on how many are in flight.

The texts go through **Africa's Talking**, a gateway used across Kenya. You practise on their **sandbox**: it's free and no real phone gets a text. And for practising with no account at all, there's a **fake** gateway that only prints the texts.

```
$ java -jar cli/target/boxoffice.jar remind --fake
254712345678: Kilele Nights, Sat 14 Nov, 18:00. Your ticket: VIP-0001. Show the QR code at the gate.
254722000111: Kilele Nights, Sat 14 Nov, 18:00. Your tickets: REG-0001, REG-0002. Show the QR code at the gate.
Sent 2 of 2 reminders
```

## What you'll learn

- Why a program that mostly waits for the network is a job for many threads
- What a **virtual thread** is, and how `Executors.newVirtualThreadPerTaskExecutor()` gives one to each text
- How a **`Semaphore`** caps how many texts are in flight at once
- Why `permits.release()` goes in a `finally` block
- How a `Future` hands back each text's result, in the order you sent them
- How to POST a form to a web API with `HttpClient`, and read its JSON answer with **Jackson**
- Why an API key comes from an **environment variable**, never from the code or Git

## New words

- **SMS gateway**: a company that sends texts for you. Your program asks it over the internet; it passes the text on to Safaricom, Airtel and the rest.
- **Africa's Talking**: an SMS gateway based in Nairobi. Its **sandbox** is a practice copy: same API, free, and texts only show up on its on-screen phone simulator.
- **API**: the way one program asks another for something, here by sending a web request.
- **API key**: a long secret string that says "this request is from my account". Anyone who has it can send texts and spend your money.
- **Environment variable**: a named value your terminal hands to every program it starts, like `AT_API_KEY`. It lives in the terminal, not in any file.
- **Thread**: one line of work inside a running program. Several threads can run at the same moment.
- **Platform thread**: Java's usual kind of thread, one per operating-system thread. Each costs about a megabyte, so a few thousand is the limit.
- **Virtual thread**: a very light thread run by Java itself (from Java 21). When it waits for the network, Java parks it and uses the real thread for something else. You can have one per text, even 100,000.
- **Executor** (`ExecutorService`): an object you hand jobs to; it runs them on its threads.
- **`Future`**: a ticket for a job's result. `get()` waits until the job is done, then hands back what it returned.
- **Semaphore**: a counter of **permits**. `acquire()` takes one, waiting if none are left; `release()` gives it back.
- **JSON**: the text format most web APIs answer in, like `{"status":"Success"}`.
- **Jackson**: the Java library that reads and writes JSON.
- **Form body**: `name=value&name=value`, the way a web form sends its fields.

## What's already written

From Days 101–113: Box Office v1, working, with tests. New today, already done:

- The poms add **Jackson** (`jackson-databind`, in `core`).
- A new package, `com.boxoffice.core.sms`:
  - `SmsGateway`: an interface with one method, `send(phone, message)`.
  - `SmsResult`: a record, `sent` or `failed` with a reason.
  - `FakeSms`: pretends to send. It can be slow on purpose, refuse a number, and it counts the most sends that were ever running at the same moment, so a test can check your limit.
  - `AfricasTalkingSms`: the real gateway. `send` builds the form, adds the `apiKey` header and POSTs it. You write `readAnswer`.
  - `ReminderSender.messages(event, tickets)`: one text per phone number. You write `sendAll`.
- The CLI has a new command, `remind`, with `--fake` and `--at-once` (in `RemindCommand.java`). `BoxOfficeCli` now takes the environment variables, so tests can pass their own.

New, for you to finish: `ReminderSender.sendAll` and `AfricasTalkingSms.readAnswer`. New tests: `ReminderSenderTest` (8), `AfricasTalkingSmsTest` (5), and two more in `BoxOfficeCliTest`.

## What you'll use

**Waiting is the slow part.** A text spends almost all its half second waiting: for Wi-Fi, for Nairobi, for the gateway's answer. The laptop is doing nothing. If 100 texts wait at the same time instead of one after another, the whole job takes about as long as one text.

**One virtual thread per text.** A platform thread is heavy, so you'd normally keep a small pool of them. A virtual thread is so cheap that you just make one per job:

```java
try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
    Future<SmsResult> result = threads.submit(() -> gateway.send(phone, message));
    // ... submit more ...
}   // close() waits here until every job has finished
```

The `try (...)` is try-with-resources from Part 1. At the closing brace it calls `threads.close()`, and `close()` waits for every job. So after the `try`, every text has been tried.

**A Semaphore is a bowl of permits.** `new Semaphore(10)` puts 10 permits in the bowl. Each text takes one before it sends and puts it back after. The eleventh text finds the bowl empty and waits until one comes back. That's how "100 texts at once" becomes "never more than 10 at once":

```java
permits.acquire();          // take a permit (wait if there are none)
try {
    return gateway.send(phone, message);
} finally {
    permits.release();      // always give it back
}
```

**Why `finally`.** If a send throws and the permit isn't given back, the bowl has one fewer permit for ever. After ten such sends it's empty, every other text waits for a permit that never comes, and the program hangs. `finally` runs whether the send worked or threw.

**One bad number doesn't stop the rest.** A gateway can throw: a bug, a strange answer. Catch `RuntimeException` inside the job and turn it into `SmsResult.failed(phone, e.getMessage())`. The other 299 texts carry on.

**Results in the same order.** `sendAll` keeps a `List<Future<SmsResult>>`, one per text, in the order it submitted them. Afterwards it calls `get()` on each. That part is already written, below your TODO.

**Talking to Africa's Talking.** `send` is written. It POSTs to `/version1/messaging`:

```
POST https://api.sandbox.africastalking.com/version1/messaging
apiKey: (your key)                      a header, never in the URL
Accept: application/json
Content-Type: application/x-www-form-urlencoded

username=sandbox&to=%2B254712345678&message=Kilele+Nights...
```

The `+` in `+254...` must be sent as `%2B`. In a form, a plain `+` means a space. `URLEncoder.encode` does that.

**Reading the answer with Jackson.** Africa's Talking answers like this, one recipient per number:

```json
{"SMSMessageData":{"Message":"Sent to 1/1","Recipients":[{"number":"+254712345678","status":"Success"}]}}
```

`JSON.readTree(body)` turns the text into a tree of `JsonNode`s. `.path("SMSMessageData").path("Recipients")` walks down it. `.isEmpty()` says if the list is empty, `.get(0)` takes the first recipient and `.path("status").asText()` gives its status as a `String`. `path` never gives `null`: a missing name gives an empty node, so a strange answer can't crash you with a `NullPointerException`.

**Testing without the internet.** `AfricasTalkingSmsTest` starts a tiny pretend Africa's Talking on your own laptop (`HttpServer` on port 0, "any free port") and checks exactly what your code sent, and how it reads each answer. No account, no internet.

**The key comes from the environment.** Anyone who has your API key can send texts on your account. If it's written in the code, it ends up in Git, then on GitHub, and bots search GitHub for keys all day. So `RemindCommand` reads it from the environment variable `AT_API_KEY`, which lives only in your terminal. `AT_USERNAME` is your Africa's Talking username, and it's `sandbox` if you leave it out. The root `.gitignore` already ignores `.env` files, in case you keep keys in one later.

## Steps

1. **Open the starter and run the tests.** `cd day-114-bulk-sms-reminders/starter`, then `../../mvnw test`. You should see `Tests run: 116, Failures: 1, Errors: 4` in `core`, and `BUILD FAILURE`. `cli` and `desktop` are `SKIPPED`. It takes about 20 seconds longer than usual, because the starter sends the 100 slow texts one by one. The failures:
   - three in `AfricasTalkingSmsTest`, saying `Not written yet: Day 114, step 4`;
   - `ReminderSenderTest.sendsAtTheSameTimeInsteadOfOneByOne`: `Expecting actual: 20.13...S to be less than: 5S`;
   - `ReminderSenderTest.aGatewayThatThrowsCountsAsAFailedSend`: `IllegalState Gateway exploded`.

2. **Read the fake and the messages.** Open `starter/core/src/main/java/com/boxoffice/core/sms/FakeSms.java`. Find where `send` counts `running` and keeps `mostAtOnce`: that's how a test sees your limit. Then read `ReminderSender.messages`, and see how a family that shares a phone gets one text. Nothing to write.

3. **Send many at once, but not too many.** Open `ReminderSender.java` and find `sendAll`. Follow the TODO:
   - open `try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) { ... }`;
   - inside, for each phone and message, `pending.add(threads.submit(() -> { ... }))`;
   - inside that lambda: `permits.acquire();` then `try { return gateway.send(phone, message); }`, `catch (RuntimeException e) { return SmsResult.failed(phone, e.getMessage()); }`, `finally { permits.release(); }`;
   - then delete the old `messages.forEach(...)` loop below the TODO.

   `messages.forEach((phone, message) -> ...)` walks a map, giving you each key and value. Leave the part after your `try` (the loop over `pending`) as it is.

   Run `../../mvnw test -Dtest=ReminderSenderTest -Dsurefire.failIfNoSpecifiedTests=false`. All 8 pass, and the 100 slow texts take well under a second.

4. **Read Africa's Talking's answer.** Open `AfricasTalkingSms.java` and find `readAnswer`. Replace the `throw`:
   - `JsonNode recipients = JSON.readTree(body).path("SMSMessageData").path("Recipients");`
   - if `recipients.isEmpty()`, return `SmsResult.failed(phone, "No recipients in the answer")`;
   - take `recipients.get(0).path("status").asText()`. `"Success"` gives `SmsResult.sent(phone)`; anything else gives `SmsResult.failed(phone, status)`. Compare with `equals`, never `==`.

   Run `AfricasTalkingSmsTest` the same way: all 5 pass.

5. **Run all the tests.** `../../mvnw test`. You should see 169 tests in all and `BUILD SUCCESS`.

6. **Send the reminders.** Follow "Run it and test it" below: first with `--fake`, then, if you like, through the Africa's Talking sandbox.

7. **Stretch (not required): watch the limit work.** Run `remind --fake --at-once 1` and then `--at-once 50` on a `gate.db` with a few phone numbers. With the fake, both are fast. To feel the difference, change `new FakeSms(Duration.ZERO)` in your own copy of `RemindCommand` to `Duration.ofMillis(500)`, sell ten tickets with different phones, and time both. Put it back afterwards.

## Run it and test it

From the day's `starter` folder:

```
cd day-114-bulk-sms-reminders/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=ReminderSenderTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=ReminderSenderTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **116 tests** in `core` (including ReminderSender 8, AfricasTalkingSms 5), **12** in `cli` and **41** in `desktop`, **169** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] BUILD SUCCESS
```

**Get some phone numbers in.** The CLI's `sell` has no phone box, so sell in the window, where Tab moves to the phone box (Day 108). Build first:

```
../../mvnw package -DskipTests
```

```
java -jar desktop/target/boxoffice-desktop.jar
```

(On Windows, write `desktop\target\boxoffice-desktop.jar` and, below, `cli\target\boxoffice.jar`.) Sell a VIP to Wanjiku with `0712 345 678`, and two Regulars to Kamau, both with `0722 000 111`. Close the window.

**Send them with the fake.** Nothing leaves your laptop:

```
java -jar cli/target/boxoffice.jar remind --fake
```

```
254712345678: Kilele Nights, Sat 14 Nov, 18:00. Your ticket: VIP-0001. Show the QR code at the gate.
254722000111: Kilele Nights, Sat 14 Nov, 18:00. Your tickets: REG-0001, REG-0002. Show the QR code at the gate.
Sent 2 of 2 reminders
```

Your codes may differ if your `gate.db` already had sales. `--at-once 5` sets the limit (it's 10 if you leave it out). `remind --help` lists the options.

**Send them through the Africa's Talking sandbox (optional).** Make a free account at africastalking.com, go to the sandbox, and make an API key in its settings. Copy it once; you won't see it again. Then set the key in the **same terminal** you run the command from. Never paste it into a file in the repository.

macOS / Linux (bash or zsh), all on one line:

```
AT_API_KEY=your-sandbox-api-key java -jar cli/target/boxoffice.jar remind
```

Windows PowerShell:

```
$env:AT_API_KEY="your-sandbox-api-key"
```

```
java -jar cli\target\boxoffice.jar remind
```

Windows cmd (no quotes, no spaces around `=`):

```
set AT_API_KEY=your-sandbox-api-key
```

```
java -jar cli\target\boxoffice.jar remind
```

`AT_USERNAME` works the same way. Leave it out on the sandbox: it's `sandbox`. The bash form sets the key for that one command only. In PowerShell and cmd it lasts until you close the terminal.

The sandbox doesn't send to real phones. To see a text arrive, open the sandbox's phone simulator on the Africa's Talking site, type one of your buyers' numbers into it, and run `remind` again.

## When you're stuck

- **`Not written yet: Day 114, step 4`**: `readAnswer` still has its `throw`.
- **`sendsAtTheSameTimeInsteadOfOneByOne` says `Expecting actual: 20.13...S to be less than: 5S`**: the old one-by-one loop is still there. Do step 3, and delete the loop.
- **`aGatewayThatThrowsCountsAsAFailedSend` says `IllegalState Gateway exploded`**: that's the starter's loop. After step 3, if it says **`IllegalState A send failed unexpectedly`** (`Caused by: ... Gateway exploded`), the `catch (RuntimeException e)` inside the lambda is missing.
- **`neverRunsMoreThanTheLimitAtOnce` says `Expecting actual: 40 to be between: [1, 5]`**: all 40 texts ran at once. `permits.acquire()` is missing.
- **The tests stop at `Running com.boxoffice.core.sms.ReminderSenderTest` and never finish**: a permit is never given back, so the texts wait for ever. Put `permits.release()` in a `finally`. Press Ctrl+C to stop Maven.
- **`expected: SmsResult[phone=254712345678, sent=true, detail=Sent] but was: SmsResult[phone=254712345678, sent=false, detail=Success]`**: you compared the status with `==`. Use `status.equals("Success")`.
- **`remind` says `Set AT_API_KEY to your Africa's Talking sandbox key, or use --fake`**: the key isn't set in this terminal. On Windows, set it in the same window you run `java` from. In cmd, check there are no spaces around `=`.
- **`Sent 0 of 0 reminders`**: no ticket in `gate.db` has a phone number. Sell some in the window, with a phone.
- **Each number says `HTTP 401`**: Africa's Talking didn't accept the key. Check you copied the whole sandbox key, and that `AT_USERNAME` is `sandbox` (or not set).
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/sms/ReminderSender.java` (or `AfricasTalkingSms.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say why waiting for the network is a job for many threads
- [ ] Can say what a virtual thread is, and why one per text is fine
- [ ] Can explain how a Semaphore keeps 10 texts in flight, and why `release()` is in `finally`
- [ ] Wrote `sendAll` with virtual threads and a Semaphore
- [ ] Wrote `readAnswer` with Jackson
- [ ] All 169 tests pass
- [ ] Ran `remind --fake` and saw one text per phone
- [ ] Can say why the API key is in an environment variable, not in the code
- [ ] Committed your work to Git (not `gate.db`, and never an API key)

## Next

Today the gate reached the internet for the first time, and assumed the internet would answer. Venue Wi-Fi doesn't always. Tomorrow you build an HTTP client that survives it: a time limit on every request, retries that wait a little longer each time, and the rule that decides which requests are safe to send twice.
