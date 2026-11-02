# Day 121: Public ticket site

> 14:00, the Monday before. Otieno is on a matatu on Ngong Road. He opens the Kilele Nights page on his phone, types his name and number, and holds two Regular tickets before his stop.

Watch the video: *(not recorded yet)*

## The brief

Until today, every ticket was sold at a gate. Fans had to queue at Ngong Racecourse with cash or a phone, and the gate laptop did the rest. Today fans can order **before the night**, from any browser.

The sync server from Days 119 and 120 already runs on the organiser's laptop. Now it also serves a **public ticket site**: real web pages, made on the server, that any phone can open.

```
http://localhost:8080/            -> goes to the Kilele Nights page
http://localhost:8080/events/kilele-2026
http://localhost:8080/orders/8CKS-H2GA
```

The event page shows each ticket type with its price and the places left, and a form: ticket type, how many, the name on the tickets and an M-Pesa number. Press "Hold my tickets" and the server keeps an **order** with a reference like `8CKS-H2GA`:

```
Your tickets are held
Order 8CKS-H2GA
2 × REG for Otieno: KES 3,000
We'll ask 0712 ••• 678 to pay with M-Pesa.
```

Paying comes tomorrow (Day 122). Today the order **holds** its places: while Otieno is paying, nobody else can have them, online or at a gate.

The pages are written with **Thymeleaf** (HTML templates filled in on the server) and **htmx** (a small script that updates part of a page without reloading it). The form works without any JavaScript at all. htmx only makes it nicer.

You write four pieces: counting held places, checking the form, answering htmx and a plain browser differently, and making the "places left" box refresh itself.

## What you'll learn

- What a **server-rendered** page is, and how a Thymeleaf template becomes HTML
- The difference between `@Controller` (returns a page) and `@RestController` (returns JSON)
- What a **fragment** is, and how `"event :: places"` answers one piece of a page
- What **htmx** does with `hx-get`, `hx-post`, `hx-trigger`, `hx-target` and `hx-swap`, and how the `HX-Request` header tells the server who's asking
- What **progressive enhancement** means: the page works with no JavaScript, and htmx makes it better when it's there
- Why a plain form post gets a **redirect** (post, redirect, get), and a wrong form gets **422**
- Why orders still waiting for payment count as taken places, for the site and for the gates
- Why order references are random, and why they skip 0, O, 1, I and L
- Why a method a template calls must be `public`

## New words

- **Server-rendered page**: HTML made by the server for each request, with the data already in it. The browser just shows it.
- **Template**: an HTML file with blanks. The server fills them in. Ours are in `server/src/main/resources/templates`.
- **Thymeleaf**: the template engine we use. Its blanks are attributes that start with `th:`, like `th:text="${order.buyer}"`.
- **Model**: the box of values a controller hands to the template: `model.addAttribute("order", order)`.
- **Fragment**: a named piece of a template, marked with `th:fragment="places"`. The server can answer just that piece.
- **htmx**: a small JavaScript library. It reads `hx-...` attributes in the HTML and does the requests for you.
- **Swap**: htmx putting the server's answer into the page in place of something that's there.
- **Progressive enhancement**: build the page so it works with plain HTML first, then add JavaScript that makes it better. If the script doesn't load, nothing breaks.
- **Webjar**: a JavaScript library packed as a Maven jar. Spring Boot serves the files inside it under `/webjars/...`.
- **Order**: a fan's request for tickets on the site. It holds places while the fan pays.
- **Held place**: a place an order is keeping. Nobody else can buy it while the order waits.
- **Reference**: the code that names an order, like `8CKS-H2GA`. The fan quotes it if anything goes wrong.
- **Redirect**: an answer that says "go and get this other address instead" (HTTP 302, with a `Location` header).
- **422 Unprocessable Content**: "your form arrived, and what's in it can't be used".
- **SpEL**: Spring Expression Language, what Thymeleaf uses inside `${...}`.

## What's already written

From Days 101–120: the gate app and the sync server, with device login and the oversold check. New today, already done:

In `server/pom.xml`:

