# Day 122: Pay with M-Pesa

> 14:03, the Monday before. Otieno's phone buzzes on the matatu: "Pay KES 3,000 to KILELE NIGHTS. Enter M-Pesa PIN." He types it. Before his stop, the page shows W-REG-0001 and W-REG-0002.

Watch the video: *(not recorded yet)*

## The brief

Yesterday Otieno held two Regular tickets on the site. Holding isn't paying. Today the site asks **M-Pesa** for the money.

Safaricom's API for M-Pesa is called **Daraja**. The way a website asks a fan to pay is an **STK push**: the server asks Daraja, and Daraja makes the fan's phone show "Enter M-Pesa PIN to pay KES 3,000". The fan types their PIN on their own phone. The website never sees it.

The answer doesn't come back in the same request. A few seconds (or minutes) later, Safaricom **calls our server** at an address we gave it, the **callback**, and says what happened: paid, cancelled, wrong PIN, not enough money. So the night goes like this:

1. Otieno presses "Hold my tickets". The order is saved `AWAITING_PAYMENT` and its places are held (Day 121).
2. Straight away, the server sends the STK push. His phone buzzes.
3. The order page says "Check your phone", and asks the server every 3 seconds whether anything has changed.
4. He types his PIN. Safaricom posts the result to `/mpesa/callback/<our secret>`.
5. The server checks it, marks the order `PAID`, and issues tickets `W-REG-0001` and `W-REG-0002`. The page shows them.

If he presses Cancel, the order is `CANCELLED` and the places go back on sale. If he does nothing for 10 minutes, the order `EXPIRES`. If money comes in that doesn't look right, the order is `FLAGGED` for a person.

You don't need a Safaricom account to practise. The server starts in **practice mode** (`MPESA_MODE=stub`): no real M-Pesa, no real money, and the order page has "Pretend to pay" and "Pretend to cancel" buttons that send exactly the callback Safaricom would.

You write five pieces: the STK password, keeping Daraja's token, reading a callback, settling an order, and the callback address that always says "Accepted".

## What you'll learn

- How an **STK push** and its **callback** work, and why the answer comes later, on a different request
- How Daraja's **access token** works, and why we keep it and fetch a new one a minute early
- How the STK **password** is made, and why the timestamp must be on a Nairobi clock
- Why M-Pesa amounts are **whole shillings**, and why we refuse KES 1,500.50 instead of rounding it
- How to read a callback that comes in several shapes, without trusting its order
- What **idempotent** means for a callback Safaricom sends twice
- Why the callback address always answers `200 {"ResultCode":0,"ResultDesc":"Accepted"}`, even when something went wrong
- How a secret in the callback address stands in for a signature Daraja doesn't send
- What a **reconciler** is, and what happens to a payment that arrives late
- What you need for the real Daraja sandbox, and why we don't use it in class

## New words

- **M-Pesa**: Safaricom's mobile money. Most fans at Kilele Nights pay with it.
- **Daraja**: Safaricom's API for M-Pesa ("daraja" is Swahili for "bridge").
- **STK push**: a request from our server that makes M-Pesa show a "pay this much, enter your PIN" prompt on the fan's phone. (STK is the SIM Toolkit, the menu on the SIM card that shows it.)
- **Callback**: an address on **our** server that Safaricom calls when it knows the result. We give Daraja the address with every STK push.
- **Shortcode**: our M-Pesa business number: a paybill or a till. The sandbox's shared paybill is `174379`.
- **Passkey**: a secret Safaricom gives each shortcode for STK pushes.
- **Consumer key** and **consumer secret**: the username and password of our app on Daraja. We swap them for a token.
- **Access token**: a short-lived pass (about an hour) that Daraja wants with every request, in `Authorization: Bearer <token>`.
- **CheckoutRequestID**: Daraja's name for one STK push, like `ws_CO_191220191020363925`. The callback carries it, and it's how we find the order.
- **Receipt**: M-Pesa's code for a payment that went through, like `QK12ABC3DE`. The fan gets it in their SMS too.
- **Stub**: a stand-in that behaves like the real thing, for practice and tests. `StubDaraja` says yes to every push and sends nothing.
- **Idempotent**: doing it twice has the same effect as doing it once (Day 120).
- **Flagged**: money came in, but something about it is wrong. A person has to look.
- **Reconciler**: a job that runs every minute and tidies up: expires unpaid orders, and shouts about flagged ones.
- **Tunnel**: a program that gives a server on your laptop a public `https://` address on the internet, so Safaricom can reach it.

## What's already written

From Days 101–121: the gate app, the sync server and the ticket site, with tests. New today, already done:

In `server/src/main/java/com/boxoffice/server/mpesa/`, the M-Pesa code, kept apart from the rest:

