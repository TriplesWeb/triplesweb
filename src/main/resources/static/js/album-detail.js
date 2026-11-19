(function () {
  var ALL_LABEL = "ALL";
  var DEFAULT_COLOR = "#e3e2e8";
  var TAG_RE = /\[([^\]]+)\]/g;

  function readMembers() {
    var el = document.getElementById("albumLyricsMembers");
    if (!el) return {};
    try {
      var list = JSON.parse(el.textContent || "[]");
      var map = Object.create(null);
      (Array.isArray(list) ? list : []).forEach(function (item) {
        if (!item || !item.name) return;
        var name = String(item.name).trim();
        if (!name) return;
        map[name] = String(item.color || DEFAULT_COLOR).trim() || DEFAULT_COLOR;
      });
      return map;
    } catch (_) {
      return {};
    }
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function isAllTag(name) {
    var n = name.trim().toUpperCase();
    return n === "ALL" || name.trim() === "모두";
  }

  function resolvePart(rawName, colorMap) {
    var name = String(rawName || "").trim();
    if (!name) return { kind: "plain", label: "", color: DEFAULT_COLOR };
    if (isAllTag(name)) {
      // 전원 파트는 멤버 색 없이 기본 본문색
      return { kind: "all", label: ALL_LABEL, color: DEFAULT_COLOR };
    }
    if (Object.prototype.hasOwnProperty.call(colorMap, name)) {
      return { kind: "member", label: name, color: colorMap[name] || DEFAULT_COLOR };
    }
    return { kind: "unknown", label: name, color: DEFAULT_COLOR };
  }

  /** [멤버명] 태그 기준 구간 색칠. HTML은 escape만 사용. */
  function renderLyrics(raw, colorMap) {
    var text = String(raw || "");
    var legendOrder = [];
    var legendSeen = Object.create(null);
    var html = "";
    var lastIndex = 0;
    var current = { kind: "plain", label: "", color: DEFAULT_COLOR };
    var match;

    TAG_RE.lastIndex = 0;
    while ((match = TAG_RE.exec(text)) !== null) {
      if (match.index > lastIndex) {
        html += wrapSegment(text.slice(lastIndex, match.index), current.color);
      }
      current = resolvePart(match[1], colorMap);
      if (current.kind === "member" || current.kind === "all") {
        if (!legendSeen[current.label]) {
          legendSeen[current.label] = true;
          legendOrder.push({ label: current.label, color: current.color });
        }
      }
      lastIndex = match.index + match[0].length;
    }
    if (lastIndex < text.length) {
      html += wrapSegment(text.slice(lastIndex), current.color);
    }
    if (!html) html = wrapSegment(text, DEFAULT_COLOR);

    // ALL은 범례 맨 앞
    legendOrder.sort(function (a, b) {
      if (a.label === ALL_LABEL) return -1;
      if (b.label === ALL_LABEL) return 1;
      return 0;
    });

    return { html: html, legend: legendOrder };
  }

  function wrapSegment(segment, color) {
    if (!segment) return "";
    return '<span style="color:' + escapeHtml(color) + '">' + escapeHtml(segment) + "</span>";
  }

  function fillLegend(root, legend) {
    root.innerHTML = "";
    if (!legend.length) {
      root.hidden = true;
      return;
    }
    root.hidden = false;
    legend.forEach(function (item) {
      var chip = document.createElement("span");
      chip.className = "album-lyrics-legend-chip";
      if (item.label === ALL_LABEL) {
        chip.classList.add("is-all");
        chip.innerHTML =
          '<span class="album-lyrics-legend-dot"></span>' +
          '<span class="album-lyrics-legend-name">' +
          escapeHtml(item.label) +
          "</span>";
      } else {
        chip.innerHTML =
          '<span class="album-lyrics-legend-dot" style="background:' +
          escapeHtml(item.color) +
          '"></span>' +
          '<span class="album-lyrics-legend-name" style="color:' +
          escapeHtml(item.color) +
          '">' +
          escapeHtml(item.label) +
          "</span>";
      }
      root.appendChild(chip);
    });
  }

  function closePanel(panel, trigger) {
    if (!panel) return;
    var slide = panel.querySelector(".album-lyrics-slide");
    panel.classList.remove("is-open");
    if (trigger) {
      trigger.setAttribute("aria-expanded", "false");
      trigger.classList.remove("is-expanded");
    }
    if (!slide) {
      panel.hidden = true;
      return;
    }
    var done = false;
    var finish = function () {
      if (done) return;
      done = true;
      panel.hidden = true;
    };
    slide.addEventListener("transitionend", function onEnd(event) {
      if (event.target !== slide) return;
      slide.removeEventListener("transitionend", onEnd);
      finish();
    });
    window.setTimeout(finish, 320);
  }

  function openPanel(panel, trigger, lyrics, colorMap) {
    var legendEl = panel.querySelector(".album-lyrics-legend");
    var bodyEl = panel.querySelector(".album-lyrics-body");
    if (!legendEl || !bodyEl) return;

    if (!panel.dataset.rendered) {
      var rendered = renderLyrics(lyrics, colorMap);
      fillLegend(legendEl, rendered.legend);
      bodyEl.innerHTML = rendered.html;
      panel.dataset.rendered = "1";
    }

    panel.hidden = false;
    // force reflow so CSS transition runs
    void panel.offsetHeight;
    panel.classList.add("is-open");
    if (trigger) {
      trigger.setAttribute("aria-expanded", "true");
      trigger.classList.add("is-expanded");
    }
  }

  function bind() {
    var colorMap = readMembers();
    var section = document.querySelector(".album-track-section");
    if (!section) return;

    section.addEventListener("click", function (event) {
      if (event.target.closest(".album-track-link")) return;
      var trigger = event.target.closest("[data-track-lyrics-toggle]");
      if (!trigger) return;

      var trackRow = trigger.closest("tr");
      var panel = trackRow && trackRow.nextElementSibling;
      if (!panel || !panel.classList.contains("album-track-lyrics-panel")) return;

      var src = trackRow.querySelector(".album-track-lyrics-src");
      var lyrics = src ? src.textContent : "";
      if (!lyrics || !lyrics.trim()) return;

      event.preventDefault();
      var isOpen = panel.classList.contains("is-open");

      section.querySelectorAll(".album-track-lyrics-panel.is-open").forEach(function (openPanelEl) {
        var prevRow = openPanelEl.previousElementSibling;
        var prevBtn = prevRow && prevRow.querySelector("[data-track-lyrics-toggle]");
        closePanel(openPanelEl, prevBtn);
      });

      if (!isOpen) {
        openPanel(panel, trigger, lyrics, colorMap);
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bind);
  } else {
    bind();
  }
})();
