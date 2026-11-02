// The landing page: welcome back, the gate replay, and the route of phases.
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
      $(id).textContent = "Continue with Day " + next.day + " →";
    });
    $("resume").textContent = done === days.length
      ? "All " + days.length + " days done. Box Office 1.0 is yours."
      : done + " of " + days.length + " days done · up next: Day " + next.day + ", " + next.title;
    $("resume").hidden = false;
    $("cta-text").textContent = "Up next: Day " + next.day + ", " + next.title + ". About 2 to 3 hours.";
  }

  // ----- the gate replay: a recorded run of the finished CLI -----
  var out = $("gate-out");
  var lines = [
    '<span class="p">$</span> java -jar cli/target/boxoffice.jar sell VIP Wanjiku',
    'Sold <span class="y">VIP-0001</span> to Wanjiku for KES 5,000',
    '<span class="p">$</span> java -jar cli/target/boxoffice.jar sell REG Kamau -q 2',
    'Sold 2 tickets to Kamau for KES 3,000: <span class="y">REG-0001</span> to <span class="y">REG-0002</span>',
    '<span class="p">$</span> java -jar cli/target/boxoffice.jar report',
    'Kilele Nights · Sat 14 Nov, 18:00',
    'Type        Paid  Guests   Left        Takings',
    'Regular        2       0    498      KES 3,000',
    'VIP            1       0     49      KES 5,000',
    '<span class="ok">Total          3       0    547      KES 8,000</span>'
  ];
  var running = false;
  $("run-gate").addEventListener("click", function () {
    if (running) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) { out.innerHTML = lines.join("\n"); return; }
    running = true;
    out.innerHTML = "";
    lines.forEach(function (line, i) {
      setTimeout(function () {
        out.innerHTML += (i ? "\n" : "") + line;
        if (i === lines.length - 1) running = false;
      }, 120 + i * (line.indexOf("$") > -1 ? 420 : 160));
    });
  });

  // ----- the route -----
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
      '<span class="stop" aria-hidden="true">' + ph.n + "</span>" +
      '<div class="card">' +
        "<small>Phase " + ph.n + " · Days " + ph.first + "–" + ph.last + "</small>" +
        "<h3>" + P.esc(ph.name) + (isHere ? '<span class="here-tag">' + (next ? "You are here" : "Start here") + "</span>" : "") + "</h3>" +
        (ph.about ? "<p>" + P.esc(ph.about) + "</p>" : "") +
        '<div class="meter"><div class="progress" role="progressbar" aria-label="Phase ' + ph.n + ' progress" aria-valuemin="0" aria-valuemax="' + own.length + '" aria-valuenow="' + finished + '"><span style="width:' + pct + '%"></span></div>' + finished + "/" + own.length + " done</div>" +
        "<details" + (isHere ? " open" : "") + "><summary>Days " + ph.first + "–" + ph.last + '</summary><ul class="day-list"></ul></details>' +
      "</div>";
    var ul = li.querySelector(".day-list");
    own.forEach(function (d) {
      var p = P.percent(state, d);
      var status = p === 100 ? '<span class="label label-done">Done</span>'
        : next && d.day === next.day ? '<span class="label label-next">Up next</span>'
        : p > 0 ? '<span class="label">' + p + "%</span>" : "";
      var item = document.createElement("li");
      item.innerHTML = '<a href="' + P.dayUrl(d.day) + '"><span class="n">Day ' + d.day + '</span><span class="t">' + P.esc(d.title) + "</span>" + status + "</a>";
      ul.appendChild(item);
    });
    list.appendChild(li);
  });

  $("first-day").textContent = "Day " + first.day + ": " + first.title;
  $("first-day").href = P.dayUrl(first.day);
  $("last-day").textContent = "Day " + last.day + ": " + last.title;
  $("last-day").href = P.dayUrl(last.day);
})();
