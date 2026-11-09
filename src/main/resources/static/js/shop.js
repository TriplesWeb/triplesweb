(() => {
  const API = "/api/shop";
  const auth = window.AboutAdminAuth;
  let isAdmin = false;
  let sectionOptions = [];
  let eventOptions = [];

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
    const options = eventOptions.length
      ? eventOptions.map((e) =>
          `<option value="${e.eventId}" ${String(e.eventId) === String(selectedId || "") ? "selected" : ""}>${escapeAttr(e.eventName)}</option>`
        ).join("")
      : '<option value="">등록된 공연이 없습니다</option>';
    sel.innerHTML = options;
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
    document.getElementById("shopEditSalesPeriod").value = "";
    document.getElementById("shopEditMdList").value = "";
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
      document.getElementById("shopEditSalesPeriod").value = data.salesPeriod || "";
      document.getElementById("shopEditMdList").value = data.mdList || "";
      fillSectionSelect(data.sectionId);
      fillEventSelect(data.eventId);
      if (data.coverImage && preview) {
        preview.src = data.coverImage;
        preview.classList.remove("hidden");
      }
    }
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
      const eventId = document.getElementById("shopEditEventId").value;
      const salesPeriod = document.getElementById("shopEditSalesPeriod").value.trim();
      if (!eventId) {
        if (err) { err.hidden = false; err.textContent = "공연을 선택해 주세요."; }
        return;
      }
      if (!salesPeriod) {
        if (err) { err.hidden = false; err.textContent = "판매 기간을 입력해 주세요."; }
        return;
      }
      const fd = new FormData();
      fd.append("eventId", eventId);
      fd.append("salesPeriod", salesPeriod);
      fd.append("mdList", document.getElementById("shopEditMdList")?.value || "");
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
