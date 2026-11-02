// Progress, kept in this browser only: { "106": [true, true, false, ...] } is Day 106's checklist.
// Shared by every page. No accounts, no server.
(function () {
  var KEY = "boxoffice30-progress";
  var REPO = "https://github.com/sagebeme/box-office-30-days";

  function load() {
    try {
      var state = JSON.parse(localStorage.getItem(KEY) || "{}");
      return state && typeof state === "object" ? state : {};
    } catch (e) {
      return {};
    }
  }
  function save(state) {
    try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) { /* private window: progress just isn't kept */ }
  }
  function ticks(state, n) {
    var saved = state[String(n)];
    return Array.isArray(saved) ? saved : [];
  }
  // 0 to 100 for one day: its README's checklist, ticked off here.
  function percent(state, day) {
    var steps = day.checklist.length || 1;
    var done = ticks(state, day.day).slice(0, steps).filter(Boolean).length;
    return Math.round((done / steps) * 100);
  }
  function isDone(state, day) { return percent(state, day) === 100; }

  // Where to go next: the first unfinished day after the last one touched. Null if nothing's touched.
  function nextDay(state, days) {
    var touched = days.filter(function (d) { return ticks(state, d.day).some(Boolean); });
    if (!touched.length) return null;
    var last = touched[touched.length - 1];
    if (!isDone(state, last)) return last;
    var after = days.filter(function (d) { return d.day > last.day && !isDone(state, d); });
    if (after.length) return after[0];
    var any = days.filter(function (d) { return !isDone(state, d); });
    return any.length ? any[0] : days[days.length - 1];
  }

  function byNumber(days, n) {
    for (var i = 0; i < days.length; i++) if (days[i].day === n) return days[i];
    return null;
  }
  function dayUrl(n) { return "day.html?day=" + n; }

  // Escape text before it goes into innerHTML.
  function esc(text) {
    return String(text).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  // README text in a label: escaped, with `code` and **bold** kept.
  function inline(text) {
    return esc(text)
      .replace(/`([^`]+)`/g, "<code>$1</code>")
      .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  }

  function backToTop() {
    var a = document.querySelector(".to-top");
    if (!a) return;
    var update = function () { a.classList.toggle("show", window.scrollY > 700); };
    window.addEventListener("scroll", update, { passive: true });
    update();
  }
  document.addEventListener("DOMContentLoaded", backToTop);

  window.BOX_PROGRESS = {
    REPO: REPO, load: load, save: save, ticks: ticks, percent: percent, isDone: isDone,
    nextDay: nextDay, byNumber: byNumber, dayUrl: dayUrl, esc: esc, inline: inline
  };
})();
