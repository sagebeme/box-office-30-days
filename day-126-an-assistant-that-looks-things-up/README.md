# Day 126: An assistant that looks things up

> 23:15. At gate B a man waves a crumpled ticket, VIP-0031, and says he paid at 21:00. Kamau has one question for the laptop.

Watch the video: *(not recorded yet)*

## The brief

Yesterday Claude answered from a six-line table: totals per ticket type, and how many came in. That's enough for "how many VIP are left?". It isn't enough for the questions the gate really gets at 23:15:

- "Is VIP-0031 a real ticket? Has it already come in?"
- "How many did we sell between 21:00 and 22:00?"
- "How many VIP are inside?"

Putting every ticket into the prompt would be slow, cost more with every sale, and send every buyer away from the laptop. So today Claude gets **tools**: three lookups it can ask the gate to run. Claude doesn't run them; it asks. The gate checks the request, runs it against `gate.db`, and hands the result back. Claude reads it and answers:

```
$ java -jar cli/target/boxoffice.jar ask "Is VIP-0001 a real ticket? Has it come in?"
(looking up find_ticket {"code":"VIP-0001"})
Yes. VIP-0001 is a VIP ticket, KES 5,000, sold at 19:00 to Wanjiku K. It hasn't come through a gate yet.
```

(That is an **example** of what Claude might say and look up, not output captured from a real run. Claude chooses its own lookups and its own words.)

The `(looking up ...)` line is the gate telling the staff what's happening while they wait. Every lookup only **reads**: none of them can sell, refund or change anything.

You write the input check and the three lookups' missing parts, the loop that runs them, and the two places where the adapter turns our tools into the SDK's and Claude's requests into ours.

## What you'll learn

- What **tool use** is: Claude asks for a lookup, your program runs it, and the result goes back
- How a tool is described to Claude: a name, a description, and a **JSON Schema** for its inputs
- Why a conversation is sent whole each time, and why Claude's own turns go back **exactly** as they came, thinking and signatures included
- Why Claude's tool inputs are checked like anything typed in from outside, and why a bad one becomes a **failed result**, never an exception
- How a **bounded loop** stops a question from looking things up for ever
- Why the stop reason decides whether a turn's tools may run at all
- What `eager_input_streaming` does, and why it makes your own input check matter more
- What happens when a tool's input arrives as broken JSON
- How much of a buyer to show: "Wanjiku K." and "phone ending 678", never the full name or number
- Why every lookup is read-only

## New words

- **Tool**: a lookup or action your program offers Claude. Claude can ask for it; only your program can run it.
- **Tool use**: Claude's answer saying "run this tool with these inputs, then I'll carry on". Its stop reason is `tool_use`.
- **Tool call**: one such request: an id, the tool's name, and the inputs as JSON.
- **Tool result**: what your program sends back for one call: the call's id, the text it found, and `is_error` if it couldn't run.
- **JSON Schema**: a way of writing in JSON what an input should look like, e.g. `{"type": "string"}`.
- **Turn**: one go from Claude: either it has finished, or it wants tools first.
- **Conversation**: every message so far, in order: the question, Claude's turns, the tool results. The API remembers nothing between requests, so it's all sent each time.
- **Signature**: a code the API puts on Claude's thinking, so it can tell later if anyone changed it.
- **Eager input streaming**: the inputs of a tool call are streamed as Claude writes them, before the API has checked them.
- **Read-only**: can look, can't change.
- **Round**: one batch of tool calls and their results.

## What's already written

From Days 101–125: the gate app, the server, and yesterday's `ask`, working, with tests. New today, already done:

In core, `com.boxoffice.core.ai`:

