# Day 125: Ask about tonight's sales

> 21:40. The queue at gate A reaches the car park. Otieno has two seconds between customers, and wants to know if VIP is nearly gone.

Watch the video: *(not recorded yet)*

## The brief

Phase 5 starts today: AI in the app. The gate staff know how to sell, but numbers are slow to dig out in the middle of a rush. `report` (Day 105) gives the whole table; nobody has time to read a table while Kamau family's six children are pulling at the barrier. So today the staff can ask a question in their own words, and **Claude**, Anthropic's AI model, answers from tonight's figures:

```
$ java -jar cli/target/boxoffice.jar ask "How many VIP are left?"
49 of the 50 VIP tickets are left. One has been sold, for KES 5,000.
```

(That answer is an **example** of what Claude might say, not output captured from a real run. Every real answer is worded a little differently.)

The answer **streams**: it appears word by word as Claude writes it, so Otieno starts reading at once instead of staring at a blank screen.

Asking the real Claude needs an **API key** from Anthropic, and every question costs a little money. So there's also a practice mode, `--fake`, that sends nothing and shows exactly what Claude would be given:

```
$ java -jar cli/target/boxoffice.jar ask --fake "How many VIP are left?"
Practice mode: nothing was sent. Claude would get your question and these facts:
Event: Kilele Nights, Sat 14 Nov, 18:00 to Sun 15 Nov, 04:00 (Nairobi time)
Now: Sat 14 Nov, 19:00
Regular (REG): KES 1,500 each. 3 paid, 0 guests, 497 left of 500. Takings KES 4,500
VIP (VIP): KES 5,000 each. 1 paid, 0 guests, 49 left of 50. Takings KES 5,000
Total: 4 paid, 0 guests, 546 left of 550. Takings KES 9,500
Through the gates: 0 of the 4 tickets issued
```

You write the facts, the check on the question, the request to Claude, the streaming, and what the command does with each kind of answer.

## What you'll learn

- What a **large language model** is, and how a program asks one a question through an **API**
- How the **Anthropic Java SDK** builds a request, and what `model`, `max_tokens`, `system` and the user message are
- Why only counts and money go into the prompt, never a buyer's name or phone number
- How to write a **system prompt**: who the staff are, the facts in `<facts>` tags, "answer from the facts", and plain text for a terminal
- What **streaming** is, what **Server-Sent Events** look like, and how `MessageAccumulator` builds the whole message while you print the pieces
- Why you read the **stop reason** before you trust the text: a refusal, or an answer cut short
- What **effort** and **thinking** are, and why a question about one table gets effort LOW
- What a **beta header** is, and how `fallbacks: "default"` asks another model when the first one declines
- How to test real SDK code with no network and no key: a pretend API on your own laptop
- Why `ANTHROPIC_API_KEY`, like `AT_API_KEY` on Day 114, lives in an environment variable and never in the code or Git

## New words

- **Claude**: Anthropic's AI model. You send it text, it sends text back.
- **Large language model** (LLM): a program trained on a huge amount of writing, so it can read a question and write an answer. It writes what is likely, not what is checked, so it needs the real facts in front of it.
- **Anthropic API**: the web address your program sends questions to (`https://api.anthropic.com/v1/messages`). It answers with Claude's reply.
- **SDK** ("software development kit"): a library that does the HTTP, the JSON and the retries for you. Today's is `com.anthropic:anthropic-java`.
- **Model id**: the exact name of the model you ask, here `claude-opus-5-5`.
- **Prompt**: everything you send: the instructions, the facts and the question.
- **System prompt**: the part of the prompt that sets the scene ("you help the gate staff..."). The staff never see it.
- **Token**: the unit a model reads and writes in, about three quarters of a word. You pay per token, both ways.
- **`max_tokens`**: the most tokens the answer may use. When it runs out, the answer stops mid-sentence.
- **Streaming**: getting the answer in pieces as it's written, instead of all at once at the end.
- **Server-Sent Events** (SSE): one HTTP answer that stays open, with short `event:` and `data:` lines sent one after another as things happen.
- **Stop reason**: why Claude stopped: `end_turn` (finished), `max_tokens` (out of room), `refusal` (declined) and a few more.
- **Refusal**: Claude declining to answer, usually because the request looks harmful.
- **Thinking**: Claude working the problem through before it answers. With this model it's always on; the thinking itself isn't shown to you.
- **Effort**: how much thinking Claude puts in: LOW, MEDIUM or HIGH. Less effort is faster and cheaper.
- **Beta header**: an `anthropic-beta` HTTP header that switches on an API feature that's still new.
- **Fallback model**: a second model the API asks, in the same call, if the first one declines.
- **Snapshot**: the night's figures at one moment, read fresh from `gate.db`.

