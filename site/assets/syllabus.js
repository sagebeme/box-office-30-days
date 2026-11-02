// The syllabus: every day, by phase, with a search box.
(function () {
  var days = window.BOX_DAYS, phases = window.BOX_PHASES, P = window.BOX_PROGRESS;
  var $ = function (id) { return document.getElementById(id); };
  var state = P.load();
  var next = P.nextDay(state, days) || days[0];

  phases.forEach(function (ph) {
    var a = document.createElement("a");
    a.href = "#phase-" + ph.n;
    a.textContent = ph.n;
    a.setAttribute("aria-label", "Phase " + ph.n + ": " + ph.name);
    a.title = ph.name;
    $("jump").appendChild(a);
  });

  var container = $("phases");
  phases.forEach(function (ph) {
    var section = document.createElement("section");
    section.className = "panel syllabus-phase";
    section.id = "phase-" + ph.n;
    section.setAttribute("aria-labelledby", "phase-" + ph.n + "-title");
    section.innerHTML =
      '<div class="top"><div><h2 id="phase-' + ph.n + '-title">Phase ' + ph.n + ": " + P.esc(ph.name) + "</h2>" +
      (ph.about ? "<p>" + P.esc(ph.about) + "</p>" : "") + "</div>" +
      "<small>Days " + ph.first + "–" + ph.last + "</small></div>" +
      '<div class="table-wrap" style="margin:0"><table class="rows"><thead><tr><th scope="col">Day</th><th scope="col">Milestone</th><th scope="col" class="b">The night</th><th scope="col"><span class="sr-only">Status</span></th></tr></thead><tbody></tbody></table></div>';
    var tbody = section.querySelector("tbody");
    days.filter(function (d) { return d.phase === ph.n; }).forEach(function (d) {
      var p = P.percent(state, d);
      var status = p === 100 ? '<span class="label label-done">Done</span>'
        : d.day === next.day ? '<span class="label label-next">Up next</span>'
        : p > 0 ? '<span class="label">' + p + "%</span>" : "";
      var tr = document.createElement("tr");
      tr.dataset.text = [d.day, d.title, d.topic, d.scene, d.brief, ph.name].join(" ").toLowerCase();
      tr.innerHTML =
        '<td class="n">Day ' + d.day + "</td>" +
        '<td class="p"><a href="' + P.dayUrl(d.day) + '">' + P.esc(d.title) + "</a>" +
        '<span class="topic">' + P.esc(d.topic) + "</span></td>" +
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