- `ToolSpec(name, description, properties, required)`: a tool as Claude is told about it. `properties` maps each input's name to its JSON Schema.
- `ToolCall(id, name, input)`: Claude asking for one lookup. `input` is the JSON text exactly as Claude wrote it.
- `ToolResult(callId, content, failed)`, with `ToolResult.found(call, text)` and `ToolResult.failed(call, problem)`. `failed` becomes the API's `is_error`.
- `Turn`, sealed: `Turn.Finished(reply)` or `Turn.UsesTools(calls)`.
- `Claude` has changed: `chat(system, tools)` starts a **`Chat`**, which remembers what was said. `chat.ask(question, onText)` gives the first turn; `chat.answer(results, onText)` hands the results back and gives the next.
- `FakeClaude` plays back turns: `thenLooksUp(calls...)` and `thenAnswers(text)`. It keeps `systems()`, `tools()`, `questions()` and `results()`, so a test sees everything that would have gone out.
- `SalesTools`: `specs()` (the three tools' descriptions), `salesBetween`, `checkIns`, the start of `onTheNight`, `lastThree` and the `TICKET_CODE` pattern. You write `run`, `findTicket`, `shortName` and the end of `onTheNight`.
- `SalesQuestions`: `MOST_LOOKUP_ROUNDS = 5`, a new `ask(question, onText, onLookup)` (yesterday's `ask(question, onText)` now calls it with an `onLookup` that does nothing), and a longer system prompt. You write the loop.

In the cli module:

- `AnthropicClaude`: `chat(...)`, `params(...)` with the tools and the whole conversation, and `AnthropicChat`, which keeps the conversation, sends tool results back as `tool_result` blocks with `is_error`, and asks once more if a lookup's input arrives garbled. `turnFrom` already handles `refusal` and `max_tokens`. You write `tool(spec)` and the `tool_use` part of `turnFrom`.
- `AskCommand` prints `(looking up <name> <input>)` on `err` for each lookup.
- `FakeAnthropicApi.toolUse(...)`: a streamed answer asking for one lookup, its input in pieces of JSON.

Tests: `SalesToolsTest` (12), `LookupLoopTest` (5), one more in `SalesQuestionsTest` (8 now), seven more in `AnthropicClaudeTest` (13 now) and one more in `AskCommandTest` (8 now).

New, for you to finish: `SalesTools.run` (step 2), `findTicket` and `shortName` (step 3), `onTheNight` (step 4), the loop in `SalesQuestions.ask` (step 5), `AnthropicClaude.tool` (step 6) and the `tool_use` turn in `turnFrom` (step 7). The TODOs are labelled with their step numbers.

## What you'll use

**How tool use works.** Claude can't open `gate.db`. It can only write text. So the request carries a list of tools, each with a name, a description and its inputs. When Claude needs one, it stops writing and answers with a `tool_use` block instead of words:

```json
{"type": "tool_use", "id": "toolu_1", "name": "find_ticket", "input": {"code": "VIP-0031"}}
```

and the stop reason `tool_use`. Your program runs the lookup and sends back, in a new user message:

```json
{"type": "tool_result", "tool_use_id": "toolu_1", "content": "No ticket VIP-0031 was sold for Kilele Nights", "is_error": false}
```

Claude reads it and carries on: maybe another lookup, maybe the answer. The `id` ties each result to its call. One turn can ask for several tools at once; all their results go back together, in one message, in the same order.

**The three lookups.** All in `SalesTools`, all read-only:

- `find_ticket(code)`: one ticket by the code printed on it: `VIP-0001: VIP, KES 5,000, sold Sat 14 Nov, 21:05 to Wanjiku K., phone ending 678. It came in at 21:30`. A ticket that doesn't exist is an **answer**, not a failure: `No ticket VIP-0099 was sold for Kilele Nights`. Claude asked properly; the ticket just isn't there.
- `sales_between(from, to)`: tickets sold in a window, `HH:mm` Nairobi time, `from` included, `to` not: `Between 20:00 and 21:00: Regular 1 paid and 1 guest, VIP 2 paid and 0 guests. Takings KES 11,500`.
- `check_ins(type?)`: who has come in, per type, and when the last one did: `Came in so far: Regular 1 of 2, VIP 1 of 1. The last came in at 21:40`. `type` is optional.

**Describing a tool to Claude.** Read `SalesTools.specs()`. The description is written for Claude, not for a programmer: what the tool gives back, and when to use it ("Use it when the staff ask about one particular ticket."). The inputs are a JSON Schema: `"code" -> {"type": "string", "description": "The ticket code, e.g. VIP-0001"}`, and `required` lists the inputs Claude must always give. For `sales_between`, the description says the times are Nairobi time, 24-hour, and that "a time in the early morning means the hours after midnight", because the night runs from 18:00 to 04:00.

**Claude's inputs are input from outside.** Claude writes `{"code": "VIP-0031"}`. Usually. It could also write `{"code": "'; drop table tickets; --"}`, `{"code": 42}`, `["VIP-0001"]`, a tool name that doesn't exist, or JSON cut off halfway. The question came from a person at the gate, and the person could have typed anything into it. So `SalesTools.run` checks every input, exactly as the server checks a sale from a gate:

- not JSON at all: `The input wasn't valid JSON`;
- JSON but not an object: `The input should be a JSON object`;
- an unknown tool: `There's no tool called sell_ticket`;
- then each tool checks its own inputs: a code must match `[A-Za-z0-9-]{3,20}`, times must be `HH:mm` and `from` before `to`, a type must be one the event sells.

**A bad input is a failed result, not an exception.** `run` never throws for bad input. It returns `ToolResult.failed(call, "code should be a ticket code like VIP-0001")`, which goes back with `is_error: true`. Claude reads that, sees what was wrong, and usually asks again properly in the next round. An exception would end the whole question with a stack trace; a failed result lets Claude fix its own mistake. `aBadLookupGoesBackAsAFailureClaudeCanFix` shows exactly that.

**How much of a buyer to show.** Whatever a tool returns goes to the API, like the prompt. Kamau needs enough to say "is this yours?" to the man at the gate, not a full identity. So `find_ticket` gives the first name and last initial, `Wanjiku K.` (from `Wanjiku Wambui Kamau`), and only the last three digits of the phone, `phone ending 678`. The system prompt says that a buyer's full name or phone number is something Claude can't see from here.

**The loop.** One question can take several rounds: look up, read, look up something else, answer. `SalesQuestions.ask` runs it:

```java
Claude.Chat chat = claude.chat(systemPrompt(snapshot()), tools.specs());
Turn turn = chat.ask(question.strip(), onText);
int rounds = 0;
while (turn instanceof Turn.UsesTools(List<ToolCall> calls)) {
    if (rounds == MOST_LOOKUP_ROUNDS) {
        return new Reply.Refused("Claude still wanted to look things up after "
                + MOST_LOOKUP_ROUNDS + " goes. Ask something narrower");
    }
    rounds++;
    // run each call, in order, telling onLookup first; then turn = chat.answer(results, onText)
}
return ((Turn.Finished) turn).reply();
```

`turn instanceof Turn.UsesTools(List<ToolCall> calls)` is a **record pattern** (Java 21): "if `turn` is a `UsesTools`, take its `calls` out into a variable". When the loop ends, the turn can only be a `Finished`.

**Why a limit.** A real question needs one or two rounds. If Claude gets confused and keeps asking, the loop would go on until someone pulls the plug, and every round costs money and time while a customer waits. Five rounds, then a plain "ask something narrower". `givesUpAfterFiveRoundsOfLookups` queues ten rounds and checks only five ran.

**`onLookup`.** Each call is handed to `onLookup` before it runs. The `ask` command prints it on `err`, `(looking up find_ticket {"code":"VIP-0001"})`, so the staff see that something is happening, and the answer on `out` stays clean.

**The conversation goes back whole, Claude's part unchanged.** The API remembers nothing between requests. So the second request carries everything: the question, Claude's first turn, the tool results. `AnthropicChat` keeps that list. Claude's own turn is added with `message.toParam()`, which turns the message exactly as it came into something that can be sent back: its thinking blocks with their signatures, and its `tool_use` blocks with their ids. The API checks that the thinking is unchanged, and refuses a request where it isn't. That's why it's never rebuilt from the text, or tidied up. `theNextRequestCarriesTheWholeConversation` checks the thinking and its signature go back first.

**Read the stop reason before running anything.** `turnFrom` decides what a message means from how it stopped:

- `refusal`: Claude declined, perhaps halfway through writing a `tool_use` block. Never run that turn's tools.
- `max_tokens`: out of room. A `tool_use` block's input may be cut off too. Never run it. (`aLookupCutOffByMaxTokensIsNeverRun`)
- `tool_use`: now, and only now, the `tool_use` blocks are calls to run.
- anything else: the answer.

**Eager input streaming.** `tool(spec)` sets `eager_input_streaming: true` on every tool. Without it, the API waits until Claude has written a tool's whole input and checks it against the schema before sending it on. With it, the input streams as Claude writes it, so the lookup starts sooner. The price: the API no longer checks it first, so the input can be anything, even broken JSON. That's fine here because `SalesTools.run` checks every input anyway. It's also why that check isn't optional.

**Broken JSON.** When a tool's input arrives as broken JSON, like `{"code": VIP-0001}` (no quotes), the SDK's `MessageAccumulator` reads it with a strict JSON parser, and the parser's own exception, Jackson's `JsonProcessingException`, comes straight out. There's no call to answer: the input can't be read. So `AnthropicChat.streamOrNull` catches exactly that exception (a wrong key or a rate limit must still go up), and `send` asks Claude once more. If it comes back garbled twice, it gives up: `Claude's lookup came back garbled twice. Ask again`. Nothing is run. Read `send` and `streamOrNull`: they're already written.

**From our ToolSpec to the SDK's Tool.** The SDK has its own `Tool` class. `tool(spec)` builds it: each property's schema goes in with `putAdditionalProperty(name, JsonValue.from(schema))` (`JsonValue.from` turns a Java `Map` into JSON), then the name, description, `required` and `eagerInputStreaming(true)`. Keeping our own `ToolSpec` in core means core never sees the SDK.

**Why read-only.** Each tool only reads `gate.db`. Claude can misread a question; Kamau can type "refund him" meaning "can I refund him?". If a tool could sell or refund, a confused answer could change the night's money. With read-only tools, the worst a confused answer can do is be wrong, and the staff can check it on the gate screen. The system prompt says so: nothing Claude does can sell, refund or change a ticket, so if the staff ask for that, Claude tells them to do it on the gate screen. `brokenInputAndUnknownToolsFail` checks that `sell_ticket` doesn't exist.

## Steps

1. **Open the starter and run the tests.** `cd day-126-an-assistant-that-looks-things-up/starter`, then `../../mvnw test`. You should see `Tests run: 214, Failures: 0, Errors: 22` in `core` and `BUILD FAILURE`; `cli`, `desktop` and `sync server` are `SKIPPED`:
   - eleven `SalesToolsTest` tests end with `UnsupportedOperation Not written yet: Day 126, step 2`, and `namesAreShortened` with `Not written yet: Day 126, step 3`;
   - the five `LookupLoopTest` tests and five `SalesQuestionsTest` tests end with `UnsupportedOperation Not written yet: Day 126, step 5`.

   `aBlankQuestionIsNeverSent`, `aVeryLongQuestionIsNeverSent` and `theInstructionsSayToStickToWhatItCanSee` already pass: the question check is yesterday's.

2. **Check the input.** Open `starter/core/src/main/java/com/boxoffice/core/ai/SalesTools.java` and find `run`. Replace the `throw`:
   - `JsonNode input;` then in a `try`, `input = JSON.readTree(...)` of `call.input()`, or of `"{}"` if the input is `null` or blank;
   - `catch (JsonProcessingException e)`: return `ToolResult.failed(call, "The input wasn't valid JSON")`;
   - if `!input.isObject()`: return `ToolResult.failed(call, "The input should be a JSON object")`;
   - then `return switch (call.name())`: `"find_ticket" -> findTicket(call, input)`, `"sales_between" -> salesBetween(call, input)`, `"check_ins" -> checkIns(call, input)`, `default -> ToolResult.failed(call, "There's no tool called " + call.name())`.

   Run `../../mvnw test -Dtest=SalesToolsTest -Dsurefire.failIfNoSpecifiedTests=false` (Windows: `..\..\mvnw.cmd test "-Dtest=SalesToolsTest" "-Dsurefire.failIfNoSpecifiedTests=false"`). 4 of the 12 pass now, including `brokenInputAndUnknownToolsFail` and `countsWhoCameIn`. Five say `Not written yet: Day 126, step 3`; three say `Not written yet: Day 126, step 4`.

3. **Look one ticket up.** Still in `SalesTools.java`:
   - In `shortName`: `String[] words = buyer.strip().split("\\s+")`. One word: return it. Otherwise: `words[0] + " " + words[words.length - 1].charAt(0) + "."`. (`\\s+` means "one or more spaces", so `"  Otieno  "` is one word.)
   - In `findTicket`: `String code = input.path("code").asText("").strip()`. If `!TICKET_CODE.matcher(code).matches()`, return `ToolResult.failed(call, "code should be a ticket code like VIP-0001")`. Then `code = code.toUpperCase()`, so `vip-0001` finds `VIP-0001`.
   - `store.findTicket(event.id(), code)`. Empty: return `ToolResult.found(call, "No ticket " + code + " was sold for " + event.name())`.
   - Otherwise build the text with `"%s: %s, %s, sold %s to %s, %s. It %s".formatted(...)` from: the code; the type's name (`event.ticketType(ticket.typeCode()).map(TicketType::name).orElse(ticket.typeCode())`); `ticket.price().format()`, or `"a guest ticket"` if the price equals `Money.ZERO`; `NairobiTime.format(ticket.soldAt())`; `shortName(ticket.buyer())`; `"phone ending " + lastThree(ticket.phone())`, or `"no phone given"` if the phone is empty; `"came in at " + NairobiTime.timeOfDay(at)` from `store.checkedInAt(event.id(), code)`, or `"hasn't come in yet"`.

   Never the full name, never the full number. Run `SalesToolsTest` again: 9 of 12 pass. The three left say `Not written yet: Day 126, step 4`.

4. **After midnight.** Find `onTheNight`. Parsing `"02:00"` into a `LocalTime` is written. Below the TODO:
   - `ZonedDateTime start = event.startsAt();` and `ZonedDateTime at = start.with(time);` (the same day as the start, at that time);
   - if `at.isBefore(start.minusHours(6))`, then `at = at.plusDays(1)`. The doors open at 18:00, so anything before 12:00 on Saturday means Sunday morning;
   - `return Optional.of(at.toInstant())`.

   Run `SalesToolsTest`: all 12 pass. `theSmallHoursAreAfterMidnight` sells a ticket at 01:30 on Sunday and asks for 23:00 to 02:00.

5. **The loop.** Open `SalesQuestions.java` and find the TODO in `ask(question, onText, onLookup)`. Write the loop from "What you'll use". Inside the `while`, after the limit check and `rounds++`: make a `List<ToolResult> results = new ArrayList<>()`; for each `call` in `calls`, `onLookup.accept(call)`, then `results.add(tools.run(call))`; after the `for`, `turn = chat.answer(results, onText)`. After the `while`, `return ((Turn.Finished) turn).reply()`.

   Run `../../mvnw test`. `core` passes now: 214 tests. `cli` fails: every `AnthropicClaudeTest` test says `Not written yet: Day 126, step 6`, and five `AskCommandTest` tests fail, `looksATicketUpAndSaysSo` and `printsTheAnswer` with `expected: 0 but was: 1`.

6. **Describe the tools to the SDK.** Open `starter/cli/src/main/java/com/boxoffice/cli/AnthropicClaude.java` and find `tool(spec)`. Replace the `throw`:
   - `Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();`
   - `spec.properties().forEach((name, schema) -> properties.putAdditionalProperty(name, JsonValue.from(schema)));`
   - return `Tool.builder().name(spec.name()).description(spec.description())`, then `.inputSchema(Tool.InputSchema.builder().properties(properties.build()).required(spec.required()).build())`, then `.eagerInputStreaming(true)`, then `.build()`.

   Run `../../mvnw test -Dtest="AnthropicClaudeTest,AskCommandTest" -Dsurefire.failIfNoSpecifiedTests=false`. 10 of the 13 `AnthropicClaudeTest` tests pass. `aFailedLookupIsSentAsAnError`, `aLookupComesBackWithItsInputJoinedUp` and `theNextRequestCarriesTheWholeConversation` say `Not written yet: Day 126, step 7`. `AskCommandTest.looksATicketUpAndSaysSo` still says `expected: 0 but was: 1`.

7. **Turn tool_use blocks into calls.** Find the TODO marked `step 7` in `turnFrom`, inside `if (stop.equals(StopReason.TOOL_USE))`. Replace the `throw`: go through `message.content().stream()`, `flatMap(block -> block.toolUse().stream())` to keep only the `tool_use` blocks, `map(AnthropicClaude::callFrom)`, `toList()`, and return `new Turn.UsesTools(calls)`. `callFrom` is written: it turns the block's input back into JSON text.

   It sits **after** the `refusal` and `max_tokens` checks, and that order is the point: a turn that stopped for any other reason never has its tools run.

8. **Run all the tests.** `../../mvnw test`. You should see 453 tests in all and `BUILD SUCCESS`.

9. **Ask with lookups.** Follow "Run it and test it" below.

10. **Stretch (not required): a fourth read-only lookup.** Add `guests_in()` to `SalesTools`: how many guest tickets have come in, without names. Give it a `ToolSpec` in `specs()`, a `case` in `run`, and a test in `SalesToolsTest`. Then `offersTheThreeLookups` fails: that's a test doing its job; update it in your copy. Keep it read-only. Your changes aren't part of the course's tests, so keep them in your own Git.

## Run it and test it

From the day's `starter` folder:

```
cd day-126-an-assistant-that-looks-things-up/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=LookupLoopTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=LookupLoopTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **214 tests** in `core` (including SalesTools 12, LookupLoop 5, SalesQuestions 8, SalesSnapshot 4), **51** in `cli` (including AnthropicClaude 13, AskCommand 8), **41** in `desktop` and **147** in `sync server`, **453** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] Box Office sync server ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**Build the jars.** `../../mvnw package -DskipTests`.

(On Windows, write `cli\target\boxoffice.jar` below.)

**Sell a few tickets.** On a fresh `gate.db`:

```
$ java -jar cli/target/boxoffice.jar sell VIP "Wanjiku Kamau"
Sold VIP-0001 to Wanjiku Kamau for KES 5,000

$ java -jar cli/target/boxoffice.jar sell -q 2 REG "Kamau family"
Sold 2 tickets to Kamau family for KES 3,000: REG-0001 to REG-0002
```

**Practice with `--fake`.** As yesterday, it shows the facts and sends nothing. It doesn't run lookups: Claude decides which lookups it wants, and in practice mode there's no Claude to decide. The tests are where the lookups run without a key.

```
$ java -jar cli/target/boxoffice.jar ask --fake "Whose is VIP-0001?"
Practice mode: nothing was sent. Claude would get your question and these facts:
Event: Kilele Nights, Sat 14 Nov, 18:00 to Sun 15 Nov, 04:00 (Nairobi time)
Now: Sat 14 Nov, 19:00
Regular (REG): KES 1,500 each. 2 paid, 0 guests, 498 left of 500. Takings KES 3,000
VIP (VIP): KES 5,000 each. 1 paid, 0 guests, 49 left of 50. Takings KES 5,000
Total: 3 paid, 0 guests, 547 left of 550. Takings KES 8,000
Through the gates: 0 of the 3 tickets issued
```

**Ask the real Claude (optional, costs a little).** Set up as on Day 125: a key from console.anthropic.com, in `ANTHROPIC_API_KEY`, in the same terminal, never in a file in the repository. Each lookup round is one more request to the API, so a question with a lookup costs a little more than one without.

macOS / Linux, all on one line:

```
ANTHROPIC_API_KEY=your-anthropic-api-key java -jar cli/target/boxoffice.jar ask "Is VIP-0001 a real ticket?"
```

Windows PowerShell:

```
$env:ANTHROPIC_API_KEY="your-anthropic-api-key"
```

```
java -jar cli\target\boxoffice.jar ask "Is VIP-0001 a real ticket?"
```

Windows cmd: `set ANTHROPIC_API_KEY=your-anthropic-api-key`, then the same `java` line.

Here is an **example** of what you might see. It is not captured output: Claude picks its own lookups and its own words, so yours will differ. The `(looking up ...)` lines are on `err`, the answer on `out`:

```
$ java -jar cli/target/boxoffice.jar ask "Is VIP-0001 a real ticket?"
(looking up find_ticket {"code":"VIP-0001"})
Yes. VIP-0001 is a VIP ticket, KES 5,000, sold at 19:00 to Wanjiku K. It hasn't come through a gate yet.

$ java -jar cli/target/boxoffice.jar ask "Is VIP-0031 real?"
(looking up find_ticket {"code":"VIP-0031"})
No. There's no ticket VIP-0031 for Kilele Nights. Check the code on the ticket again.

$ java -jar cli/target/boxoffice.jar ask "How many VIP are left?"
49 of the 50 VIP tickets are left.

$ java -jar cli/target/boxoffice.jar ask "Refund the Kamau family"
I can only look things up, not change tickets. Do the refund on the gate screen.
```

The third question needs no lookup: the facts answer it. The fourth is the read-only rule at work.

To hide the lookup lines and keep only the answer, send `err` away: `2>/dev/null` on macOS / Linux, `2>$null` in PowerShell.

`gate.db` is in `.gitignore`. Delete it to start the night again.

## When you're stuck

- **`Not written yet: Day 126, step 2`**: `run` still has its `throw`. Do step 2.
- **`brokenInputAndUnknownToolsFail` says `expected: ToolResult[callId=call_1, content=The input should be a JSON object, failed=true]`**: the `isObject()` check is missing, so `["VIP-0001"]` went on to `find_ticket`. Put it after the `catch`, before the `switch`.
- **`anEmptyGateHasNobodyIn` fails**: it sends a blank input, and a blank input must count as `"{}"`. Check for `null` and `isBlank()` before `readTree`.
- **`Not written yet: Day 126, step 3`**: `findTicket` or `shortName` still has its `throw`.
- **`findsATicketWithoutGivingAwayTheBuyer` gets `No ticket vip-0001 was sold...`**: the test asks in lower case. Add `code = code.toUpperCase()` after the pattern check.
- **`findsATicketWithoutGivingAwayTheBuyer` says the content contains `Kamau` or `254712`**: the full name or phone got into the result. Use `shortName(...)` and `lastThree(...)`.
- **`Not written yet: Day 126, step 4`**: `onTheNight` still has its `throw`.
- **`theSmallHoursAreAfterMidnight` says `Expecting actual: "from should be before to" to start with: "Between 23:00 and 02:00: Regular 1 paid"`**: 02:00 was read as 02:00 on Saturday, before 23:00. Add the `plusDays(1)` rule from step 4.
- **`Not written yet: Day 126, step 5`**: the loop in `SalesQuestions.ask` still has its `throw`.
- **`runsTheLookupAndHandsTheResultBack` says `Expecting actual: [] to contain exactly (and in same order): ["find_ticket"]`**: the lookups ran, but `onLookup.accept(call)` is missing, so the screen never hears about them.
- **`givesUpAfterFiveRoundsOfLookups` says `expected: Refused[why=Claude still wanted to look things up after 5 goes. Ask something narrower] but was: Answered[text=(The fake has no more answers.)]`**: there's no limit, so the loop ran all ten rounds the test queued. Add the `rounds == MOST_LOOKUP_ROUNDS` check at the top of the `while`.
- **`Not written yet: Day 126, step 6`** in every `AnthropicClaudeTest` test: `tool(spec)` still has its `throw`. Every chat builds its tools first, so nothing works until it's done.
- **`offersTheToolsWithTheirInputsStreamed` says `Expecting value to be true but was false`**: `.eagerInputStreaming(true)` is missing.
- **`Not written yet: Day 126, step 7`**: the `tool_use` part of `turnFrom` still has its `throw`.
- **`looksATicketUpAndSaysSo` says `expected: "(looking up find_ticket {"code":"VIP-0001"})" but was: ""`**: nothing was looked up. Check `onLookup.accept(call)` in step 5.
- **`AskCommandTest.looksATicketUpAndSaysSo` says `expected: 0 but was: 1`**: step 7 (or step 6) isn't done, so the command stops with an error.
- **`cli`, `desktop` and `sync server` are `SKIPPED`**: `core` failed first. Finish steps 2 to 5.
- **`No answer: Claude's lookup came back garbled twice. Ask again`** on a real run: Claude wrote broken JSON for a lookup twice in a row. Rare; ask again, maybe in other words.
- **`No answer: Claude still wanted to look things up after 5 goes. Ask something narrower`**: the question needed more lookups than the limit. Split it: one question about one ticket or one hour.
- **`Set ANTHROPIC_API_KEY ...`, `refused the key (HTTP 401)` or `Can't reach the Anthropic API`**: see Day 125's "When you're stuck".
- **Still stuck?** Open `solution/core/src/main/java/com/boxoffice/core/ai/SalesTools.java` (or `SalesQuestions.java`, `solution/cli/src/main/java/com/boxoffice/cli/AnthropicClaude.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say what happens in one round of tool use, from `tool_use` to `tool_result`
- [ ] Can say why Claude's tool inputs are checked, and why a bad one is a failed result and not an exception
- [ ] Can say why `find_ticket` shows "Wanjiku K." and "phone ending 678"
- [ ] Can say why the loop stops after 5 rounds
- [ ] Can say why Claude's turns go back exactly as they came
- [ ] Can say why a turn that stopped on `max_tokens` or `refusal` never has its tools run
- [ ] Can say what `eager_input_streaming` changes, and what happens with broken JSON
- [ ] Can say why every tool is read-only
- [ ] Wrote the input check, `find_ticket`, the after-midnight rule, the loop, `tool(spec)` and the `tool_use` turn
- [ ] All 453 tests pass
- [ ] Committed your work to Git (not `gate.db`, and never an API key)

## Next

Claude can now look things up. But is it any good at it? A test that checks "the answer is exactly this" can't work when every answer is worded differently. Tomorrow you write **evals**: a set of real gate questions, graders in JUnit that check what matters in each answer (the right number, no full name, no Markdown), and a scorecard, so you know whether a change to the prompt made Claude better or worse.