## What's already written

From Days 101–124: the gate app, the sync server and the public ticket site, deployed, with tests. New today, already done:

- The poms add the **Anthropic Java SDK**, `com.anthropic:anthropic-java:2.34.0`: its version in the parent pom, the dependency in `cli/pom.xml` only. `core` and `desktop` don't get it: `core` stays free of web libraries, and it never needs to know which AI it's talking to.
- A new package in core, `com.boxoffice.core.ai`:
  - `Claude`: an interface with one method, `ask(system, question, onText)`. `onText` gets each piece of the answer as it arrives.
  - `Reply`: a sealed interface with three records: `Answered(text)`, `CutShort(text)` (it ran out of `max_tokens`) and `Refused(why)`.
  - `FakeClaude`: pretends to be Claude. It hands back the replies a test gives it, streams each one a word at a time, and remembers every question it was asked, so a test can check what would have been sent.
  - `SalesSnapshot`: a record of the night in numbers, with `of(...)` and `counts(...)`. You write `facts()`.
  - `SalesQuestions`: `snapshot()` and `systemPrompt(...)` are written. You write `ask` and `problemWith`.
- `TicketStore.countCheckedIn(eventId)`: how many of the event's tickets have come through a gate.
- In the cli module:
  - `AnthropicClaude`: the real Claude, through the SDK. The model, `MAX_TOKENS`, the beta header's name, `connect(...)` and the first half of `replyFrom` are written. You write `params`, `ask` and the end of `replyFrom`.
  - `AskCommand`, the new `ask` command, with `--fake`. Everything but `finish` is written: the key check, the practice mode, and the error messages.
- Tests: `SalesSnapshotTest` (4), `SalesQuestionsTest` (7), `AnthropicClaudeTest` (6) and `AskCommandTest` (7). `FakeAnthropicApi` is the pretend API the cli tests talk to.

New, for you to finish: `SalesSnapshot.facts` (step 2), `SalesQuestions.ask` and `problemWith` (step 3), `AnthropicClaude.params` (step 4), `AnthropicClaude.ask` and `replyFrom` (step 5), and `AskCommand.finish` (step 6). The TODOs are labelled with their step numbers.

## What you'll use

**What Claude is good at, and what it isn't.** A language model is very good at reading a question in everyday words ("are we past half on Regular?") and answering in everyday words. It is not a database. Ask it "how many VIP are left?" with nothing else, and it will make up a likely-sounding number. So the program does what it's good at (counting from `gate.db`), Claude does what it's good at (reading the question and wording the answer), and the prompt carries the counts from one to the other.

**The snapshot: counts and money only.** `SalesSnapshot.facts()` writes the night in a few short lines:

```
Event: Kilele Nights, Sat 14 Nov, 18:00 to Sun 15 Nov, 04:00 (Nairobi time)
Now: Sat 14 Nov, 21:00
Regular (REG): KES 1,500 each. 2 paid, 1 guest, 497 left of 500. Takings KES 3,000
VIP (VIP): KES 5,000 each. 1 paid, 0 guests, 49 left of 50. Takings KES 5,000
Total: 3 paid, 1 guest, 546 left of 550. Takings KES 8,000
Through the gates: 1 of the 4 tickets issued
```