- `Daraja.java`: the one call the site needs, `stkPush(StkRequest)`. Two versions: `DarajaClient` (the real one) and `StubDaraja` (practice).
- `StkRequest.java`: one "please pay" prompt. It refuses a phone that isn't like `254712345678` (12 digits, no `+`), refuses an amount with cents (`M-Pesa takes whole shillings only, not KES 1,500.50`) or of zero, cuts `AccountReference` to 12 characters and `TransactionDesc` to 13 (Daraja quietly refuses longer ones), and gives the amount in `shillings()`.
- `StkAnswer.java`: Daraja's "OK, prompt sent": the `CheckoutRequestID` and `MerchantRequestID`.
- `DarajaClient.java`: `stkPush` builds the body Daraja wants and checks the answer. A 200 with a `ResponseCode` other than `"0"` is still a no. An HTML error page, a wrong key or no network at all become a `DarajaException` with a message fit to show a fan. It sends each push **once**, never again: a push sent twice is two prompts, and the fan might pay twice. Its `Account` record holds our secrets, and its `toString()` is written by hand so they never land in a log: `Account[shortcode=174379, transactionType=CustomerPayBillOnline, secrets hidden]`.
- `StkCallback.java`: what Safaricom posts. `parse(body)` unwraps a body that came as a JSON **string**, reads `ResultCode` whether it's a number or a string like `"0"`, and finds `Amount`, `MpesaReceiptNumber` and `PhoneNumber` by name (with `item`, step 4). `PhoneNumber` comes as a number, `254712345678`, and is turned into text. `paid()` is `ResultCode == 0`. `CANCELLED_BY_USER` is `1032`.
- `StubDaraja.java`: says yes to every push with a made-up `ws_CO_STUB_...` id, and remembers the requests for the tests.

In `server/src/main/java/com/boxoffice/server/`:

- `MpesaSettings.java` and `MpesaConfig.java`: read the `boxoffice.mpesa.*` settings and pick `StubDaraja` or `DarajaClient` from `MPESA_MODE`. In `sandbox` or `live` mode, a missing setting stops the server at start-up, not at the first fan's payment.
- `Payments.java`: `requestPayment(reference)` sends the push and saves its `CheckoutRequestID` in `stk_requests` (not `@Transactional`: it never holds the database open while waiting up to 15 seconds for Safaricom). `settle(callback)` finds the order, locks its row, and settles it (step 5). `expireOrders`, `flagged`, `tickets`, `latestCheckout`, `mark`, `flag` and `issueTickets` are written. `HOLD` is 10 minutes. `WEB = "W"`: tickets paid on the site are `W-REG-0001`, `W-VIP-0001`...
- `MpesaCallback.java`: `POST /mpesa/callback/{secret}`, with `ACCEPTED` ready (step 6).
- `PracticePayments.java`: `POST /practice/orders/{ref}/pay?result=0` (or `1032`), the "Pretend to pay" and "Pretend to cancel" buttons. It builds the callback Safaricom would send and settles it the same way. `@ConditionalOnProperty` means it only exists in stub mode.
- `Reconciler.java`: every minute (`@Scheduled`, switched on by `@EnableScheduling` in `SyncServerApplication`), it expires orders not paid in 10 minutes and logs every flagged order as an `ERROR`.
- `Order.java` has all six statuses: `AWAITING_PAYMENT`, `PAID`, `CANCELLED`, `FAILED`, `EXPIRED`, `FLAGGED`, and a `receipt` and `detail`. `Places.taken` counts `FLAGGED` orders as holding their places too.
- `TicketSite.java`: sends the push as soon as an order is placed. `/orders/{ref}/status` is the payment piece alone. `POST /orders/{ref}/pay` is "No prompt? Send it again".
- `AdminController.java` refuses a gate called `W`: `W is the ticket site's. Pick another gate id`.
- `order.html`: the payment piece, which asks again every 3 seconds while the order waits, and shows the tickets once it's paid.
- `V4__payments.sql`: the `stk_requests` table, and `receipt` and `detail` on `orders`. Tickets can now come from an order (`order_reference`) instead of a gate's sale.
- `application.properties`: the M-Pesa settings, each from an environment variable (see "What you'll use").
- Tests: `StkPasswordTest` (6), `DarajaClientTest` (12, against `FakeDaraja`, a small pretend Safaricom on your own laptop), `StkCallbackTest` (8), `PaymentsTest` (12), `MpesaCallbackTest` (5) and `PayOnTheSiteTest` (6).

New, for you to finish: `StkPassword` (step 2), keeping the token in `DarajaClient.token` (step 3), `StkCallback.item` (step 4), `Payments.settle` (step 5) and `MpesaCallback.receive` (step 6). The TODOs are labelled with their step numbers.

## What you'll use

**Two requests, not one.** The STK push answers in a second or two, but only to say "prompt sent". It can't say "paid": Otieno hasn't typed his PIN yet. Safaricom tells us later, with a POST to our callback. So the order waits as `AWAITING_PAYMENT`, and the `CheckoutRequestID` from the push is what joins the two up: we save it in `stk_requests` with the order's reference, and the callback carries the same id.

