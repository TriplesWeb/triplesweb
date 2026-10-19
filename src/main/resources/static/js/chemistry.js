/**
 * tripleS 케미명 매트릭스
 * 데이터 소스: GET /api/chemistry (DB, Artist FK). 멤버 목록·색만 프론트 상수.
 * 프로필 링크는 API의 artistAId/artistBId를 우선 사용한다.
 */
(function () {
  const BASE = "/uploads/chemistry";

  const MEMBERS = [
    "윤서연","정혜린","이지우","김채연","김유연","김수민","김나경","공유빈",
    "카에데","서다현","코토네","곽연지","니엔","박소현","신위","마유",
    "린","주빈","정하연","박시온","김채원","설린","서아","지연"
  ];

  const MEMBER_COLORS = [
    "#22aeff","#9200ff","#fff800","#98f21d","#db0c74","#fc83a4","#6799a0","#ffe3e2",
    "#ffc935","#ff9ad6","#ffde00","#5974ff","#ff953f","#1222b5","#d51313","#fe8e76",
    "#ac62b7","#b7f54c","#52d9bb","#ff428a","#c7a3e0","#7bba8d","#cff3ff","#ffab62"
  ];

  /** DB 응답으로 채우는 설명 맵. person -> { chemKey -> {description,image,links} } */
  let chemistryDescriptions = {};
  /** 한글 이름 → Artist.artistId (API artistAId/artistBId로 채움) */
  let artistIdByName = Object.create(null);

  function memberHref(indexOrName) {
    const name = typeof indexOrName === "number" ? MEMBERS[indexOrName] : indexOrName;
    const id = name ? artistIdByName[name] : null;
    if (id != null) return "/triples/artist/" + id;
    const idx = typeof indexOrName === "number" ? indexOrName : memberIndex(name);
    return idx >= 0 ? "/triples/artist/" + (idx + 1) : "#";
  }

  function rememberArtistIds(entry) {
    if (!entry) return;
    if (entry.memberA && entry.artistAId != null) artistIdByName[entry.memberA] = entry.artistAId;
    if (entry.memberB && entry.artistBId != null) artistIdByName[entry.memberB] = entry.artistBId;
  }

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  /** Artist.artistId 기준 정렬 키 (없으면 MEMBERS 순번+1) */
  function artistSortKey(name) {
    const id = name ? artistIdByName[name] : null;
    if (id != null && Number.isFinite(Number(id))) return Number(id);
    const idx = memberIndex(name);
    return idx >= 0 ? idx + 1 : Number.MAX_SAFE_INTEGER;
  }

  /** 아티스트 ID가 빠른 순으로 [A, B] */
  function orderedPairNames(a, b) {
    const ka = artistSortKey(a);
    const kb = artistSortKey(b);
    if (ka !== kb) return ka < kb ? [a, b] : [b, a];
    const ia = memberIndex(a);
    const ib = memberIndex(b);
    return ia <= ib ? [a, b] : [b, a];
  }

  function formatMemberTitleLink(name) {
    const index = memberIndex(name);
    const color = MEMBER_COLORS[index] || "#00f5d4";
    const escaped = escapeHtml(name);
    return (
      '<a href="' +
      memberHref(name) +
      '" title="' +
      escaped +
      '" class="chem-title-member" style="--member-color:' +
      color +
      '">' +
      escaped +
      "</a>"
    );
  }

  /** 모달 타이틀: 아티스트 ID 오름차순 "A x B" */
  function formatChemistryTitle(memberA, memberB) {
    const [a, b] = orderedPairNames(memberA, memberB);
    return formatMemberTitleLink(a) + " x " + formatMemberTitleLink(b);
  }

  function pairTitleText(memberA, memberB) {
    const [a, b] = orderedPairNames(memberA, memberB);
    return a + " x " + b;
  }

  function formatLabel(label) {
    return escapeHtml(label).replace(/\n/g, "<br>");
  }

  let isAdmin = false;
  let adminConfigured = false;
  let dbEntries = [];
  let editingPair = null;
  let currentView = null;

  const ADMIN_TOKEN_KEY = "chemistryAdminToken";

  function getAdminToken() {
    try {
      return sessionStorage.getItem(ADMIN_TOKEN_KEY) || "";
    } catch (_) {
      return "";
    }
  }

  function setAdminToken(token) {
    try {
      if (token) sessionStorage.setItem(ADMIN_TOKEN_KEY, token);
      else sessionStorage.removeItem(ADMIN_TOKEN_KEY);
    } catch (_) {
      /* private mode 등 */
    }
  }

  /** 쿠키가 막혀도 동작하도록 관리자 토큰 헤더를 붙인다. */
  function chemFetch(url, options) {
    const opts = Object.assign({ credentials: "same-origin" }, options || {});
    const headers = new Headers(opts.headers || {});
    const token = getAdminToken();
    if (token) headers.set("X-Chemistry-Admin-Token", token);
    opts.headers = headers;
    return fetch(url, opts);
  }

  async function readChemError(res, fallback) {
    try {
      const text = await res.text();
      if (!text) return fallback;
      try {
        const json = JSON.parse(text);
        if (json && json.message) return json.message;
        if (res.status === 403) return "관리자 로그인이 필요합니다. 다시 로그인해 주세요.";
        if (json && json.error) return json.error + (json.message ? ": " + json.message : "");
      } catch (_) {
        return text.length > 180 ? fallback : text;
      }
      return fallback;
    } catch (_) {
      return fallback;
    }
  }

  function chemKey(a, b) {
    return a + "과 " + b + "의 케미명";
  }

  function memberIndex(name) {
    return MEMBERS.indexOf(name);
  }

  function ensureDescBucket(person) {
    if (!chemistryDescriptions[person]) chemistryDescriptions[person] = {};
    return chemistryDescriptions[person];
  }

  function clearDescriptions() {
    Object.keys(chemistryDescriptions).forEach((k) => {
      delete chemistryDescriptions[k];
    });
  }

  /** 대각선(self)만 채운 빈 24×24 매트릭스 */
  function createEmptyMatrix() {
    return MEMBERS.map((_, ri) =>
      MEMBERS.map((_, ci) =>
        ri === ci ? { type: "self", s: ri + 1 } : { type: "empty" }
      )
    );
  }

  function countChemCells(matrix) {
    if (!matrix) return 0;
    let n = 0;
    matrix.forEach((row) => {
      row.forEach((cell) => {
        if (cell && cell.type === "chem") n += 1;
      });
    });
    // 대칭 셀이 양쪽에 있으므로 페어 수는 절반
    return Math.floor(n / 2);
  }

  function updateEmptyState(pairCount) {
    const el = document.getElementById("chemEmptyState");
    if (!el) return;
    const empty = !pairCount;
    el.classList.toggle("hidden", !empty);
    el.setAttribute("aria-hidden", empty ? "false" : "true");
    const hint = el.querySelector("[data-chem-empty-hint]");
    if (hint) {
      hint.textContent = isAdmin
        ? "관리자 모드에서 빈 칸(+)을 눌러 케미를 추가할 수 있어요."
        : "아직 등록된 케미명이 없습니다.";
    }
  }

  /** DB 목록으로 매트릭스·설명 맵을 구성한다. */
  function buildFromDb(entries) {
    clearDescriptions();
    artistIdByName = Object.create(null);
    const matrix = createEmptyMatrix();
    if (!Array.isArray(entries)) {
      return matrix;
    }
    entries.forEach((entry) => {
      if (!entry) return;
      rememberArtistIds(entry);
      const ia = memberIndex(entry.memberA);
      const ib = memberIndex(entry.memberB);
      if (ia < 0 || ib < 0 || ia === ib) return;

      const label = entry.label || "";
      if (!String(label).trim()) return;

      const desc = entry.description || "";
      const image = entry.imageUrl || "";
      const cross = Boolean(entry.crossFlag);
      const labelTone = normalizeLabelTone(entry.labelTone);
      const links = entry.links && typeof entry.links === "object" ? entry.links : {};

      [[ia, ib], [ib, ia]].forEach(([ri, ci]) => {
        const person = MEMBERS[ri];
        const other = MEMBERS[ci];
        const key = chemKey(person, other);
        matrix[ri][ci] = {
          type: "chem",
          label: label,
          person: person,
          key: key,
          cross: cross,
          labelTone: labelTone,
          dbId: entry.id
        };
        const payload = {
          description: desc,
          image: image,
          labelTone: labelTone
        };
        if (links && Object.keys(links).length) {
          payload.links = links;
        }
        ensureDescBucket(person)[key] = payload;
      });
    });
    return matrix;
  }

  function applyDbToTable(entries) {
    dbEntries = Array.isArray(entries) ? entries : [];
    pairMatrix = buildFromDb(dbEntries);
    updateEmptyState(countChemCells(pairMatrix));
    if (currentMode === "pair") buildTable(pairMatrix);
  }

  function buildTable(matrix) {
    const thead = document.getElementById("chemThead");
    const tbody = document.getElementById("chemTbody");
    if (!thead || !tbody) return;

    let headHtml = '<tr><th class="chem-corner"><img src="' + BASE + '/logo.png" alt="tripleS"></th>';
    MEMBERS.forEach((name, i) => {
      const color = MEMBER_COLORS[i] || "#00f5d4";
      headHtml +=
        '<th class="chem-col-head" style="--member-color:' + color + '">' +
        '<a href="' + memberHref(i) + '" title="' + escapeHtml(name) + '" class="chem-member-link">' +
        '<span class="chem-member-name">' + escapeHtml(name) + "</span>" +
        "</a></th>";
    });
    headHtml += "</tr>";
    thead.innerHTML = headHtml;

    let bodyHtml = "";
    matrix.forEach((row, ri) => {
      const color = MEMBER_COLORS[ri] || "#00f5d4";
      bodyHtml +=
        '<tr><th class="chem-row-head" style="--member-color:' + color + '">' +
        '<a href="' + memberHref(ri) + '" title="' + escapeHtml(MEMBERS[ri]) + '" class="chem-member-link">' +
        '<span class="chem-member-name">' + escapeHtml(MEMBERS[ri]) + "</span>" +
        "</a></th>";

      row.forEach((cell, ci) => {
        if (cell.type === "self") {
          bodyHtml +=
            '<td class="chem-self"><img src="' +
            BASE +
            "/symbols/S" +
            cell.s +
            '.png" alt="S' +
            cell.s +
            '"></td>';
        } else if (cell.type === "chem" && cell.key) {
          const toneCls = toneClass(cell.labelTone);
          bodyHtml +=
            '<td class="chem-cell' +
            (toneCls ? " " + toneCls : "") +
            '" data-person="' +
            escapeHtml(cell.person) +
            '" data-key="' +
            escapeHtml(cell.key) +
            '" data-m1="' +
            escapeHtml(MEMBERS[ri]) +
            '" data-m2="' +
            escapeHtml(MEMBERS[ci]) +
            '" data-tone="' +
            escapeHtml(normalizeLabelTone(cell.labelTone)) +
            '" tabindex="0" role="button">' +
            formatLabel(cell.label) +
            "</td>";
        } else if (isAdmin) {
          bodyHtml +=
            '<td class="chem-empty chem-addable" data-m1="' +
            escapeHtml(MEMBERS[ri]) +
            '" data-m2="' +
            escapeHtml(MEMBERS[ci]) +
            '" tabindex="0" role="button" title="케미 추가">+</td>';
        } else {
          bodyHtml += '<td class="chem-empty"></td>';
        }
      });
      bodyHtml += "</tr>";
    });
    tbody.innerHTML = bodyHtml;

    tbody.querySelectorAll(".chem-cell").forEach((el) => {
      const open = () => {
        const ri = memberIndex(el.dataset.m1);
        const ci = memberIndex(el.dataset.m2);
        const cell = pairMatrix && pairMatrix[ri] && pairMatrix[ri][ci];
        editingPair = { member1: el.dataset.m1, member2: el.dataset.m2 };
        currentView = {
          person: el.dataset.person,
          key: el.dataset.key,
          member1: el.dataset.m1,
          member2: el.dataset.m2,
          label: (el.textContent || "").replace(/\s+/g, " ").trim(),
          labelTone: (cell && cell.labelTone) || el.dataset.tone || "NONE",
          dbId: cell && cell.dbId ? cell.dbId : null
        };
        openChemistryName(el.dataset.person, el.dataset.key);
      };
      el.addEventListener("click", open);
      el.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          open();
        }
      });
    });

    tbody.querySelectorAll(".chem-addable").forEach((el) => {
      const open = () => openEditForm(el.dataset.m1, el.dataset.m2, "", "", "", {});
      el.addEventListener("click", open);
      el.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          open();
        }
      });
    });
  }

  /** description에서 "photo by …" 출처를 분리 (서식 HTML 유지) */
  function splitPhotoCredit(rawDescription) {
    const raw = String(rawDescription || "");
    const plain = raw
      .replace(/<br\s*\/?>/gi, " ")
      .replace(/<[^>]+>/g, " ")
      .replace(/\s+/g, " ")
      .trim();
    const match = plain.match(/\s*photo\s+by\.?\s*(@?[^\s]+)\s*$/i);
    if (!match) {
      return { description: raw, credit: "" };
    }
    const credit = match[1].replace(/[.,;]+$/, "").trim();
    const stripped = raw
      .replace(/\s*photo\s+by\.?\s*@?[\w.-]+\s*$/i, "")
      .replace(/(?:<br\s*\/?>|\s)*photo\s+by\.?\s*@?[\w.-]+(?:\s*<[^>]+>)*\s*$/i, "")
      .trim();
    return { description: stripped || raw, credit };
  }

  function extractElementColor(el) {
    if (!el || el.nodeType !== Node.ELEMENT_NODE) return "";
    if (el.style && el.style.color) return el.style.color;
    const attr = el.getAttribute && (el.getAttribute("color") || el.getAttribute("face"));
    if (attr && /^#|^rgb/i.test(attr)) return attr;
    if (el.getAttribute) {
      const colorAttr = el.getAttribute("color");
      if (colorAttr) return colorAttr;
    }
    return "";
  }

  function normalizeFontColorsIn(root) {
    if (!root) return;
    root.querySelectorAll("font").forEach((font) => {
      const span = document.createElement("span");
      const color = extractElementColor(font);
      if (color) span.style.color = color;
      while (font.firstChild) span.appendChild(font.firstChild);
      font.replaceWith(span);
    });
  }

  function sanitizeChemistryHtml(html) {
    const allowed = new Set(["B", "STRONG", "I", "EM", "U", "SPAN", "BR", "A", "DIV", "P", "FONT"]);
    const wrap = document.createElement("div");
    wrap.innerHTML = String(html || "");
    normalizeFontColorsIn(wrap);

    function clean(node) {
      Array.from(node.childNodes).forEach((child) => {
        if (child.nodeType === Node.TEXT_NODE) return;
        if (child.nodeType !== Node.ELEMENT_NODE) {
          child.remove();
          return;
        }
        if (child.tagName === "FONT") {
          const span = document.createElement("span");
          const color = extractElementColor(child);
          if (color) span.style.color = color;
          while (child.firstChild) span.appendChild(child.firstChild);
          node.insertBefore(span, child);
          child.remove();
          clean(span);
          return;
        }
        if (!allowed.has(child.tagName)) {
          while (child.firstChild) node.insertBefore(child.firstChild, child);
          child.remove();
          return;
        }
        if (child.tagName === "SPAN") {
          const color = extractElementColor(child);
          Array.from(child.attributes).forEach((attr) => child.removeAttribute(attr.name));
          if (color) child.style.color = color;
        } else if (child.tagName === "A") {
          const href = child.getAttribute("href") || "";
          Array.from(child.attributes).forEach((attr) => child.removeAttribute(attr.name));
          if (/^https?:\/\//i.test(href)) {
            child.setAttribute("href", href);
            child.setAttribute("target", "_blank");
            child.setAttribute("rel", "noopener");
            child.className = "chem-highlight";
          } else {
            while (child.firstChild) node.insertBefore(child.firstChild, child);
            child.remove();
            return;
          }
        } else if (child.tagName === "DIV" || child.tagName === "P") {
          Array.from(child.attributes).forEach((attr) => child.removeAttribute(attr.name));
        }
        clean(child);
      });
    }

    clean(wrap);
    return wrap.innerHTML
      .replace(/<\/div>\s*<div>/gi, "<br>")
      .replace(/<\/?div>/gi, "")
      .replace(/<\/p>\s*<p>/gi, "<br>")
      .replace(/<\/?p>/gi, "");
  }

  function descriptionToEditorHtml(description) {
    const raw = String(description || "");
    if (/<(b|strong|i|em|u|span|br|a|font)\b/i.test(raw)) {
      return sanitizeChemistryHtml(raw.replace(/\n/g, "<br>"));
    }
    return escapeHtml(raw).replace(/\n/g, "<br>");
  }

  function getEditorDescriptionHtml() {
    const editor = document.getElementById("chemEditDescription");
    if (!editor) return "";
    normalizeFontColorsIn(editor);
    return sanitizeChemistryHtml(editor.innerHTML);
  }

  /** 표 칸 케미명 글자색 (NONE=기본 흰) */
  const LABEL_TONES = ["NONE", "UNDECIDED", "RECENT", "FAN"];

  function normalizeLabelTone(value) {
    const v = String(value || "").trim().toUpperCase();
    return LABEL_TONES.includes(v) ? v : "NONE";
  }

  function toneClass(tone) {
    switch (normalizeLabelTone(tone)) {
      case "UNDECIDED":
        return "chem-tone-undecided";
      case "RECENT":
        return "chem-tone-recent";
      case "FAN":
        return "chem-tone-fan";
      default:
        return "";
    }
  }

  function selectedLabelTone() {
    const checked = document.querySelector('input[name="labelTone"]:checked');
    return normalizeLabelTone(checked && checked.value);
  }

  function setLabelToneInputs(tone) {
    const target = normalizeLabelTone(tone);
    document.querySelectorAll('input[name="labelTone"]').forEach((el) => {
      el.checked = el.value === target;
    });
  }

  /** 편집기 기본 색상 = S1~S24 멤버 상징색 (MEMBER_COLORS 순서) */
  const CHEM_COLOR_PALETTE = MEMBER_COLORS;
  const CHEM_RECENT_COLORS_KEY = "chemRecentTextColors";
  const CHEM_RECENT_COLORS_MAX = 8;

  function clampColorChannel(n) {
    const v = Number(n);
    if (!Number.isFinite(v)) return 0;
    return Math.max(0, Math.min(255, Math.round(v)));
  }

  function hexToRgb(hex) {
    const raw = String(hex || "").trim().replace(/^#/, "");
    if (!/^[0-9a-fA-F]{6}$/.test(raw)) return null;
    return {
      r: parseInt(raw.slice(0, 2), 16),
      g: parseInt(raw.slice(2, 4), 16),
      b: parseInt(raw.slice(4, 6), 16)
    };
  }

  function rgbToHex(r, g, b) {
    return (
      "#" +
      [r, g, b]
        .map((n) => clampColorChannel(n).toString(16).padStart(2, "0"))
        .join("")
    );
  }

  function normalizeHexColor(value) {
    let raw = String(value || "").trim();
    if (!raw) return null;
    if (raw.startsWith("rgb")) {
      const m = raw.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i);
      if (!m) return null;
      return rgbToHex(m[1], m[2], m[3]);
    }
    if (!raw.startsWith("#")) raw = "#" + raw;
    if (/^#[0-9a-fA-F]{3}$/.test(raw)) {
      raw =
        "#" +
        raw
          .slice(1)
          .split("")
          .map((c) => c + c)
          .join("");
    }
    return /^#[0-9a-fA-F]{6}$/.test(raw) ? raw.toLowerCase() : null;
  }

  function syncColorInputs(hex) {
    const normalized = normalizeHexColor(hex) || "#00f5d4";
    const rgb = hexToRgb(normalized);
    const picker = document.getElementById("chemColorPicker");
    const hexInput = document.getElementById("chemColorHex");
    const r = document.getElementById("chemColorR");
    const g = document.getElementById("chemColorG");
    const b = document.getElementById("chemColorB");
    const bar = document.getElementById("chemColorBar");
    if (picker) picker.value = normalized;
    if (hexInput) hexInput.value = normalized;
    if (rgb) {
      if (r) r.value = String(rgb.r);
      if (g) g.value = String(rgb.g);
      if (b) b.value = String(rgb.b);
    }
    if (bar) bar.style.background = normalized;
    return normalized;
  }

  function loadRecentColors() {
    try {
      const raw = JSON.parse(localStorage.getItem(CHEM_RECENT_COLORS_KEY) || "[]");
      if (!Array.isArray(raw)) return [];
      const seen = new Set();
      const out = [];
      raw.forEach((item) => {
        const hex = normalizeHexColor(item);
        if (!hex || seen.has(hex)) return;
        seen.add(hex);
        out.push(hex);
      });
      return out.slice(0, CHEM_RECENT_COLORS_MAX);
    } catch (_) {
      return [];
    }
  }

  function rememberRecentColor(color) {
    const hex = normalizeHexColor(color);
    if (!hex) return;
    const next = [hex, ...loadRecentColors().filter((c) => c !== hex)].slice(0, CHEM_RECENT_COLORS_MAX);
    try {
      localStorage.setItem(CHEM_RECENT_COLORS_KEY, JSON.stringify(next));
    } catch (_) {
      /* quota / private mode */
    }
    renderRecentColors();
  }

  function renderRecentColors() {
    const block = document.getElementById("chemColorRecentBlock");
    const box = document.getElementById("chemColorRecent");
    if (!block || !box) return;
    const colors = loadRecentColors();
    box.innerHTML = "";
    if (!colors.length) {
      block.classList.add("hidden");
      return;
    }
    block.classList.remove("hidden");
    colors.forEach((color) => {
      const swatch = document.createElement("button");
      swatch.type = "button";
      swatch.className = "chem-color-recent-swatch";
      swatch.style.background = color;
      swatch.title = color;
      swatch.setAttribute("aria-label", "최근 색상 " + color);
      swatch.addEventListener("click", () => applyTextColor(color));
      box.appendChild(swatch);
    });
  }

  function applyTextColor(color) {
    const hex = syncColorInputs(color);
    applyEditorFormat("foreColor", hex);
    rememberRecentColor(hex);
    closeColorPalette();
  }

  function readCustomColor() {
    const hexInput = document.getElementById("chemColorHex");
    const fromHex = normalizeHexColor(hexInput && hexInput.value);
    if (fromHex) return fromHex;
    const r = document.getElementById("chemColorR");
    const g = document.getElementById("chemColorG");
    const b = document.getElementById("chemColorB");
    if (r && g && b) return rgbToHex(r.value, g.value, b.value);
    const picker = document.getElementById("chemColorPicker");
    return normalizeHexColor(picker && picker.value) || "#00f5d4";
  }

  /** 설명·링크 가로폭을 렌더된 이미지 너비에 맞춤 */
  function syncModalContentWidth() {
    const img = document.getElementById("chemistryImage");
    const desc = document.getElementById("chemistryDescription");
    const links = document.getElementById("chemistryLinks");
    const width =
      img && img.style.display !== "none" && img.offsetWidth > 0
        ? Math.round(img.getBoundingClientRect().width) + "px"
        : "";
    if (desc) desc.style.width = width;
    if (links) links.style.width = width;
  }

  function openChemistryName(person, chemistryName) {
    const personData = chemistryDescriptions[person];
    if (!personData || !personData[chemistryName]) {
      console.warn("케미 데이터 없음:", person, chemistryName);
      return;
    }
    const data = personData[chemistryName];
    const { description, credit } = splitPhotoCredit(data.description);

    let formattedDescription = sanitizeChemistryHtml(description)
      .replace(/\*\*(.*?)\*\*/g, (match, p1) => {
        if (data.links && data.links[p1]) {
          return '<a href="' + data.links[p1] + '" target="_blank" rel="noopener" class="chem-highlight">' + p1 + "</a>";
        }
        return '<span class="chem-highlight">' + p1 + "</span>";
      })
      .replace(/\n/g, "<br>");

    const titleMember1 =
      (currentView && currentView.member1) || person;
    const titleMember2 =
      (currentView && currentView.member2) ||
      (() => {
        const m = String(chemistryName || "").match(/^(.+)과 (.+)의 케미명$/);
        if (!m) return person;
        return m[1] === person ? m[2] : m[1];
      })();
    document.getElementById("chemistryTitle").innerHTML = formatChemistryTitle(
      titleMember1,
      titleMember2
    );
    const img = document.getElementById("chemistryImage");
    const media = img && img.closest(".chem-modal-media");
    const creditEl = document.getElementById("chemistryCredit");
    const hasImage = Boolean(data.image);
    if (img) {
      img.onload = () => syncModalContentWidth();
      img.src = data.image || "";
      img.alt = pairTitleText(titleMember1, titleMember2);
      img.style.display = hasImage ? "block" : "none";
      if (hasImage && img.complete) syncModalContentWidth();
    }
    if (media) media.style.display = hasImage ? "block" : "none";
    if (creditEl) {
      if (credit && hasImage) {
        creditEl.textContent = "photo by. " + credit;
        creditEl.hidden = false;
      } else {
        creditEl.textContent = "";
        creditEl.hidden = true;
      }
    }
    document.getElementById("chemistryDescription").innerHTML = formattedDescription;
    if (!hasImage) syncModalContentWidth();

    const linkDiv = document.getElementById("chemistryLinks");
    linkDiv.innerHTML = "";
    if (data.links) {
      for (const [text, url] of Object.entries(data.links)) {
        if (!data.description.includes("**" + text + "**")) {
          const a = document.createElement("a");
          a.href = url;
          a.target = "_blank";
          a.rel = "noopener";
          a.className = "chem-extra-link";
          a.textContent = text;
          linkDiv.appendChild(a);
        }
      }
    }

    const adminActions = document.getElementById("chemistryAdminActions");
    if (adminActions) {
      adminActions.classList.toggle("hidden", !isAdmin);
    }

    const modal = document.getElementById("chemistryModal");
    modal.classList.remove("hidden");
    modal.classList.add("flex");
    document.body.style.overflow = "hidden";
    requestAnimationFrame(syncModalContentWidth);
  }

  function setAdminUi() {
    const btn = document.getElementById("chemAdminBtn");
    const hint = document.getElementById("chemAdminHint");
    if (btn) {
      if (!adminConfigured) {
        btn.classList.add("hidden");
      } else {
        btn.classList.remove("hidden");
        btn.textContent = isAdmin ? "관리자 로그아웃" : "관리자";
        btn.setAttribute("aria-pressed", isAdmin ? "true" : "false");
        btn.classList.toggle("is-active", isAdmin);
      }
    }
    if (hint) hint.classList.toggle("hidden", !isAdmin);
    if (pairMatrix && currentMode === "pair") {
      updateEmptyState(countChemCells(pairMatrix));
      buildTable(pairMatrix);
    }
  }

  function setLoginPasswordVisible(visible) {
    const input = document.getElementById("chemLoginPassword");
    const toggle = document.getElementById("chemLoginPasswordToggle");
    if (!input || !toggle) return;
    input.type = visible ? "text" : "password";
    toggle.setAttribute("aria-pressed", visible ? "true" : "false");
    toggle.setAttribute("aria-label", visible ? "비밀번호 숨기기" : "비밀번호 보기");
    const icon = toggle.querySelector(".material-symbols-outlined");
    if (icon) icon.textContent = visible ? "visibility_off" : "visibility";
  }

  function openLoginModal() {
    const modal = document.getElementById("chemistryLoginModal");
    const err = document.getElementById("chemLoginError");
    const input = document.getElementById("chemLoginPassword");
    if (err) {
      err.hidden = true;
      err.textContent = "";
    }
    if (input) input.value = "";
    setLoginPasswordVisible(false);
    if (modal) {
      modal.classList.remove("hidden");
      modal.classList.add("flex");
      document.body.style.overflow = "hidden";
    }
    if (input) input.focus();
  }

  function closeLoginModal() {
    const modal = document.getElementById("chemistryLoginModal");
    if (!modal) return;
    modal.classList.add("hidden");
    modal.classList.remove("flex");
    if (document.getElementById("chemistryModal")?.classList.contains("hidden")
        && document.getElementById("chemistryEditModal")?.classList.contains("hidden")) {
      document.body.style.overflow = "";
    }
  }

  function createLinkRow(text, url) {
    const row = document.createElement("div");
    row.className = "chem-edit-link-row";
    row.innerHTML =
      '<input type="text" class="chem-link-text" placeholder="표시 텍스트" autocomplete="off">' +
      '<input type="url" class="chem-link-url" placeholder="https://" autocomplete="off">' +
      '<button type="button" class="chem-edit-link-remove" aria-label="링크 삭제">' +
      '<span class="material-symbols-outlined" style="font-size:18px">close</span></button>';
    row.querySelector(".chem-link-text").value = text || "";
    row.querySelector(".chem-link-url").value = url || "";
    row.querySelector(".chem-edit-link-remove").addEventListener("click", () => row.remove());
    return row;
  }

  function renderLinkRows(links) {
    const box = document.getElementById("chemEditLinks");
    if (!box) return;
    box.innerHTML = "";
    const entries = links && typeof links === "object" ? Object.entries(links) : [];
    if (!entries.length) {
      box.appendChild(createLinkRow("", ""));
      return;
    }
    entries.forEach(([text, url]) => box.appendChild(createLinkRow(text, url)));
  }

  function collectLinksJson() {
    const box = document.getElementById("chemEditLinks");
    const links = {};
    if (!box) return "{}";
    box.querySelectorAll(".chem-edit-link-row").forEach((row) => {
      const text = (row.querySelector(".chem-link-text")?.value || "").trim();
      const url = (row.querySelector(".chem-link-url")?.value || "").trim();
      if (text && url) links[text] = url;
    });
    return JSON.stringify(links);
  }

  function closeColorPalette() {
    const palette = document.getElementById("chemColorPalette");
    const toggle = document.getElementById("chemColorToggle");
    if (palette) palette.classList.add("hidden");
    if (toggle) toggle.setAttribute("aria-expanded", "false");
  }

  function applyEditorFormat(command, value) {
    const editor = document.getElementById("chemEditDescription");
    if (!editor) return;
    editor.focus();
    try {
      if (command === "foreColor") {
        try {
          document.execCommand("styleWithCSS", false, true);
        } catch (_) {
          /* older browsers */
        }
        document.execCommand("foreColor", false, value || "#00f5d4");
        normalizeFontColorsIn(editor);
        return;
      }
      document.execCommand(command, false, value || null);
    } catch (_) {
      /* ignore unsupported command */
    }
  }

  function setEditImagePreview(url) {
    const wrap = document.getElementById("chemEditImagePreviewWrap");
    const img = document.getElementById("chemEditImagePreview");
    if (!wrap || !img) return;
    const src = (url || "").trim();
    if (!src) {
      img.removeAttribute("src");
      wrap.classList.add("hidden");
      return;
    }
    img.src = src;
    wrap.classList.remove("hidden");
  }

  function openEditForm(member1, member2, label, description, imageUrl, links, labelTone) {
    editingPair = { member1, member2 };
    closeModal();
    closeColorPalette();
    const modal = document.getElementById("chemistryEditModal");
    const title = document.getElementById("chemistryEditTitle");
    const err = document.getElementById("chemEditError");
    const editor = document.getElementById("chemEditDescription");
    document.getElementById("chemEditMember1").value = member1;
    document.getElementById("chemEditMember2").value = member2;
    document.getElementById("chemEditLabel").value = label || "";
    setLabelToneInputs(labelTone);
    if (editor) editor.innerHTML = descriptionToEditorHtml(description || "");
    document.getElementById("chemEditImage").value = "";
    const clearCb = document.getElementById("chemEditClearImage");
    if (clearCb) clearCb.checked = false;
    setEditImagePreview(imageUrl || "");
    renderLinkRows(links || {});
    if (title) title.textContent = pairTitleText(member1, member2);
    if (err) {
      err.hidden = true;
      err.textContent = "";
    }
    if (modal) {
      modal.classList.remove("hidden");
      modal.classList.add("flex");
      document.body.style.overflow = "hidden";
    }
  }

  function closeEditModal() {
    const modal = document.getElementById("chemistryEditModal");
    if (!modal) return;
    modal.classList.add("hidden");
    modal.classList.remove("flex");
    document.body.style.overflow = "";
  }

  async function refreshOverlaysAndTable() {
    try {
      const res = await chemFetch("/api/chemistry");
      if (res.ok) {
        applyDbToTable(await res.json());
        return;
      }
    } catch (_) {
      /* fall through */
    }
    applyDbToTable([]);
  }

  async function deleteCurrentChemistry() {
    const member1 = (currentView && currentView.member1)
      || document.getElementById("chemEditMember1")?.value
      || (editingPair && editingPair.member1);
    const member2 = (currentView && currentView.member2)
      || document.getElementById("chemEditMember2")?.value
      || (editingPair && editingPair.member2);
    if (!member1 || !member2) return;
    if (!confirm(pairTitleText(member1, member2) + " 케미를 삭제할까요?")) return;
    try {
      const qs = new URLSearchParams({ member1, member2 });
      const res = await chemFetch("/api/chemistry?" + qs.toString(), { method: "DELETE" });
      if (!res.ok) {
        alert(await readChemError(res, "삭제에 실패했습니다."));
        if (res.status === 403) {
          isAdmin = false;
          setAdminToken("");
          setAdminUi();
        }
        return;
      }
      closeEditModal();
      closeModal();
      currentView = null;
      await refreshOverlaysAndTable();
    } catch (_) {
      alert("삭제에 실패했습니다.");
    }
  }

  function bindAdminUi() {
    const adminBtn = document.getElementById("chemAdminBtn");
    if (adminBtn) {
      adminBtn.addEventListener("click", async () => {
        if (isAdmin) {
          await chemFetch("/api/chemistry/admin/logout", { method: "POST" });
          isAdmin = false;
          setAdminToken("");
          setAdminUi();
          return;
        }
        openLoginModal();
      });
    }

    const loginForm = document.getElementById("chemistryLoginForm");
    if (loginForm) {
      loginForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const err = document.getElementById("chemLoginError");
        const password = document.getElementById("chemLoginPassword").value;
        try {
          const res = await chemFetch("/api/chemistry/admin/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ password })
          });
          if (!res.ok) {
            if (err) {
              err.textContent = await readChemError(res, "비밀번호가 올바르지 않습니다.");
              err.hidden = false;
            }
            return;
          }
          const data = await res.json();
          setAdminToken(data && data.token ? data.token : "");
          isAdmin = true;
          closeLoginModal();
          setAdminUi();
        } catch (_) {
          if (err) {
            err.textContent = "로그인에 실패했습니다.";
            err.hidden = false;
          }
        }
      });
    }

    const passwordToggle = document.getElementById("chemLoginPasswordToggle");
    if (passwordToggle) {
      passwordToggle.addEventListener("click", () => {
        const input = document.getElementById("chemLoginPassword");
        const visible = input && input.type === "password";
        setLoginPasswordVisible(Boolean(visible));
      });
    }

    document.querySelectorAll(".chem-login-close").forEach((btn) => {
      btn.addEventListener("click", closeLoginModal);
    });

    const editBtn = document.getElementById("chemEditBtn");
    if (editBtn) {
      editBtn.addEventListener("click", () => {
        if (!currentView && !editingPair) return;
        const member1 = (currentView && currentView.member1) || editingPair.member1;
        const member2 = (currentView && currentView.member2) || editingPair.member2;
        const person = (currentView && currentView.person) || member1;
        const key = (currentView && currentView.key) || chemKey(member1, member2);
        const bucket = chemistryDescriptions[person] || {};
        const data = bucket[key] || {};
        let cellLabel = (currentView && currentView.label) || "";
        if (!cellLabel && pairMatrix) {
          const ri = memberIndex(member1);
          const ci = memberIndex(member2);
          const cell = pairMatrix[ri] && pairMatrix[ri][ci];
          if (cell && cell.label) cellLabel = String(cell.label).replace(/\n/g, " ");
        }
        let labelTone =
          (currentView && currentView.labelTone) || data.labelTone || "NONE";
        if ((!currentView || !currentView.labelTone) && pairMatrix) {
          const ri = memberIndex(member1);
          const ci = memberIndex(member2);
          const cell = pairMatrix[ri] && pairMatrix[ri][ci];
          if (cell && cell.labelTone) labelTone = cell.labelTone;
        }
        openEditForm(
          member1,
          member2,
          cellLabel,
          data.description || "",
          data.image || "",
          data.links || {},
          labelTone
        );
      });
    }

    const deleteBtn = document.getElementById("chemDeleteBtn");
    if (deleteBtn) deleteBtn.addEventListener("click", deleteCurrentChemistry);
    const editDeleteBtn = document.getElementById("chemEditDeleteBtn");
    if (editDeleteBtn) editDeleteBtn.addEventListener("click", deleteCurrentChemistry);

    const addLinkBtn = document.getElementById("chemAddLinkBtn");
    if (addLinkBtn) {
      addLinkBtn.addEventListener("click", () => {
        const box = document.getElementById("chemEditLinks");
        if (box) box.appendChild(createLinkRow("", ""));
      });
    }

    const palette = document.getElementById("chemColorPalette");
    const swatchBox = document.getElementById("chemColorSwatches");
    if (swatchBox && !swatchBox.dataset.ready) {
      swatchBox.dataset.ready = "1";
      CHEM_COLOR_PALETTE.forEach((color, index) => {
        const sLabel = "S" + (index + 1);
        const memberName = MEMBERS[index] || "";
        const swatch = document.createElement("button");
        swatch.type = "button";
        swatch.className = "chem-color-swatch";
        swatch.style.background = color;
        swatch.title = sLabel + (memberName ? " " + memberName : "") + " (" + color + ")";
        swatch.setAttribute("aria-label", sLabel + (memberName ? " " + memberName : "") + " 상징색");
        swatch.dataset.s = String(index + 1);
        const rgb = hexToRgb(color);
        if (rgb) {
          // 밝은 배경은 어두운 숫자, 어두운 배경은 밝은 숫자
          const luma = (0.2126 * rgb.r + 0.7152 * rgb.g + 0.0722 * rgb.b) / 255;
          swatch.classList.toggle("is-dark", luma < 0.55);
        }
        swatch.addEventListener("click", () => applyTextColor(color));
        swatchBox.appendChild(swatch);
      });
    }

    syncColorInputs("#00f5d4");
    renderRecentColors();

    const colorPicker = document.getElementById("chemColorPicker");
    if (colorPicker) {
      // 피커는 미리보기만 — 확인 버튼으로 적용
      colorPicker.addEventListener("input", () => syncColorInputs(colorPicker.value));
      colorPicker.addEventListener("change", () => syncColorInputs(colorPicker.value));
    }

    const hexInput = document.getElementById("chemColorHex");
    if (hexInput) {
      hexInput.addEventListener("input", () => {
        const hex = normalizeHexColor(hexInput.value);
        if (hex) syncColorInputs(hex);
      });
      hexInput.addEventListener("change", () => {
        const hex = normalizeHexColor(hexInput.value);
        if (hex) syncColorInputs(hex);
      });
      hexInput.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          const hex = normalizeHexColor(hexInput.value);
          if (hex) syncColorInputs(hex);
        }
      });
    }

    ["chemColorR", "chemColorG", "chemColorB"].forEach((id) => {
      const el = document.getElementById(id);
      if (!el) return;
      const syncRgb = () => {
        const r = document.getElementById("chemColorR");
        const g = document.getElementById("chemColorG");
        const b = document.getElementById("chemColorB");
        syncColorInputs(rgbToHex(r && r.value, g && g.value, b && b.value));
      };
      el.addEventListener("input", syncRgb);
      el.addEventListener("change", syncRgb);
    });

    const colorApply = document.getElementById("chemColorApply");
    if (colorApply) {
      colorApply.addEventListener("click", () => applyTextColor(readCustomColor()));
    }
    const colorReset = document.getElementById("chemColorReset");
    if (colorReset) {
      colorReset.addEventListener("click", () => {
        applyEditorFormat("removeFormat");
        syncColorInputs("#00f5d4");
        closeColorPalette();
      });
    }

    document.querySelectorAll(".chem-fmt-btn[data-fmt]").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.preventDefault();
        const cmd = btn.getAttribute("data-fmt");
        if (cmd) applyEditorFormat(cmd);
      });
    });

    const colorToggle = document.getElementById("chemColorToggle");
    if (colorToggle && palette) {
      colorToggle.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        const willOpen = palette.classList.contains("hidden");
        palette.classList.toggle("hidden", !willOpen);
        colorToggle.setAttribute("aria-expanded", willOpen ? "true" : "false");
        if (willOpen) {
          syncColorInputs((document.getElementById("chemColorBar") || {}).style.background || "#00f5d4");
          renderRecentColors();
        }
      });
    }

    document.addEventListener("click", (e) => {
      const wrap = e.target.closest(".chem-color-wrap");
      if (!wrap) closeColorPalette();
    });

    document.querySelectorAll(".chem-edit-close, .chem-edit-cancel").forEach((btn) => {
      btn.addEventListener("click", () => {
        closeColorPalette();
        closeEditModal();
      });
    });

    const editImageInput = document.getElementById("chemEditImage");
    if (editImageInput) {
      editImageInput.addEventListener("change", () => {
        const clearCb = document.getElementById("chemEditClearImage");
        const file = editImageInput.files && editImageInput.files[0];
        if (file) {
          if (clearCb) clearCb.checked = false;
          setEditImagePreview(URL.createObjectURL(file));
        }
      });
    }
    const clearImageCb = document.getElementById("chemEditClearImage");
    if (clearImageCb) {
      clearImageCb.addEventListener("change", () => {
        if (clearImageCb.checked) {
          const input = document.getElementById("chemEditImage");
          if (input) input.value = "";
          setEditImagePreview("");
        }
      });
    }

    const editForm = document.getElementById("chemistryEditForm");
    if (editForm) {
      editForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const err = document.getElementById("chemEditError");
        const fd = new FormData();
        fd.append("member1", document.getElementById("chemEditMember1").value);
        fd.append("member2", document.getElementById("chemEditMember2").value);
        fd.append("label", document.getElementById("chemEditLabel").value);
        fd.append("description", getEditorDescriptionHtml());
        fd.append("labelTone", selectedLabelTone());
        // 체크했을 때만 true — 미전송 시 서버 기본값 false로 기존 사진 유지
        const clearChecked = Boolean(document.getElementById("chemEditClearImage")?.checked);
        fd.append("clearImage", clearChecked ? "true" : "false");
        fd.append("linksJson", collectLinksJson());
        const file = document.getElementById("chemEditImage").files[0];
        if (file && !clearChecked) fd.append("image", file);
        try {
          const res = await chemFetch("/api/chemistry", { method: "POST", body: fd });
          if (!res.ok) {
            const msg = await readChemError(res, "저장에 실패했습니다.");
            if (err) {
              err.textContent = msg;
              err.hidden = false;
            }
            if (res.status === 403) {
              isAdmin = false;
              setAdminToken("");
              setAdminUi();
            }
            return;
          }
          closeEditModal();
          await refreshOverlaysAndTable();
        } catch (_) {
          if (err) {
            err.textContent = "저장에 실패했습니다.";
            err.hidden = false;
          }
        }
      });
    }
  }

  function closeModal() {
    const modal = document.getElementById("chemistryModal");
    modal.classList.add("hidden");
    modal.classList.remove("flex");
    document.body.style.overflow = "";
    const desc = document.getElementById("chemistryDescription");
    const links = document.getElementById("chemistryLinks");
    if (desc) desc.style.width = "";
    if (links) links.style.width = "";
  }

  window.addEventListener("resize", () => {
    const modal = document.getElementById("chemistryModal");
    if (modal && !modal.classList.contains("hidden")) {
      syncModalContentWidth();
    }
  });

  let pairMatrix = null;
  let currentMode = "pair";

  function setModeButtons(mode) {
    document.querySelectorAll(".chem-mode-toggle .chem-mode-btn[data-mode]").forEach((btn) => {
      const active = btn.dataset.mode === mode;
      btn.classList.toggle("is-active", active);
      btn.setAttribute("aria-pressed", active ? "true" : "false");
    });
  }

  function showPairView() {
    const pairView = document.getElementById("chemPairView");
    const trioView = document.getElementById("chemTrioView");
    if (pairView) pairView.classList.remove("hidden");
    if (trioView) trioView.classList.add("hidden");
  }

  function showTrioPlaceholder() {
    const pairView = document.getElementById("chemPairView");
    const trioView = document.getElementById("chemTrioView");
    if (pairView) pairView.classList.add("hidden");
    if (trioView) trioView.classList.remove("hidden");
  }

  function setMode(mode) {
    currentMode = mode === "trio" ? "trio" : "pair";
    setModeButtons(currentMode);
    if (currentMode === "trio") {
      // 3인 케미 구조화 데이터 없음(이미지 몇 장만) → 플레이스홀더
      showTrioPlaceholder();
      return;
    }
    showPairView();
    if (pairMatrix) buildTable(pairMatrix);
  }

  function bindModeToggle() {
    document.addEventListener("click", (e) => {
      const btn = e.target.closest(".chem-mode-btn[data-mode]");
      if (!btn) return;
      e.preventDefault();
      setMode(btn.getAttribute("data-mode"));
    });
  }

  function closeLegendPanel() {
    const panel = document.getElementById("chemLegendPanel");
    const toggle = document.getElementById("chemLegendToggle");
    if (panel) panel.classList.add("hidden");
    if (toggle) toggle.setAttribute("aria-expanded", "false");
  }

  function bindLegendToggle() {
    const toggle = document.getElementById("chemLegendToggle");
    const panel = document.getElementById("chemLegendPanel");
    if (!toggle || !panel) return;
    toggle.addEventListener("click", (e) => {
      e.preventDefault();
      e.stopPropagation();
      const willOpen = panel.classList.contains("hidden");
      panel.classList.toggle("hidden", !willOpen);
      toggle.setAttribute("aria-expanded", willOpen ? "true" : "false");
    });
    document.addEventListener("click", (e) => {
      if (!e.target.closest(".chem-legend-wrap")) closeLegendPanel();
    });
  }

  document.addEventListener("DOMContentLoaded", async () => {
    const closeBtn = document.querySelector("#chemistryModal .chem-close");
    if (closeBtn) closeBtn.addEventListener("click", closeModal);

    document.addEventListener("keydown", (e) => {
      if (e.key !== "Escape") return;
      closeLegendPanel();
      closeEditModal();
      closeLoginModal();
      closeModal();
    });

    bindModeToggle();
    bindLegendToggle();
    bindAdminUi();

    try {
      const statusRes = await chemFetch("/api/chemistry/admin/status");
      if (statusRes.ok) {
        const status = await statusRes.json();
        adminConfigured = Boolean(status.configured);
        isAdmin = Boolean(status.admin);
        if (!isAdmin) setAdminToken("");
      }
    } catch (_) {
      adminConfigured = false;
      isAdmin = false;
    }

    try {
      const res = await chemFetch("/api/chemistry");
      if (!res.ok) throw new Error("API " + res.status);
      applyDbToTable(await res.json());
      setAdminUi();
      setMode("pair");
    } catch (err) {
      console.error("케미 데이터 로드 실패", err);
      applyDbToTable([]);
      const tbody = document.getElementById("chemTbody");
      if (tbody) {
        tbody.innerHTML =
          '<tr><td colspan="25" class="text-center text-[#b9cac4] py-8">케미 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</td></tr>';
      }
      const empty = document.getElementById("chemEmptyState");
      if (empty) empty.classList.add("hidden");
      setAdminUi();
    }
  });

  window.openChemistryName = openChemistryName;
  window.__chemSetMode = setMode;
})();