There are no names and no phone numbers in it, on purpose. Whatever goes into a prompt **leaves the laptop**: it travels over the internet to Anthropic's servers. Wanjiku Kamau gave her phone number to get a text about her ticket, not to have it sent anywhere else. Counts answer every question the staff ask today, so counts are all that go. `holdsNoNamesAndNoPhoneNumbers` checks it.

The line `Now: ...` matters too. Claude has no clock. Without it, "how long until the doors close?" has no answer.

**Fresh for every question.** `SalesQuestions.ask` calls `snapshot()` each time, which reads `gate.db` again. Ask at 21:00, sell a ticket, ask again, and the second question sees the new sale. `looksAtTheDatabaseAgainForEachQuestion` checks it.

**Checking the question first.** The question is typed by a person at a busy gate, so `problemWith` checks it before anything is sent: blank gives `Type a question first`; longer than 500 characters (after `strip()`) gives `Keep the question under 500 characters`. A blank question would cost money and get a confused answer; 500 characters is longer than any real question and stops someone pasting a whole page in by mistake. A refused question never reaches Claude: `aBlankQuestionIsNeverSent` checks `claude.asked()` is empty.

**The system prompt.** Open `SalesQuestions.systemPrompt` and read it. Each part is there for a reason:

- *"You help the gate staff at Kilele Nights ... between customers, so they need short answers they can read at a glance."* Claude writes better when it knows who is reading and in what situation. "Short" is said once, with the reason, instead of shouted.
- *The facts inside `<facts>` and `</facts>`.* Tags mark clearly where the data starts and ends, so Claude doesn't mix the figures up with the instructions.
- *"Answer from these facts only. If they don't hold the answer ... say you can't see that from here rather than guessing, because the staff will act on what you say."* This is the most important line. A wrong answer said confidently is worse than "I can't see that", because Otieno will tell a customer.
- *"give money in Kenyan shillings the way the facts write it (KES 1,500)"*: the same style as the rest of the app.
- *"Reply in plain sentences without Markdown: the answer is shown in a terminal, where asterisks and pound signs print as they are."* Models often write `**49**` to make things bold. A terminal shows the asterisks. Again, the reason is given, so Claude can apply it sensibly.

**The request.** `params` builds what's sent:

```java
MessageCreateParams.builder()
        .model(MODEL)                          // "claude-opus-5-5"
        .maxTokens(MAX_TOKENS)                 // 8000: room for thinking and the answer
        .system(system)
        .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
        .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        .addUserMessage(question)
        .build();
```

**Effort LOW.** Claude Opus 5.5 always thinks before it answers; you don't switch thinking on or off, and the request has no `thinking` field (`asksForLittleThinkingAndAFallbackModel` checks that). **Effort** says how much thinking to do. "How many VIP are left?" is a lookup in a six-line table: a lot of thinking would make Otieno wait longer and cost more for the same answer. So effort is LOW. The 8,000 `max_tokens` covers the thinking and the answer together, which is why it's far bigger than a two-sentence answer needs.

**The fallback.** Now and then a model declines a perfectly ordinary request. The `server-side-fallback-2026-07-01` beta header, with `"fallbacks": "default"` in the body, tells the API: if this model declines, run the same request again on the model Anthropic recommends as a fallback, in the same call. You don't write a retry; you just get the other model's answer. `putAdditionalBodyProperty` is how the SDK sends a field it doesn't have its own method for yet.

**Streaming with Server-Sent Events.** With `createStreaming`, the API doesn't wait until the whole answer is written. It keeps the HTTP answer open and sends **events**, one after another, each an `event:` line, a `data:` line of JSON, and a blank line:

```
event: content_block_delta
data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"49 VIP"}}

event: content_block_delta
data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":" tickets"}}

event: message_delta
data: {"type":"message_delta","delta":{"stop_reason":"end_turn", ...}}
```

That's **Server-Sent Events**. Before the text there's a `message_start`, and a thinking block that sends only a signature (you never see the thinking itself); at the very end comes `message_stop`. The SDK reads the events for you and hands them over as a Java `Stream`.