**The token.** Every Daraja request carries a token. To get one, send our consumer key and secret, as Basic auth (Day 119's `Authorization` header, with `base64(key:secret)`), to `GET /oauth/v1/generate?grant_type=client_credentials`. The answer looks like this:

```json
{"access_token":"c9SQxWWhmdVRlyh0zh8gZDTkubVF","expires_in":"3599"}
```

`expires_in` is a **string**, `"3599"`, not a number, so read it with `asString` and `Long.parseLong`. The token lasts 3599 seconds. Asking for a new one before every push wastes a request, and Safaricom limits how often you may ask. So keep it in a field, with the moment it stops being good:

```java
if (token != null && now.isBefore(tokenGoodUntil)) {
    return token;
}
...
tokenGoodUntil = now.plusSeconds(seconds).minus(REFRESH_EARLY);
```

**A minute early.** `REFRESH_EARLY` is 60 seconds. A token that has 2 seconds left when we start a push might be dead by the time the push arrives. Fetching a new one a minute before the old one runs out means it never runs out in the middle of a request.

**One fetch for ten fans.** `token()` is `synchronized` (Day 118): only one thread at a time can be inside it. When ten fans pay at the same moment and the token has just run out, the first thread fetches a new one while the other nine wait at the door. When they get in, the token is fresh, and they use it. One request to Safaricom, not ten. `DarajaClientTest.tenPaymentsAtOnceShareOneTokenRequest` checks it.

**The password and the timestamp.** Every STK push carries a `Timestamp` and a `Password`:

- `Timestamp` is `yyyyMMddHHmmss` on a **Nairobi** clock (EAT, UTC+3), whatever clock the server runs on. A server in a data centre in Frankfurt or Virginia runs on UTC. 15:30:05 UTC on 14 November is `20261114183005`. Get it wrong, and Daraja refuses the push.
- `Password` is base64 of the shortcode, the passkey and that same timestamp, run together: `base64("174379" + passkey + "20261114183005")`.

```java
STAMP.format(now.atOffset(EAT));
Base64.getEncoder().encodeToString((shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));
```

Base64 isn't a lock. Anyone can turn the password back into its three parts, passkey included. So the password is as secret as the passkey: never log it, never show it.

**Whole shillings only.** M-Pesa has no cents. Since Day 103, `Money` keeps cents, so `StkRequest` refuses any amount that isn't a whole number of shillings. Why not round? Rounding KES 1,500.50 down loses 50 cents on every ticket; rounding it up charges the fan more than the price they saw. Refusing makes the mistake show up in a test, not in the money. `shillings()` turns cents into shillings once, at the edge.

**A callback, in all its shapes.** A payment that went through:

```json
{"Body":{"stkCallback":{
  "MerchantRequestID":"...", "CheckoutRequestID":"ws_CO_...",
  "ResultCode":0, "ResultDesc":"The service request is processed successfully.",
  "CallbackMetadata":{"Item":[
    {"Name":"Amount","Value":3000}, {"Name":"MpesaReceiptNumber","Value":"QK12ABC3DE"},
    {"Name":"Balance"},
    {"Name":"TransactionDate","Value":20261114183512}, {"Name":"PhoneNumber","Value":254712345678}]}}}}
```

Things that have really happened, and that the code is ready for:

- The items come **in any order**. Never read `Item[0]` and hope it's the amount: find each by its `Name`.
- An item can have **no `Value`** at all (`{"Name":"Balance"}`), or `"Value": null`. That's "missing", not a crash.
- Names have come in other cases (`amount`). Compare without caring about case.
- `ResultCode` is a number from the live system and sometimes a string (`"0"`) from the sandbox.
- The whole body can arrive as a JSON **string**: `"{\"Body\":...}"`. `parse` unwraps it once.
- A payment that **didn't** go through (cancelled, wrong PIN, not enough money) has **no `CallbackMetadata` at all**: no amount, no receipt.

**Case without surprises.** `"Amount".toLowerCase()` uses your laptop's language. On a laptop set to Turkish, `"I".toLowerCase()` isn't `"i"`, so `"MpesaReceiptNumber"` stops matching. `toLowerCase(Locale.ROOT)` lowercases the same way everywhere.

**Settling, in order.** `Payments.settle` has found the order and locked its row (`for update`, Day 120). Then:

- Not paid: if the order isn't `AWAITING_PAYMENT` any more, it's `ALREADY_SETTLED`. If the code is `1032`, `CANCELLED` (`You cancelled the M-Pesa payment`). Any other code, `FAILED` (`M-Pesa said: ` and Daraja's words). Either way, the places are free again.
- Paid: keep the receipt on the order whatever happens next. Then: an `EXPIRED` order is flagged (`Paid after the order ran out`). An order not waiting any more is `ALREADY_SETTLED`. No amount: flagged. An amount that isn't the order's: flagged (`Paid KES 1,500 for an order of KES 3,000`). Otherwise `PAID`, and `issueTickets`.

**Idempotent on `CheckoutRequestID`.** Safaricom sends a callback again when it isn't sure we got it. If the second one issued tickets too, Otieno would have four tickets for two places. The rule: **only an order that's still waiting gets changed.** The second callback finds the order `PAID` and returns `ALREADY_SETTLED`, doing nothing. The row lock means two copies arriving at the same moment can't both see "waiting".

**Check the amount.** The callback says how much was paid. If it isn't what we asked for, something is wrong: a bug, a mix-up, or someone sending a made-up callback. Never issue tickets for it. Flag it, log it as an `ERROR`, and keep the places held: someone has paid something, and a person sorts it out.

**Always say "Accepted".** The callback address answers `200 {"ResultCode":0,"ResultDesc":"Accepted"}` **every time**: for a good callback, for a wrong secret, for rubbish, even when our own database is down. Two reasons:

- Anything else (a 500, a 401) makes Safaricom think we didn't get it, and it sends it again, and again. A storm of retries, all failing the same way.
- A different answer for a wrong secret tells a stranger poking at our address that they've found something. Same answer, same status, whatever they send: they learn nothing.

So problems go to the **log**, loudly, never into the answer: `log.warn` for a stranger or rubbish, `log.error` with the exception for our own failure. Daraja may send it again later, and the reconciler is the safety net.

**The secret in the address.** Daraja doesn't **sign** its callbacks: there's nothing in the request that proves it came from Safaricom. So we put a long random secret at the end of the address we give Daraja: `https://tickets.example.co.ke/mpesa/callback/<secret>`. Only Safaricom ever sees it. A callback with the wrong secret is ignored. Compare it with `Tokens.same` (written on Day 119), never `equals`: `equals` stops at the first different letter, so how long it takes leaks how many letters were right. A blank secret in the settings means "accept no callbacks at all", never "accept every callback". In a real deployment you'd also check the request came from Safaricom's published list of callback IP addresses, which changes, so ask Safaricom for the current one.

**Practice mode, the default.** `MPESA_MODE` is `stub` unless you say otherwise. `StubDaraja` says yes to every push and sends nothing to any phone. The order page shows "Pretend to pay" and "Pretend to cancel". They call `PracticePayments`, which builds the JSON Safaricom would send (including a `{"Name":"Balance"}` with no value, to keep you honest) and settles it with the same `StkCallback.parse` and `Payments.settle` as a real callback. Receipts are `PRACTICE01`, `PRACTICE02`...

**The page keeps asking, politely.** While the order waits, the payment piece asks `/orders/{ref}/status?while=AWAITING_PAYMENT` every 3 seconds. If nothing has changed, the server answers with the header `HX-Reswap: none`, which tells htmx to leave the page alone, so a button Otieno is about to press isn't swapped out from under his finger. Once the order is paid (or cancelled), the new piece has no `hx-trigger`, so the asking stops by itself.

**The reconciler.** Some fans never answer the prompt: the phone was off, they changed their mind. Their places can't stay held forever. Every minute, `Reconciler` runs `expireOrders`: any order still `AWAITING_PAYMENT` 10 minutes after it was placed becomes `EXPIRED` (`Not paid within 10 minutes`), and its places go back on sale. It also logs every `FLAGGED` order as an `ERROR`, every minute, until a person deals with it: someone has paid and has no ticket.

**An honest limit: a late payment is flagged.** Daraja has an **STK query** (`POST /mpesa/stkpushquery/v1/query`) that asks "what happened to this push?". A careful reconciler asks it before expiring an order. Ours doesn't. So if Otieno pays at minute 9:58 and Safaricom's callback arrives at minute 10:30, after the reconciler gave his places back, the order is `FLAGGED` with `Paid after the order ran out`. His money isn't lost and his order isn't forgotten (`PaymentsTest.aPaymentAfterTheOrderRanOutIsFlaggedNotLost`), but a person has to sort it out: give him tickets if there are places, or refund him. (If you add the query one day: asked too soon, Daraja answers an error, often with HTTP 500, saying the request is still being processed. That's normal, not a failure: ask again later.)

**The real sandbox, and why we don't use it in class.** Safaricom has a test version of Daraja, the **sandbox**, with pretend money. To use it you need:

1. A free account at Safaricom's developer portal (developer.safaricom.co.ke), and an app there. The app gives you a **consumer key** and **consumer secret**.
2. The sandbox's shared shortcode `174379` and its **passkey**, from the portal's test credentials.
3. A **public `https://` address** for your server, because Safaricom's computers must be able to call your callback. Your laptop on home Wi-Fi isn't reachable from the internet, so you'd run a **tunnel** (a program that gives your laptop a public address for as long as it runs).
4. A long random callback secret of your own.

Then you'd start the server with, on macOS / Linux:

```
export MPESA_MODE=sandbox
export MPESA_CONSUMER_KEY=your-consumer-key
export MPESA_CONSUMER_SECRET=your-consumer-secret
export MPESA_PASSKEY=the-sandbox-passkey
export MPESA_CALLBACK_BASE=https://your-tunnel-address
export MPESA_CALLBACK_SECRET=a-long-random-secret-of-your-own
```

(`MPESA_SHORTCODE` defaults to `174379` and `MPESA_TRANSACTION_TYPE` to `CustomerPayBillOnline`; a till uses `CustomerBuyGoodsOnline`.) In PowerShell, each line is `$env:MPESA_MODE="sandbox"` and so on. These are real secrets: set them in the terminal, never write them in a file in the repository. `MPESA_MODE=live` is the same with real money and Safaricom's approval.

The course doesn't do this, and you don't need to: it needs accounts, a tunnel and the internet, and practice mode runs the very same `parse` and `settle`. The tests run `DarajaClient` against `FakeDaraja`, a pretend Safaricom on your own laptop, so the real HTTP code is tested too.

## Steps

1. **Open the starter and run the tests.** `cd day-122-pay-with-mpesa/starter`, then `../../mvnw test`. `core` (181), `cli` (28) and `desktop` (41) pass. `sync server` fails with `Tests run: 89, Failures: 12, Errors: 26` and `BUILD FAILURE`. Every failure says `Not written yet: Day 122, step` and a number:
   - `StkPasswordTest` (3 of 6) and `DarajaClientTest` (11 of 12): `java.lang.UnsupportedOperationException: Not written yet: Day 122, step 2`. Some say `Expecting actual throwable to be an instance of: com.boxoffice.server.mpesa.DarajaException but was: java.lang.UnsupportedOperationException: Not written yet: Day 122, step 2`;
   - `StkCallbackTest` (7), `PaymentsTest` (11) and two `PayOnTheSiteTest` tests: `Not written yet: Day 122, step 4`;
   - `MpesaCallbackTest` (4): `Request failed unexpectedly: jakarta.servlet.ServletException: Request processing failed: java.lang.UnsupportedOperationException: Not written yet: Day 122, step 6`.

   To run only today's tests, which is quicker:

   ```
   ../../mvnw test -Dtest="StkPasswordTest,DarajaClientTest,StkCallbackTest,PaymentsTest,MpesaCallbackTest,PayOnTheSiteTest" -Dsurefire.failIfNoSpecifiedTests=false
   ```

   On Windows: `..\..\mvnw.cmd test "-Dtest=StkPasswordTest,DarajaClientTest,StkCallbackTest,PaymentsTest,MpesaCallbackTest,PayOnTheSiteTest" "-Dsurefire.failIfNoSpecifiedTests=false"`. You should see `Tests run: 49, Failures: 12, Errors: 26`. Use this command after each step below.

2. **The timestamp and the password.** Open `starter/server/src/main/java/com/boxoffice/server/mpesa/StkPassword.java`. In `timestamp`, replace the `throw` with `return STAMP.format(now.atOffset(EAT));`. In `password`, return the base64 of `shortcode + passkey + timestamp`, as UTF-8 bytes: `Base64.getEncoder().encodeToString((shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8))`.

   Run today's tests: `Tests run: 49, Failures: 8, Errors: 18`. `StkPasswordTest` passes (6). Two `DarajaClientTest` tests still fail: `oneTokenServesManyPayments` says `expected: 1 but was: 3`, and `tenPaymentsAtOnceShareOneTokenRequest` says `expected: 1 but was: 10`. Every push fetched its own token.

3. **Keep the token.** Open `DarajaClient.java` and find `token()`. Where the TODO marked `step 3` is, at the top: if `token` isn't `null` and `now.isBefore(tokenGoodUntil)`, `return token;`. At the end, after `token = ...`: `tokenGoodUntil = now.plusSeconds(seconds).minus(REFRESH_EARLY);`.

   Run today's tests: `DarajaClientTest` passes (12). `Tests run: 49, Failures: 6, Errors: 18`.

4. **Find a callback item by name.** Open `StkCallback.java` and find `item`. Replace the `throw` with a loop over `items.values()` (every item in the list, in whatever order). For each `item`, compare `item.path("Name").asString("").toLowerCase(Locale.ROOT)` with `name.toLowerCase(Locale.ROOT)`. When they match, take `JsonNode value = item.path("Value");` and return `Optional.empty()` if `value.isMissingNode() || value.isNull()`, otherwise `Optional.of(value)`. After the loop, `return Optional.empty();`.

   Run today's tests: `StkCallbackTest` passes (8). `Tests run: 49, Failures: 6, Errors: 10`. The `PaymentsTest` tests now say `Not written yet: Day 122, step 5`.

5. **Settle the order.** Open `Payments.java` and find `settle`. Below the TODO marked `step 5`, the order is found and locked, and `status` is what it was. Replace the `throw` with the rules from "Settling, in order" above:
   - `if (!callback.paid())`: not `AWAITING_PAYMENT` returns `Outcome.ALREADY_SETTLED`. Then `boolean cancelled = callback.resultCode() == StkCallback.CANCELLED_BY_USER;`, `mark(order, cancelled ? Order.Status.CANCELLED : Order.Status.FAILED, null, cancelled ? "You cancelled the M-Pesa payment" : "M-Pesa said: " + callback.resultDesc());` and return `Outcome.CANCELLED` or `Outcome.FAILED`.
   - Paid: `String receipt = callback.receipt().orElse(null);`. `status == Order.Status.EXPIRED`: `return flag(order, receipt, "Paid after the order ran out");`. Not `AWAITING_PAYMENT`: `return Outcome.ALREADY_SETTLED;`. `Optional<Long> paid = callback.amount();` empty: `return flag(order, receipt, "M-Pesa didn't say how much was paid");`. `!Money.ofKes(paid.get()).equals(order.amount())`: `return flag(order, receipt, "Paid " + Money.ofKes(paid.get()).format() + " for an order of " + order.amount().format());`.
   - Otherwise: `mark(order, Order.Status.PAID, receipt, "");`, `issueTickets(order);`, and `return Outcome.PAID;`. A `log.info` with the reference and receipt is a nice touch.

   Keep the checks in that order: the `EXPIRED` check must come before "not waiting", or a late payment would be `ALREADY_SETTLED` and quietly forgotten.

   Run today's tests: `PaymentsTest` (12) and `PayOnTheSiteTest` (6) pass. Only four `MpesaCallbackTest` tests fail, with `Not written yet: Day 122, step 6`.

6. **The callback that always says "Accepted".** Open `MpesaCallback.java` and find `receive`. Replace the `throw` with:
   - `String expected = settings.callbackSecret();`. If `expected` is `null` or blank, or `!Tokens.same(secret, expected)`: `log.warn("M-Pesa callback with the wrong secret: ignored");` and `return ACCEPTED;`.
   - `try { Payments.Outcome outcome = payments.settle(StkCallback.parse(body)); log.info("M-Pesa callback: {}", outcome); }`
   - `catch (IllegalArgumentException e)`: not a callback. `log.warn("M-Pesa callback we can't read: ignored ({})", e.getMessage());`.
   - `catch (RuntimeException e)`: our own failure. `log.error("M-Pesa callback failed on our side", e);`. (The `IllegalArgumentException` catch must come first: it's a kind of `RuntimeException`, and Java refuses a catch that can never be reached.)
   - `return ACCEPTED;` at the end, whatever happened.

7. **Run all the tests.** `../../mvnw test`. You should see 339 tests in all and `BUILD SUCCESS`.

8. **Order and pretend to pay.** Follow "Run it and test it" below: order in the browser, press "Pretend to pay", and see your tickets. Then do it again from `curl`.

9. **Stretch (not required): a test of your own.** In `PaymentsTest`, write a test that a payment of KES 4,500 for an order of KES 3,000 is flagged with `Paid KES 4,500 for an order of KES 3,000` (too much is as wrong as too little). Or, in `MpesaCallbackTest`, check that a callback with the right secret and a made-up `CheckoutRequestID` still gets `Accepted` and changes nothing. Copy the shape of the tests above. Your test isn't part of the course's tests, so keep it in your own Git.

## Run it and test it

From the day's `starter` folder:

```
cd day-122-pay-with-mpesa/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=PaymentsTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=PaymentsTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **181 tests** in `core`, **28** in `cli`, **41** in `desktop` and **89** in `sync server` (DarajaClient 12, DeviceLogin 9, GateToServer 3, HeldPlaces 5, LastTicket 5, MpesaCallback 5, PayOnTheSite 6, Payments 12, RaceToTheServer 1, StkCallback 8, StkPassword 6, SyncApi 5, TicketSite 12), **339** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] Box Office sync server ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**Build the jars.** `../../mvnw package -DskipTests`.

**Start the server, in practice mode.** `MPESA_MODE` is `stub` when you don't set it, so you only need the admin key as yesterday. To try the callback address with `curl` below, also give it a callback secret. Both values here are placeholders: pick your own, long and random (`openssl rand -base64 32` on macOS / Linux, or in PowerShell `[Convert]::ToBase64String((1..32 | % { Get-Random -Max 256 }))`).

macOS / Linux:

```
BOXOFFICE_ADMIN_KEY=change-me-to-a-long-random-secret-123 MPESA_CALLBACK_SECRET=practice-callback-secret-0123456789 java -jar server/target/boxoffice-server.jar
```

Windows PowerShell:

```
$env:BOXOFFICE_ADMIN_KEY="change-me-to-a-long-random-secret-123"
$env:MPESA_CALLBACK_SECRET="practice-callback-secret-0123456789"
```

```
java -jar server\target\boxoffice-server.jar
```

Windows cmd:

```
set BOXOFFICE_ADMIN_KEY=change-me-to-a-long-random-secret-123
set MPESA_CALLBACK_SECRET=practice-callback-secret-0123456789
```

```
java -jar server\target\boxoffice-server.jar
```

A new `sync-server-db.mv.db` gets four migrations (`Successfully applied 4 migrations to schema "PUBLIC", now at version v4`). If yesterday's database is in this folder, Flyway just adds V4. Wait for `Started SyncServerApplication`.

**In a browser.** Open http://localhost:8080/, and hold one Regular ticket for Achieng, `0700 111 222`. The page shows `Your order`, then `Your tickets are held for 10 minutes`, `Check your phone (0700 ••• 222) and enter your M-Pesa PIN to pay KES 1,500.`, a "No prompt? Send it again" button, and a box: `Practice mode: no real M-Pesa, no real money.` with "Pretend to pay" and "Pretend to cancel". Press "Pretend to pay". A moment later:

```
Paid. Enjoy the night!
M-Pesa receipt PRACTICE01
Show these at the gate:
W-REG-0001
```

Order again and press "Pretend to cancel": `Not paid`, `You cancelled the M-Pesa payment`, `Your places are free again. Order again`. Hold one more and leave it alone: within 11 minutes (10 minutes' hold, plus up to a minute for the reconciler), the server's log says `1 unpaid order(s) ran out; their places are back on sale`, and the page shows `Not paid` and `Not paid within 10 minutes`.

**With `curl`.** On macOS and Linux, in a second terminal. Otieno orders two Regular, the plain-form way (the server sends the STK push straight away; in practice mode it goes nowhere):

```
$ curl -i -X POST http://localhost:8080/events/kilele-2026/orders -d type=REG -d quantity=2 -d buyer=Otieno --data-urlencode "phone=0712 345 678"
HTTP/1.1 302
Location: http://localhost:8080/orders/2FYQ-N5WN
```

His order page (use your own reference), the payment piece:

```
$ curl http://localhost:8080/orders/2FYQ-N5WN
...
                <h3>Your tickets are held for 10 minutes</h3>
...
                <div hx-trigger="every 3s" hx-target="#payment" hx-swap="outerHTML" hx-get="/orders/2FYQ-N5WN/status?while=AWAITING_PAYMENT">
                    <p>
                        Check your phone (<span>0712 ••• 678</span>)
                        and enter your M-Pesa PIN to pay <strong>KES 3,000</strong>.
                    </p>
                </div>
                <button class="quiet" hx-target="#payment" hx-swap="outerHTML" hx-post="/orders/2FYQ-N5WN/pay">No prompt? Send it again</button>
                <div class="practice">
                    <p>Practice mode: no real M-Pesa, no real money.</p>
                    <button hx-target="#payment" hx-swap="outerHTML" hx-post="/practice/orders/2FYQ-N5WN/pay">Pretend to pay</button>
                    <button class="quiet" hx-target="#payment" hx-swap="outerHTML" hx-post="/practice/orders/2FYQ-N5WN/pay?result=1032">Pretend to cancel</button>
                </div>
```

What the page's every-3-seconds question gets while nothing has changed: `HX-Reswap: none`, "leave the page alone":

```
$ curl -i "http://localhost:8080/orders/2FYQ-N5WN/status?while=AWAITING_PAYMENT" -H "HX-Request: true"
HTTP/1.1 200
HX-Reswap: none
...
```

He pretends to pay:

```
$ curl -X POST http://localhost:8080/practice/orders/2FYQ-N5WN/pay -H "HX-Request: true"
<div id="payment">
                <h3>Paid. Enjoy the night!</h3>
                <p>M-Pesa receipt <strong>PRACTICE02</strong></p>
                <p>Show these at the gate:</p>
                <ul class="tickets">
                    <li>W-REG-0001</li>
                    <li>W-REG-0002</li>
                </ul>
        </div>
```

(The blank lines in between are left out here.) The server's log says `Order 2FYQ-N5WN paid, M-Pesa receipt PRACTICE02`. Press it again, as Safaricom redelivering: still two tickets, `W-REG-0001` and `W-REG-0002`, not four. The places: two Regular are now tickets, not held, so still 498:

```
$ curl -s http://localhost:8080/events/kilele-2026/places | grep left
                <span class="left">498 places left</span>
                <span class="left">50 places left</span>
```

Wanjiku orders a VIP and cancels:

```
$ curl -X POST "http://localhost:8080/practice/orders/AQKQ-K4RG/pay?result=1032" -H "HX-Request: true"
...
                <h3>Not paid</h3>
                <p>You cancelled the M-Pesa payment</p>
                <p>Your places are free again. <a href="/">Order again</a></p>
```

and VIP is back to `50 places left`.

**The callback address, as a stranger sees it.** A guessed secret, rubbish with the right secret, and a payment we never asked for all get exactly the same answer:

```
$ curl -X POST http://localhost:8080/mpesa/callback/guessed-secret -H "Content-Type: application/json" -d '{}'
{"ResultDesc":"Accepted","ResultCode":0}

$ curl -X POST http://localhost:8080/mpesa/callback/practice-callback-secret-0123456789 -H "Content-Type: application/json" -d 'hello'
{"ResultDesc":"Accepted","ResultCode":0}

$ curl -i -X POST http://localhost:8080/mpesa/callback/practice-callback-secret-0123456789 -H "Content-Type: application/json" -d '{"Body":{"stkCallback":{"CheckoutRequestID":"ws_CO_SOMEONE_ELSES","ResultCode":0,"ResultDesc":"ok"}}}'
HTTP/1.1 200
...
{"ResultDesc":"Accepted","ResultCode":0}
```

Only the server's log knows the difference:

```
WARN  ... M-Pesa callback with the wrong secret: ignored
WARN  ... M-Pesa callback we can't read: ignored (Not JSON)
WARN  ... M-Pesa callback for a payment we never asked for: ws_CO_SOMEONE_ELSES
INFO  ... M-Pesa callback: UNKNOWN_PAYMENT
```

And gate `W` is the site's now:

```
$ curl -X POST http://localhost:8080/admin/devices -H "X-Admin-Key: change-me-to-a-long-random-secret-123" -H "Content-Type: application/json" -d '{"gate":"W"}'
{"error":"W is the ticket site's. Pick another gate id"}
```

Stop the server with Ctrl+C. `sync-server-db.mv.db` is in `.gitignore`.

## When you're stuck

- **`java.lang.UnsupportedOperationException: Not written yet: Day 122, step N`**: that step's `throw` is still there. Do step N. A step-2 message in `DarajaClientTest`, or a step-4 message in `PaymentsTest`, is normal until those steps are done: they use the earlier pieces.
- **`theTimestampIsOnANairobiClock` says `expected: "20261114183005" but was: "20261114153005"`**, and `justBeforeMidnightInUtcItIsAlreadyTomorrowInNairobi` says `expected: "20261115011500" but was: "20261114221500"`: you formatted the time on UTC (or your laptop's clock). Use `now.atOffset(EAT)`.
- **`oneTokenServesManyPayments` says `expected: 1 but was: 3`, and `tenPaymentsAtOnceShareOneTokenRequest` says `expected: 1 but was: 10`**: every push fetched a new token. Do step 3, both parts: the check at the top, and setting `tokenGoodUntil` at the end.
- **`aNewTokenIsFetchedAMinuteBeforeTheOldOneRunsOut` says `expected: 2 but was: 1`**: you set `tokenGoodUntil` to the full 3599 seconds. Take `REFRESH_EARLY` off: `.minus(REFRESH_EARLY)`.
- **`theItemsAreFoundByNameInAnyOrderAndAnyCase` says `Expecting Optional to contain: 1500L but was empty.`**: you compared the names with `equals` as they are, and the test sends `amount`. Lowercase both sides with `toLowerCase(Locale.ROOT)`.
- **`theSameCallbackTwiceIssuesTheTicketsOnce` says `expected: ALREADY_SETTLED but was: PAID`**: the second callback paid the order again (and issued two more tickets). In the paid branch, return `Outcome.ALREADY_SETTLED` when the order isn't `AWAITING_PAYMENT`, after the `EXPIRED` check.
- **`aPaymentForTheRightAmountIssuesTheTickets` says `Expecting actual: [] to contain exactly (and in same order): ["W-REG-0001", "W-REG-0002"]`**: the order is `PAID`, but you forgot `issueTickets(order)`.
- **`rubbishGetsTheSameAnswerToo` says `Request failed unexpectedly: jakarta.servlet.ServletException: Request processing failed: java.lang.IllegalArgumentException: Not JSON`**: nothing catches the problem, so Spring answers a 500, and Safaricom would retry forever. Wrap `settle` in the `try` with both catches (step 6).
- **`aWrongSecretGetsTheSameAnswerAndChangesNothing` says `expected: AWAITING_PAYMENT but was: PAID`**: the secret check is missing, or you `return`ed only when the secret matched. Check it first, and return `ACCEPTED` without settling when it's wrong.
- **The compiler says the `IllegalArgumentException` `has already been caught`**: the `catch (RuntimeException e)` is above the `catch (IllegalArgumentException e)`. Put the narrower one first.
- **The server won't start: `Set MPESA_CONSUMER_KEY to use M-Pesa (or MPESA_MODE=stub to practise)`**: you (or an old terminal) set `MPESA_MODE=sandbox`. Remove it, or set it to `stub`, to practise. (`unset MPESA_MODE` on macOS / Linux, `Remove-Item Env:MPESA_MODE` in PowerShell.)
- **No "Pretend to pay" buttons on the order page**: the server isn't in practice mode, so `PracticePayments` doesn't exist. Start it without `MPESA_MODE`, or with `MPESA_MODE=stub`.
- **The `curl` callbacks with the right secret do nothing, and the log says `M-Pesa callback with the wrong secret: ignored`**: the server was started without `MPESA_CALLBACK_SECRET`, or with a different one. With no secret set, it accepts no callbacks at all, on purpose. "Pretend to pay" doesn't need it.
- **`cli`, `desktop` or `sync server` are `SKIPPED`**: an earlier module failed. Use the `-Dtest=...` command from step 1 for today's tests. If `desktop` failed with a `TimeoutException` in `SellScreenTest`, that's JavaFX starting slowly on a busy computer, not your code: close some programs and run again.
- **`Web server failed to start. Port 8080 was already in use.`**: yesterday's server is still running. Stop it with Ctrl+C.
- **Still stuck?** Open `solution/server/src/main/java/com/boxoffice/server/Payments.java` (or `MpesaCallback.java`, or `mpesa/StkPassword.java`, `mpesa/DarajaClient.java`, `mpesa/StkCallback.java`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say how an STK push and its callback fit together, and what joins them up
- [ ] Can say why the token is kept, and why it's refreshed a minute early
- [ ] Can say why the timestamp is on a Nairobi clock, and why the password is as secret as the passkey
- [ ] Can say why amounts are refused, not rounded
- [ ] Can say why a second callback changes nothing, and why a wrong amount is flagged
- [ ] Can say why the callback always answers "Accepted", and what the secret in its address is for
- [ ] Can say what happens to a payment that arrives after its order expired, and why
- [ ] Can list what the real sandbox needs
- [ ] All 339 tests pass
- [ ] Ordered on the site, pretended to pay, and saw `W-REG-...` tickets
- [ ] Committed your work to Git (not `sync-server-db.mv.db`, and never an admin key, consumer key, consumer secret, passkey or callback secret)

## Next

Fans can now order and pay from anywhere, so the server is on the internet, holding people's names, phone numbers and money. Tomorrow is a security pass: going through the server the way an attacker would, and closing what they'd find.