- `spring-boot-starter-thymeleaf`, for the templates.
- `org.webjars.npm:htmx.org:2.0.4`. Spring Boot serves it at `/webjars/htmx.org/2.0.4/dist/htmx.min.js`, from our own server, not a CDN (a server somewhere else on the internet), so the page works on the venue Wi-Fi too.
- `core` is no longer test-only: the site uses the same `Money`, `PhoneNumbers` and `NairobiTime` as the gate.
- `<maven.compiler.parameters>true</maven.compiler.parameters>`: keeps parameter names in the compiled code, so Spring can match `@PathVariable String id` to `{id}` in the address without being told.

On the server:

- `V3__events_and_orders.sql`: an `events` table with Kilele Nights in it (name, venue, start and end), a `name` and `price_cents` for each ticket type (Regular KES 1,500, VIP KES 5,000), and an `orders` table. Read it: it's short.
- `Places.java`: `lockCapacity` (the `select ... for update` from Day 120, moved here), `taken` (tickets sold plus places held) and `left` (never below 0). `Sales.receive` now uses it too, so the gates and the site count places the same way.
- `Catalogue.java`: the event and each `Offer` (code, name, price, places left) for the page.
- `OrderForm.java`: the form as the fan filled it in. Any field can be missing or nonsense.
- `Order.java`: an order, with `maskedPhone()` (`0712 ••• 678`).
- `References.java`: random references like `8CKS-H2GA`.
- `Orders.java`: `place(eventId, form)` returns `Placed.Done` with the order, or `Placed.Refused` with a message for each field that's wrong. After the form check, it locks the type's row, checks there are enough places left, and saves the order as `AWAITING_PAYMENT`. `find(reference)` reads one back. At most 6 tickets per order (`MOST_PER_ORDER`).
- `TicketSite.java`: the controller. `/` redirects to `/events/kilele-2026`. `/events/{id}` is the whole page. `/events/{id}/places` is the "places left" piece alone. `/orders/{reference}` is an order's own page. Unknown events and orders get `not-found.html` with a 404.
- Templates: `event.html` (the event page, with the `order-form` fragment), `order.html` (with the `summary` fragment) and `not-found.html`. `static/site.css` styles them.
- Tests: `HeldPlacesTest` (5) and `TicketSiteTest` (12). `ServerTestSupport.emptyTheServer` now empties `orders` too.

New, for you to finish: the held places in `Places.taken` (step 2), the form check in `Orders.place` (step 3), the answer to a posted order in `TicketSite.order` (step 4), and the "places left" fragment in `event.html` (step 5). The TODOs are labelled with their step numbers.

## What you'll use

**A page made on the server.** Since Day 119 the server answered JSON, for the gates. A fan's phone wants a page. A `@Controller` method (not `@RestController`) returns the **name of a template**, and Thymeleaf fills it in with what's in the Model:

```java
@GetMapping("/orders/{reference}")
String orderPage(@PathVariable String reference, Model model, HttpServletResponse response) {
    return orders.find(reference).map(order -> {
        model.addAttribute("order", order);
        return "order";                       // templates/order.html
    }).orElseGet(() -> notFound(response, model, "No order " + reference));
}
```

In the template, `th:` attributes say what to put where:

```html
<p class="reference">Order <strong th:text="${order.reference}">K7MP-Q3XW</strong></p>
```

The browser gets `<strong>8CKS-H2GA</strong>`. The `th:` attributes never reach it. The `K7MP-Q3XW` inside is only a sample, so the file still looks right when you open it on its own.

**Text, never code.** `th:text` always **escapes** what it prints. A buyer who types `<script>alert(1)</script>` as their name shows up as those letters, not as a script that runs in the next person's browser. `TicketSiteTest.whatAFanTypesIsShownAsTextNeverRunAsCode` checks it. Never print what a fan typed with `th:utext`, which doesn't escape.

