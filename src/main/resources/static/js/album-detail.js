(function () {
  var ALL_LABEL = "ALL";
  var ALL_COLOR = "#00f5d4";
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
      return { kind: "all", label: ALL_LABEL, color: ALL_COLOR };
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

    return { html: html, legend: legendOrder };
  }

  function wrapSegment(segment, color) {
    if (!segment) return "";
    return '<span style="color:' + escapeHtml(color) + '">' + escapeHtml(segment) + "</span>";
  }

  function renderLegend(legend) {
    var root = document.getElementById("albumLyricsLegend");
    if (!root) return;
    root.innerHTML = "";
    if (!legend.length) {
      root.hidden = true;
      return;
    }
    root.hidden = false;
    legend.forEach(function (item) {
      var chip = document.createElement("span");
      chip.className = "album-lyrics-legend-chip";
      chip.innerHTML =
        '<span class="album-lyrics-legend-dot" style="background:' +
        escapeHtml(item.color) +
        '"></span>' +
        '<span class="album-lyrics-legend-name" style="color:' +
        escapeHtml(item.color) +
        '">' +
        escapeHtml(item.label) +
        "</span>";
      root.appendChild(chip);
    });
  }

  function openLyricsModal(title, lyrics, colorMap) {
    var modal = document.getElementById("albumLyricsModal");
    var titleEl = document.getElementById("albumLyricsTitle");
    var bodyEl = document.getElementById("albumLyricsBody");
    if (!modal || !titleEl || !bodyEl) return;

    var rendered = renderLyrics(lyrics, colorMap);
    titleEl.textContent = title || "가사";
    renderLegend(rendered.legend);
    bodyEl.innerHTML = rendered.html;

    if (window.SiteUi && typeof window.SiteUi.modal === "function") {
      window.SiteUi.modal(modal).show();
    } else {
      modal.classList.add("is-open", "show");
      modal.setAttribute("aria-hidden", "false");
      document.body.classList.add("site-modal-open");
    }
  }

  function bind() {
    var colorMap = readMembers();
    var section = document.querySelector(".album-track-section");
    if (!section) return;

    section.addEventListener("click", function (event) {
      if (event.target.closest(".album-track-link")) return;
      var trigger = event.target.closest("[data-track-lyrics-open]");
      if (!trigger) return;
      var src = trigger.parentElement && trigger.parentElement.querySelector(".album-track-lyrics-src");
      var lyrics = src ? src.textContent : "";
      if (!lyrics || !lyrics.trim()) return;
      event.preventDefault();
      openLyricsModal(trigger.getAttribute("data-track-title") || trigger.textContent.trim(), lyrics, colorMap);
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bind);
  } else {
    bind();
  }
})();
