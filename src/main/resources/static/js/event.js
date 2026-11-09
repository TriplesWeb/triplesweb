(() => {
  const API = "/api/event";
  const auth = window.AboutAdminAuth;
  let isAdmin = false;
  let sectionOptions = [];

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
    const hint = document.getElementById("eventAdminHint");
    document.querySelectorAll("[data-event-edit], #eventAddBtn, #eventSectionBtn").forEach((control) => {
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

  function openLoginModal() {
    if (window.SiteAdmin && typeof window.SiteAdmin.openLogin === "function") {
      window.SiteAdmin.openLogin();
    }
  }

  function escapeAttr(s) {
    return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  }

  function fillSectionSelect(selectedId) {
    const sel = document.getElementById("eventEditSectionId");
    if (!sel) return;
    sel.innerHTML = '<option value="">기타 (구분 없음)</option>' +
      sectionOptions.map((s) =>
        `<option value="${s.sectionId}" ${String(s.sectionId) === String(selectedId || "") ? "selected" : ""}>${escapeAttr(s.name)}</option>`
      ).join("");
  }

  async function loadOptions() {
    const secRes = await apiFetch(API + "/sections");
    if (secRes.ok) sectionOptions = await secRes.json();
  }

  function escapeText(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;");
  }

  /** 표시용 날짜(2026.02.28) → input[type=date] 값 */
  function toIsoDate(dotted) {
    const m = String(dotted || "").trim().match(/^(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})$/);
    if (!m) return "";
    return `${m[1]}-${m[2].padStart(2, "0")}-${m[3].padStart(2, "0")}`;
  }

  /** input[type=date] 값 → 저장/표시용 2026.02.28 */
  function toDisplayDate(iso) {
    const m = String(iso || "").trim().match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (!m) return "";
    return `${m[1]}.${m[2]}.${m[3]}`;
  }

  function parseEventDate(raw) {
    const s = String(raw || "").trim();
    if (!s) return { start: "", end: "" };
    const parts = s.split(/\s*[~～〜]\s*/);
    return {
      start: toIsoDate(parts[0] || ""),
      end: toIsoDate(parts[1] || "")
    };
  }

  function formatEventDate(startIso, endIso) {
    const start = toDisplayDate(startIso);
    if (!start) return "";
    const end = toDisplayDate(endIso);
    if (!end || end === start) return start;
    return `${start}~${end}`;
  }

  function stopRowsHtml(stops) {
    const list = Array.isArray(stops) && stops.length
      ? stops
      : [{ sortOrder: 0, eventDate: "", region: "", venue: "", detail: "", setlist: "" }];
    return list.map((s, i) => {
      const dates = parseEventDate(s.eventDate || "");
      return `
      <div class="event-stop-row" data-idx="${i}">
        <div class="event-stop-row-top">
          <div class="stop-date-range" title="개최일">
            <input type="date" class="stop-date-start" value="${escapeAttr(dates.start)}" title="시작일" aria-label="시작일">
            <span class="stop-date-sep" aria-hidden="true">~</span>
            <input type="date" class="stop-date-end" value="${escapeAttr(dates.end)}" title="종료일 (선택)" aria-label="종료일 (선택)">
          </div>
          <input type="text" class="stop-region" placeholder="지역" value="${escapeAttr(s.region || "")}" title="지역" aria-label="지역">
          <input type="text" class="stop-venue" placeholder="공연장" value="${escapeAttr(s.venue || "")}" title="공연장" aria-label="공연장">
          <button type="button" class="about-link-remove stop-remove" title="회차 삭제" aria-label="회차 삭제">
            <span class="material-symbols-outlined" aria-hidden="true" style="font-size:1.05rem">close</span>
          </button>
        </div>
        <label class="event-stop-field">
          <span>상세 안내</span>
          <input type="text" class="stop-detail" placeholder="선택" value="${escapeAttr(s.detail || "")}" aria-label="상세 안내">
        </label>
        <div class="event-stop-lists">
          <label class="event-stop-field">
            <span>셋리스트</span>
            <textarea class="stop-setlist" rows="6" placeholder="# VCR 1 : OPENING VCR&#10;곡명|앨범|비고&#10;곡A|앨범A || 곡B|앨범B">${escapeText(s.setlist || "")}</textarea>
          </label>
        </div>
      </div>`;
    }).join("");
  }

  function collectStops() {
    const rows = document.querySelectorAll("#eventEditStops .event-stop-row");
    const out = [];
    rows.forEach((row, i) => {
      const start = row.querySelector(".stop-date-start")?.value?.trim() || "";
      const end = row.querySelector(".stop-date-end")?.value?.trim() || "";
      const eventDate = formatEventDate(start, end);
      if (!eventDate) return;
      out.push({
        sortOrder: i,
        eventDate,
        region: row.querySelector(".stop-region")?.value?.trim() || "",
        venue: row.querySelector(".stop-venue")?.value?.trim() || "",
        detail: row.querySelector(".stop-detail")?.value?.trim() || "",
        setlist: row.querySelector(".stop-setlist")?.value || ""
      });
    });
    return out;
  }

  function renderSectionList() {
    const list = document.getElementById("eventSectionList");
    if (!list) return;
    if (!sectionOptions.length) {
      list.innerHTML = '<li class="text-sm text-[#b9cac4]/70 py-2">등록된 구분이 없습니다.</li>';
      return;
    }
    list.innerHTML = sectionOptions.map((s, i) => `
      <li class="flex flex-wrap items-center gap-2 border border-[#3a4a46]/40 rounded-lg p-2" data-section-id="${s.sectionId}">
        <input type="text" class="section-name-input flex-1 min-w-[8rem] bg-[#0d0e12] border border-[#3a4a46]/50 rounded px-2 py-1 text-sm" value="${escapeAttr(s.name)}" maxlength="120" aria-label="구분명">
        <button type="button" class="about-mode-btn section-rename" title="이름 저장">저장</button>
        <button type="button" class="about-mode-btn section-up" ${i === 0 ? "disabled" : ""} title="위로">↑</button>
        <button type="button" class="about-mode-btn section-down" ${i === sectionOptions.length - 1 ? "disabled" : ""} title="아래로">↓</button>
        <button type="button" class="about-mode-btn about-btn-danger section-delete" title="삭제">삭제</button>
      </li>`).join("");
  }

  async function openSectionModal() {
    if (!isAdmin) { openLoginModal(); return; }
    const err = document.getElementById("eventSectionError");
    if (err) { err.hidden = true; err.textContent = ""; }
    const nameInput = document.getElementById("eventSectionNewName");
    if (nameInput) nameInput.value = "";
    await loadOptions();
    renderSectionList();
    openModal("eventSectionModal");
  }

  async function openEditModal(eventId) {
    if (!isAdmin) { openLoginModal(); return; }
    const err = document.getElementById("eventEditError");
    if (err) { err.hidden = true; err.textContent = ""; }
    document.getElementById("eventEditId").value = eventId || "";
    document.getElementById("eventEditTitle").textContent = eventId ? "이벤트 수정" : "이벤트 추가";
    document.getElementById("eventEditDeleteBtn").classList.toggle("hidden", !eventId);
    document.getElementById("eventEditName").value = "";
    document.getElementById("eventEditInfo").value = "";
    document.getElementById("eventEditClearImage").checked = false;
    document.getElementById("eventEditImage").value = "";
    document.getElementById("eventEditImageUrl").value = "";
    const preview = document.getElementById("eventEditImagePreview");
    preview.classList.add("hidden");
    preview.removeAttribute("src");

    await loadOptions();

    if (eventId) {
      const res = await apiFetch(API + "/" + eventId);
      if (!res.ok) {
        alert(await readError(res, "이벤트를 불러오지 못했습니다."));
        return;
      }
      const data = await res.json();
      document.getElementById("eventEditName").value = data.eventName || "";
      document.getElementById("eventEditInfo").value = data.eventInfo || "";
      fillSectionSelect(data.sectionId);
      document.getElementById("eventEditStops").innerHTML = stopRowsHtml(data.stops);
      if (/^https?:\/\//i.test(data.eventImage || "")) document.getElementById("eventEditImageUrl").value = data.eventImage;
      if (data.eventImage) {
        preview.src = data.eventImage;
        preview.classList.remove("hidden");
      }
    } else {
      fillSectionSelect(null);
      document.getElementById("eventEditStops").innerHTML = stopRowsHtml([]);
    }
    openModal("eventEditModal");
  }

  function bindUi() {
    document.querySelectorAll(".event-edit-close").forEach((el) => {
      el.addEventListener("click", () => closeModal("eventEditModal"));
    });
    document.querySelectorAll(".event-section-close").forEach((el) => {
      el.addEventListener("click", () => {
        closeModal("eventSectionModal");
        location.reload();
      });
    });

    document.getElementById("eventAddBtn")?.addEventListener("click", () => openEditModal(null));
    document.getElementById("eventSectionBtn")?.addEventListener("click", () => openSectionModal());
    document.querySelectorAll("[data-event-edit]").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        openEditModal(btn.getAttribute("data-event-edit"));
      });
    });

    document.getElementById("eventAddStopBtn")?.addEventListener("click", () => {
      const wrap = document.getElementById("eventEditStops");
      const tmp = document.createElement("div");
      tmp.innerHTML = stopRowsHtml([{ sortOrder: wrap.children.length, eventDate: "", region: "", venue: "", detail: "" }]);
      wrap.appendChild(tmp.firstElementChild);
    });

    document.getElementById("eventEditStops")?.addEventListener("click", (e) => {
      if (e.target.closest(".stop-remove")) e.target.closest(".event-stop-row")?.remove();
    });

    document.getElementById("eventSectionAddForm")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("eventSectionError");
      const name = document.getElementById("eventSectionNewName")?.value?.trim();
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
        document.getElementById("eventSectionNewName").value = "";
        sectionOptions = await (await apiFetch(API + "/sections")).json();
        renderSectionList();
      } catch (ex) {
        if (err) { err.hidden = false; err.textContent = ex.message || "추가 실패"; }
      }
    });

    document.getElementById("eventSectionList")?.addEventListener("click", async (e) => {
      const row = e.target.closest("[data-section-id]");
      if (!row) return;
      const id = row.getAttribute("data-section-id");
      const err = document.getElementById("eventSectionError");
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
          if (!confirm("이 구분을 삭제하시겠습니까? 소속 이벤트는 「기타」로 이동합니다.")) return;
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

    document.getElementById("eventEditForm")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("eventEditError");
      const id = document.getElementById("eventEditId").value;
      const eventName = document.getElementById("eventEditName").value.trim();
      if (!eventName) {
        if (err) { err.hidden = false; err.textContent = "이벤트 이름을 입력해 주세요."; }
        return;
      }
      const stops = collectStops();
      if (!stops.length) {
        if (err) { err.hidden = false; err.textContent = "지역 회차를 하나 이상 입력해 주세요."; }
        return;
      }
      const fd = new FormData();
      fd.append("eventName", eventName);
      fd.append("eventInfo", document.getElementById("eventEditInfo").value);
      fd.append("stopsJson", JSON.stringify(stops));
      const sectionId = document.getElementById("eventEditSectionId")?.value;
      if (sectionId) fd.append("sectionId", sectionId);
      const file = document.getElementById("eventEditImage").files?.[0];
      if (file) fd.append("image", file);
      const imageUrl = document.getElementById("eventEditImageUrl").value.trim();
      if (imageUrl) fd.append("imageUrl", imageUrl);
      const clearImage = document.getElementById("eventEditClearImage").checked;
      if (clearImage) fd.append("clearImage", "true");
      if (!window.confirm(clearImage
        ? "이 이벤트를 저장하시겠습니까? 체크한 사진은 삭제됩니다."
        : (id ? "이 이벤트를 수정하시겠습니까?" : "이 이벤트를 추가하시겠습니까?"))) return;
      try {
        const url = id ? API + "/" + id : API;
        const res = await apiFetch(url, { method: "POST", body: fd });
        if (!res.ok) throw new Error(await readError(res, "저장 실패"));
        location.reload();
      } catch (ex) {
        if (err) { err.hidden = false; err.textContent = ex.message || "저장 실패"; }
      }
    });

    document.getElementById("eventEditDeleteBtn")?.addEventListener("click", async () => {
      const id = document.getElementById("eventEditId").value;
      if (!id || !confirm("이 이벤트를 삭제하시겠습니까? 삭제하면 되돌릴 수 없습니다.")) return;
      try {
        const res = await apiFetch(API + "/" + id, { method: "DELETE" });
        if (!res.ok) {
          alert(await readError(res, "삭제에 실패했습니다."));
          return;
        }
        location.href = "/cosmo/event";
      } catch (_) {
        alert("삭제 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.");
      }
    });
  }

  function bindSectionTabs() {
    const tabButtons = document.querySelectorAll("#eventSectionTabs .event-section-tab");
    const tabPanes = document.querySelectorAll("#eventSectionTabContent .tab-pane");
    if (!tabButtons.length) return;

    function activateTab(targetId) {
      if (!targetId) return;
      const btn = document.querySelector('#eventSectionTabs .event-section-tab[data-target="' + targetId + '"]');
      const targetPane = document.querySelector(targetId);
      if (!targetPane) return;

      tabButtons.forEach((b) => {
        b.classList.remove("text-[#00f5d4]", "border-[#00f5d4]");
        b.classList.add("text-[#b9cac4]", "border-transparent");
      });

      if (btn) {
        btn.classList.remove("text-[#b9cac4]", "border-transparent");
        btn.classList.add("text-[#00f5d4]", "border-[#00f5d4]");
        const scroller = btn.closest("#eventSectionTabs");
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

    const tabsEl = document.getElementById("eventSectionTabs");
    if (tabsEl) {
      tabsEl.addEventListener("wheel", (e) => {
        if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return;
        if (tabsEl.scrollWidth <= tabsEl.clientWidth + 1) return;
        e.preventDefault();
        tabsEl.scrollLeft += e.deltaY;
      }, { passive: false });
    }

    if (window.AboutTabs && typeof window.AboutTabs.enhanceTabs === "function") {
      window.AboutTabs.enhanceTabs("#eventSectionTabs", {
        onActivate: (btn) => activateTab(btn.getAttribute("data-target")),
      });
    }

    const params = new URLSearchParams(window.location.search);
    const tab = params.get("tab");
    const fromQuery = tab
      ? document.querySelector('#eventSectionTabs .event-section-tab[data-target="#' + CSS.escape(tab) + '"]')
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