**Printing the pieces, keeping the whole.** You want two things from the stream: each piece of text on the screen as it comes, and, at the end, the whole message, so you can see how it stopped. `MessageAccumulator` does the second:

```java
MessageAccumulator accumulator = MessageAccumulator.create();
try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params(system, question))) {
    stream.stream()
            .map(accumulator::accumulate)                        // remember it, hand it on
            .flatMap(event -> event.contentBlockDelta().stream()) // only the "delta" events
            .flatMap(delta -> delta.delta().text().stream())      // only text pieces, not the signature
            .forEach(text -> onText.accept(text.text()));
}
return replyFrom(accumulator.message());
```

`accumulate(event)` adds the event to the message it's building and gives the same event back, so it fits in a `map`. `contentBlockDelta()` and `text()` return an `Optional`; `.stream()` on an `Optional` gives a stream of one thing or of nothing, so `flatMap` quietly drops every event that isn't a piece of text. The `try (...)` closes the connection even if something goes wrong halfway.

**Read the stop reason before you trust the text.** The text alone doesn't say whether it's the whole answer. `replyFrom` looks at `message.stopReason()` first:

- `REFUSAL`: Claude declined. Any text so far isn't an answer. Give back `Reply.Refused` with the API's explanation from `message.stopDetails()`, or `Claude declined to answer` if there isn't one.
- `MAX_TOKENS`: it ran out of room. The text stops mid-sentence: `Regular has 497 left and`. Give back `Reply.CutShort(text)`, so the screen can say so. Shown as if complete, "497 left and" reads like a finished answer that's missing a part nobody notices.
- Anything else (`END_TURN` nearly always): `Reply.Answered(text)`.

**A switch the compiler checks.** `Reply` is sealed, like `SaleOutcome` on Day 102. So in `finish`, a `switch (reply)` with one `case` for each of the three records needs no `default`: the compiler knows those are the only three, and if one day a fourth is added, every switch that forgets it stops compiling.

**Testing the real SDK code with no network.** `AnthropicClaudeTest` doesn't use `FakeClaude`. It uses the real `AnthropicClaude`, real SDK and all, pointed at `FakeAnthropicApi`: a tiny `HttpServer` on `127.0.0.1`, port 0 ("any free port"), like the pretend Africa's Talking on Day 114. It answers with Server-Sent Events built by `FakeAnthropicApi.textAnswer(...)` or `refusal(...)`, or with a `401` from `fails(...)`, and keeps every request, so a test can check the model, the `x-api-key` header, `"effort":"low"`, `"fallbacks":"default"` and `"stream":true`. `AnthropicClaude.connect(apiKey, baseUrl)` takes the address; `AskCommand` reads it from `ANTHROPIC_BASE_URL`, which the tests set and you leave unset. No account, no key, no internet, no cost.

**The errors the command knows.** Read `AskCommand.call`. Before anything is sent: the question check, and `Set ANTHROPIC_API_KEY to your Anthropic API key, or use --fake to see what would be sent`. While asking, the SDK throws its own exceptions, and the command turns them into one plain line:

- `AnthropicServiceException` with status 401: `The Anthropic API refused the key (HTTP 401). Check ANTHROPIC_API_KEY`.
- Any other status (a 429 "too many requests", a 529 "overloaded"): `The Anthropic API answered HTTP 529. Try again in a minute`.
- `AnthropicIoException`, no network at all: `Can't reach the Anthropic API. Check the internet connection`.

The SDK already retries a couple of times on its own before it gives up, so by the time you see one of these, trying again at once won't help.

**The key and the money.** Anyone who has your Anthropic key can ask questions on your account, and you pay for them. Same rule as `AT_API_KEY` on Day 114: it comes from the environment variable `ANTHROPIC_API_KEY`, which lives only in your terminal, never in the code, a file in the repository, or Git. The root `.gitignore` already ignores `.env` files. Each question costs a small amount, charged per token, for the prompt and the answer. The prompt is the same few hundred tokens every time, so a night of questions costs little; still, look at the prices and set a spending limit in the Anthropic Console before you start.

