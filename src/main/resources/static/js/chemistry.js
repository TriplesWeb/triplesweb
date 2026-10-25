/**
 * tripleS 케미명 매트릭스
 * 데이터 소스: GET /api/chemistry (DB, Artist FK). 멤버 목록·색만 프론트 상수.
 * 프로필 링크는 API의 artistAId/artistBId를 우선 사용한다.
 */
(function () {
  const BASE = "/uploads/chemistry";

  const catalog = window.TripleSMembers || {};
  const MEMBERS = catalog.NAMES_KO || [];
  const MEMBER_EN = catalog.NAME_EN_BY_KO || {};
  const MEMBER_COLORS = catalog.COLORS || [];

  const CHEM_LANG =
    (document.documentElement.getAttribute("data-chem-lang") ||
      document.documentElement.lang ||
      "ko")
      .toLowerCase()
      .startsWith("en")
      ? "en"
      : "ko";
  const IS_EN = CHEM_LANG === "en";

  // 공개 UI만 페이지 언어 따름. 관리자 편집/로그인은 항상 한국어.
  const I18N = IS_EN
    ? {
        emptyHint: "No chemistry names have been registered.",
        emptyAdminHint: "관리자 모드에서 빈 칸(+)을 눌러 케미를 추가할 수 있어요.",
        addChemTitle: "케미 추가",
        loadFailed: "Couldn't load chemistry data. Please try again shortly.",
        noChemData: "No chemistry data:",
        adminNeeded: "관리자 로그인이 필요합니다. 다시 로그인해 주세요.",
        adminLogout: "로그아웃",
        admin: "관리자",
        editTitle: "케미 편집",
        editTitleNew: "케미 추가"
      }
    : {
        emptyHint: "아직 등록된 케미명이 없습니다.",
        emptyAdminHint: "관리자 모드에서 빈 칸(+)을 눌러 케미를 추가할 수 있어요.",
        addChemTitle: "케미 추가",
        loadFailed: "케미 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
        noChemData: "케미 데이터 없음:",
        adminNeeded: "관리자 로그인이 필요합니다. 다시 로그인해 주세요.",
        adminLogout: "로그아웃",
        admin: "관리자",
        editTitle: "케미 편집",
        editTitleNew: "케미 추가"
      };

  /** DB 응답으로 채우는 설명 맵. person -> { chemKey -> {description,image,links} } */
  let chemistryDescriptions = {};
  /** 한글 이름 → Artist.artistId (API artistAId/artistBId로 채움) */
  let artistIdByName = Object.create(null);
  /** 3인 이상 케미 목록 */
  let groupEntries = [];
  /** 편집 중 그룹 멤버 (pair 편집과 구분) */
  let editingGroupMembers = null;
  let currentGroupView = null;

  function memberLabel(name) {
    if (!name) return "";
    if (IS_EN && MEMBER_EN[name]) return MEMBER_EN[name];
    return name;
  }

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
    const label = memberLabel(name);
    const escaped = escapeHtml(label);
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
    return memberLabel(a) + " x " + memberLabel(b);
  }

  function formatLabel(label) {
    return escapeHtml(label).replace(/\n/g, "<br>");
  }

  /** 케미명 여러 개(줄바꿈)를 배열로 */
  function splitChemLabels(label) {
    return String(label || "")
      .split(/\n+/)
      .map((s) => s.replace(/\s+/g, " ").trim())
      .filter(Boolean);
  }

  function chemLabelPlain(label) {
    return splitChemLabels(label).join(" · ");
  }

  /** 모달 제목 — 복수 케미명은 · 로 나란히 */
  function formatModalChemLabel(label) {
    const names = splitChemLabels(label);
    if (!names.length) return "";
    if (names.length === 1) return escapeHtml(names[0]);
    return names
      .map((n) => '<span class="chem-title-name">' + escapeHtml(n) + "</span>")
      .join('<span class="chem-title-sep" aria-hidden="true"> · </span>');
  }

  /**
   * 연속 <br>는 문단 간격용으로 유지하고, 불필요한 공백만 정리한다.
   * (예전엔 문장 중간 개행을 공백으로 바꿨는데, 에디터에서 Enter로 줄 간격을
   * 벌리려는 의도와 충돌해서 개행은 그대로 둔다.)
   */
  function softenDescriptionBreaks(html) {
    return String(html || "")
      .replace(/(?:\s*<br\s*\/?>\s*){3,}/gi, "<br><br>")
      .replace(/^(?:\s*<br\s*\/?>\s*)+/i, "")
      .replace(/(?:\s*<br\s*\/?>\s*)+$/i, "");
  }

  let isAdmin = false;
  let adminConfigured = false;
  let dbEntries = [];
  let editingPair = null;
  let currentView = null;

  const CHEM_MAX_IMAGES = 10;
  /** @type {{id:string, kind:'url'|'file', url?:string, file?:File, preview?:string}[]} */
  let editImageItems = [];
  let carouselUrls = [];
  let carouselIndex = 0;
  let carouselTouchX = null;
  let carouselAlt = "";

  const auth = window.AboutAdminAuth;

  /** localStorage — ABOUT 전 페이지·탭 간 관리자 로그인 유지 */
  function getAdminToken() {
    return auth ? auth.getToken() : "";
  }

  function setAdminToken(token) {
    if (auth) auth.setToken(token);
  }

  /** 쿠키가 막혀도 동작하도록 관리자 토큰 헤더를 붙인다. */
  function chemFetch(url, options) {
    const opts = Object.assign({ credentials: "same-origin" }, options || {});
    opts.headers = auth ? auth.applyAuthHeaders(opts.headers || {}) : new Headers(opts.headers || {});
    return fetch(url, opts);
  }

  async function readChemError(res, fallback) {
    if (res.status === 401) return "비밀번호가 올바르지 않습니다.";
    try {
      const text = await res.text();
      if (!text) return fallback;
      try {
        const json = JSON.parse(text);
        if (json && json.message) return json.message;
        if (res.status === 403) return I18N.adminNeeded;
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
      hint.textContent = isAdmin ? I18N.emptyAdminHint : I18N.emptyHint;
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

      const labelKo = String(entry.label || "").trim();
      if (!labelKo) return;

      const labelEn = String(entry.labelEn || "").trim();
      // 영문 페이지: labelEn 있으면 사용, 없으면 한국어 케미명 폴백
      const displayLabel = IS_EN ? (labelEn ? entry.labelEn : entry.label) : entry.label;
      const descKo = entry.description || "";
      const descEn = entry.descriptionEn || "";

      const images = normalizeImageList(entry);
      const cross = Boolean(entry.crossFlag);
      const labelTone = normalizeLabelTone(entry.labelTone);
      const links = entry.links && typeof entry.links === "object" ? entry.links : {};

      [[ia, ib], [ib, ia]].forEach(([ri, ci]) => {
        const person = MEMBERS[ri];
        const other = MEMBERS[ci];
        const key = chemKey(person, other);
        matrix[ri][ci] = {
          type: "chem",
          label: displayLabel,
          person: person,
          key: key,
          cross: cross,
          labelTone: labelTone,
          dbId: entry.id
        };
        const payload = {
          description: descKo,
          descriptionEn: descEn,
          image: images[0] || "",
          images: images,
          label: entry.label || "",
          labelEn: entry.labelEn || "",
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
    if (currentMode === "pair") {
      buildTable(pairMatrix);
    } else if (currentMode === "trio") {
      renderTrioResults(
        window.MemberPortraitPicker
          ? MemberPortraitPicker.selectedValues(document.getElementById("chemTrioView"))
          : []
      );
    }
  }

  function buildTable(matrix) {
    const thead = document.getElementById("chemThead");
    const tbody = document.getElementById("chemTbody");
    if (!thead || !tbody) return;

    let headHtml = '<tr><th class="chem-corner"><img src="' + BASE + '/logo.png" alt="tripleS"></th>';
    MEMBERS.forEach((name, i) => {
      const color = MEMBER_COLORS[i] || "#00f5d4";
      const label = memberLabel(name);
      headHtml +=
        '<th class="chem-col-head" style="--member-color:' + color + '">' +
        '<a href="' + memberHref(i) + '" title="' + escapeHtml(label) + '" class="chem-member-link">' +
        '<span class="chem-member-name">' + escapeHtml(label) + "</span>" +
        "</a></th>";
    });
    headHtml += "</tr>";
    thead.innerHTML = headHtml;

    let bodyHtml = "";
    matrix.forEach((row, ri) => {
      const color = MEMBER_COLORS[ri] || "#00f5d4";
      const rowLabel = memberLabel(MEMBERS[ri]);
      bodyHtml +=
        '<tr><th class="chem-row-head" style="--member-color:' + color + '">' +
        '<a href="' + memberHref(ri) + '" title="' + escapeHtml(rowLabel) + '" class="chem-member-link">' +
        '<span class="chem-member-name">' + escapeHtml(rowLabel) + "</span>" +
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
            '" tabindex="0" role="button" title="' +
            escapeHtml(I18N.addChemTitle) +
            '">+</td>';
        } else {
          bodyHtml += '<td class="chem-empty"></td>';
        }
      });
      bodyHtml += "</tr>";
    });
    tbody.innerHTML = bodyHtml;
    fillPairFilterOptions();

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
          label:
            (cell && cell.label) ||
            (el.textContent || "").replace(/\s+/g, " ").trim(),
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
      const open = () =>
        openEditForm(el.dataset.m1, el.dataset.m2, "", "", "", "", "", {}, "NONE");
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

  function isTransparentColor(value) {
    const raw = String(value || "").trim().toLowerCase();
    if (!raw || raw === "transparent") return true;
    if (raw === "rgba(0, 0, 0, 0)" || raw === "rgba(0,0,0,0)") return true;
    return false;
  }

  function extractElementBackground(el) {
    if (!el || el.nodeType !== Node.ELEMENT_NODE || !el.style) return "";
    const bg = el.style.backgroundColor || "";
    if (bg && !isTransparentColor(bg)) return bg;
    return "";
  }

  function applySpanColors(span, color, background) {
    if (color) span.style.color = color;
    if (background && !isTransparentColor(background)) {
      span.style.backgroundColor = background;
    }
  }

  function normalizeFontColorsIn(root) {
    if (!root) return;
    root.querySelectorAll("font").forEach((font) => {
      const span = document.createElement("span");
      applySpanColors(span, extractElementColor(font), extractElementBackground(font));
      while (font.firstChild) span.appendChild(font.firstChild);
      font.replaceWith(span);
    });
  }

  function sanitizeChemistryHtml(html) {
    if (window.ChemistrySanitize && typeof window.ChemistrySanitize.sanitize === "function") {
      return window.ChemistrySanitize.sanitize(html);
    }
    return String(html || "");
  }

  function descriptionToEditorHtml(description) {
    const raw = String(description || "");
    if (/<(b|strong|i|em|u|span|br|a|font|mark)\b/i.test(raw)) {
      return sanitizeChemistryHtml(raw.replace(/\n/g, "<br>"));
    }
    return escapeHtml(raw).replace(/\n/g, "<br>");
  }

  function getEditorDescriptionHtml(editorId) {
    const editor = document.getElementById(editorId || "chemEditDescription");
    if (!editor) return "";
    normalizeFontColorsIn(editor);
    return sanitizeChemistryHtml(editor.innerHTML);
  }

  function activeDescriptionEditor() {
    const active = document.activeElement;
    if (active && active.id === "chemEditDescriptionEn") return active;
    if (active && active.id === "chemEditDescription") return active;
    const ko = document.getElementById("chemEditDescription");
    const en = document.getElementById("chemEditDescriptionEn");
    if (en && document.getElementById("chemistryEditModal")?.contains(active)) {
      /* keep last focused if possible — default KO */
    }
    return ko || en;
  }

  /** 표 칸 케미명 글자색 (NONE=공식 흰) */
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
  const CHEM_RECENT_COLORS_MAX = 24;
  const CHEM_PINNED_COLORS_KEY = "chemPinnedTextColors";
  const CHEM_PINNED_COLORS_MAX = 12;
  const CHEM_DEFAULT_TEXT_COLOR = "#00f5d4";
  const CHEM_DEFAULT_HIGHLIGHT_COLOR = "#ffe566";
  // 관리자 팔레트 문구는 페이지 언어와 무관하게 한국어
  const CHEM_COLOR_PAGE_TITLES = {
    1: "멤버 상징색 (S1–S24)",
    2: "고정 · 최근"
  };
  let chemColorPage = 1;
  /** foreColor | hiliteColor — 공유 팔레트가 글자색/형광펜 중 어디에 적용될지 */
  let chemColorMode = "foreColor";

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

  function activeColorBarId() {
    return chemColorMode === "hiliteColor" ? "chemHighlightBar" : "chemColorBar";
  }

  function defaultColorForMode(mode) {
    return mode === "hiliteColor" ? CHEM_DEFAULT_HIGHLIGHT_COLOR : CHEM_DEFAULT_TEXT_COLOR;
  }

  function syncColorInputs(hex) {
    const normalized = normalizeHexColor(hex) || defaultColorForMode(chemColorMode);
    const rgb = hexToRgb(normalized);
    const picker = document.getElementById("chemColorPicker");
    const hexInput = document.getElementById("chemColorHex");
    const r = document.getElementById("chemColorR");
    const g = document.getElementById("chemColorG");
    const b = document.getElementById("chemColorB");
    const bar = document.getElementById(activeColorBarId());
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
    renderPage2Colors();
  }

  function loadPinnedColors() {
    try {
      const raw = JSON.parse(localStorage.getItem(CHEM_PINNED_COLORS_KEY) || "[]");
      if (!Array.isArray(raw)) return [];
      const seen = new Set();
      const out = [];
      raw.forEach((item) => {
        const hex = normalizeHexColor(item);
        if (!hex || seen.has(hex)) return;
        seen.add(hex);
        out.push(hex);
      });
      return out.slice(0, CHEM_PINNED_COLORS_MAX);
    } catch (_) {
      return [];
    }
  }

  function savePinnedColors(colors) {
    try {
      localStorage.setItem(CHEM_PINNED_COLORS_KEY, JSON.stringify(colors.slice(0, CHEM_PINNED_COLORS_MAX)));
    } catch (_) {
      /* quota / private mode */
    }
  }

  function pinCustomColor(color) {
    const hex = normalizeHexColor(color);
    if (!hex) return false;
    const next = [hex, ...loadPinnedColors().filter((c) => c !== hex)].slice(0, CHEM_PINNED_COLORS_MAX);
    savePinnedColors(next);
    renderPage2Colors();
    return true;
  }

  function unpinColor(color) {
    const hex = normalizeHexColor(color);
    if (!hex) return;
    savePinnedColors(loadPinnedColors().filter((c) => c !== hex));
    renderPage2Colors();
  }

  function setColorPalettePage(page) {
    const next = page === 2 ? 2 : 1;
    chemColorPage = next;
    const page1 = document.getElementById("chemColorPage1");
    const page2 = document.getElementById("chemColorPage2");
    const title = document.getElementById("chemColorPageTitle");
    const indicator = document.getElementById("chemColorPageIndicator");
    const prev = document.getElementById("chemColorPagePrev");
    const nextBtn = document.getElementById("chemColorPageNext");
    if (page1) {
      page1.classList.toggle("hidden", next !== 1);
      page1.hidden = next !== 1;
    }
    if (page2) {
      page2.classList.toggle("hidden", next !== 2);
      page2.hidden = next !== 2;
    }
    if (title) title.textContent = CHEM_COLOR_PAGE_TITLES[next] || "";
    if (indicator) indicator.textContent = next + " / 2";
    if (prev) prev.disabled = next === 1;
    if (nextBtn) nextBtn.disabled = next === 2;
    if (next === 2) renderPage2Colors();
  }

  function resolveSwatchText(value, color, fallback) {
    if (typeof value === "function") return value(color);
    if (value != null && value !== "") return String(value);
    return fallback;
  }

  function createColorSwatch(color, opts) {
    const options = opts || {};
    const swatch = document.createElement("button");
    swatch.type = "button";
    swatch.className = "chem-color-swatch";
    swatch.style.background = color;
    swatch.title = resolveSwatchText(options.title, color, color);
    swatch.setAttribute("aria-label", resolveSwatchText(options.ariaLabel, color, color));
    const rgb = hexToRgb(color);
    if (rgb) {
      const luma = (0.2126 * rgb.r + 0.7152 * rgb.g + 0.0722 * rgb.b) / 255;
      swatch.classList.toggle("is-dark", luma < 0.55);
    }
    swatch.addEventListener("click", () => applyPaletteColor(color));
    if (typeof options.onContextMenu === "function") {
      swatch.addEventListener("contextmenu", (e) => {
        e.preventDefault();
        e.stopPropagation();
        options.onContextMenu(color);
      });
    }
    return swatch;
  }

  function renderSwatchList(boxId, emptyId, colors, opts) {
    const box = document.getElementById(boxId);
    const empty = document.getElementById(emptyId);
    if (!box) return;
    box.innerHTML = "";
    if (!colors.length) {
      box.classList.add("hidden");
      if (empty) empty.classList.remove("hidden");
      return;
    }
    box.classList.remove("hidden");
    if (empty) empty.classList.add("hidden");
    colors.forEach((color) => box.appendChild(createColorSwatch(color, opts)));
  }

  function renderPinnedColors() {
    renderSwatchList("chemColorPinned", "chemColorPinnedEmpty", loadPinnedColors(), {
      title: (c) => c + " (우클릭: 고정 해제)",
      ariaLabel: (c) => "고정 색상 " + c,
      onContextMenu: unpinColor
    });
  }

  function renderRecentColors() {
    renderSwatchList("chemColorRecent", "chemColorRecentEmpty", loadRecentColors(), {
      title: (c) => c,
      ariaLabel: (c) => "최근 색상 " + c
    });
  }

  function renderPage2Colors() {
    renderPinnedColors();
    renderRecentColors();
  }

  function applyPaletteColor(color) {
    const hex = syncColorInputs(color);
    applyEditorFormat(chemColorMode === "hiliteColor" ? "hiliteColor" : "foreColor", hex);
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
    return normalizeHexColor(picker && picker.value) || defaultColorForMode(chemColorMode);
  }

  /** /uploads/... 경로의 한글·공백 세그먼트를 안전하게 인코딩 */
  function encodeUploadUrl(url) {
    const raw = String(url || "").trim();
    if (!raw) return "";
    if (/^https?:\/\//i.test(raw) || raw.startsWith("blob:") || raw.startsWith("data:")) {
      return raw;
    }
    return raw
      .split("/")
      .map((seg, idx) => {
        if (idx === 0 && seg === "") return "";
        try {
          return encodeURIComponent(decodeURIComponent(seg));
        } catch (_) {
          return encodeURIComponent(seg);
        }
      })
      .join("/");
  }

  function normalizeImageList(entry) {
    if (!entry) return [];
    if (Array.isArray(entry.imageUrls) && entry.imageUrls.length) {
      return entry.imageUrls.map((u) => String(u || "").trim()).filter(Boolean);
    }
    if (Array.isArray(entry.images) && entry.images.length) {
      return entry.images.map((u) => String(u || "").trim()).filter(Boolean);
    }
    const single = entry.imageUrl || entry.image || "";
    return single ? [String(single).trim()] : [];
  }

  function setChemistryDetailImages(urls, altText) {
    carouselAlt = altText || "";
    carouselUrls = (urls || []).map((u) => encodeUploadUrl(u)).filter(Boolean);
    carouselIndex = 0;
    renderChemistryCarousel();
  }

  function hideChemistryDetailImage() {
    carouselUrls = [];
    carouselIndex = 0;
    carouselAlt = "";
    const media = document.getElementById("chemistryMedia");
    const track = document.getElementById("chemistryCarouselTrack");
    const dots = document.getElementById("chemistryCarouselDots");
    const count = document.getElementById("chemistryCarouselCount");
    const prev = document.getElementById("chemistryCarouselPrev");
    const next = document.getElementById("chemistryCarouselNext");
    if (media) media.hidden = true;
    if (track) track.innerHTML = "";
    if (dots) {
      dots.innerHTML = "";
      dots.hidden = true;
    }
    if (count) {
      count.textContent = "";
      count.hidden = true;
    }
    if (prev) prev.hidden = true;
    if (next) next.hidden = true;
    syncModalContentWidth();
  }

  function renderChemistryCarousel() {
    const media = document.getElementById("chemistryMedia");
    const track = document.getElementById("chemistryCarouselTrack");
    const dots = document.getElementById("chemistryCarouselDots");
    const count = document.getElementById("chemistryCarouselCount");
    const prev = document.getElementById("chemistryCarouselPrev");
    const next = document.getElementById("chemistryCarouselNext");
    if (!media || !track) return;

    if (!carouselUrls.length) {
      hideChemistryDetailImage();
      return;
    }

    media.hidden = false;
    const multi = carouselUrls.length > 1;
    track.innerHTML = carouselUrls
      .map(
        (src, i) =>
          '<div class="chem-carousel-slide' +
          (i === carouselIndex ? " is-active" : "") +
          '" data-i="' +
          i +
          '">' +
          '<img class="chem-modal-img" src="' +
          escapeHtml(src) +
          '" alt="' +
          escapeHtml(carouselAlt) +
          '" draggable="false">' +
          "</div>"
      )
      .join("");

    if (dots) {
      if (multi) {
        dots.hidden = false;
        dots.innerHTML = carouselUrls
          .map(
            (_, i) =>
              '<button type="button" class="chem-carousel-dot' +
              (i === carouselIndex ? " is-active" : "") +
              '" data-dot="' +
              i +
              '" role="tab" aria-label="' +
              (i + 1) +
              " / " +
              carouselUrls.length +
              '" aria-selected="' +
              (i === carouselIndex ? "true" : "false") +
              '"></button>'
          )
          .join("");
      } else {
        dots.innerHTML = "";
        dots.hidden = true;
      }
    }

    if (count) {
      if (multi) {
        count.hidden = false;
        count.textContent = carouselIndex + 1 + " / " + carouselUrls.length;
      } else {
        count.textContent = "";
        count.hidden = true;
      }
    }
    if (prev) {
      prev.hidden = !multi;
      prev.disabled = carouselIndex <= 0;
    }
    if (next) {
      next.hidden = !multi;
      next.disabled = carouselIndex >= carouselUrls.length - 1;
    }

    const activeImg = track.querySelector(".chem-carousel-slide.is-active img");
    if (activeImg) {
      activeImg.onerror = () => {
        if (carouselUrls.length <= 1) hideChemistryDetailImage();
      };
      if (activeImg.complete && activeImg.naturalWidth > 0) syncModalContentWidth();
      else activeImg.onload = () => syncModalContentWidth();
    }
  }

  function goChemistryCarousel(index) {
    if (!carouselUrls.length) return;
    const next = Math.max(0, Math.min(carouselUrls.length - 1, index));
    if (next === carouselIndex) return;
    carouselIndex = next;
    renderChemistryCarousel();
  }

  /**
   * 설명·링크 가로폭: 가능하면 이미지에 맞추되, 세로로 긴 사진 등
   * 이미지가 좁을 때는 본문 가독 최소폭(약 32~36자)을 유지한다.
   */
  const CHEM_DESC_MIN_PX = 600;

  function syncModalContentWidth() {
    const desc = document.getElementById("chemistryDescription");
    const links = document.getElementById("chemistryLinks");
    const panel = document.querySelector("#chemistryModal .chem-modal-panel");
    const media = document.getElementById("chemistryMedia");
    const img = media && !media.hidden
      ? media.querySelector(".chem-carousel-slide.is-active .chem-modal-img")
      : null;
    let width = "";
    if (img && img.naturalWidth > 0 && img.offsetWidth > 0) {
      const imgW = img.getBoundingClientRect().width;
      let panelInner = imgW;
      if (panel) {
        const cs = getComputedStyle(panel);
        const padX =
          (parseFloat(cs.paddingLeft) || 0) + (parseFloat(cs.paddingRight) || 0);
        panelInner = Math.max(0, panel.clientWidth - padX);
      }
      width = Math.round(Math.min(panelInner, Math.max(imgW, CHEM_DESC_MIN_PX))) + "px";
    }
    if (desc) desc.style.width = width;
    if (links) links.style.width = width;
  }

  function pickViewDescription(data) {
    if (!data) return "";
    if (IS_EN) {
      const en = data.descriptionEn;
      if (en != null && String(en).trim() !== "") return en;
      // 영문 없으면 관리자만 한국어 폴백 (일반 사용자는 빈 설명)
      return isAdmin ? data.description || "" : "";
    }
    return data.description || "";
  }

  function openChemistryName(person, chemistryName) {
    const personData = chemistryDescriptions[person];
    if (!personData || !personData[chemistryName]) {
      console.warn(I18N.noChemData, person, chemistryName);
      return;
    }
    const data = personData[chemistryName];
    const { description, credit } = splitPhotoCredit(pickViewDescription(data));

    let formattedDescription = softenDescriptionBreaks(
      sanitizeChemistryHtml(description)
        .replace(/\*\*(.*?)\*\*/g, (match, p1) => {
          if (data.links && data.links[p1]) {
            return '<a href="' + data.links[p1] + '" target="_blank" rel="noopener" class="chem-highlight">' + p1 + "</a>";
          }
          return '<span class="chem-highlight">' + p1 + "</span>";
        })
        .replace(/\n/g, "<br>")
    );

    const titleMember1 =
      (currentView && currentView.member1) || person;
    const titleMember2 =
      (currentView && currentView.member2) ||
      (() => {
        const m = String(chemistryName || "").match(/^(.+)과 (.+)의 케미명$/);
        if (!m) return person;
        return m[1] === person ? m[2] : m[1];
      })();
    const chemLabel = IS_EN
      ? String(data.labelEn || "").trim()
        ? data.labelEn
        : data.label || (currentView && currentView.label) || ""
      : (currentView && currentView.label) || data.label || "";
    const titleEl = document.getElementById("chemistryTitle");
    const pairEl = document.getElementById("chemistryPair");
    if (titleEl) {
      titleEl.innerHTML = chemLabel
        ? formatModalChemLabel(chemLabel)
        : formatChemistryTitle(titleMember1, titleMember2);
      titleEl.classList.toggle("is-multi", splitChemLabels(chemLabel).length > 1);
    }
    if (pairEl) {
      pairEl.innerHTML = formatChemistryTitle(titleMember1, titleMember2);
      pairEl.hidden = !chemLabel;
    }
    const creditEl = document.getElementById("chemistryCredit");
    const imageList = normalizeImageList(data);
    const altText = chemLabel
      ? chemLabelPlain(chemLabel) + " — " + pairTitleText(titleMember1, titleMember2)
      : pairTitleText(titleMember1, titleMember2);
    setChemistryDetailImages(imageList, altText);
    if (creditEl) {
      if (credit && imageList.length) {
        creditEl.textContent = "photo by. " + credit;
        creditEl.hidden = false;
      } else {
        creditEl.textContent = "";
        creditEl.hidden = true;
      }
    }
    document.getElementById("chemistryDescription").innerHTML = formattedDescription;

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
        btn.textContent = isAdmin ? I18N.adminLogout : I18N.admin;
        btn.setAttribute("aria-pressed", isAdmin ? "true" : "false");
        btn.classList.toggle("is-active", isAdmin);
      }
    }
    if (hint) hint.classList.toggle("hidden", !isAdmin);
    // 관리자 on/off 시 영문 미번역 폴백 칸 포함 여부가 바뀌므로 매트릭스를 다시 만든다
    if (dbEntries) {
      applyDbToTable(dbEntries);
    } else if (pairMatrix && currentMode === "pair") {
      updateEmptyState(countChemCells(pairMatrix));
      buildTable(pairMatrix);
    }
    if (currentMode === "trio") {
      renderTrioResults(
        window.MemberPortraitPicker
          ? MemberPortraitPicker.selectedValues(document.getElementById("chemTrioView"))
          : []
      );
    }
  }

  function setLoginPasswordVisible(visible) {
    const input = document.getElementById("chemLoginPassword");
    const toggle = document.getElementById("chemLoginPasswordToggle");
    if (!input || !toggle) return;
    input.type = visible ? "text" : "password";
    toggle.setAttribute("aria-pressed", visible ? "true" : "false");
    toggle.setAttribute(
      "aria-label",
      visible ? "비밀번호 숨기기" : "비밀번호 보기"
    );
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
    const highlightToggle = document.getElementById("chemHighlightToggle");
    if (palette) palette.classList.add("hidden");
    if (toggle) toggle.setAttribute("aria-expanded", "false");
    if (highlightToggle) highlightToggle.setAttribute("aria-expanded", "false");
  }

  function openColorPalette(mode) {
    const palette = document.getElementById("chemColorPalette");
    const toggle = document.getElementById("chemColorToggle");
    const highlightToggle = document.getElementById("chemHighlightToggle");
    if (!palette) return;
    chemColorMode = mode === "hiliteColor" ? "hiliteColor" : "foreColor";
    palette.classList.remove("hidden");
    if (toggle) toggle.setAttribute("aria-expanded", chemColorMode === "foreColor" ? "true" : "false");
    if (highlightToggle) {
      highlightToggle.setAttribute("aria-expanded", chemColorMode === "hiliteColor" ? "true" : "false");
    }
    const bar = document.getElementById(activeColorBarId());
    syncColorInputs((bar && bar.style.background) || defaultColorForMode(chemColorMode));
    setColorPalettePage(1);
    renderPage2Colors();
  }

  function toggleColorPalette(mode) {
    const palette = document.getElementById("chemColorPalette");
    if (!palette) return;
    const sameModeOpen = !palette.classList.contains("hidden") && chemColorMode === mode;
    if (sameModeOpen) {
      closeColorPalette();
      return;
    }
    openColorPalette(mode);
  }

  function applyEditorFormat(command, value) {
    const editor = activeDescriptionEditor();
    if (!editor) return;
    editor.focus();
    try {
      if (command === "foreColor" || command === "hiliteColor" || command === "backColor") {
        try {
          document.execCommand("styleWithCSS", false, true);
        } catch (_) {
          /* older browsers */
        }
        if (command === "foreColor") {
          document.execCommand("foreColor", false, value || CHEM_DEFAULT_TEXT_COLOR);
          normalizeFontColorsIn(editor);
          return;
        }
        const highlight = value || CHEM_DEFAULT_HIGHLIGHT_COLOR;
        const applied = document.execCommand("hiliteColor", false, highlight);
        if (!applied) document.execCommand("backColor", false, highlight);
        return;
      }
      document.execCommand(command, false, value || null);
    } catch (_) {
      /* ignore unsupported command */
    }
  }

  function revokeEditFilePreviews() {
    editImageItems.forEach((item) => {
      if (item.kind === "file" && item.preview) {
        try {
          URL.revokeObjectURL(item.preview);
        } catch (_) {
          /* ignore */
        }
      }
    });
  }

  function setEditImages(urls) {
    revokeEditFilePreviews();
    editImageItems = (urls || [])
      .map((u) => String(u || "").trim())
      .filter(Boolean)
      .slice(0, CHEM_MAX_IMAGES)
      .map((url, i) => ({ id: "u-" + i + "-" + url, kind: "url", url: url }));
    const clearCb = document.getElementById("chemEditClearImage");
    if (clearCb) clearCb.checked = false;
    const input = document.getElementById("chemEditImage");
    if (input) input.value = "";
    renderEditImageThumbs();
  }

  function clearEditImages() {
    revokeEditFilePreviews();
    editImageItems = [];
    const input = document.getElementById("chemEditImage");
    if (input) input.value = "";
    renderEditImageThumbs();
  }

  function renderEditImageThumbs() {
    const wrap = document.getElementById("chemEditImagePreviewWrap");
    const box = document.getElementById("chemEditImageThumbs");
    if (!wrap || !box) return;
    if (!editImageItems.length) {
      box.innerHTML = "";
      wrap.classList.add("hidden");
      return;
    }
    wrap.classList.remove("hidden");
    box.innerHTML = editImageItems
      .map((item, index) => {
        const src =
          item.kind === "file"
            ? item.preview || ""
            : encodeUploadUrl(item.url) || item.url || "";
        return (
          '<div class="chem-edit-thumb" data-idx="' +
          index +
          '">' +
          '<img src="' +
          escapeHtml(src) +
          '" alt="">' +
          '<button type="button" class="chem-edit-thumb-remove" data-remove-idx="' +
          index +
          '" aria-label="사진 삭제">×</button>' +
          "</div>"
        );
      })
      .join("");
  }

  function addEditImageFiles(fileList) {
    const files = Array.from(fileList || []).filter((f) => f && f.type && f.type.startsWith("image/"));
    if (!files.length) return;
    const clearCb = document.getElementById("chemEditClearImage");
    if (clearCb) clearCb.checked = false;
    const room = CHEM_MAX_IMAGES - editImageItems.length;
    if (room <= 0) {
      window.alert("사진은 최대 " + CHEM_MAX_IMAGES + "장까지 등록할 수 있습니다.");
      return;
    }
    files.slice(0, room).forEach((file, i) => {
      editImageItems.push({
        id: "f-" + Date.now() + "-" + i,
        kind: "file",
        file: file,
        preview: URL.createObjectURL(file)
      });
    });
    if (files.length > room) {
      window.alert("사진은 최대 " + CHEM_MAX_IMAGES + "장까지 등록할 수 있습니다.");
    }
    renderEditImageThumbs();
  }

  function removeEditImageAt(index) {
    const item = editImageItems[index];
    if (!item) return;
    if (item.kind === "file" && item.preview) {
      try {
        URL.revokeObjectURL(item.preview);
      } catch (_) {
        /* ignore */
      }
    }
    editImageItems.splice(index, 1);
    const clearCb = document.getElementById("chemEditClearImage");
    if (clearCb && !editImageItems.length) clearCb.checked = true;
    renderEditImageThumbs();
  }

  function setEditModeUi(mode, members) {
    const modeEl = document.getElementById("chemEditMode");
    const membersJson = document.getElementById("chemEditMembersJson");
    const groupLine = document.getElementById("chemEditGroupMembers");
    if (modeEl) modeEl.value = mode === "group" ? "group" : "pair";
    if (membersJson) membersJson.value = JSON.stringify(members || []);
    if (groupLine) {
      if (mode === "group" && members && members.length) {
        groupLine.textContent =
          (IS_EN ? "Members: " : "선택 멤버: ") + formatGroupMembers(members);
        groupLine.classList.remove("hidden");
      } else {
        groupLine.textContent = "";
        groupLine.classList.add("hidden");
      }
    }
  }

  function openEditForm(member1, member2, label, labelEn, description, descriptionEn, imageUrlOrUrls, links, labelTone) {
    editingPair = { member1, member2 };
    editingGroupMembers = null;
    currentGroupView = null;
    closeModal();
    closeColorPalette();
    const modal = document.getElementById("chemistryEditModal");
    const title = document.getElementById("chemistryEditTitle");
    const err = document.getElementById("chemEditError");
    const editorKo = document.getElementById("chemEditDescription");
    const editorEn = document.getElementById("chemEditDescriptionEn");
    setEditModeUi("pair", []);
    document.getElementById("chemEditMember1").value = member1;
    document.getElementById("chemEditMember2").value = member2;
    const labelKoEl = document.getElementById("chemEditLabel");
    const labelEnEl = document.getElementById("chemEditLabelEn");
    if (labelKoEl) labelKoEl.value = label || "";
    if (labelEnEl) labelEnEl.value = labelEn || "";
    setLabelToneInputs(labelTone);
    if (editorKo) editorKo.innerHTML = descriptionToEditorHtml(description || "");
    if (editorEn) editorEn.innerHTML = descriptionToEditorHtml(descriptionEn || "");
    setEditImages(
      Array.isArray(imageUrlOrUrls)
        ? imageUrlOrUrls
        : imageUrlOrUrls
          ? [imageUrlOrUrls]
          : []
    );
    renderLinkRows(links || {});
    if (title) {
      title.textContent =
        (label ? I18N.editTitle : I18N.editTitleNew) + " · " + pairTitleText(member1, member2);
    }
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

  function openEditFormGroup(members, label, labelEn, description, descriptionEn, imageUrlOrUrls, links, labelTone) {
    editingPair = null;
    currentView = null;
    editingGroupMembers = (members || []).slice();
    closeModal();
    closeColorPalette();
    const modal = document.getElementById("chemistryEditModal");
    const title = document.getElementById("chemistryEditTitle");
    const err = document.getElementById("chemEditError");
    const editorKo = document.getElementById("chemEditDescription");
    const editorEn = document.getElementById("chemEditDescriptionEn");
    setEditModeUi("group", editingGroupMembers);
    document.getElementById("chemEditMember1").value = "";
    document.getElementById("chemEditMember2").value = "";
    const labelKoEl = document.getElementById("chemEditLabel");
    const labelEnEl = document.getElementById("chemEditLabelEn");
    if (labelKoEl) labelKoEl.value = label || "";
    if (labelEnEl) labelEnEl.value = labelEn || "";
    setLabelToneInputs(labelTone);
    if (editorKo) editorKo.innerHTML = descriptionToEditorHtml(description || "");
    if (editorEn) editorEn.innerHTML = descriptionToEditorHtml(descriptionEn || "");
    setEditImages(
      Array.isArray(imageUrlOrUrls)
        ? imageUrlOrUrls
        : imageUrlOrUrls
          ? [imageUrlOrUrls]
          : []
    );
    renderLinkRows(links || {});
    if (title) {
      title.textContent =
        (label ? I18N.editTitle : I18N.editTitleNew) +
        " · " +
        formatGroupMembers(editingGroupMembers);
    }
    if (err) {
      err.hidden = true;
      err.textContent = "";
    }
    if (modal) {
      modal.classList.remove("hidden");
      modal.classList.add("flex");
      document.body.style.overflow = "hidden";
    }
    // 추가/수정 모두 케미명부터 입력하도록 포커스
    if (labelKoEl) {
      requestAnimationFrame(() => {
        labelKoEl.focus();
        labelKoEl.select();
      });
    }
  }

  function openChemistryGroup(group) {
    if (!group) return;
    currentGroupView = group;
    currentView = null;
    editingPair = null;
    const data = {
      description: group.description || "",
      descriptionEn: group.descriptionEn || "",
      image: group.imageUrl || "",
      images: normalizeImageList(group),
      label: group.label || "",
      labelEn: group.labelEn || "",
      links: group.links || {}
    };
    const { description, credit } = splitPhotoCredit(pickViewDescription(data));
    let formattedDescription = softenDescriptionBreaks(
      sanitizeChemistryHtml(description)
        .replace(/\*\*(.*?)\*\*/g, (match, p1) => {
          if (data.links && data.links[p1]) {
            return '<a href="' + data.links[p1] + '" target="_blank" rel="noopener" class="chem-highlight">' + p1 + "</a>";
          }
          return '<span class="chem-highlight">' + p1 + "</span>";
        })
        .replace(/\n/g, "<br>")
    );
    const chemLabel = IS_EN
      ? String(data.labelEn || "").trim()
        ? data.labelEn
        : data.label || ""
      : data.label || "";
    const titleEl = document.getElementById("chemistryTitle");
    const pairEl = document.getElementById("chemistryPair");
    if (titleEl) {
      titleEl.innerHTML = chemLabel
        ? formatModalChemLabel(chemLabel)
        : escapeHtml(formatGroupMembers(group.members));
    }
    if (pairEl) pairEl.innerHTML = escapeHtml(formatGroupMembers(group.members));
    const descEl = document.getElementById("chemistryDescription");
    if (descEl) descEl.innerHTML = formattedDescription;
    const imageList = normalizeImageList(data);
    setChemistryDetailImages(
      imageList,
      chemLabel ? chemLabelPlain(chemLabel) : formatGroupMembers(group.members)
    );
    const creditEl = document.getElementById("chemistryCredit");
    if (creditEl) {
      if (credit && imageList.length) {
        creditEl.textContent = "photo by. " + credit;
        creditEl.hidden = false;
      } else {
        creditEl.textContent = "";
        creditEl.hidden = true;
      }
    }
    const linksEl = document.getElementById("chemistryLinks");
    if (linksEl) {
      const entries = Object.entries(data.links || {});
      linksEl.innerHTML = entries
        .map(
          ([text, url]) =>
            '<a href="' +
            escapeHtml(url) +
            '" target="_blank" rel="noopener" class="chem-modal-link">' +
            escapeHtml(text) +
            "</a>"
        )
        .join("");
    }
    const adminActions = document.getElementById("chemistryAdminActions");
    if (adminActions) adminActions.classList.toggle("hidden", !isAdmin);
    const modal = document.getElementById("chemistryModal");
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
    revokeEditFilePreviews();
    editImageItems = [];
  }

  async function refreshGroupEntries() {
    try {
      const res = await chemFetch("/api/chemistry/groups");
      if (res.ok) {
        groupEntries = await res.json();
        if (!Array.isArray(groupEntries)) groupEntries = [];
        return;
      }
    } catch (_) {
      /* fall through */
    }
    groupEntries = [];
  }

  async function refreshOverlaysAndTable() {
    try {
      const res = await chemFetch("/api/chemistry");
      if (res.ok) {
        applyDbToTable(await res.json());
      } else {
        applyDbToTable([]);
      }
    } catch (_) {
      applyDbToTable([]);
    }
    await refreshGroupEntries();
    if (currentMode === "trio") {
      renderTrioResults(
        window.MemberPortraitPicker
          ? MemberPortraitPicker.selectedValues(document.getElementById("chemTrioView"))
          : []
      );
    }
  }

  async function deleteCurrentChemistry() {
    if (currentGroupView || editingGroupMembers) {
      const members =
        (currentGroupView && currentGroupView.members) ||
        editingGroupMembers ||
        [];
      if (!members.length) return;
      if (!confirm(formatGroupMembers(members) + " 케미를 삭제할까요?")) return;
      try {
        const qs = new URLSearchParams();
        members.forEach((m) => qs.append("members", m));
        const res = await chemFetch("/api/chemistry/groups?" + qs.toString(), {
          method: "DELETE"
        });
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
        currentGroupView = null;
        editingGroupMembers = null;
        await refreshOverlaysAndTable();
      } catch (_) {
        alert("삭제에 실패했습니다.");
      }
      return;
    }

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
        if (!isAdmin) return;
        if (currentGroupView) {
          const g = currentGroupView;
          openEditFormGroup(
            g.members || [],
            g.label || "",
            g.labelEn || "",
            g.description || "",
            g.descriptionEn || "",
            normalizeImageList(g),
            g.links || {},
            g.labelTone || "NONE"
          );
          return;
        }
        if (!currentView && !editingPair) return;
        const member1 = (currentView && currentView.member1) || editingPair.member1;
        const member2 = (currentView && currentView.member2) || editingPair.member2;
        const person = (currentView && currentView.person) || member1;
        const key = (currentView && currentView.key) || chemKey(member1, member2);
        const bucket = chemistryDescriptions[person] || {};
        const data = bucket[key] || {};
        let cell = null;
        if (pairMatrix) {
          const ri = memberIndex(member1);
          const ci = memberIndex(member2);
          cell = pairMatrix[ri] && pairMatrix[ri][ci];
        }
        let labelTone =
          (currentView && currentView.labelTone) || data.labelTone || "NONE";
        if ((!currentView || !currentView.labelTone) && cell && cell.labelTone) {
          labelTone = cell.labelTone;
        }
        openEditForm(
          member1,
          member2,
          (data && data.label) || "",
          (data && data.labelEn) || "",
          (data && data.description) || "",
          (data && data.descriptionEn) || "",
          normalizeImageList(data),
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
        swatch.addEventListener("click", () => applyPaletteColor(color));
        swatchBox.appendChild(swatch);
      });
    }

    chemColorMode = "foreColor";
    syncColorInputs(CHEM_DEFAULT_TEXT_COLOR);
    const highlightBar = document.getElementById("chemHighlightBar");
    if (highlightBar) highlightBar.style.background = CHEM_DEFAULT_HIGHLIGHT_COLOR;
    setColorPalettePage(1);
    renderPage2Colors();

    const pagePrev = document.getElementById("chemColorPagePrev");
    const pageNext = document.getElementById("chemColorPageNext");
    if (pagePrev) {
      pagePrev.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        setColorPalettePage(chemColorPage - 1);
      });
    }
    if (pageNext) {
      pageNext.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        setColorPalettePage(chemColorPage + 1);
      });
    }

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

    const colorPin = document.getElementById("chemColorPin");
    if (colorPin) {
      colorPin.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        if (!pinCustomColor(readCustomColor())) return;
        setColorPalettePage(2);
      });
    }
    const colorApply = document.getElementById("chemColorApply");
    if (colorApply) {
      colorApply.addEventListener("click", () => applyPaletteColor(readCustomColor()));
    }
    const colorReset = document.getElementById("chemColorReset");
    if (colorReset) {
      colorReset.addEventListener("click", () => {
        if (chemColorMode === "hiliteColor") {
          applyEditorFormat("hiliteColor", "transparent");
          syncColorInputs(CHEM_DEFAULT_HIGHLIGHT_COLOR);
        } else {
          applyEditorFormat("removeFormat");
          syncColorInputs(CHEM_DEFAULT_TEXT_COLOR);
        }
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
        toggleColorPalette("foreColor");
      });
    }
    const highlightToggle = document.getElementById("chemHighlightToggle");
    if (highlightToggle && palette) {
      highlightToggle.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        toggleColorPalette("hiliteColor");
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
        addEditImageFiles(editImageInput.files);
        editImageInput.value = "";
      });
    }
    const clearImageCb = document.getElementById("chemEditClearImage");
    if (clearImageCb) {
      clearImageCb.addEventListener("change", () => {
        if (clearImageCb.checked) clearEditImages();
      });
    }
    const thumbsBox = document.getElementById("chemEditImageThumbs");
    if (thumbsBox) {
      thumbsBox.addEventListener("click", (e) => {
        const btn = e.target.closest("[data-remove-idx]");
        if (!btn) return;
        e.preventDefault();
        removeEditImageAt(Number(btn.getAttribute("data-remove-idx")));
      });
    }

    const editForm = document.getElementById("chemistryEditForm");
    if (editForm) {
      editForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const err = document.getElementById("chemEditError");
        const mode = document.getElementById("chemEditMode")?.value || "pair";
        const fd = new FormData();
        fd.append("label", document.getElementById("chemEditLabel").value);
        fd.append(
          "labelEn",
          (document.getElementById("chemEditLabelEn")?.value || "").trim()
        );
        fd.append("description", getEditorDescriptionHtml("chemEditDescription"));
        fd.append("descriptionEn", getEditorDescriptionHtml("chemEditDescriptionEn"));
        fd.append("labelTone", selectedLabelTone());
        const keepUrls = editImageItems
          .filter((item) => item.kind === "url" && item.url)
          .map((item) => item.url);
        const clearingAll = editImageItems.length === 0;
        fd.append("clearImage", clearingAll ? "true" : "false");
        fd.append("keepImageUrlsJson", JSON.stringify(keepUrls));
        fd.append("linksJson", collectLinksJson());
        editImageItems.forEach((item) => {
          if (item.kind === "file" && item.file) fd.append("images", item.file);
        });

        let endpoint = "/api/chemistry";
        if (mode === "group") {
          let members = editingGroupMembers || [];
          try {
            const raw = document.getElementById("chemEditMembersJson")?.value || "[]";
            const parsed = JSON.parse(raw);
            if (Array.isArray(parsed) && parsed.length) members = parsed;
          } catch (_) {
            /* keep editingGroupMembers */
          }
          if (members.length < 3) {
            if (err) {
              err.textContent = "3인 이상 케미는 멤버 3명 이상이 필요합니다.";
              err.hidden = false;
            }
            return;
          }
          members.forEach((m) => fd.append("members", m));
          endpoint = "/api/chemistry/groups";
        } else {
          fd.append("member1", document.getElementById("chemEditMember1").value);
          fd.append("member2", document.getElementById("chemEditMember2").value);
        }

        if (
          !window.confirm(
            clearingAll
              ? "이 케미를 저장하시겠습니까? 사진이 모두 삭제됩니다."
              : "이 케미를 저장하시겠습니까?"
          )
        ) {
          return;
        }
        try {
          const res = await chemFetch(endpoint, { method: "POST", body: fd });
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
          editingGroupMembers = null;
          currentGroupView = null;
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
    hideChemistryDetailImage();
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

  function showTrioView() {
    const pairView = document.getElementById("chemPairView");
    const trioView = document.getElementById("chemTrioView");
    if (pairView) pairView.classList.add("hidden");
    if (trioView) trioView.classList.remove("hidden");
    renderTrioResults(MemberPortraitPicker
      ? MemberPortraitPicker.selectedValues(document.getElementById("chemTrioView"))
      : []);
  }

  function trioHint(count) {
    if (IS_EN) {
      if (count <= 0) return "Select members to see chemistry they belong to.";
      if (count === 1) return "1 selected · chemistry including this member.";
      if (count === 2) return "2 selected · chemistry that includes both.";
      return count + " selected · exact group chemistry for this set.";
    }
    if (count <= 0) return "멤버를 고르면 관련 케미를 보여 줍니다. 1명·2명·3명 이상 모두 가능해요.";
    if (count === 1) return "1명 선택됨 · 이 멤버가 속한 케미를 표시합니다.";
    if (count === 2) return "2명 선택됨 · 두 멤버가 함께 속한 케미를 표시합니다.";
    return count + "명 선택됨 · 이 조합의 3인 이상 케미를 표시합니다.";
  }

  function sameMemberSet(a, b) {
    if (!a || !b || a.length !== b.length) return false;
    const sa = a.slice().sort();
    const sb = b.slice().sort();
    return sa.every((name, i) => name === sb[i]);
  }

  /** 24명 전원 케미(tripleS) — 이스터에그. 전원 선택 시에만 노출. */
  function isFullRosterGroup(group) {
    const members = group && group.members ? group.members : [];
    if (members.length < MEMBERS.length) return false;
    return MEMBERS.every((name) => members.includes(name));
  }

  function isFullRosterSelection(selectedNames) {
    const selected = selectedNames || [];
    if (selected.length < MEMBERS.length) return false;
    return MEMBERS.every((name) => selected.includes(name));
  }

  /** 3명 이상: 선택 집합과 멤버가 완전히 같은 그룹만. */
  function findExactGroups(selectedNames) {
    return (groupEntries || []).filter((g) => sameMemberSet(g.members || [], selectedNames));
  }

  /** 1·2명: 선택한 멤버를 모두 포함하는 3인 이상 그룹. 전원 케미는 제외. */
  function findGroupsContainingAll(selectedNames) {
    const selected = selectedNames || [];
    if (!selected.length) return [];
    const fullPick = isFullRosterSelection(selected);
    return (groupEntries || []).filter((g) => {
      if (isFullRosterGroup(g) && !fullPick) return false;
      const members = g.members || [];
      return selected.every((name) => members.includes(name));
    });
  }

  function formatGroupMembers(names) {
    return (names || []).map(memberLabel).join(" · ");
  }

  function pairCellBetween(a, b) {
    const ia = memberIndex(a);
    const ib = memberIndex(b);
    if (ia < 0 || ib < 0 || ia === ib || !pairMatrix) return null;
    const lo = Math.min(ia, ib);
    const hi = Math.max(ia, ib);
    const cell = pairMatrix[lo] && pairMatrix[lo][hi];
    if (!cell || cell.type !== "chem") return null;
    return {
      member1: MEMBERS[lo],
      member2: MEMBERS[hi],
      person: MEMBERS[lo],
      key: cell.key,
      label: cell.label || "",
      labelTone: cell.labelTone || "NONE",
      dbId: cell.dbId || null
    };
  }

  /** 선택 멤버 사이(2명 이상)의 2인 케미. */
  function collectTrioPairs(selectedNames) {
    const list = selectedNames || [];
    if (list.length < 2 || !pairMatrix) return [];
    const seen = new Set();
    const pairs = [];
    for (let i = 0; i < list.length; i++) {
      for (let j = i + 1; j < list.length; j++) {
        const pair = pairCellBetween(list[i], list[j]);
        if (!pair) continue;
        const key = pair.member1 + "\0" + pair.member2;
        if (seen.has(key)) continue;
        seen.add(key);
        pairs.push(pair);
      }
    }
    return pairs;
  }

  /** 1명: 그 멤버가 들어간 모든 2인 케미. */
  function collectPairsForOneMember(name) {
    if (!name || !pairMatrix) return [];
    const pairs = [];
    MEMBERS.forEach((other) => {
      if (other === name) return;
      const pair = pairCellBetween(name, other);
      if (pair) pairs.push(pair);
    });
    return pairs;
  }

  function renderPairCardsHtml(pairs) {
    return pairs
      .map((pair) => {
        const toneCls = toneClass(pair.labelTone);
        return (
          '<button type="button" class="chem-trio-card" data-m1="' +
          escapeHtml(pair.member1) +
          '" data-m2="' +
          escapeHtml(pair.member2) +
          '" data-person="' +
          escapeHtml(pair.person) +
          '" data-key="' +
          escapeHtml(pair.key) +
          '" data-tone="' +
          escapeHtml(normalizeLabelTone(pair.labelTone)) +
          '">' +
          '<span class="chem-trio-card-pair">' +
          escapeHtml(pairTitleText(pair.member1, pair.member2)) +
          "</span>" +
          '<span class="chem-trio-card-label' +
          (toneCls ? " " + toneCls : "") +
          '">' +
          escapeHtml(pair.label) +
          "</span>" +
          "</button>"
        );
      })
      .join("");
  }

  function renderGroupCardsHtml(groups, names) {
    return groups
      .map((group) => {
        const toneCls = toneClass(group.labelTone);
        const displayLabel = IS_EN
          ? String(group.labelEn || "").trim() || group.label || ""
          : group.label || "";
        let html =
          '<article class="chem-trio-card chem-trio-card--group" data-group-id="' +
          escapeHtml(String(group.id || "")) +
          '">' +
          '<button type="button" class="chem-trio-card-main" data-group-open="' +
          escapeHtml(String(group.id || "")) +
          '">' +
          '<span class="chem-trio-card-pair">' +
          escapeHtml(formatGroupMembers(group.members)) +
          "</span>" +
          '<span class="chem-trio-card-label' +
          (toneCls ? " " + toneCls : "") +
          '">' +
          escapeHtml(displayLabel) +
          "</span>" +
          "</button>";
        if (isAdmin) {
          html +=
            '<div class="chem-trio-card-actions">' +
            '<button type="button" class="chem-trio-edit-name" data-group-edit="' +
            escapeHtml(String(group.id || "")) +
            '">' +
            escapeHtml(IS_EN ? "Edit name" : "케미명 수정") +
            "</button>" +
            "</div>";
        }
        html += "</article>";
        return html;
      })
      .join("");
  }

  function renderTrioResults(selectedNames) {
    const meta = document.getElementById("chemTrioMeta");
    const box = document.getElementById("chemTrioResults");
    const jump = document.getElementById("chemTrioJump");
    const names = selectedNames || [];
    if (meta) meta.textContent = trioHint(names.length);
    if (jump) jump.classList.toggle("hidden", names.length < 1);
    if (!box) return;

    if (names.length < 1) {
      box.innerHTML =
        '<p class="chem-trio-empty">' +
        escapeHtml(
          IS_EN
            ? "Select one or more members to see related chemistry."
            : "멤버를 한 명 이상 선택해 주세요."
        ) +
        "</p>";
      return;
    }

    const exactMode = names.length >= 3;
    const groups = exactMode ? findExactGroups(names) : findGroupsContainingAll(names);
    const pairs = names.length === 1
      ? collectPairsForOneMember(names[0])
      : collectTrioPairs(names);

    let html = "";
    html +=
      '<p class="chem-trio-selected-line">' +
      escapeHtml(formatGroupMembers(names)) +
      "</p>";

    // 3인 이상 그룹 섹션
    html += '<section class="chem-trio-section">';
    html +=
      '<p class="chem-trio-section-title">' +
      escapeHtml(
        exactMode
          ? IS_EN ? "3+ chemistry (exact match)" : "3인 이상 케미 (완전 일치)"
          : IS_EN ? "3+ chemistry including selection" : "선택 멤버가 속한 3인 이상 케미"
      ) +
      "</p>";
    html += '<div class="chem-trio-card-grid chem-trio-card-grid--primary">';

    if (exactMode && isAdmin && !groups.length) {
      html +=
        '<button type="button" class="chem-trio-plus-zone" id="chemTrioAddBtn" aria-label="' +
        escapeHtml(IS_EN ? "Add 3+ chemistry" : "3인 이상 케미 추가") +
        '">' +
        '<span class="chem-trio-plus-mark" aria-hidden="true">+</span>' +
        '<span class="chem-trio-plus-caption">' +
        escapeHtml(IS_EN ? "Add chemistry for this selection" : "이 조합으로 케미 추가") +
        "</span>" +
        "</button>";
    } else if (!groups.length) {
      html +=
        '<p class="chem-trio-empty">' +
        escapeHtml(
          IS_EN
            ? "No 3+ chemistry matches this selection."
            : "해당하는 3인 이상 케미가 없어요."
        ) +
        "</p>";
    } else {
      html += renderGroupCardsHtml(groups, names);
    }
    html += "</div></section>";

    // 2인 케미 섹션
    html += '<section class="chem-trio-section">';
    html +=
      '<p class="chem-trio-section-title">' +
      escapeHtml(
        names.length === 1
          ? IS_EN ? "2-person chemistry with this member" : "이 멤버의 2인 케미"
          : IS_EN ? "2-person chemistry among selection" : "선택 멤버 사이 2인 케미"
      ) +
      "</p>";
    if (pairs.length) {
      html += '<div class="chem-trio-card-grid">' + renderPairCardsHtml(pairs) + "</div>";
    } else {
      html +=
        '<p class="chem-trio-empty">' +
        escapeHtml(
          IS_EN ? "No 2-person chemistry for this selection." : "해당하는 2인 케미가 없어요."
        ) +
        "</p>";
    }
    html += "</section>";

    box.innerHTML = html;

    function openGroupEditor(group) {
      if (!group) return;
      openEditFormGroup(
        group.members && group.members.length ? group.members : names,
        group.label || "",
        group.labelEn || "",
        group.description || "",
        group.descriptionEn || "",
        normalizeImageList(group),
        group.links || {},
        group.labelTone || "NONE"
      );
    }

    box.querySelectorAll("[data-group-open]").forEach((el) => {
      el.addEventListener("click", () => {
        const id = Number(el.getAttribute("data-group-open"));
        const group = (groupEntries || []).find((g) => Number(g.id) === id);
        if (group) openChemistryGroup(group);
      });
    });

    box.querySelectorAll("[data-group-edit]").forEach((el) => {
      el.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        const id = Number(el.getAttribute("data-group-edit"));
        const group = (groupEntries || []).find((g) => Number(g.id) === id);
        openGroupEditor(group || { members: names });
      });
    });

    box.querySelectorAll(".chem-trio-card:not(.chem-trio-card--group)").forEach((el) => {
      el.addEventListener("click", () => {
        const ri = memberIndex(el.dataset.m1);
        const ci = memberIndex(el.dataset.m2);
        const cell = pairMatrix && pairMatrix[ri] && pairMatrix[ri][ci];
        editingGroupMembers = null;
        currentGroupView = null;
        editingPair = { member1: el.dataset.m1, member2: el.dataset.m2 };
        currentView = {
          person: el.dataset.person,
          key: el.dataset.key,
          member1: el.dataset.m1,
          member2: el.dataset.m2,
          label: (cell && cell.label) || "",
          labelTone: (cell && cell.labelTone) || el.dataset.tone || "NONE",
          dbId: cell && cell.dbId ? cell.dbId : null
        };
        openChemistryName(el.dataset.person, el.dataset.key);
      });
    });

    const addBtn = document.getElementById("chemTrioAddBtn");
    if (addBtn) {
      addBtn.addEventListener("click", () => {
        openEditFormGroup(names, "", "", "", "", "", {}, "NONE");
      });
    }
  }

  function fillPairFilterOptions() {
    const a = document.getElementById("chemFilterA");
    const b = document.getElementById("chemFilterB");
    if (!a || !b) return;
    const makeOptions = (select, keep) => {
      const prev = keep || select.value;
      select.innerHTML =
        '<option value="">' +
        escapeHtml(IS_EN ? "Any" : "선택") +
        "</option>" +
        MEMBERS.map((name, i) =>
          '<option value="' + i + '">' + escapeHtml(memberLabel(name)) + "</option>"
        ).join("");
      if (prev !== "" && select.querySelector('option[value="' + prev + '"]')) {
        select.value = prev;
      }
    };
    makeOptions(a, a.value);
    makeOptions(b, b.value);
  }

  function bindPairFilter() {
    const go = document.getElementById("chemFilterGo");
    if (!go) return;
    go.addEventListener("click", () => {
      const ai = Number(document.getElementById("chemFilterA")?.value);
      const bi = Number(document.getElementById("chemFilterB")?.value);
      if (!Number.isFinite(ai) || !Number.isFinite(bi) || ai === bi) {
        alert(IS_EN ? "Pick two different members." : "서로 다른 멤버 두 명을 선택해 주세요.");
        return;
      }
      const cell =
        document.querySelector('#chemTbody td[data-m1="' + CSS.escape(MEMBERS[ai]) + '"][data-m2="' + CSS.escape(MEMBERS[bi]) + '"]') ||
        document.querySelector('#chemTbody td[data-m1="' + CSS.escape(MEMBERS[bi]) + '"][data-m2="' + CSS.escape(MEMBERS[ai]) + '"]');
      if (!cell) {
        alert(IS_EN ? "No cell found for that pair." : "해당 교차 칸을 찾지 못했습니다.");
        return;
      }
      const wrap = document.querySelector(".chem-table-wrap");
      cell.scrollIntoView({ behavior: "smooth", block: "center", inline: "center" });
      cell.classList.add("chem-cell-flash");
      setTimeout(() => cell.classList.remove("chem-cell-flash"), 1200);
      if (wrap) {
        /* ensure horizontal scroll if needed */
      }
      cell.focus();
    });
  }

  function bindTrioPicker() {
    const root = document.getElementById("chemTrioView");
    if (!root || !window.MemberPortraitPicker) return;
    MemberPortraitPicker.bindMultiSelect(root, {
      multi: true,
      onChange: (values) => renderTrioResults(values)
    });
    const clearBtn = document.getElementById("chemTrioClear");
    if (clearBtn) {
      clearBtn.addEventListener("click", () => {
        MemberPortraitPicker.clear(root);
        renderTrioResults([]);
      });
    }
  }

  function setMode(mode) {
    currentMode = mode === "trio" ? "trio" : "pair";
    setModeButtons(currentMode);
    if (currentMode === "trio") {
      showTrioView();
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

  function bindChemistryCarousel() {
    const prev = document.getElementById("chemistryCarouselPrev");
    const next = document.getElementById("chemistryCarouselNext");
    const dots = document.getElementById("chemistryCarouselDots");
    const viewport = document.getElementById("chemistryCarouselViewport");
    if (prev) prev.addEventListener("click", () => goChemistryCarousel(carouselIndex - 1));
    if (next) next.addEventListener("click", () => goChemistryCarousel(carouselIndex + 1));
    if (dots) {
      dots.addEventListener("click", (e) => {
        const btn = e.target.closest("[data-dot]");
        if (!btn) return;
        goChemistryCarousel(Number(btn.getAttribute("data-dot")));
      });
    }
    if (viewport) {
      viewport.addEventListener(
        "touchstart",
        (e) => {
          carouselTouchX = e.changedTouches && e.changedTouches[0] ? e.changedTouches[0].clientX : null;
        },
        { passive: true }
      );
      viewport.addEventListener(
        "touchend",
        (e) => {
          if (carouselTouchX == null || !e.changedTouches || !e.changedTouches[0]) return;
          const dx = e.changedTouches[0].clientX - carouselTouchX;
          carouselTouchX = null;
          if (Math.abs(dx) < 40) return;
          goChemistryCarousel(carouselIndex + (dx < 0 ? 1 : -1));
        },
        { passive: true }
      );
    }
  }

  document.addEventListener("DOMContentLoaded", async () => {
    const closeBtn = document.querySelector("#chemistryModal .chem-close");
    if (closeBtn) closeBtn.addEventListener("click", closeModal);

    document.addEventListener("keydown", (e) => {
      const modal = document.getElementById("chemistryModal");
      const modalOpen = modal && !modal.classList.contains("hidden");
      if (modalOpen && carouselUrls.length > 1) {
        if (e.key === "ArrowLeft") {
          e.preventDefault();
          goChemistryCarousel(carouselIndex - 1);
          return;
        }
        if (e.key === "ArrowRight") {
          e.preventDefault();
          goChemistryCarousel(carouselIndex + 1);
          return;
        }
      }
      if (e.key !== "Escape") return;
      closeLegendPanel();
      closeEditModal();
      closeLoginModal();
      closeModal();
    });

    bindModeToggle();
    bindTrioPicker();
    bindPairFilter();
    bindLegendToggle();
    bindChemistryCarousel();
    bindAdminUi();

    // 영어 페이지에는 로그인 버튼 없음. 한국어에서 로그인된 세션이면 추가/수정 가능.
    try {
      const [statusRes, chemRes, groupRes] = await Promise.all([
        chemFetch("/api/chemistry/admin/status"),
        chemFetch("/api/chemistry"),
        chemFetch("/api/chemistry/groups")
      ]);
      if (statusRes.ok) {
        const status = await statusRes.json();
        adminConfigured = Boolean(status.configured);
        isAdmin = Boolean(status.admin);
        if (!isAdmin) setAdminToken("");
      }
      if (!chemRes.ok) throw new Error("API " + chemRes.status);
      applyDbToTable(await chemRes.json());
      if (groupRes.ok) {
        groupEntries = await groupRes.json();
      } else {
        groupEntries = [];
      }
      setAdminUi();
      setMode("pair");
    } catch (err) {
      console.error("케미 데이터 로드 실패", err);
      applyDbToTable([]);
      groupEntries = [];
      const tbody = document.getElementById("chemTbody");
      if (tbody) {
        tbody.innerHTML =
          '<tr><td colspan="25" class="text-center text-[#b9cac4] py-8">' +
          escapeHtml(I18N.loadFailed) +
          "</td></tr>";
      }
      const empty = document.getElementById("chemEmptyState");
      if (empty) empty.classList.add("hidden");
      setAdminUi();
    }
  });

  window.openChemistryName = openChemistryName;
  window.__chemSetMode = setMode;
})();
