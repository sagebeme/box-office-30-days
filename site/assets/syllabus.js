// The syllabus: every day, by phase, with a search box.
(function () {
  var days = window.BOX_DAYS, phases = window.BOX_PHASES, P = window.BOX_PROGRESS;
  var $ = function (id) { return document.getElementById(id); };
  var state = P.load();
  var next = P.nextDay(state, days) || days[0];

  phases.forEach(function (ph) {
    var a = document.createElement("a");
    a.href = "#phase-" + ph.n;
    a.textContent = ph.n + ". " + ph.name;
    a.className = "ph-" + ph.n;
    $("jump").appendChild(a);
  });

  var container = $("phases");
  phases.forEach(function (ph) {
    var section = document.createElement("section");
    section.className = "syllabus-phase ph-" + ph.n;
    section.id = "phase-" + ph.n;
    section.setAttribute("aria-labelledby", "phase-" + ph.n + "-title");
    section.innerHTML =
      '<div class="top"><div class="container"><p class="when">Phase ' + ph.n + ", Days " + ph.first + " to " + ph.last + "</p>" +
      '<h2 id="phase-' + ph.n + '-title">' + P.esc(ph.name) + "</h2>" +
      (ph.about ? "<p>" + P.esc(ph.about) + "</p>" : "") + "</div></div>" +
      '<div class="rows-wrap"><div class="container table-wrap"><table class="rows"><thead><tr><th scope="col">Day</th><th scope="col">Milestone</th><th scope="col" class="b">The night</th><th scope="col"><span class="sr-only">Status</span></th></tr></thead><tbody></tbody></table></div></div>';
    var tbody = section.querySelector("tbody");
    days.filter(function (d) { return d.phase === ph.n; }).forEach(function (d) {
      var p = P.percent(state, d);
      var status = p === 100 ? '<span class="label label-done">Done</span>'
        : d.day === next.day ? '<span class="label label-next">Up next</span>'
        : p > 0 ? '<span class="label">' + p + "%</span>" : "";
      var tr = document.createElement("tr");
      tr.dataset.text = [d.day, d.title, d.topic, d.scene, d.brief, ph.name].join(" ").toLowerCase();
      tr.innerHTML =
        '<td class="n"><span class="stub" aria-label="Day ' + d.day + '">' + d.day + "</span></td>" +
        '<td class="p"><a href="' + P.dayUrl(d.day) + '">' + P.esc(d.title) + "</a>" +
        '<span class="topic">' + P.inline(d.topic) + "</span></td>" +
        '<td class="b">' + P.inline(d.scene) + "</td>" +
        '<td class="s">' + status + "</td>";
      tbody.appendChild(tr);
    });
    container.appendChild(section);
  });

  $("q").addEventListener("input", function () {
    var words = this.value.toLowerCase().split(/\s+/).filter(Boolean);
    var shown = 0;
    document.querySelectorAll(".syllabus-phase").forEach(function (section) {
      var any = false;
      section.querySelectorAll("tbody tr").forEach(function (tr) {
        var match = words.every(function (w) { return tr.dataset.text.indexOf(w) !== -1; });
        tr.hidden = !match;
        if (match) { any = true; shown++; }
      });
      section.hidden = !any;
    });
    $("empty").hidden = shown > 0;
    $("count").textContent = words.length ? shown + " of " + days.length + " days match" : "";
  });
})();

// Progress to another browser: "BOX30:" and the saved ticks as base64 JSON. Nothing leaves this page.
(function () {
  var P = window.BOX_PROGRESS, days = window.BOX_DAYS;
  var $ = function (id) { return document.getElementById(id); };
  var box = $("carry-code"), status = $("carry-status");
  if (!box) return;
  var PREFIX = "BOX30:";

  function encode(state) { return PREFIX + btoa(JSON.stringify(state)); }
  // The saved ticks from a code, or null if it isn't one of ours.
  function decode(text) {
    text = text.trim();
    if (text.indexOf(PREFIX) !== 0) return null;
    var state;
    try { state = JSON.parse(atob(text.slice(PREFIX.length))); } catch (e) { return null; }
    if (!state || typeof state !== "object" || Array.isArray(state)) return null;
    var clean = {};
    for (var key in state) {
      if (!P.byNumber(days, Number(key))) return null;
      var ticks = state[key];
      if (!Array.isArray(ticks) || !ticks.every(function (t) { return typeof t === "boolean"; })) return null;
      clean[key] = ticks;
    }
    return clean;
  }
  function doneCount(state) { return days.filter(function (d) { return P.isDone(state, d); }).length; }

  if (location.hash === "#carry-loaded") {
    status.textContent = "Loaded. Days done in this browser now: " + doneCount(P.load()) + " of " + days.length + ".";
    history.replaceState(null, "", location.pathname + location.search + "#carry");
  }

  $("carry-copy").addEventListener("click", function () {
    box.value = encode(P.load());
    box.select();
    var copied = function () { status.textContent = "Copied. Paste it into this page on the other browser."; };
    if (navigator.clipboard) navigator.clipboard.writeText(box.value).then(copied, function () { status.textContent = "Select the code and copy it yourself."; });
    else status.textContent = "Select the code and copy it yourself.";
  });

  $("carry-load").addEventListener("click", function () {
    var state = decode(box.value);
    if (!state) { status.textContent = "That isn't a progress code. It starts with BOX30: and comes from \"Copy my code\"."; return; }
    if (!confirm("Replace the progress in this browser with this code? Days done in it: " + doneCount(state) + " of " + days.length + ".")) return;
    P.save(state);
    location.hash = "carry-loaded";
    location.reload();
  });
})();
