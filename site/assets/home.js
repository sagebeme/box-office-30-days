// The landing page: welcome back, the gate replay and its receipts, and the route of phases.
(function () {
  var days = window.BOX_DAYS, phases = window.BOX_PHASES, P = window.BOX_PROGRESS;
  var state = P.load();
  var next = P.nextDay(state, days);
  var first = days[0], last = days[days.length - 1];
  var $ = function (id) { return document.getElementById(id); };

  // ----- welcome back -----
  if (next) {
    var done = days.filter(function (d) { return P.isDone(state, d); }).length;
    ["cta", "cta-2"].forEach(function (id) {
      $(id).href = P.dayUrl(next.day);
      $(id).textContent = "Continue with Day " + next.day;
    });
    $("resume").textContent = done === days.length
      ? "All " + days.length + " days done. Box Office 1.0 is yours."
      : done + " of " + days.length + " days done. Up next: Day " + next.day + ", " + next.title + ".";
    $("resume").hidden = false;
    $("cta-title").textContent = "Back to the gate at Day " + next.day;
    $("cta-text").textContent = next.title + ". About 2 to 3 hours.";
  }

  // ----- the receipt: Receipt.forTicket's 32-column layout, and the QR that QrCodes.encode makes -----
  // Each path is the ZXing grid for that code (one-square border), drawn as runs of dark squares.
  var QR = {"VIP-0001": "M1 1h7v1h-7zM11 1h1v1h-1zM13 1h1v1h-1zM15 1h7v1h-7zM1 2h1v1h-1zM7 2h1v1h-1zM13 2h1v1h-1zM15 2h1v1h-1zM21 2h1v1h-1zM1 3h1v1h-1zM3 3h3v1h-3zM7 3h1v1h-1zM9 3h1v1h-1zM11 3h1v1h-1zM15 3h1v1h-1zM17 3h3v1h-3zM21 3h1v1h-1zM1 4h1v1h-1zM3 4h3v1h-3zM7 4h1v1h-1zM13 4h1v1h-1zM15 4h1v1h-1zM17 4h3v1h-3zM21 4h1v1h-1zM1 5h1v1h-1zM3 5h3v1h-3zM7 5h1v1h-1zM10 5h1v1h-1zM12 5h2v1h-2zM15 5h1v1h-1zM17 5h3v1h-3zM21 5h1v1h-1zM1 6h1v1h-1zM7 6h1v1h-1zM10 6h3v1h-3zM15 6h1v1h-1zM21 6h1v1h-1zM1 7h7v1h-7zM9 7h1v1h-1zM11 7h1v1h-1zM13 7h1v1h-1zM15 7h7v1h-7zM9 8h1v1h-1zM11 8h1v1h-1zM1 9h3v1h-3zM5 9h5v1h-5zM11 9h1v1h-1zM13 9h3v1h-3zM19 9h1v1h-1zM1 10h2v1h-2zM8 10h1v1h-1zM11 10h2v1h-2zM14 10h1v1h-1zM16 10h1v1h-1zM18 10h2v1h-2zM1 11h1v1h-1zM3 11h3v1h-3zM7 11h1v1h-1zM9 11h4v1h-4zM14 11h3v1h-3zM18 11h4v1h-4zM1 12h1v1h-1zM4 12h2v1h-2zM8 12h1v1h-1zM12 12h3v1h-3zM16 12h3v1h-3zM20 12h1v1h-1zM2 13h1v1h-1zM5 13h4v1h-4zM12 13h1v1h-1zM14 13h3v1h-3zM18 13h2v1h-2zM9 14h2v1h-2zM15 14h2v1h-2zM19 14h1v1h-1zM1 15h7v1h-7zM9 15h1v1h-1zM11 15h1v1h-1zM13 15h1v1h-1zM17 15h1v1h-1zM19 15h3v1h-3zM1 16h1v1h-1zM7 16h1v1h-1zM9 16h2v1h-2zM15 16h2v1h-2zM18 16h1v1h-1zM20 16h1v1h-1zM1 17h1v1h-1zM3 17h3v1h-3zM7 17h1v1h-1zM9 17h1v1h-1zM13 17h1v1h-1zM15 17h1v1h-1zM19 17h3v1h-3zM1 18h1v1h-1zM3 18h3v1h-3zM7 18h1v1h-1zM10 18h3v1h-3zM14 18h1v1h-1zM16 18h1v1h-1zM18 18h1v1h-1zM20 18h1v1h-1zM1 19h1v1h-1zM3 19h3v1h-3zM7 19h1v1h-1zM9 19h2v1h-2zM12 19h1v1h-1zM14 19h2v1h-2zM17 19h1v1h-1zM19 19h1v1h-1zM21 19h1v1h-1zM1 20h1v1h-1zM7 20h1v1h-1zM9 20h1v1h-1zM11 20h4v1h-4zM16 20h1v1h-1zM18 20h1v1h-1zM20 20h2v1h-2zM1 21h7v1h-7zM9 21h2v1h-2zM12 21h1v1h-1zM14 21h2v1h-2zM17 21h1v1h-1zM19 21h1v1h-1zM21 21h1v1h-1z", "REG-0001": "M1 1h7v1h-7zM9 1h2v1h-2zM13 1h1v1h-1zM15 1h7v1h-7zM1 2h1v1h-1zM7 2h1v1h-1zM10 2h1v1h-1zM13 2h1v1h-1zM15 2h1v1h-1zM21 2h1v1h-1zM1 3h1v1h-1zM3 3h3v1h-3zM7 3h1v1h-1zM9 3h1v1h-1zM11 3h1v1h-1zM13 3h1v1h-1zM15 3h1v1h-1zM17 3h3v1h-3zM21 3h1v1h-1zM1 4h1v1h-1zM3 4h3v1h-3zM7 4h1v1h-1zM9 4h1v1h-1zM12 4h1v1h-1zM15 4h1v1h-1zM17 4h3v1h-3zM21 4h1v1h-1zM1 5h1v1h-1zM3 5h3v1h-3zM7 5h1v1h-1zM9 5h3v1h-3zM15 5h1v1h-1zM17 5h3v1h-3zM21 5h1v1h-1zM1 6h1v1h-1zM7 6h1v1h-1zM15 6h1v1h-1zM21 6h1v1h-1zM1 7h7v1h-7zM9 7h1v1h-1zM11 7h1v1h-1zM13 7h1v1h-1zM15 7h7v1h-7zM10 8h2v1h-2zM1 9h4v1h-4zM7 9h1v1h-1zM9 9h1v1h-1zM11 9h1v1h-1zM14 9h1v1h-1zM17 9h3v1h-3zM21 9h1v1h-1zM1 10h2v1h-2zM4 10h1v1h-1zM8 10h3v1h-3zM13 10h1v1h-1zM16 10h1v1h-1zM18 10h3v1h-3zM1 11h2v1h-2zM5 11h3v1h-3zM10 11h2v1h-2zM13 11h5v1h-5zM20 11h1v1h-1zM1 12h1v1h-1zM3 12h2v1h-2zM6 12h1v1h-1zM9 12h3v1h-3zM13 12h2v1h-2zM18 12h3v1h-3zM5 13h1v1h-1zM7 13h6v1h-6zM14 13h1v1h-1zM17 13h2v1h-2zM20 13h2v1h-2zM9 14h3v1h-3zM14 14h1v1h-1zM18 14h1v1h-1zM21 14h1v1h-1zM1 15h7v1h-7zM10 15h2v1h-2zM14 15h2v1h-2zM17 15h1v1h-1zM19 15h1v1h-1zM1 16h1v1h-1zM7 16h1v1h-1zM10 16h1v1h-1zM12 16h5v1h-5zM19 16h1v1h-1zM1 17h1v1h-1zM3 17h3v1h-3zM7 17h1v1h-1zM10 17h1v1h-1zM12 17h1v1h-1zM15 17h1v1h-1zM17 17h2v1h-2zM20 17h2v1h-2zM1 18h1v1h-1zM3 18h3v1h-3zM7 18h1v1h-1zM9 18h3v1h-3zM14 18h1v1h-1zM17 18h1v1h-1zM20 18h1v1h-1zM1 19h1v1h-1zM3 19h3v1h-3zM7 19h1v1h-1zM9 19h2v1h-2zM12 19h1v1h-1zM14 19h1v1h-1zM16 19h1v1h-1zM19 19h1v1h-1zM1 20h1v1h-1zM7 20h1v1h-1zM9 20h5v1h-5zM15 20h1v1h-1zM18 20h1v1h-1zM1 21h7v1h-7zM9 21h2v1h-2zM12 21h2v1h-2zM17 21h1v1h-1zM20 21h1v1h-1z", "REG-0002": "M1 1h7v1h-7zM9 1h2v1h-2zM13 1h1v1h-1zM15 1h7v1h-7zM1 2h1v1h-1zM7 2h1v1h-1zM10 2h1v1h-1zM13 2h1v1h-1zM15 2h1v1h-1zM21 2h1v1h-1zM1 3h1v1h-1zM3 3h3v1h-3zM7 3h1v1h-1zM9 3h1v1h-1zM11 3h1v1h-1zM13 3h1v1h-1zM15 3h1v1h-1zM17 3h3v1h-3zM21 3h1v1h-1zM1 4h1v1h-1zM3 4h3v1h-3zM7 4h1v1h-1zM9 4h1v1h-1zM12 4h1v1h-1zM15 4h1v1h-1zM17 4h3v1h-3zM21 4h1v1h-1zM1 5h1v1h-1zM3 5h3v1h-3zM7 5h1v1h-1zM9 5h3v1h-3zM15 5h1v1h-1zM17 5h3v1h-3zM21 5h1v1h-1zM1 6h1v1h-1zM7 6h1v1h-1zM15 6h1v1h-1zM21 6h1v1h-1zM1 7h7v1h-7zM9 7h1v1h-1zM11 7h1v1h-1zM13 7h1v1h-1zM15 7h7v1h-7zM10 8h2v1h-2zM1 9h4v1h-4zM7 9h1v1h-1zM9 9h1v1h-1zM11 9h1v1h-1zM14 9h1v1h-1zM17 9h3v1h-3zM21 9h1v1h-1zM1 10h4v1h-4zM6 10h1v1h-1zM9 10h1v1h-1zM11 10h1v1h-1zM13 10h1v1h-1zM16 10h1v1h-1zM18 10h3v1h-3zM1 11h1v1h-1zM3 11h1v1h-1zM5 11h3v1h-3zM10 11h1v1h-1zM13 11h5v1h-5zM20 11h1v1h-1zM6 12h1v1h-1zM9 12h1v1h-1zM13 12h2v1h-2zM18 12h3v1h-3zM1 13h1v1h-1zM3 13h2v1h-2zM7 13h2v1h-2zM10 13h1v1h-1zM12 13h1v1h-1zM14 13h1v1h-1zM17 13h2v1h-2zM20 13h2v1h-2zM9 14h1v1h-1zM14 14h1v1h-1zM18 14h1v1h-1zM21 14h1v1h-1zM1 15h7v1h-7zM11 15h1v1h-1zM14 15h2v1h-2zM17 15h1v1h-1zM19 15h1v1h-1zM1 16h1v1h-1zM7 16h1v1h-1zM11 16h6v1h-6zM19 16h1v1h-1zM1 17h1v1h-1zM3 17h3v1h-3zM7 17h1v1h-1zM12 17h1v1h-1zM15 17h1v1h-1zM18 17h1v1h-1zM20 17h2v1h-2zM1 18h1v1h-1zM3 18h3v1h-3zM7 18h1v1h-1zM9 18h1v1h-1zM11 18h1v1h-1zM14 18h1v1h-1zM16 18h2v1h-2zM20 18h1v1h-1zM1 19h1v1h-1zM3 19h3v1h-3zM7 19h1v1h-1zM9 19h4v1h-4zM14 19h1v1h-1zM16 19h1v1h-1zM19 19h1v1h-1zM1 20h1v1h-1zM7 20h1v1h-1zM9 20h5v1h-5zM15 20h1v1h-1zM18 20h1v1h-1zM1 21h7v1h-7zM9 21h5v1h-5zM17 21h1v1h-1zM20 21h1v1h-1z"};
  var RULE = "--------------------------------";
  function field(label, value) { return (label + "        ").slice(0, 8) + value; }
  function receiptFor(t) {
    return ["KILELE NIGHTS", "Sat 14 Nov, 18:00", RULE,
      field("Ticket", t.code), field("Type", t.type), field("Buyer", t.buyer), field("Price", t.price), field("Sold", t.sold),
      RULE, "Show this at the gate.", "One entry per ticket."].join("\n");
  }
  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  function print(t) {
    $("receipt-text").textContent = receiptFor(t);
    $("receipt-qr").setAttribute("d", QR[t.code]);
    $("receipt-qr-label").textContent = "QR code for " + t.code;
    $("receipt-code").textContent = t.code;
    var paper = $("receipt");
    paper.classList.remove("printing");
    if (!reduced) {
      void paper.offsetWidth; // restart the animation
      paper.classList.add("printing");
    }
  }

  // ----- the gate replay: a recorded run of the finished CLI. Each sale prints its receipts. -----
  var out = $("gate-out");
  var vip = { code: "VIP-0001", type: "VIP", buyer: "Wanjiku", price: "KES 5,000", sold: "Sat 14 Nov, 19:00" };
  var reg1 = { code: "REG-0001", type: "Regular", buyer: "Kamau", price: "KES 1,500", sold: "Sat 14 Nov, 19:01" };
  var reg2 = { code: "REG-0002", type: "Regular", buyer: "Kamau", price: "KES 1,500", sold: "Sat 14 Nov, 19:01" };
  var lines = [
    { html: '<span class="p">$</span> java -jar cli/target/boxoffice.jar sell VIP Wanjiku' },
    { html: 'Sold <span class="y">VIP-0001</span> to Wanjiku for KES 5,000', prints: [vip] },
    { html: '<span class="p">$</span> java -jar cli/target/boxoffice.jar sell REG Kamau -q 2' },
    { html: 'Sold 2 tickets to Kamau for KES 3,000: <span class="y">REG-0001</span> to <span class="y">REG-0002</span>', prints: [reg1, reg2] },
    { html: '<span class="p">$</span> java -jar cli/target/boxoffice.jar report' },
    { html: 'Kilele Nights · Sat 14 Nov, 18:00' },
    { html: 'Type        Paid  Guests   Left        Takings' },
    { html: 'Regular        2       0    498      KES 3,000' },
    { html: 'VIP            1       0     49      KES 5,000' },
    { html: '<span class="ok">Total          3       0    547      KES 8,000</span>' }
  ];
  var running = false;
  $("run-gate").addEventListener("click", function () {
    if (running) return;
    if (reduced) {
      out.innerHTML = lines.map(function (l) { return l.html; }).join("\n");
      print(reg2);
      return;
    }
    running = true;
    out.innerHTML = "";
    var at = 120;
    lines.forEach(function (line, i) {
      at += line.html.indexOf("$") > -1 ? 420 : 160;
      setTimeout(function () {
        out.innerHTML += (i ? "\n" : "") + line.html;
        (line.prints || []).forEach(function (t, k) { setTimeout(function () { print(t); }, k * 700); });
        if (i === lines.length - 1) running = false;
      }, at);
    });
  });

  // ----- the route: one flat field per phase, sunset to 04:00 -----
  var currentPhase = next ? next.phase : 1;
  var list = $("timeline");
  phases.forEach(function (ph) {
    var own = days.filter(function (d) { return d.phase === ph.n; });
    var finished = own.filter(function (d) { return P.isDone(state, d); }).length;
    var pct = Math.round((finished / own.length) * 100);
    var isHere = ph.n === currentPhase;
    var li = document.createElement("li");
    li.className = "stage ph-" + ph.n + (isHere ? " here" : "");
    li.innerHTML =
      '<div class="container"><div>' +
        '<p class="when">Phase ' + ph.n + ", Days " + ph.first + " to " + ph.last + "</p>" +
        "<h3>" + P.esc(ph.name) + "</h3>" +
        (ph.about ? '<p class="about">' + P.esc(ph.about) + "</p>" : "") +
        (isHere ? '<p class="here-tag">' + (next ? "You are here" : "Start here") + "</p>" : "") +
        '<div class="meter"><div class="progress" role="progressbar" aria-label="Phase ' + ph.n + ' progress" aria-valuemin="0" aria-valuemax="' + own.length + '" aria-valuenow="' + finished + '"><span style="width:' + pct + '%"></span></div>' + finished + " of " + own.length + " days done</div>" +
      '</div><ul class="day-list"></ul></div>';
    var ul = li.querySelector(".day-list");
    own.forEach(function (d) {
      var p = P.percent(state, d);
      var status = p === 100 ? '<span class="label label-done">Done</span>'
        : next && d.day === next.day ? '<span class="label label-next">Up next</span>'
        : p > 0 ? '<span class="label">' + p + "%</span>" : "";
      var item = document.createElement("li");
      item.innerHTML = '<a href="' + P.dayUrl(d.day) + '"><span class="stub">' + d.day + '</span><span class="t">' + P.esc(d.title) + "</span>" + status + "</a>";
      ul.appendChild(item);
    });
    list.appendChild(li);
  });

  $("first-day").textContent = "Day " + first.day + ": " + first.title;
  $("first-day").href = P.dayUrl(first.day);
  $("last-day").textContent = "Day " + last.day + ": " + last.title;
  $("last-day").href = P.dayUrl(last.day);

  // ----- entrance: each section's parts rise once as it comes into view -----
  // Only below the first screen, and only with IntersectionObserver, so nothing stays hidden.
  if (!reduced && "IntersectionObserver" in window) {
    var blocks = Array.prototype.filter.call(document.querySelectorAll(".reveal"), function (el) {
      return el.getBoundingClientRect().top > window.innerHeight;
    });
    blocks.forEach(function (el) {
      Array.prototype.forEach.call(el.children, function (child, i) { child.style.setProperty("--i", Math.min(i, 3)); });
    });
    document.documentElement.classList.add("js");
    var seen = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) { e.target.classList.add("in"); seen.unobserve(e.target); }
      });
    }, { rootMargin: "0px 0px -10% 0px" });
    blocks.forEach(function (el) { seen.observe(el); });
    document.querySelectorAll(".reveal").forEach(function (el) { if (blocks.indexOf(el) < 0) el.classList.add("in"); });
  }
})();
