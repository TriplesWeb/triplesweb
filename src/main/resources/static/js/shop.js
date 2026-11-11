(() => {
  const API = "/api/shop";
  const auth = window.AboutAdminAuth;
  let isAdmin = false;
  let sectionOptions = [];
  let eventOptions = [];
  /** @type {{name:string,salesStart:string,salesEnd:string,categories:{title:string,photos:string[]}[]}[]} */
  let mdRegions = [];
  /** @type {{ri:number,ci:number}|null} */
  let mdPhotoTarget = null;

  function setToken(token) { if (auth) auth.setToken(token); }

  async function apiFetch(url, options) {
    const opts = options ? { ...options } : {};
    opts.headers = auth ? auth.applyAuthHeaders(opts.headers || {}) : new Headers(opts.headers || {});
    opts.credentials = "same-origin";
    opts.cache = "no-store";
    const response = await fetch(url, opts);
    if (response.status === 403) {
      isAdmin = false;
      setToken("");
      setAdminUi();
    }
    return response;
  }

  async function readError(res, fallback) {
    try {
      const data = await res.json();
      if (data && (data.detail || data.message || data.error || data.title)) {
        return data.detail || data.message || data.error || data.title;
      }
    } catch (_) { /* ignore */ }
    if (res.status === 403) return "관리자 로그인이 필요합니다.";
    return fallback || "요청에 실패했습니다.";
  }

  function setAdminUi() {
    const page = document.querySelector(".about-admin-page");
    const hint = document.getElementById("shopAdminHint");
    document.querySelectorAll("[data-shop-edit], #shopAddBtn, #shopSectionBtn").forEach((control) => {
      control.hidden = !isAdmin;
    });
    if (page) page.classList.toggle("is-admin", Boolean(isAdmin));
    if (hint) hint.classList.toggle("hidden", !isAdmin);
  }

  function applyAdminState(state) {
    if (!state) return;
    isAdmin = Boolean(state.admin);
    if (!isAdmin) setToken("");
    setAdminUi();
  }

  function openModal(id) {
    const modal = document.getElementById(id);
    if (!modal) return;
    modal.classList.remove("hidden");
    modal.classList.add("flex");
    document.body.style.overflow = "hidden";
  }
  function closeModal(id) {
    const modal = document.getElementById(id);
    if (!modal) return;
    modal.classList.add("hidden");
    modal.classList.remove("flex");
    if (!document.querySelector(".about-modal.flex")) document.body.style.overflow = "";
  }

  function escapeAttr(s) {
    return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  }

  /** 표시용 날짜(2024.06.15) → input[type=date] 값 */
  function toIsoDate(dotted) {
    const m = String(dotted || "").trim().match(/^(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})$/);
    if (!m) return "";
    return `${m[1]}-${m[2].padStart(2, "0")}-${m[3].padStart(2, "0")}`;
  }

  /** input[type=date] 값 → 저장/표시용 2024.06.15 */
  function toDisplayDate(iso) {
    const m = String(iso || "").trim().match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (!m) return "";
    return `${m[1]}.${m[2]}.${m[3]}`;
  }

  function parseSalesPeriod(raw) {
    const s = String(raw || "").trim();
    if (!s) return { start: "", end: "" };
    const parts = s.split(/\s*[~～〜]\s*/);
    return {
      start: toIsoDate(parts[0] || ""),
      end: toIsoDate(parts[1] || "")
    };
  }

  function formatSalesPeriod(startIso, endIso) {
    const start = toDisplayDate(startIso);
    if (!start) return "";
    const end = toDisplayDate(endIso);
    if (!end || end === start) return start;
    return `${start}~${end}`;
  }

  function setSalesPeriodInputs(raw) {
    const dates = parseSalesPeriod(raw);
    const startEl = document.getElementById("shopEditSalesStart");
    const endEl = document.getElementById("shopEditSalesEnd");
    if (startEl) startEl.value = dates.start;
    if (endEl) endEl.value = dates.end;
    syncDateRangePair(startEl, endEl);
  }

  function readSalesPeriod() {
    const startEl = document.getElementById("shopEditSalesStart");
    const endEl = document.getElementById("shopEditSalesEnd");
    normalizeDateInputYear(startEl);
    normalizeDateInputYear(endEl);
    syncDateRangePair(startEl, endEl);
    const start = startEl?.value?.trim() || "";
    const end = endEl?.value?.trim() || "";
    return formatSalesPeriod(start, end);
  }

  /** 26 → 2026 처럼 두 자리(또는 0026) 연도를 보정한다. */
  function normalizeDateInputYear(el) {
    if (!el || !el.value) return;
    const m = String(el.value).trim().match(/^(\d{1,4})-(\d{2})-(\d{2})$/);
    if (!m) return;
    let year = Number(m[1]);
    if (!Number.isFinite(year)) return;
    if (year < 100) year += 2000;
    const next = `${String(year).padStart(4, "0")}-${m[2]}-${m[3]}`;
    if (next !== el.value) el.value = next;
  }

  function isValidDateRange(startIso, endIso) {
    if (!startIso) return false;
    if (!endIso) return true;
    return endIso >= startIso;
  }

  function syncDateRangePair(startEl, endEl) {
    if (!startEl && !endEl) return true;
    normalizeDateInputYear(startEl);
    normalizeDateInputYear(endEl);
    const start = startEl?.value || "";
    const end = endEl?.value || "";
    if (endEl) {
      if (start) endEl.min = start;
      else endEl.removeAttribute("min");
      endEl.classList.toggle("is-invalid", Boolean(end && start && end < start));
    }
    if (startEl) {
      startEl.classList.toggle("is-invalid", Boolean(end && start && end < start));
    }
    return isValidDateRange(start, end);
  }

  function syncAllShopDateRanges() {
    let ok = syncDateRangePair(
      document.getElementById("shopEditSalesStart"),
      document.getElementById("shopEditSalesEnd")
    );
    document.querySelectorAll("#shopEditMdBuilder [data-md-region]").forEach((regionEl) => {
      const startEl = regionEl.querySelector("[data-md-sales-start]");
      const endEl = regionEl.querySelector("[data-md-sales-end]");
      if (!syncDateRangePair(startEl, endEl)) ok = false;
    });
    return ok;
  }

  function hasLinkedEvent() {
    return Boolean(document.getElementById("shopEditEventId")?.value);
  }

  function emptyCategory() {
    return { title: "", photos: [] };
  }

  function emptyRegion() {
    return { name: "", salesStart: "", salesEnd: "", categories: [emptyCategory()] };
  }

  function parseImageLine(line) {
    const t = String(line || "").trim();
    if (!t) return "";
    if (t.startsWith("!")) return t.slice(1).trim();
    if (/^img:/i.test(t)) return t.replace(/^img:/i, "").trim();
    if (/^https?:\/\/\S+\.(png|jpe?g|webp|gif)(\?\S*)?$/i.test(t)) return t;
    return "";
  }

  function parseMdDocument(raw) {
    const lines = String(raw || "").split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    if (!lines.length) return [];
    const hasRegionMarker = lines.some((l) => l.startsWith("##"));
    if (!hasRegionMarker) {
      const regions = [];
      let title = null;
      let photos = [];
      const flush = () => {
        if (!title) return;
        const cleaned = [];
        const seen = new Set();
        photos.forEach((u) => { if (u && !seen.has(u)) { seen.add(u); cleaned.push(u); } });
        if (!cleaned.length) return;
        regions.push({
          name: title,
          salesStart: "",
          salesEnd: "",
          categories: [{ title, photos: cleaned }]
        });
      };
      lines.forEach((line) => {
        if (line.startsWith("#")) {
          flush();
          title = line.replace(/^#+/, "").trim() || "공지";
          photos = [];
          return;
        }
        const url = parseImageLine(line);
        if (!url) return;
        if (!title) title = "공지";
        photos.push(url);
      });
      flush();
      return regions;
    }

    const regions = [];
    let region = null;
    let category = null;
    const flushCategory = () => {
      if (!region || !category) return;
      const cleaned = [];
      const seen = new Set();
      category.photos.forEach((u) => { if (u && !seen.has(u)) { seen.add(u); cleaned.push(u); } });
      if (!cleaned.length && !category.title) return;
      region.categories.push({ title: category.title || "공지", photos: cleaned });
      category = null;
    };
    const flushRegion = () => {
      flushCategory();
      if (!region) return;
      if (!region.categories.length) region.categories.push(emptyCategory());
      regions.push(region);
      region = null;
    };
    lines.forEach((line) => {
      if (line.startsWith("##")) {
        flushRegion();
        region = {
          name: line.replace(/^##/, "").trim() || "지역",
          salesStart: "",
          salesEnd: "",
          categories: []
        };
        return;
      }
      if (!region) {
        region = { name: "지역", salesStart: "", salesEnd: "", categories: [] };
      }
      if (line.startsWith("@")) {
        let period = line.slice(1).trim();
        if (/^sales:/i.test(period)) period = period.replace(/^sales:/i, "").trim();
        const dates = parseSalesPeriod(period);
        region.salesStart = dates.start;
        region.salesEnd = dates.end;
        return;
      }
      if (line.startsWith("#")) {
        flushCategory();
        category = { title: line.replace(/^#/, "").trim() || "공지", photos: [] };
        return;
      }
      const url = parseImageLine(line);
      if (!url) return;
      if (!category) category = { title: "공지", photos: [] };
      category.photos.push(url);
    });
    flushRegion();
    return regions;
  }

  function collectAllPhotos(regions) {
    return (regions || []).flatMap((region) =>
      (region.categories || []).flatMap((cat) => (cat.photos || []).filter(Boolean))
    );
  }

  function ensureFlatMdStructure() {
    const photos = collectAllPhotos(mdRegions);
    mdRegions = [{
      name: "",
      salesStart: "",
      salesEnd: "",
      categories: [{ title: "공지", photos }]
    }];
  }

  function serializeMdDocument(regions) {
    if (!hasLinkedEvent()) {
      const photos = collectAllPhotos(regions);
      if (!photos.length) return "";
      return ["# 공지"].concat(photos.map((url) => "!" + url)).join("\n");
    }
    const blocks = [];
    (regions || []).forEach((region) => {
      const name = String(region.name || "").trim() || "지역";
      const lines = ["## " + name];
      const period = formatSalesPeriod(region.salesStart, region.salesEnd);
      if (period) lines.push("@" + period);
      (region.categories || []).forEach((cat) => {
        const photos = (cat.photos || []).filter(Boolean);
        if (!photos.length && !String(cat.title || "").trim()) return;
        lines.push("# " + (String(cat.title || "").trim() || "공지"));
        photos.forEach((url) => lines.push("!" + url));
      });
      if (lines.length > 1 || (region.categories || []).some((c) => (c.photos || []).length)) {
        blocks.push(lines.join("\n"));
      } else if (period || name !== "지역") {
        blocks.push(lines.join("\n"));
      }
    });
    return blocks.join("\n");
  }

  function syncMdListField() {
    const ta = document.getElementById("shopEditMdList");
    if (ta) ta.value = serializeMdDocument(mdRegions);
  }

  function syncMdBuilderFromDom() {
    const root = document.getElementById("shopEditMdBuilder");
    if (!root) return;
    if (!hasLinkedEvent()) {
      const photos = Array.from(root.querySelectorAll("[data-md-photo]"))
        .map((img) => img.getAttribute("data-md-photo") || "")
        .filter(Boolean);
      mdRegions = [{
        name: "",
        salesStart: "",
        salesEnd: "",
        categories: [{ title: "공지", photos }]
      }];
      syncMdListField();
      return;
    }
    const next = [];
    root.querySelectorAll("[data-md-region]").forEach((regionEl) => {
      const cats = [];
      regionEl.querySelectorAll("[data-md-category]").forEach((catEl) => {
        const photos = Array.from(catEl.querySelectorAll("[data-md-photo]"))
          .map((img) => img.getAttribute("data-md-photo") || "")
          .filter(Boolean);
        cats.push({
          title: catEl.querySelector("[data-md-cat-title]")?.value?.trim() || "",
          photos
        });
      });
      next.push({
        name: regionEl.querySelector("[data-md-region-name]")?.value?.trim() || "",
        salesStart: regionEl.querySelector("[data-md-sales-start]")?.value || "",
        salesEnd: regionEl.querySelector("[data-md-sales-end]")?.value || "",
        categories: cats.length ? cats : [emptyCategory()]
      });
    });
    mdRegions = next;
    syncMdListField();
  }

  function setMdEditorMode() {
    const linked = hasLinkedEvent();
    const globalSales = document.getElementById("shopEditGlobalSales");
    const hint = document.getElementById("shopEditMdSalesHint");
    if (globalSales) globalSales.hidden = linked;
    if (hint) hint.hidden = !linked;
    if (!linked) ensureFlatMdStructure();
    else if (!mdRegions.length) mdRegions = [emptyRegion()];
    renderMdBuilder();
  }

  function renderMdBuilder() {
    const root = document.getElementById("shopEditMdBuilder");
    if (!root) return;
    const linked = hasLinkedEvent();
    if (!linked) {
      ensureFlatMdStructure();
      const photos = mdRegions[0]?.categories?.[0]?.photos || [];
      root.innerHTML = `
        <div class="shop-md-flat" data-md-flat>
          <div class="shop-md-category-head">
            <button type="button" class="about-mode-btn shop-md-file-btn" data-md-add-photos>사진 추가</button>
            <span class="text-xs text-gray-400">상세에 보이는 MD 공지 사진입니다.</span>
          </div>
          <div class="shop-md-photo-list">
            ${photos.map((url, pi) => `
              <div class="shop-md-photo-item">
                <img src="${escapeAttr(url)}" alt="" data-md-photo="${escapeAttr(url)}" class="shop-md-thumb" loading="lazy" decoding="async">
                <button type="button" class="shop-md-photo-remove" data-md-remove-photo="${pi}" title="사진 삭제" aria-label="사진 삭제">×</button>
              </div>
            `).join("")}
          </div>
        </div>`;
      syncMdListField();
      return;
    }
    if (!mdRegions.length) mdRegions = [emptyRegion()];
    root.innerHTML = mdRegions.map((region, ri) => `
      <div class="shop-md-region" data-md-region="${ri}">
        <div class="shop-md-region-head">
          <input type="text" class="shop-md-input" data-md-region-name maxlength="120"
                 placeholder="지역 이름 (예: 서울, 방콕)" value="${escapeAttr(region.name || "")}" aria-label="지역 이름">
          <button type="button" class="about-mode-btn btn btn-xs btn-error" data-md-remove-region title="지역 삭제">삭제</button>
        </div>
        <div class="shop-md-region-sales stop-date-range">
          <input type="date" data-md-sales-start min="2000-01-01" max="2099-12-31" value="${escapeAttr(region.salesStart || "")}" title="판매 시작일" aria-label="지역 판매 시작일">
          <span class="stop-date-sep" aria-hidden="true">~</span>
          <input type="date" data-md-sales-end min="2000-01-01" max="2099-12-31" value="${escapeAttr(region.salesEnd || "")}" title="판매 종료일 (선택)" aria-label="지역 판매 종료일">
        </div>
        <div class="shop-md-categories">
          ${(region.categories || [emptyCategory()]).map((cat, ci) => `
            <div class="shop-md-category-edit" data-md-category="${ci}">
              <div class="shop-md-category-head">
                <input type="text" class="shop-md-input" data-md-cat-title maxlength="120"
                       placeholder="구분 이름 (예: Official MD)" value="${escapeAttr(cat.title || "")}" aria-label="구분 이름">
                <button type="button" class="about-mode-btn shop-md-file-btn shop-md-file-btn--sm" data-md-add-photos>사진 추가</button>
                <button type="button" class="about-mode-btn btn btn-xs btn-error" data-md-remove-category title="구분 삭제">삭제</button>
              </div>
              <div class="shop-md-photo-list">
                ${(cat.photos || []).map((url, pi) => `
                  <div class="shop-md-photo-item">
                    <img src="${escapeAttr(url)}" alt="" data-md-photo="${escapeAttr(url)}" class="shop-md-thumb" loading="lazy" decoding="async">
                    <button type="button" class="shop-md-photo-remove" data-md-remove-photo="${pi}" title="사진 삭제" aria-label="사진 삭제">×</button>
                  </div>
                `).join("")}
              </div>
            </div>
          `).join("")}
        </div>
        <button type="button" class="about-mode-btn btn btn-xs btn-outline btn-primary" data-md-add-category>구분 추가</button>
      </div>
    `).join("") + `
      <button type="button" class="shop-md-add-region" data-md-add-region>
        <span class="material-symbols-outlined" aria-hidden="true">add</span>
        지역 추가
      </button>`;
    syncMdListField();
    syncAllShopDateRanges();
  }

  async function uploadMdPhotos(files) {
    if (!mdPhotoTarget) return;
    const list = Array.from(files || []).filter((f) => f && f.type && f.type.startsWith("image/"));
    if (!list.length) {
      alert("이미지 파일을 선택해 주세요.");
      return;
    }
    syncMdBuilderFromDom();
    if (!hasLinkedEvent()) ensureFlatMdStructure();
    const { ri, ci } = mdPhotoTarget;
    if (!mdRegions[ri] || !mdRegions[ri].categories[ci]) return;
    const err = document.getElementById("shopEditError");
    for (const file of list) {
      const fd = new FormData();
      fd.append("file", file);
      const res = await apiFetch(API + "/md-photo", { method: "POST", body: fd });
      if (!res.ok) throw new Error(await readError(res, "사진 업로드에 실패했습니다."));
      const data = await res.json();
      if (!data?.url) throw new Error("업로드 응답에 URL이 없습니다.");
      mdRegions[ri].categories[ci].photos.push(data.url);
    }
    if (err) { err.hidden = true; err.textContent = ""; }
    renderMdBuilder();
  }

  function summarizeRegionSalesPeriods() {
    const periods = mdRegions
      .map((r) => formatSalesPeriod(r.salesStart, r.salesEnd))
      .filter(Boolean);
    if (!periods.length) return "";
    if (periods.length === 1) return periods[0];
    return periods.join(" / ");
  }

  function fillSectionSelect(selectedId) {
    const sel = document.getElementById("shopEditSectionId");
    if (!sel) return;
    sel.innerHTML = '<option value="">기타 (구분 없음)</option>' +
      sectionOptions.map((s) =>
        `<option value="${s.sectionId}" ${String(s.sectionId) === String(selectedId || "") ? "selected" : ""}>${escapeAttr(s.name)}</option>`
      ).join("");
  }

  function fillEventSelect(selectedId) {
    const sel = document.getElementById("shopEditEventId");
    if (!sel) return;
    const blank = '<option value="">공연 없음 (별도 MD)</option>';
    const options = eventOptions.length
      ? eventOptions.map((e) =>
          `<option value="${e.eventId}" ${String(e.eventId) === String(selectedId || "") ? "selected" : ""}>${escapeAttr(e.eventName)}</option>`
        ).join("")
      : '<option value="" disabled>등록된 공연이 없습니다</option>';
    sel.innerHTML = blank + options;
  }

  async function loadOptions() {
    const [secRes, eventRes] = await Promise.all([
      apiFetch(API + "/sections"),
      apiFetch(API + "/events")
    ]);
    if (secRes.ok) sectionOptions = await secRes.json();
    if (eventRes.ok) eventOptions = await eventRes.json();
  }

  function renderSectionList() {
    const list = document.getElementById("shopSectionList");
    if (!list) return;
    if (!sectionOptions.length) {
      list.innerHTML = '<li class="text-sm text-[#b9cac4]/70">등록된 구분이 없습니다.</li>';
      return;
    }
    list.innerHTML = sectionOptions.map((s) => `
      <li class="flex flex-wrap items-center gap-2 p-2 rounded-lg border border-[#3a4a46]/40" data-section-id="${s.sectionId}">
        <input type="text" class="section-name-input flex-1 min-w-[8rem] bg-transparent border border-[#3a4a46]/40 rounded px-2 py-1 text-sm" value="${escapeAttr(s.name)}" maxlength="120">
        <button type="button" class="section-rename about-mode-btn btn btn-xs btn-outline btn-primary">이름</button>
        <button type="button" class="section-up about-mode-btn btn btn-xs btn-outline btn-primary" title="위로">↑</button>
        <button type="button" class="section-down about-mode-btn btn btn-xs btn-outline btn-primary" title="아래로">↓</button>
        <button type="button" class="section-delete about-mode-btn btn btn-xs btn-error">삭제</button>
      </li>
    `).join("");
  }

  async function openSectionModal() {
    await loadOptions();
    const err = document.getElementById("shopSectionError");
    if (err) { err.hidden = true; err.textContent = ""; }
    renderSectionList();
    openModal("shopSectionModal");
  }

  async function openEditModal(id) {
    await loadOptions();
    const err = document.getElementById("shopEditError");
    if (err) { err.hidden = true; err.textContent = ""; }
    document.getElementById("shopEditId").value = id || "";
    document.getElementById("shopEditTitle").textContent = id ? "굿즈리스트 수정" : "굿즈리스트 추가";
    const cardNameEl = document.getElementById("shopEditCardName");
    if (cardNameEl) cardNameEl.value = "";
    setSalesPeriodInputs("");
    mdRegions = [emptyRegion()];
    mdPhotoTarget = null;
    document.getElementById("shopEditImage").value = "";
    document.getElementById("shopEditImageUrl").value = "";
    document.getElementById("shopEditClearImage").checked = false;
    const preview = document.getElementById("shopEditImagePreview");
    if (preview) {
      preview.src = "";
      preview.classList.add("hidden");
    }
    const deleteBtn = document.getElementById("shopEditDeleteBtn");
    if (deleteBtn) deleteBtn.classList.toggle("hidden", !id);

    fillSectionSelect(null);
    fillEventSelect(null);

    if (id) {
      const res = await apiFetch(API + "/" + id);
      if (!res.ok) {
        alert(await readError(res, "불러오기에 실패했습니다."));
        return;
      }
      const data = await res.json();
      if (cardNameEl) cardNameEl.value = data.title || data.eventName || "";
      setSalesPeriodInputs(data.salesPeriod || "");
      mdRegions = parseMdDocument(data.mdList || "");
      if (!mdRegions.length) mdRegions = [emptyRegion()];
      fillSectionSelect(data.sectionId);
      fillEventSelect(data.eventId);
      if (data.coverImage && preview) {
        preview.src = data.coverImage;
        preview.classList.remove("hidden");
      }
    }
    setMdEditorMode();
    openModal("shopEditModal");
  }

  function bindUi() {
    document.querySelectorAll(".shop-edit-close").forEach((el) => {
      el.addEventListener("click", () => closeModal("shopEditModal"));
    });
    document.querySelectorAll(".shop-section-close").forEach((el) => {
      el.addEventListener("click", () => {
        closeModal("shopSectionModal");
        location.reload();
      });
    });

    document.getElementById("shopAddBtn")?.addEventListener("click", () => openEditModal(null));
    document.getElementById("shopSectionBtn")?.addEventListener("click", () => openSectionModal());
    document.querySelectorAll("[data-shop-edit]").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        openEditModal(btn.getAttribute("data-shop-edit"));
      });
    });

    document.getElementById("shopEditImage")?.addEventListener("change", (e) => {
      const file = e.target.files?.[0];
      const preview = document.getElementById("shopEditImagePreview");
      if (!preview) return;
      if (!file) {
        preview.src = "";
        preview.classList.add("hidden");
        return;
      }
      preview.src = URL.createObjectURL(file);
      preview.classList.remove("hidden");
    });

    document.getElementById("shopEditEventId")?.addEventListener("change", (e) => {
      const cardNameEl = document.getElementById("shopEditCardName");
      const selectedId = e.target.value;
      if (cardNameEl && !cardNameEl.value.trim()) {
        const match = eventOptions.find((ev) => String(ev.eventId) === String(selectedId));
        if (match?.eventName) cardNameEl.value = match.eventName;
      }
      syncMdBuilderFromDom();
      setMdEditorMode();
    });

    document.getElementById("shopEditMdBuilder")?.addEventListener("click", (e) => {
      if (!hasLinkedEvent()) {
        if (e.target.closest("[data-md-add-photos]")) {
          syncMdBuilderFromDom();
          ensureFlatMdStructure();
          mdPhotoTarget = { ri: 0, ci: 0 };
          document.getElementById("shopEditMdPhoto")?.click();
          return;
        }
        const removePhoto = e.target.closest("[data-md-remove-photo]");
        if (removePhoto) {
          const pi = Number(removePhoto.getAttribute("data-md-remove-photo"));
          syncMdBuilderFromDom();
          ensureFlatMdStructure();
          mdRegions[0].categories[0].photos.splice(pi, 1);
          renderMdBuilder();
        }
        return;
      }

      if (e.target.closest("[data-md-add-region]")) {
        syncMdBuilderFromDom();
        mdRegions.push(emptyRegion());
        renderMdBuilder();
        return;
      }

      const regionEl = e.target.closest("[data-md-region]");
      if (!regionEl) return;
      const ri = Number(regionEl.getAttribute("data-md-region"));
      if (Number.isNaN(ri)) return;

      if (e.target.closest("[data-md-add-category]")) {
        syncMdBuilderFromDom();
        if (!mdRegions[ri]) return;
        mdRegions[ri].categories.push(emptyCategory());
        renderMdBuilder();
        return;
      }
      if (e.target.closest("[data-md-remove-region]")) {
        syncMdBuilderFromDom();
        mdRegions.splice(ri, 1);
        if (!mdRegions.length) mdRegions = [emptyRegion()];
        renderMdBuilder();
        return;
      }
      const catEl = e.target.closest("[data-md-category]");
      if (!catEl) return;
      const ci = Number(catEl.getAttribute("data-md-category"));
      if (Number.isNaN(ci)) return;

      if (e.target.closest("[data-md-add-photos]")) {
        syncMdBuilderFromDom();
        mdPhotoTarget = { ri, ci };
        document.getElementById("shopEditMdPhoto")?.click();
        return;
      }
      if (e.target.closest("[data-md-remove-category]")) {
        syncMdBuilderFromDom();
        if (!mdRegions[ri]) return;
        mdRegions[ri].categories.splice(ci, 1);
        if (!mdRegions[ri].categories.length) mdRegions[ri].categories = [emptyCategory()];
        renderMdBuilder();
        return;
      }
      const removePhoto = e.target.closest("[data-md-remove-photo]");
      if (removePhoto) {
        const pi = Number(removePhoto.getAttribute("data-md-remove-photo"));
        syncMdBuilderFromDom();
        if (!mdRegions[ri]?.categories[ci]) return;
        mdRegions[ri].categories[ci].photos.splice(pi, 1);
        renderMdBuilder();
      }
    });

    document.getElementById("shopEditMdBuilder")?.addEventListener("change", (e) => {
      if (e.target.matches("[data-md-sales-start], [data-md-sales-end]")) {
        const regionEl = e.target.closest("[data-md-region]");
        syncDateRangePair(
          regionEl?.querySelector("[data-md-sales-start]"),
          regionEl?.querySelector("[data-md-sales-end]")
        );
      }
      if (e.target.matches("[data-md-region-name], [data-md-cat-title], [data-md-sales-start], [data-md-sales-end]")) {
        syncMdBuilderFromDom();
      }
    });

    ["shopEditSalesStart", "shopEditSalesEnd"].forEach((id) => {
      document.getElementById(id)?.addEventListener("change", () => {
        syncDateRangePair(
          document.getElementById("shopEditSalesStart"),
          document.getElementById("shopEditSalesEnd")
        );
      });
    });

    document.getElementById("shopEditMdPhoto")?.addEventListener("change", async (e) => {
      const input = e.target;
      const files = input.files;
      if (!files || !files.length) return;
      const err = document.getElementById("shopEditError");
      try {
        await uploadMdPhotos(files);
      } catch (ex) {
        if (err) { err.hidden = false; err.textContent = ex.message || "사진 업로드 실패"; }
        else alert(ex.message || "사진 업로드 실패");
      } finally {
        input.value = "";
        mdPhotoTarget = null;
      }
    });

    document.getElementById("shopSectionAddForm")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("shopSectionError");
      const name = document.getElementById("shopSectionNewName")?.value?.trim();
      if (!name) {
        if (err) { err.hidden = false; err.textContent = "구분 이름을 입력해 주세요."; }
        return;
      }
      if (!window.confirm("이 구분을 추가하시겠습니까?")) return;
      try {
        const res = await apiFetch(API + "/sections", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name })
        });
        if (!res.ok) throw new Error(await readError(res, "추가 실패"));
        document.getElementById("shopSectionNewName").value = "";
        sectionOptions = await (await apiFetch(API + "/sections")).json();
        renderSectionList();
      } catch (ex) {
        if (err) { err.hidden = false; err.textContent = ex.message || "추가 실패"; }
      }
    });

    document.getElementById("shopSectionList")?.addEventListener("click", async (e) => {
      const row = e.target.closest("[data-section-id]");
      if (!row) return;
      const id = row.getAttribute("data-section-id");
      const err = document.getElementById("shopSectionError");
      try {
        if (e.target.closest(".section-rename")) {
          const name = row.querySelector(".section-name-input")?.value?.trim();
          if (!name) throw new Error("구분 이름을 입력해 주세요.");
          if (!window.confirm("이 구분 이름을 수정하시겠습니까?")) return;
          const res = await apiFetch(API + "/sections/" + id, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name })
          });
          if (!res.ok) throw new Error(await readError(res, "저장 실패"));
          sectionOptions = await (await apiFetch(API + "/sections")).json();
          renderSectionList();
        } else if (e.target.closest(".section-up")) {
          const res = await apiFetch(API + "/sections/" + id + "/move?direction=-1", { method: "POST" });
          if (!res.ok) throw new Error(await readError(res, "순서 변경 실패"));
          sectionOptions = await res.json();
          renderSectionList();
        } else if (e.target.closest(".section-down")) {
          const res = await apiFetch(API + "/sections/" + id + "/move?direction=1", { method: "POST" });
          if (!res.ok) throw new Error(await readError(res, "순서 변경 실패"));
          sectionOptions = await res.json();
          renderSectionList();
        } else if (e.target.closest(".section-delete")) {
          if (!confirm("이 구분을 삭제하시겠습니까? 소속 항목은 「기타」로 이동합니다.")) return;
          const res = await apiFetch(API + "/sections/" + id, { method: "DELETE" });
          if (!res.ok) throw new Error(await readError(res, "삭제 실패"));
          sectionOptions = await (await apiFetch(API + "/sections")).json();
          renderSectionList();
        }
        if (err) { err.hidden = true; err.textContent = ""; }
      } catch (ex) {
        if (err) { err.hidden = false; err.textContent = ex.message || "요청 실패"; }
      }
    });

    document.getElementById("shopEditForm")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("shopEditError");
      const id = document.getElementById("shopEditId").value;
      const title = document.getElementById("shopEditCardName")?.value?.trim() || "";
      const eventId = document.getElementById("shopEditEventId").value;
      syncMdBuilderFromDom();
      const linked = Boolean(eventId);
      if (!syncAllShopDateRanges()) {
        if (err) { err.hidden = false; err.textContent = "판매 종료일은 시작일 이후여야 합니다."; }
        return;
      }
      let salesPeriod = linked ? summarizeRegionSalesPeriods() : readSalesPeriod();
      if (!title) {
        if (err) { err.hidden = false; err.textContent = "카드 이름을 입력해 주세요."; }
        return;
      }
      if (!salesPeriod) {
        if (err) {
          err.hidden = false;
          err.textContent = linked
            ? "지역별 판매 시작일을 입력해 주세요."
            : "판매 시작일을 선택해 주세요.";
        }
        return;
      }
      if (linked) {
        const missing = mdRegions.some((r) => !formatSalesPeriod(r.salesStart, r.salesEnd));
        if (missing) {
          if (err) { err.hidden = false; err.textContent = "모든 지역에 판매 시작일을 입력해 주세요."; }
          return;
        }
        const invalid = mdRegions.some((r) => !isValidDateRange(r.salesStart, r.salesEnd));
        if (invalid) {
          if (err) { err.hidden = false; err.textContent = "판매 종료일은 시작일 이후여야 합니다."; }
          return;
        }
      }
      const mdList = serializeMdDocument(mdRegions);
      const fd = new FormData();
      fd.append("title", title);
      if (eventId) fd.append("eventId", eventId);
      fd.append("salesPeriod", salesPeriod);
      fd.append("mdList", mdList);
      const sectionId = document.getElementById("shopEditSectionId")?.value;
      if (sectionId) fd.append("sectionId", sectionId);
      const file = document.getElementById("shopEditImage").files?.[0];
      if (file) fd.append("image", file);
      const imageUrl = document.getElementById("shopEditImageUrl").value.trim();
      if (imageUrl) fd.append("imageUrl", imageUrl);
      const clearImage = document.getElementById("shopEditClearImage").checked;
      if (clearImage) fd.append("clearImage", "true");
      if (!window.confirm(clearImage
        ? "이 굿즈리스트를 저장하시겠습니까? 체크한 커버는 삭제됩니다."
        : (id ? "이 굿즈리스트를 수정하시겠습니까?" : "이 굿즈리스트를 추가하시겠습니까?"))) return;
      try {
        const url = id ? API + "/" + id : API;
        const res = await apiFetch(url, { method: "POST", body: fd });
        if (!res.ok) throw new Error(await readError(res, "저장 실패"));
        location.reload();
      } catch (ex) {
        if (err) { err.hidden = false; err.textContent = ex.message || "저장 실패"; }
      }
    });

    document.getElementById("shopEditDeleteBtn")?.addEventListener("click", async () => {
      const id = document.getElementById("shopEditId").value;
      if (!id || !confirm("이 굿즈리스트를 삭제하시겠습니까? 삭제하면 되돌릴 수 없습니다.")) return;
      try {
        const res = await apiFetch(API + "/" + id, { method: "DELETE" });
        if (!res.ok) {
          alert(await readError(res, "삭제에 실패했습니다."));
          return;
        }
        location.href = "/shop";
      } catch (_) {
        alert("삭제 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.");
      }
    });
  }

  function bindSectionTabs() {
    const tabButtons = document.querySelectorAll("#shopSectionTabs .shop-section-tab");
    const tabPanes = document.querySelectorAll("#shopSectionTabContent .tab-pane");
    if (!tabButtons.length) return;

    function activateTab(targetId) {
      if (!targetId) return;
      const btn = document.querySelector('#shopSectionTabs .shop-section-tab[data-target="' + targetId + '"]');
      const targetPane = document.querySelector(targetId);
      if (!targetPane) return;

      tabButtons.forEach((b) => {
        b.classList.remove("text-[#00f5d4]", "border-[#00f5d4]");
        b.classList.add("text-[#b9cac4]", "border-transparent");
      });

      if (btn) {
        btn.classList.remove("text-[#b9cac4]", "border-transparent");
        btn.classList.add("text-[#00f5d4]", "border-[#00f5d4]");
        const scroller = btn.closest("#shopSectionTabs");
        if (scroller && scroller.scrollWidth > scroller.clientWidth + 1) {
          const btnRect = btn.getBoundingClientRect();
          const scrollerRect = scroller.getBoundingClientRect();
          if (btnRect.left < scrollerRect.left || btnRect.right > scrollerRect.right) {
            btn.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" });
          }
        }
      }

      tabPanes.forEach((pane) => pane.classList.add("hidden"));
      targetPane.classList.remove("hidden");
    }

    tabButtons.forEach((btn) => {
      btn.addEventListener("click", () => {
        const targetId = btn.getAttribute("data-target");
        activateTab(targetId);
        if (targetId && targetId.startsWith("#")) {
          const url = new URL(window.location.href);
          url.searchParams.set("tab", targetId.slice(1));
          url.hash = "";
          history.replaceState(null, "", url.pathname + url.search);
        }
      });
    });

    const tabsEl = document.getElementById("shopSectionTabs");
    if (tabsEl) {
      tabsEl.addEventListener("wheel", (e) => {
        if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return;
        if (tabsEl.scrollWidth <= tabsEl.clientWidth + 1) return;
        e.preventDefault();
        tabsEl.scrollLeft += e.deltaY;
      }, { passive: false });
    }

    if (window.AboutTabs && typeof window.AboutTabs.enhanceTabs === "function") {
      window.AboutTabs.enhanceTabs("#shopSectionTabs", {
        onActivate: (btn) => activateTab(btn.getAttribute("data-target")),
      });
    }

    const params = new URLSearchParams(window.location.search);
    const tab = params.get("tab");
    const fromQuery = tab
      ? document.querySelector('#shopSectionTabs .shop-section-tab[data-target="#' + CSS.escape(tab) + '"]')
      : null;
    const initial = fromQuery || tabButtons[0];
    if (initial) activateTab(initial.getAttribute("data-target"));
  }

  async function refreshAdmin() {
    if (window.SiteAdmin && typeof window.SiteAdmin.refresh === "function") {
      applyAdminState(await window.SiteAdmin.refresh());
      return;
    }
    try {
      const statusRes = await apiFetch(API + "/admin/status");
      if (statusRes.ok) applyAdminState(await statusRes.json());
      else applyAdminState({ configured: false, admin: false });
    } catch (_) {
      applyAdminState({ configured: false, admin: false });
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    bindSectionTabs();
    bindUi();
    if (window.SiteAdmin && typeof window.SiteAdmin.subscribe === "function") {
      window.SiteAdmin.subscribe(applyAdminState);
    } else {
      refreshAdmin();
      document.addEventListener("site-admin:change", (event) => applyAdminState(event.detail));
    }
  });
  window.addEventListener("pageshow", (event) => { if (event.persisted) refreshAdmin(); });
})();