**The jar got bigger.** `cli/target/boxoffice.jar` was about 22 MB yesterday. Today it's about 50 MB. The SDK is written partly in Kotlin, and brings the Kotlin library, OkHttp (its HTTP client) and several Jackson modules with it. That's fine for a CLI; Day 128 trims the desktop installer down.

## Steps

1. **Open the starter and run the tests.** `cd day-125-ask-about-tonights-sales/starter`, then `../../mvnw test`. You should see `Tests run: 196, Failures: 0, Errors: 10` in `core` and `BUILD FAILURE`; `cli`, `desktop` and `sync server` are `SKIPPED`:
   - three `SalesSnapshotTest` tests and `SalesQuestionsTest.theInstructionsSayToStickToTheFacts` end with `UnsupportedOperation Not written yet: Day 125, step 2`;
   - the other six `SalesQuestionsTest` tests end with `UnsupportedOperation Not written yet: Day 125, step 3`.

   `SalesSnapshotTest.theStoreCountsCheckIns` already passes: `countCheckedIn` is written.

2. **Write the facts.** Open `starter/core/src/main/java/com/boxoffice/core/ai/SalesSnapshot.java`. The comment above `facts()` shows the exact text. Build it with a `StringBuilder`, one fact per line, each ending in `\n`:
   - `Event: ` + the event's name + `, ` + `NairobiTime.format(event.startsAt().toInstant())` + ` to ` + the same for `endsAt()` + ` (Nairobi time)`;
   - `Now: ` + `NairobiTime.format(now)`;
   - one line per ticket type. `report.lines().get(i)` holds the numbers for `event.ticketTypes().get(i)`: same order, so loop with an index. Each line is the type's name, its code in brackets, `type.price().format()` + ` each. `, then `counts(line.paid(), line.guests(), line.left(), type.capacity())`, then `. Takings ` + `line.takings().format()`. Add up `paid`, `guests`, `left` and the capacity as you go;
   - `Total: ` + `counts(...)` with your totals + `. Takings ` + `report.totalTakings().format()`;
   - `Through the gates: ` + `checkedIn` + ` of the ` + `ticketsIssued` + ` tickets issued`.

   Only counts and money: no `buyer()`, no `phone()`. Run `../../mvnw test -Dtest=SalesSnapshotTest -Dsurefire.failIfNoSpecifiedTests=false`. All 4 pass. (On Windows: `..\..\mvnw.cmd test "-Dtest=SalesSnapshotTest" "-Dsurefire.failIfNoSpecifiedTests=false"`.)

3. **Check the question, then ask.** Open `SalesQuestions.java`.
   - In `problemWith`: `null` or `isBlank()` gives `"Type a question first"`; `question.strip().length() > LONGEST_QUESTION` gives `"Keep the question under " + LONGEST_QUESTION + " characters"`; anything else gives `null`.
   - In `ask`: if `problemWith(question)` isn't `null`, return `new Reply.Refused(problem)` without calling Claude. Otherwise return `claude.ask(systemPrompt(snapshot()), question.strip(), onText)`.

   Run `../../mvnw test`. `core` passes now: 196 tests. `cli` fails: 10 of the 13 new cli tests. `AnthropicClaudeTest` says `UnsupportedOperation Not written yet: Day 125, step 5`, and `AskCommandTest.printsTheAnswer` says `expected: 0 but was: 1`. The three that pass (`needsAKey`, `fakeShowsTheFactsAndSendsNothing`, `aLongQuestionIsRefusedBeforeSending`) never reach `AnthropicClaude`. `desktop` and `sync server` are `SKIPPED`.

4. **Build the request.** Open `starter/cli/src/main/java/com/boxoffice/cli/AnthropicClaude.java` and find `params`. Replace the `throw` with the builder from "What you'll use": `.model(MODEL)`, `.maxTokens(MAX_TOKENS)`, `.system(system)`, `.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())`, `.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)`, `.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))`, `.addUserMessage(question)`, then `.build()`.

   Nothing new passes yet: `ask` still throws before it calls `params`.