**Templates can only call public methods.** Inside `${...}` is SpEL. It can read a record's fields (`${offer.left}`) and call its methods (`${offer.soldOut()}`), but only **public** ones. `Catalogue` and `Order` are package-private classes, so their methods are too unless you say `public`. That's why `Offer.soldOut()` and `Order.maskedPhone()` are written `public boolean soldOut()`. Forget it, and the page fails with `Method soldOut() cannot be found on type com.boxoffice.server.Catalogue$Offer` (see "When you're stuck").

**Fragments: a piece of a page.** `th:fragment="places"` names a piece of `event.html`. A controller that returns `"event :: places"` answers **that piece alone**, not the whole page. The order form is the fragment `event :: order-form`, and the order summary is `order :: summary`.

**htmx in five attributes.** The page loads one script, from our own server:

```html
<script src="/webjars/htmx.org/2.0.4/dist/htmx.min.js" defer></script>
```

Then `hx-` attributes do the work:

- `hx-get="/events/kilele-2026/places"`: ask this address with GET.
- `hx-post="..."`: send this form with POST, in the background.
- `hx-trigger="every 15s"`: when to ask. (A form's default trigger is "submitted".)
- `hx-target="this"`: where the answer goes. `this` is the element itself.
- `hx-swap="outerHTML"`: replace the **whole** element, not just what's inside it. The new piece comes with its own `hx-` attributes, so the asking goes on.

Every request htmx makes carries the header `HX-Request: true`. That's how the server knows it should answer a piece, not a whole page.

**The places box refreshes itself.** Kamau sells VIP tickets at a gate, and other fans order online. A page Otieno opened ten minutes ago would show old numbers. So the "places left" section asks for itself again every 15 seconds:

```html
<section id="places" th:fragment="places"
         th:attr="hx-get=@{/events/{id}/places(id=${event.id})}"
         hx-trigger="every 15s" hx-swap="outerHTML">
```

`th:attr="hx-get=@{...}"` makes the `hx-get` attribute with an address Thymeleaf builds: `@{/events/{id}/places(id=${event.id})}` becomes `/events/kilele-2026/places`. (Thymeleaf has no `th:hx-get`, so `th:attr` writes it.)

**Progressive enhancement: the form works without JavaScript.** Look at the form tag:

```html
<form id="order" th:fragment="order-form" method="post"
      th:action="@{/events/{id}/orders(id=${event.id})}"
      th:attr="hx-post=@{/events/{id}/orders(id=${event.id})}"
      hx-target="this" hx-swap="outerHTML">
```

It has **both** a plain `method="post"` and `action`, and an `hx-post`. With htmx loaded, htmx catches the submit, posts in the background, and swaps the answer in where the form was. The page doesn't reload, and the "places left" box stays where it is. Without JavaScript (an old phone, a script blocked, htmx failed to download on bad Wi-Fi), the browser posts the form the old way and gets a whole page back. Either way, Otieno gets his tickets held. That's progressive enhancement: the plain HTML works on its own, and htmx is a bonus on top.

**One address, two answers.** So `POST /events/{id}/orders` has four cases:

| | htmx (`HX-Request: true`) | plain browser |
|---|---|---|
| order placed | the `order :: summary` piece, 200 | redirect to `/orders/{ref}` |
| form is wrong | the `event :: order-form` piece with the problems, 200 | the whole `event` page with the problems, 422 |

**Post, redirect, get.** After a plain form post, the server doesn't answer the order page directly. It answers `302` with `Location: /orders/8CKS-H2GA`, and the browser fetches that. If Otieno then presses reload, the browser reloads the **order page** (a GET), not the post. Without the redirect, reload would post the form again and hold a second order. In Spring, returning `"redirect:/orders/" + reference` does it.

**422 for a plain browser, 200 for htmx.** A wrong form is the fan's mistake, and 422 says so honestly. But htmx, by default, only swaps in answers that succeeded (2xx). If we answered htmx with 422, the problems would never appear on the page. So htmx gets the form back with 200.

**A sealed answer, and a switch.** `Orders.place` returns a `Placed`, which is a `sealed interface` with two records: `Done` and `Refused`. "Sealed" means there are no other kinds, so a `switch` with a `case` for each needs no `default`, and Java checks you didn't forget one:

```java
return switch (orders.place(id, form)) {
    case Orders.Placed.Done done -> { ... yield "..."; }
    case Orders.Placed.Refused refused -> { ... yield "..."; }
};
```

Inside a `{ ... }` block of a switch expression, `yield` gives the switch its value (like `return` for the switch).

**Every problem at once.** The form check puts each problem in a `Map` (field name to message), and only returns after checking **every** field. A fan who left the name empty and typed `12345` as their number sees both messages together, not one at a time. `LinkedHashMap` keeps them in the order you added them.

**Held places count as taken, everywhere.** An order `AWAITING_PAYMENT` holds its places: if Otieno holds 2 Regular, there are 498 left, for the site **and** for the gates. `Places.taken` is what both `Orders.place` and `Sales.receive` call, so a gate syncing the 499th Regular gets the same `409` it got on Day 120. Add up the **quantity** of each order, not the number of orders (one order can hold 6 places). `sum()` of no rows is `null` in SQL, so write `coalesce(sum(quantity), 0)`: "the sum, or 0 if there's nothing".

**The lock from Day 120, again.** Ten fans pressing "Hold my tickets" at the same moment for the last VIP places is the same race as two gates. `Orders.place` calls `places.lockCapacity` first, which runs `select ... for update`, so the second order waits until the first has saved. `HeldPlacesTest.tenFansRushingForFiftyVipPlacesNeverGetMoreThanFifty` sends ten orders of 6 at once: 8 get places (48), and nobody gets a 51st.

**Random references.** If orders were numbered `W-0001`, `W-0002`, anyone could read the next fan's order page (with their name and part of their number) by adding one. `References.next()` picks 8 characters with `SecureRandom`: about a trillion possibilities, so guessing one is hopeless. It leaves out `0`/`O` and `1`/`I`/`L`, so a fan reading it out over the phone can't mix them up.

**The phone number, the gate's way.** `PhoneNumbers.normalise` from Day 114 turns `0712 345 678`, `+254 712 345 678` and `254712345678` into `254712345678`. The order keeps that. The page shows `maskedPhone()`: `0712 ••• 678`, enough for the fan to recognise their number, not enough for someone looking over their shoulder to copy it.

## Steps

1. **Open the starter and run the tests.** `cd day-121-public-ticket-site/starter`, then `../../mvnw test`. `core` (181), `cli` (28) and `desktop` (41) pass. `sync server` fails with `Tests run: 40, Failures: 7, Errors: 4` and `BUILD FAILURE`:
   - four `HeldPlacesTest` tests and five `TicketSiteTest` tests end with `java.lang.UnsupportedOperationException: Not written yet: Day 121, step 3`;
   - `TicketSiteTest.aSoldOutTypeSaysSoAndCantBeOrdered` says `Expecting actual: "<!DOCTYPE html> ...` and ends `to contain: "Sold out"`: an order holding all 50 VIP places doesn't count yet (step 2);
   - `TicketSiteTest.placesHeldByAnOrderShowAsTaken` says `Expecting actual: "" to contain: ["48 places left", "500 places left", "hx-trigger="every 15s""]`.

   To run only today's tests, which is quicker:

   ```
   ../../mvnw test -Dtest="HeldPlacesTest,TicketSiteTest" -Dsurefire.failIfNoSpecifiedTests=false
   ```

   On Windows: `..\..\mvnw.cmd test "-Dtest=HeldPlacesTest,TicketSiteTest" "-Dsurefire.failIfNoSpecifiedTests=false"`. You should see `Tests run: 17, Failures: 7, Errors: 4`. Use this command after each step below.

2. **Count the held places.** Open `starter/server/src/main/java/com/boxoffice/server/Places.java` and find `taken`. Where the TODO marked `step 2` is, replace `int held = 0;` with a query, like the `sold` one above it:

   ```java
   int held = db.sql("select coalesce(sum(quantity), 0) from orders"
                   + " where event_id = ? and type_code = ? and status = 'AWAITING_PAYMENT'")
           .params(eventId, type)
           .query(Integer.class)
           .single();
   ```

   Mind the space before `where`. Run today's tests. The counts don't move yet: nearly every test places an order first, and `Orders.place` still throws (step 3). But `aSoldOutTypeSaysSoAndCantBeOrdered` now gets past the event page (it says `Sold out`), and fails later, at its order, with `Not written yet: Day 121, step 3`.

3. **Check the form.** Open `Orders.java` and find `place`. Replace the `if (true) { throw ... }` and the three lines under it with a check of each field. Put a message in `problems` for each one that's wrong:
   - `buyer`: `form.buyer()` can be `null`, so start with `String buyer = form.buyer() == null ? "" : form.buyer().strip();`. Empty: `"Type the name for the tickets"`. Longer than 60: `"Keep the name under 60 letters"`.
   - `phone`: `Optional<String> phone = PhoneNumbers.normalise(form.phone());`. Empty: `"Type a Kenyan mobile number, like 0712 345 678"`.
   - `quantity`: `Integer quantity = form.quantity();`. `null`, below 1 or above `MOST_PER_ORDER`: `"Choose 1 to " + MOST_PER_ORDER + " tickets"`.

   Then, after all three: if `problems` isn't empty, `return new Placed.Refused(problems);`. The code below uses `buyer`, `phone` and `quantity`, so keep those names.

   Run today's tests. `HeldPlacesTest` passes (5). Seven `TicketSiteTest` tests still fail; six of them now say `Not written yet: Day 121, step 4`.

4. **Answer htmx with a piece, and a browser with a page.** Open `TicketSite.java` and find `order`. Replace the `Orders.Placed placed = ...` line and the `throw` with a `switch` over `orders.place(id, form)`:
   - `case Orders.Placed.Done done`: from a plain browser, `yield "redirect:/orders/" + done.order().reference();`. From htmx, `model.addAttribute("order", done.order());` and `yield "order :: summary";`.
   - `case Orders.Placed.Refused refused`: put `event.get()` as `"event"`, `form` as `"form"` and `refused.problems()` as `"problems"` in the model. (`form` goes back so the fan sees what they typed and only fixes what's wrong.) From a plain browser, also put `"startsAt"` (copy the line from the `event` method above), `response.setStatus(422);` and `yield "event";`. From htmx, `yield "event :: order-form";`.

   `fromHtmx` is already worked out above the TODO. Run today's tests. Only `placesHeldByAnOrderShowAsTaken` still fails, with `Expecting actual: "" to contain: [...]`: the server asked Thymeleaf for `event :: places`, and there's no fragment of that name yet, so the answer is empty.

5. **Make the places box refresh itself.** Open `starter/server/src/main/resources/templates/event.html`. Find the TODO marked `step 5` and add four attributes to `<section id="places">`:

   ```html
   <section id="places" th:fragment="places"
            th:attr="hx-get=@{/events/{id}/places(id=${event.id})}"
            hx-trigger="every 15s" hx-swap="outerHTML">
   ```

   You can delete the TODO comment, or replace it with a line of your own saying what the section does.

6. **Run all the tests.** `../../mvnw test`. You should see 290 tests in all and `BUILD SUCCESS`.

7. **Order tickets in a browser.** Follow "Run it and test it" below. Try it once with JavaScript, and once pretending you have none, with `curl`.

8. **Stretch (not required): a test of your own.** In `TicketSiteTest`, write a test that a name of 61 letters is refused with `Keep the name under 60 letters` (`"x".repeat(61)` makes one). Or one that a quantity of `abc` doesn't crash the page. Copy the shape of `aBadFormComesBackWithWhatToFixAndWhatWasTyped`. Your test isn't part of the course's tests, so keep it in your own Git.

## Run it and test it

From the day's `starter` folder:

```
cd day-121-public-ticket-site/starter
```

```
../../mvnw test                  # macOS / Linux
..\..\mvnw.cmd test              # Windows
```

One test class only:

```
../../mvnw test -Dtest=TicketSiteTest -Dsurefire.failIfNoSpecifiedTests=false
..\..\mvnw.cmd test "-Dtest=TicketSiteTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Success looks like this, with **181 tests** in `core`, **28** in `cli`, **41** in `desktop` and **40** in `sync server` (DeviceLogin 9, GateToServer 3, HeldPlaces 5, LastTicket 5, RaceToTheServer 1, SyncApi 5, TicketSite 12), **290** in all:

```
[INFO] Box Office core .................................... SUCCESS
[INFO] Box Office CLI ..................................... SUCCESS
[INFO] Box Office desktop ................................. SUCCESS
[INFO] Box Office sync server ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**Build the jars.** `../../mvnw package -DskipTests` (you've just run the tests).

**Start the server.** As on Day 119. The site itself needs no key, but the server still registers gates, so give it an admin key. `change-me-to-a-long-random-secret-123` is a placeholder: pick your own, long and random. To make one: `openssl rand -base64 32` (macOS / Linux), or in PowerShell `[Convert]::ToBase64String((1..32 | % { Get-Random -Max 256 }))`.

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

Windows cmd:

```
set BOXOFFICE_ADMIN_KEY=change-me-to-a-long-random-secret-123
```

```
java -jar server\target\boxoffice-server.jar
```

Started from today's `starter` folder, it makes a new `sync-server-db.mv.db`, and Flyway says `Successfully applied 3 migrations to schema "PUBLIC", now at version v3`. Wait for `Started SyncServerApplication` (on a busy laptop it can take most of a minute).

**In a browser.** Open http://localhost:8080/. It jumps to the Kilele Nights page: Regular KES 1,500 with 500 places left, VIP KES 5,000 with 50. Then:

- Type a name, leave the M-Pesa number empty, and press "Hold my tickets". `Type a Kenyan mobile number, like 0712 345 678` appears under the box, and the address bar doesn't change: htmx swapped in the form with its problem, and kept what you typed.
- Type a number, like `0711 222 333`, and press again. The form is replaced by `Your tickets are held`, `Order JWSD-ZEJM` (yours will be different), `1 × REG for Kamau: KES 1,500`.
- Look at "places left": it still says 498. Wait up to 15 seconds, and it changes to 497 by itself. That's the `every 15s`.
- Copy your order reference and open `http://localhost:8080/orders/` followed by it. That's the order's own page. Anyone who knows the reference can see it, which is why it's random.

To try the page **without** JavaScript, turn JavaScript off in your browser's settings for `localhost` and order again. The page reloads instead, and you land on `/orders/...`. Turn it back on afterwards.

**With `curl`.** On macOS and Linux you can be the browser yourself, in a second terminal. (Windows has `curl.exe` too, but its quoting differs between PowerShell and cmd; there, use the browser.) The home page is a redirect:

```
$ curl -i http://localhost:8080/
HTTP/1.1 302
Location: http://localhost:8080/events/kilele-2026
Content-Language: en-US
Content-Length: 0
```

The "places left" piece, the way htmx asks for it every 15 seconds. It's just the `<section>`, with no `<html>` around it:

```
$ curl http://localhost:8080/events/kilele-2026/places -H "HX-Request: true"
<section id="places" hx-trigger="every 15s" hx-swap="outerHTML" hx-get="/events/kilele-2026/places">
        <h2>Tickets</h2>
        <ul class="offers">
            <li>
                <span class="name">Regular</span>
                <span class="price">KES 1,500</span>
                <span class="left">500 places left</span>
            </li>
            <li>
                <span class="name">VIP</span>
                <span class="price">KES 5,000</span>
                <span class="left">50 places left</span>
            </li>
        </ul>
    </section>
```

An order, as htmx sends it. `-d` sends form fields, exactly like the browser's form. The answer is the summary piece:

```
$ curl -X POST http://localhost:8080/events/kilele-2026/orders -H "HX-Request: true" -d type=REG -d quantity=2 -d buyer=Otieno --data-urlencode "phone=0712 345 678"
<section id="order" class="summary">
        <h2>Your tickets are held</h2>
        <p class="reference">Order <strong>8CKS-H2GA</strong></p>
        <p>
            <span>2</span> ×
            <span>REG</span> for
            <span>Otieno</span>:
            <strong>KES 3,000</strong>
        </p>
        <p class="phone">We'll ask <span>0712 ••• 678</span> to pay with M-Pesa.</p>
        <p class="note">Keep the order reference. Nobody can see this order without it.</p>
    </section>
```

The same order without `HX-Request`, as a browser with no JavaScript sends it. The answer is a redirect to the order's page (post, redirect, get):

```
$ curl -i -X POST http://localhost:8080/events/kilele-2026/orders -d type=VIP -d quantity=1 -d buyer=Wanjiku --data-urlencode "phone=+254 722 000 111"
HTTP/1.1 302
Location: http://localhost:8080/orders/9PB6-2CZ4
Content-Language: en-US
Content-Length: 0
```

`curl http://localhost:8080/orders/9PB6-2CZ4` (with your reference) shows `1 × VIP for Wanjiku: KES 5,000` and `We'll ask 0722 ••• 111 to pay with M-Pesa.`: `+254 722 000 111` was understood.

A wrong form, from a plain browser: the whole page again, with `422` and every problem at once:

```
$ curl -i -X POST http://localhost:8080/events/kilele-2026/orders -d type=REG -d quantity=9 -d "buyer=  " -d phone=12345
HTTP/1.1 422
...
<html lang="en">
...
        <p class="problem">Choose 1 to 6 tickets</p>
...
        <p class="problem">Type the name for the tickets</p>
...
        <p class="problem">Type a Kenyan mobile number, like 0712 345 678</p>
```

The same with `-H "HX-Request: true"` answers `200` and only the `<form id="order" ...>` piece, with the same three problems.

The held places count, for the site and the gates:

```
$ curl -s http://localhost:8080/events/kilele-2026/places | grep left
                <span class="left">498 places left</span>
                <span class="left">49 places left</span>
```

And an order nobody placed:

```
$ curl -i http://localhost:8080/orders/NOPE-NOPE
HTTP/1.1 404
...
    <p>No order NOPE-NOPE</p>
```

Stop the server with Ctrl+C. `sync-server-db.mv.db` is in `.gitignore`. Delete it to start with all 550 places free again.

## When you're stuck

- **`java.lang.UnsupportedOperationException: Not written yet: Day 121, step 3`**: `Orders.place` still has its `throw`. Do step 3. Every test that places an order needs it.
- **`aSoldOutTypeSaysSoAndCantBeOrdered` says `Expecting actual: "<!DOCTYPE html> ...` and ends `to contain: "Sold out"`**: `Places.taken` doesn't count held places yet. Do step 2.
- **`aPlaceAFanIsPayingForIsTakenForTheGatesToo` says `[HTTP status code] expected: 409 but was: 201`**: the same. A gate took a VIP place an order was holding. Do step 2, and check the status in your SQL is `'AWAITING_PAYMENT'`, in single quotes.
- **`tenFansRushingForFiftyVipPlacesNeverGetMoreThanFifty` says `expected: 8 but was: 10`**: your `held` counts orders, not places. Use `sum(quantity)`, not `count(*)`.
- **Almost every test fails with `org.springframework.dao.TypeMismatchDataAccessException: Result value is null but no null value expected`**: `sum(quantity)` of no orders is `null`, and `.query(Integer.class).single()` won't take `null`. Write `coalesce(sum(quantity), 0)`.
- **`java.lang.UnsupportedOperationException: Not written yet: Day 121, step 4`**: `TicketSite.order` still has its `throw`. Do step 4.
- **`anOrderWithoutHtmxRedirectsToItsOwnPage` says `[HTTP status series] expected: REDIRECTION but was: SUCCESSFUL`, and `anOrderFromHtmxGetsJustTheSummaryBack` says `[HTTP status code] expected: 200 but was: 302`**: the two branches are swapped (check the `!` in `if (!fromHtmx)`). A plain browser gets the redirect; htmx gets `order :: summary`.
- **`aBadFormComesBackWithWhatToFixAndWhatWasTyped` says `Expecting actual: "<form id="order" ...` and lists messages it `could not find`**: you returned as soon as one field was wrong. Check all three, then return.
- **`withoutHtmxABadFormIsTheWholePageAgainWith422` says `[HTTP status code] expected: 422 but was: 200`**: add `response.setStatus(422)` in the plain-browser branch.
- **On the page, a wrong form shows no problems at all with htmx**: you answered htmx with 422 too. htmx only swaps in 2xx answers. Keep 422 for the plain browser.
- **`Exception evaluating SpringEL expression: "offer.soldOut()" (template: "event" - line 33, col 51)`, caused by `EL1004E: Method call: Method soldOut() cannot be found on type com.boxoffice.server.Catalogue$Offer`**: the method isn't `public`. Templates can only call public methods, even though the method is right there. Write `public boolean soldOut()`. The same goes for any method you add and call from a template, like `maskedPhone()`.
- **`placesHeldByAnOrderShowAsTaken` says `Expecting actual: "" to contain: ["48 places left", ...]`**: there's no `th:fragment="places"` in `event.html`, so `event :: places` comes back empty (and with status 200, which is why it's easy to miss). Do step 5. Check the spelling: `places`, the same as in `TicketSite.places`.
- **`placesHeldByAnOrderShowAsTaken` says it could not find `hx-trigger="every 15s"`**: the fragment is there, but the `hx-trigger` attribute is missing or spelled differently.
- **The places box doesn't change in the browser**: wait the full 15 seconds. Then open the browser's developer tools (F12), Network tab, and reload. A 404 for `htmx.min.js` means the `<script>` line is wrong: the address must be exactly `/webjars/htmx.org/2.0.4/dist/htmx.min.js` (`TicketSiteTest.htmxIsServedByOurOwnServer` checks the server serves it there).
- **`Name for argument of type [java.lang.String] not specified, and parameter name information not available via reflection`**: the server was compiled without `-parameters`. Keep `<maven.compiler.parameters>true</maven.compiler.parameters>` in `server/pom.xml`, then `../../mvnw clean package`.
- **`cli`, `desktop` or `sync server` are `SKIPPED`**: an earlier module failed. Use the `-Dtest=...` command from step 1 for today's tests. If it's `desktop` that failed with a `TimeoutException` in `SellScreenTest`, nothing you wrote today touches it: JavaFX was slow to start on a busy computer. Close some programs and run again.
- **`Web server failed to start. Port 8080 was already in use.`**: yesterday's server is still running somewhere. Stop it with Ctrl+C, or add `--server.port=9090` and use `9090` in the addresses.
- **Still stuck?** Open `solution/server/src/main/java/com/boxoffice/server/Orders.java` (or `Places.java`, `TicketSite.java`, `solution/server/src/main/resources/templates/event.html`), read it once, close it, then write your own from memory.

## Checklist

- [ ] Can say what a server-rendered page is, and what Thymeleaf does with `th:text`
- [ ] Can say what a fragment is, and what `"event :: places"` answers
- [ ] Can say what `hx-get`, `hx-post`, `hx-trigger`, `hx-target` and `hx-swap` do, and what `HX-Request: true` tells the server
- [ ] Can explain progressive enhancement with today's order form
- [ ] Can say why a plain post gets a redirect, and why htmx gets 200 for a wrong form when a browser gets 422
- [ ] Can say why held places count for the gates too
- [ ] Can say why a method a template calls must be public
- [ ] All 290 tests pass
- [ ] Held tickets in a browser, saw the places box change by itself, and got a fragment back from `curl` with `HX-Request: true`
- [ ] Committed your work to Git (not `sync-server-db.mv.db`, and never your admin key)

## Next

Otieno's tickets are held, but he hasn't paid, and nothing stops him holding them forever. Tomorrow the site asks M-Pesa to send a "pay KES 3,000" prompt to his phone, listens for Safaricom's answer, turns paid orders into real tickets, and gives back the places of orders nobody paid for.
