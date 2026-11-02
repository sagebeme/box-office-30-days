// The lesson page: renders a day's README.md straight from its folder, plus its checklist.
(function () {
  var days = window.BOX_DAYS, phases = window.BOX_PHASES, P = window.BOX_PROGRESS;
  var $ = function (id) { return document.getElementById(id); };
  var asked = parseInt(new URLSearchParams(location.search).get("day"), 10);
  var day = P.byNumber(days, asked) || days[0];
  var n = day.day;
  var index = days.indexOf(day);
  var phase = phases[day.phase - 1];
  var BLOB = P.REPO + "/blob/main/";
  var TREE = P.REPO + "/tree/main/";

  // ----- the header -----
  document.title = "Day " + n + ": " + day.title + " · Box Office in 30 Days";
  $("badge").textContent = n;
  $("title").textContent = day.title;
  if (day.topic) {
    $("topic").textContent = day.topic;
    $("topic").hidden = false;
  }
  if (day.scene) {
    $("scene").innerHTML = P.inline(day.scene);
    $("scene").hidden = false;
  }
  $("crumb-phase").textContent = "Phase " + phase.n + ": " + phase.name;
  $("crumb-day").textContent = "Day " + n;
  document.body.classList.add("ph-" + phase.n);
  var labels = ['<span class="label label-phase">Phase ' + phase.n + " · Day " + (n - phase.first + 1) + " of " + (phase.last - phase.first + 1) + "</span>"];
  if (n >= 119 && n <= 124) labels.push('<span class="label label-web">Web phase</span>');
  if (n === phase.last) labels.push('<span class="label">Phase milestone</span>');
  $("labels").innerHTML = labels.join("");

  $("cmd").innerHTML = P.esc("cd " + day.path + "/starter") + "\n" + P.esc("../../mvnw test");
  $("cmd-win").innerHTML = '<span class="c">' + P.esc("# Windows") + "</span>\n" + P.esc("..\\..\\mvnw.cmd test");
  $("gh-folder").href = TREE + day.path;
  $("gh-solution").href = TREE + day.path + "/solution";

  var lesson = $("lesson");

  // ----- the lesson -----
  fetch("../" + day.path + "/README.md")
    .then(function (r) {
      if (!r.ok) throw new Error(r.status);
      return r.text();
    })
    .then(function (md) {
      lesson.innerHTML = marked.parse(md);
      enhance();
    })
    .catch(function () {
      lesson.innerHTML =
        '<div class="notice"><h2>This lesson couldn\'t load</h2>' +
        "<p>Browsers won't let a page opened straight from your disk read other files. From the course folder, run " +
        "<code>jwebserver -p 8000</code> (it comes with the JDK) and open <code>http://localhost:8000/site/</code>.</p>" +
        '<p>Or read it on GitHub: <a href="' + BLOB + day.path + '/README.md">' + P.esc(day.path) + "/README.md</a></p></div>";
    });

  // A link in a README is relative to its folder. Another day's README opens here; any other file
  // in the repo opens on GitHub.
  function rewrite(a) {
    var href = a.getAttribute("href");
    if (!href || /^(https?:|mailto:|#)/.test(href)) return;
    var resolved = new URL(href, "https://repo.invalid/" + day.path + "/");
    var path = decodeURIComponent(resolved.pathname.slice(1)).replace(/\/$/, "");
    var target = days.filter(function (d) { return path === d.path || path === d.path + "/README.md"; })[0];
    if (target) {
      a.href = P.dayUrl(target.day) + resolved.hash;
      return;
    }
    if (path === "README.md" || path === "") {
      a.href = "index.html";
      return;
    }
    a.href = (/\.[a-z0-9]+$/i.test(path) ? BLOB : TREE) + path + resolved.hash;
  }

  function enhance() {
    lesson.querySelectorAll("a[href]").forEach(rewrite);

    // The opening scene is already in the page head.
    var quote = lesson.querySelector("blockquote");
    if (day.scene && quote && quote === lesson.querySelector("h1 + blockquote, blockquote:first-child")) quote.remove();

    // The README's own checklist becomes the one in the sidebar, which remembers your ticks.
    lesson.querySelectorAll("h2").forEach(function (h) {
      if (h.textContent.trim() !== "Checklist") return;
      var list = h.nextElementSibling;
      if (list && (list.tagName === "UL" || list.tagName === "OL")) {
        var note = document.createElement("p");
        note.innerHTML = 'Tick these off in <a href="#check-panel">today\'s checklist</a>. Your ticks are saved in this browser.';
        list.replaceWith(note);
      }
    });

    lesson.querySelectorAll("table").forEach(function (t) {
      var wrap = document.createElement("div");
      wrap.className = "table-wrap";
      t.parentNode.insertBefore(wrap, t);
      wrap.appendChild(t);
    });

    lesson.querySelectorAll("pre").forEach(function (pre) {
      var code = pre.querySelector("code");
      // Highlight only blocks that name their language: the rest are output and examples.
      if (code && /language-/.test(code.className) && window.hljs) {
        try { hljs.highlightElement(code); } catch (e) { /* unknown language: leave it plain */ }
      }
      var wrap = document.createElement("div");
      wrap.className = "code";
      pre.parentNode.insertBefore(wrap, pre);
      wrap.appendChild(pre);
      var button = document.createElement("button");
      button.type = "button";
      button.className = "copy";
      button.textContent = "Copy";
      button.addEventListener("click", function () {
        var done = function (text) {
          button.textContent = text;
          setTimeout(function () { button.textContent = "Copy"; }, 1600);
        };
        var select = function () {
          var range = document.createRange();
          range.selectNodeContents(pre);
          var sel = getSelection();
          sel.removeAllRanges();
          sel.addRange(range);
          done("Press Ctrl+C");
        };
        try {
          navigator.clipboard.writeText(pre.innerText).then(function () { done("Copied"); }, select);
        } catch (e) {
          select();
        }
      });
      wrap.appendChild(button);
    });
  }

  // ----- the checklist -----
  var state = P.load();
  var saved = P.ticks(state, n);
  var list = $("checklist");
  day.checklist.forEach(function (text, i) {
    var li = document.createElement("li");
    li.innerHTML = '<label><input type="checkbox" id="step-' + i + '"' + (saved[i] ? " checked" : "") + "><span>" + P.inline(text) + "</span></label>";
    li.querySelector("input").addEventListener("change", update);
    list.appendChild(li);
  });

  function update() {
    state[String(n)] = Array.prototype.map.call(list.querySelectorAll("input"), function (b) { return b.checked; });
    P.save(state);
    meter();
  }
  function meter() {
    var pct = P.percent(state, day);
    $("bar").style.width = pct + "%";
    $("pct").textContent = pct + "%";
    $("meter").setAttribute("aria-valuenow", String(pct));
    var msg = $("done-msg");
    var after = days[index + 1];
    if (pct === 100) {
      var phaseDone = days.filter(function (d) { return d.phase === phase.n; }).every(function (d) { return P.isDone(state, d); });
      msg.innerHTML = !after
        ? "Day " + n + " done. That's Box Office 1.0, and the whole course."
        : phaseDone
          ? "Phase " + phase.n + " complete: " + P.esc(phase.name) + '. <a href="' + P.dayUrl(after.day) + '">On to Phase ' + (phase.n + 1) + " →</a>"
          : "Day " + n + ' done. <a href="' + P.dayUrl(after.day) + '">On to Day ' + after.day + " →</a>";
      msg.hidden = false;
    } else {
      msg.hidden = true;
    }
  }
  meter();

  // ----- previous and next -----
  var nav = $("pager");
  function link(target, cls, label) {
    var a = document.createElement("a");
    a.className = cls;
    a.href = P.dayUrl(target.day);
    a.innerHTML = "<small>" + label + "</small><strong>" + P.esc(target.title) + "</strong>";
    nav.appendChild(a);
  }
  if (index > 0) link(days[index - 1], "prev", "← Day " + days[index - 1].day);
  if (index < days.length - 1) link(days[index + 1], "next", "Day " + days[index + 1].day + " →");
})();