5. **Stream the answer, and read how it stopped.** Still in `AnthropicClaude.java`:
   - In `ask`, replace the `throw` with the `MessageAccumulator` code from "What you'll use": `createStreaming(params(system, question))` in a `try (...)`, then `map(accumulator::accumulate)`, the two `flatMap`s, `forEach` to `onText`, and after the `try`, `return replyFrom(accumulator.message())`.
   - In `replyFrom`, below the `StopReason stop = ...` line: if `stop.equals(StopReason.REFUSAL)`, return `new Reply.Refused(message.stopDetails().flatMap(RefusalStopDetails::explanation).orElse("Claude declined to answer"))`. If `stop.equals(StopReason.MAX_TOKENS)`, return `new Reply.CutShort(text)`. Otherwise return `new Reply.Answered(text)`.

   Run `../../mvnw test -Dtest="AnthropicClaudeTest,AskCommandTest" -Dsurefire.failIfNoSpecifiedTests=false`. All 6 `AnthropicClaudeTest` tests pass. Two `AskCommandTest` tests still fail: `printsTheAnswer` (`expected: 0 but was: 1`) and `aRefusalSaysWhy`, whose message starts `but was: "java.lang.UnsupportedOperationException: Not written yet: Day 125, step 6`.

6. **Say how it ended.** Open `AskCommand.java` and find `finish`. Replace the `throw` with a `switch (reply)`:
   - `case Reply.Answered answered ->`: `out.println()` (the pieces were printed with `print`, so the line isn't ended yet), `return 0`;
   - `case Reply.CutShort cut ->`: `out.println()`, then `err.println("(The answer was cut short. Ask a narrower question.)")`, `return 1`;
   - `case Reply.Refused refused ->`: `err.println("No answer: " + refused.why())`, `return 1`.

   The problems go to `err`, so they stay out of the answer itself.

7. **Run all the tests.** `../../mvnw test`. You should see 422 tests in all and `BUILD SUCCESS`.

8. **Ask about the night.** Follow "Run it and test it" below: first with `--fake`, then, if you have a key, the real Claude.

9. **Stretch (not required): test the edge.** In `SalesQuestionsTest`, write a test that a question of exactly 500 characters is sent, and one of 501 is refused. `"a".repeat(500)` makes one. Edges like this are where off-by-one mistakes hide. Your test isn't part of the course's tests, so keep it in your own Git.

## Run it and test it

From the day's `starter` folder:

```
cd day-125-ask-about-tonights-sales/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=AnthropicClaudeTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=AnthropicClaudeTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **196 tests** in `core` (including SalesSnapshot 4, SalesQuestions 7), **43** in `cli` (including AnthropicClaude 6, AskCommand 7), **41** in `desktop` and **142** in `sync server`, **422** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] Box Office sync server ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**Build the jars.** `../../mvnw package -DskipTests`. Look at `cli/target/boxoffice.jar`: about 50 MB now.

(On Windows, write `cli\target\boxoffice.jar` below.)

**Sell a few tickets.** On a fresh `gate.db`:

```
$ java -jar cli/target/boxoffice.jar sell VIP "Wanjiku Kamau"
Sold VIP-0001 to Wanjiku Kamau for KES 5,000

$ java -jar cli/target/boxoffice.jar sell -q 2 REG "Kamau family"
Sold 2 tickets to Kamau family for KES 3,000: REG-0001 to REG-0002

$ java -jar cli/target/boxoffice.jar sell REG Otieno
Sold REG-0003 to Otieno for KES 1,500
```

**Practice with `--fake`.** Nothing leaves your laptop, and no key is needed. Put the question in quotes: in zsh (the macOS terminal) a bare `?` means "any one letter" and the command never runs.

```
$ java -jar cli/target/boxoffice.jar ask --fake "How many VIP are left?"
Practice mode: nothing was sent. Claude would get your question and these facts:
Event: Kilele Nights, Sat 14 Nov, 18:00 to Sun 15 Nov, 04:00 (Nairobi time)
Now: Sat 14 Nov, 19:00
Regular (REG): KES 1,500 each. 3 paid, 0 guests, 497 left of 500. Takings KES 4,500
VIP (VIP): KES 5,000 each. 1 paid, 0 guests, 49 left of 50. Takings KES 5,000
Total: 4 paid, 0 guests, 546 left of 550. Takings KES 9,500
Through the gates: 0 of the 4 tickets issued
```

Wanjiku Kamau is in `gate.db`, and not in the facts. `Now:` says 19:00 because the CLI runs on the practice clock (Day 105): it's always the night of the event. Your numbers differ if your `gate.db` already had sales.

The question is checked even in practice mode:

```
$ java -jar cli/target/boxoffice.jar ask --fake "   "
Type a question first
```

And without `--fake`, with no key set:

```
$ java -jar cli/target/boxoffice.jar ask "How many VIP are left?"
Set ANTHROPIC_API_KEY to your Anthropic API key, or use --fake to see what would be sent
```

**Ask the real Claude (optional, costs a little).** Make an account at console.anthropic.com, add some credit, and make an API key under API keys. Copy it once; you won't see it again. Set a spending limit while you're there. Then set the key in the **same terminal** you run the command from. Never paste it into a file in the repository.

macOS / Linux (bash or zsh), all on one line:

```
ANTHROPIC_API_KEY=your-anthropic-api-key java -jar cli/target/boxoffice.jar ask "How many VIP are left?"
```

Windows PowerShell:

```
$env:ANTHROPIC_API_KEY="your-anthropic-api-key"
```

```
java -jar cli\target\boxoffice.jar ask "How many VIP are left?"
```

Windows cmd (no quotes around the key, no spaces around `=`):

```
set ANTHROPIC_API_KEY=your-anthropic-api-key
```

```
java -jar cli\target\boxoffice.jar ask "How many VIP are left?"
```

The bash form sets the key for that one command only. In PowerShell and cmd it lasts until you close the terminal.

Here is an **example** of what Claude might say. It is not captured output; your answer will be worded differently, and it appears a few words at a time:

```
$ java -jar cli/target/boxoffice.jar ask "How many VIP are left?"
49 of the 50 VIP tickets are left. One has been sold, for KES 5,000.

$ java -jar cli/target/boxoffice.jar ask "Who bought the VIP ticket?"
I can't see buyers' names from here, only counts and takings. One VIP ticket has been sold.
```

The second one is the system prompt working: the name isn't in the facts, so Claude says so instead of guessing.

**When it goes wrong.** These are the command's own messages:

```
The Anthropic API refused the key (HTTP 401). Check ANTHROPIC_API_KEY
Can't reach the Anthropic API. Check the internet connection
```

The first is a wrong or deleted key. The second comes after the SDK has tried a few times, so it can take a little while to appear.

`gate.db` is in `.gitignore`. Delete it to start the night again.

## When you're stuck

- **`Not written yet: Day 125, step 2`**: `facts()` still has its `throw`. Do step 2.
- **`aQuietNightIsAllZeros` or `countsSalesGuestsAndWhoCameIn` says `expected: ... but was: ...`**: your text differs from the comment above `facts()`, often by one character. Look for a missing `\n` at the end of a line, `. Takings` without the full stop, or `1 guests` (`counts(...)` gets that right for you: use it).
- **`holdsNoNamesAndNoPhoneNumbers` fails**: a buyer's name or phone got into the facts. Only counts and money.
- **`Not written yet: Day 125, step 3`**: `ask` in `SalesQuestions` still has its `throw`.
- **`aBlankQuestionIsNeverSent` or `aVeryLongQuestionIsNeverSent` says `Expecting empty but was: [Asked[system=You help the gate staff at Kilele Nights...`**: Claude was asked anyway. Return the `Reply.Refused` before calling `claude.ask`.
- **`aVeryLongQuestionIsNeverSent` says `expected: Refused[why=Keep the question under 500 characters]`**: build the message from `LONGEST_QUESTION`, with the exact words.
- **`AnthropicClaudeTest` says `Not written yet: Day 125, step 5`**: `ask` in `AnthropicClaude` still has its `throw`. Step 4 alone changes nothing you can see.
- **`AnthropicClaudeTest` says `Not written yet: Day 125, step 4`**: you did step 5 and not step 4. `params` still has its `throw`.
- **`IllegalState 'message_stop' event not yet received.`**: the `.map(accumulator::accumulate)` line is missing, so the accumulator never saw a single event. Put it first, before the `flatMap`s.
- **`asksForLittleThinkingAndAFallbackModel` says `expected: "low" but was: ""`**: the `.outputConfig(...)` line is missing from `params`. If it says the same about `"default"`, the `fallbacks` line is missing.
- **`AnthropicClaudeTest.aRefusalSaysWhy` says `expected: Refused[why=This looks like a request for exploit code.] but was: Answered[text=]`**, or **`anAnswerThatRanOutOfRoomIsCutShort` says `expected: CutShort[text=Regular has 497 left and] but was: Answered[text=Regular has 497 left and]`**: `replyFrom` doesn't look at the stop reason. Do the second half of step 5.
- **`AskCommandTest.printsTheAnswer` says `expected: 0 but was: 1`**, and `aRefusalSaysWhy` shows a stack trace with `Not written yet: Day 125, step 6`: `finish` still has its `throw`. Do step 6.
- **`printsTheAnswer` says `expected: "49 VIP tickets are left.` with the closing quote on the next line, `but was: "49 VIP tickets are left."`**: the answer is there, without its line end. The `out.println()` is missing from the `Answered` case.
- **`cli`, `desktop` and `sync server` are `SKIPPED`**: `core` failed first. Finish steps 2 and 3.
- **`zsh: no matches found: left?`**: zsh read the `?` as a file pattern. Put the question in quotes.
- **`Set ANTHROPIC_API_KEY to your Anthropic API key, or use --fake to see what would be sent`**: the key isn't set in this terminal. On Windows, set it in the same window you run `java` from. In cmd, check there are no spaces around `=`.
- **`The Anthropic API refused the key (HTTP 401). Check ANTHROPIC_API_KEY`**: the key is wrong, cut short when you copied it, or deleted in the Console. Make a new one.
- **`The Anthropic API answered HTTP 429. Try again in a minute`**: too many questions too fast for your account, or no credit left. Check the Console.
- **`Can't reach the Anthropic API. Check the internet connection`**: no internet, or `ANTHROPIC_BASE_URL` is set in your terminal from somewhere. Unset it: only the tests use it.
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/ai/SalesSnapshot.java` (or `SalesQuestions.java`, `solution/cli/src/main/java/com/boxoffice/cli/AnthropicClaude.java`, `AskCommand.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say why the counts come from `gate.db` and not from Claude
- [ ] Can say why no name or phone number goes into the prompt
- [ ] Can explain each part of the system prompt, and why "answer from these facts only" matters most
- [ ] Can say what streaming is, and what `MessageAccumulator` is for
- [ ] Can say why the stop reason is read before the text is trusted
- [ ] Can say what effort LOW and the fallback header do
- [ ] Wrote `facts`, the question check, the request, the streaming and `finish`
- [ ] All 422 tests pass
- [ ] Ran `ask --fake` and saw the facts, with no names in them
- [ ] Can say why `ANTHROPIC_API_KEY` is in an environment variable, and what a question costs
- [ ] Committed your work to Git (not `gate.db`, and never an API key)

## Next

Today Claude can only see the totals. Ask "whose is VIP-0031?" or "how many did we sell since 21:00?" and the honest answer is "I can't see that from here". Tomorrow Claude gets **tools**: three read-only lookups it can ask the gate to run, a ticket by its code, the sales between two times, and who has come in, and a loop that runs them and hands the results back.
