(() => {
  const API = "/api/unit";
  const auth = window.AboutAdminAuth;
  let adminConfigured = false;
  let isAdmin = false;
  let unitOptions = [];
  let artistOptions = [];

  function getToken() { return auth ? auth.getToken() : ""; }
  function setToken(token) { if (auth) auth.setToken(token); }

  async function apiFetch(url, options) {
    const opts = options ? { ...options } : {};
    opts.headers = auth ? auth.applyAuthHeaders(opts.headers || {}) : new Headers(opts.headers || {});
    opts.credentials = "same-origin";
    const res = await fetch(url, opts);
    if (res.status === 403) {
      setToken("");
      isAdmin = false;
      setAdminUi();
    }
    return res;
  }

  async function readError(res, fallback) {
    if (res.status === 403) {
      return "관리자 로그인이 만료되었습니다. 창을 닫고 관리자 로그인 후 다시 저장해 주세요.";
    }
    try {
      const data = await res.json();
      if (data && (data.detail || data.message || data.error || data.title)) {
        return data.detail || data.message || data.error || data.title;
      }
    } catch (_) { /* ignore */ }
    return fallback || "요청에 실패했습니다.";
  }

  function setAdminUi() {
    const page = document.querySelector(".about-admin-page");
    const btn = document.getElementById("unitAdminBtn");
    const hint = document.getElementById("unitAdminHint");
    if (page) page.classList.toggle("is-admin", Boolean(isAdmin));
    if (btn) {
      if (!adminConfigured) btn.classList.add("hidden");
      else {
        btn.classList.remove("hidden");
        btn.textContent = isAdmin ? "관리자 로그아웃" : "관리자";
        btn.setAttribute("aria-pressed", isAdmin ? "true" : "false");
        btn.classList.toggle("is-active", isAdmin);
      }
    }
    if (hint) hint.classList.toggle("hidden", !isAdmin);
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
    const err = document.getElementById("unitLoginError");
    const input = document.getElementById("unitLoginPassword");
    if (err) { err.hidden = true; err.textContent = ""; }
    if (input) input.value = "";
    openModal("unitLoginModal");
    if (input) input.focus();
  }

  function escapeAttr(s) {
    return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  }

  async function ensureOptions() {
    if (!unitOptions.length) {
      const res = await apiFetch(API + "/options");
      if (res.ok) unitOptions = await res.json();
    }
    if (!artistOptions.length) {
      const res = await apiFetch(API + "/artists");
      if (res.ok) artistOptions = await res.json();
    }
  }

  function fillParentSelect(selectedId, selfId) {
    const sel = document.getElementById("unitEditParentId");
    if (!sel) return;
    sel.innerHTML = '<option value="">없음 (최상위)</option>' +
      unitOptions
        .filter((u) => String(u.unitId) !== String(selfId || ""))
        .map((u) => `<option value="${u.unitId}" ${String(u.unitId) === String(selectedId || "") ? "selected" : ""}>${escapeAttr(u.unitName)}</option>`)
        .join("");
  }

  function fillArtistChecks(selectedIds) {
    const wrap = document.getElementById("unitEditArtists");
    if (!wrap) return;
    const selected = new Set((selectedIds || []).map(String));
    wrap.innerHTML = artistOptions.map((a) => `
      <label class="about-unit-chip">
        <input type="checkbox" value="${a.artistId}" ${selected.has(String(a.artistId)) ? "checked" : ""}>
        <span>${escapeAttr(a.name)}</span>
      </label>`).join("");
  }

  async function openEditModal(unitId) {
    const err = document.getElementById("unitEditError");
    if (err) { err.hidden = true; err.textContent = ""; }
    document.getElementById("unitEditId").value = unitId || "";
    document.getElementById("unitEditTitle").textContent = unitId ? "유닛 수정" : "유닛 추가";
    document.getElementById("unitEditDeleteBtn").classList.toggle("hidden", !unitId);
    document.getElementById("unitEditName").value = "";
    document.getElementById("unitEditSmallName").value = "";
    document.getElementById("unitEditFormation").value = "";
    document.getElementById("unitEditDebut").value = "";
    document.getElementById("unitEditVisible").checked = true;
    document.getElementById("unitEditClearImage").checked = false;
    document.getElementById("unitEditImage").value = "";
    const preview = document.getElementById("unitEditImagePreview");
    preview.classList.add("hidden");
    preview.removeAttribute("src");

    await ensureOptions();

    if (unitId) {
      const res = await apiFetch(API + "/" + unitId);
      if (!res.ok) {
        alert(await readError(res, "유닛을 불러오지 못했습니다."));
        return;
      }
      const data = await res.json();
      document.getElementById("unitEditName").value = data.unitName || "";
      document.getElementById("unitEditSmallName").value = data.smallName || "";
      document.getElementById("unitEditFormation").value = data.formationDate || "";
      document.getElementById("unitEditDebut").value = data.debutDate || "";
      document.getElementById("unitEditVisible").checked = data.visible !== false;
      fillParentSelect(data.parentUnitId, data.unitId);
      fillArtistChecks(data.artistIds);
      if (data.unitImage) {
        preview.src = data.unitImage;
        preview.classList.remove("hidden");
      }
    } else {
      fillParentSelect(null, null);
      fillArtistChecks([]);
    }
    openModal("unitEditModal");
  }

  function activateTab(btn) {
    if (!btn) return;
    const tabButtons = document.querySelectorAll("#triplesUnitTabs .nav-tab-btn");
    const tabPanes = document.querySelectorAll("#unitTabContent .tab-pane");
    const targetId = btn.getAttribute("data-target");
    if (/^#unit-[0-9]+$/.test(targetId)) {
      const current = new URL(window.location.href);
      current.searchParams.set("tab", targetId.slice(1));
      window.history.replaceState(null, "", current.pathname + current.search + current.hash);
    }
    tabButtons.forEach((b) => b.classList.remove("is-active"));
    btn.classList.add("is-active");
    tabPanes.forEach((pane) => pane.classList.add("hidden"));
    const pane = document.querySelector(targetId);
    if (pane) {
      pane.querySelectorAll("img[data-unit-src]").forEach(img => {
        const source = (img.getAttribute("data-unit-src") || "").trim();
        img.src = source || window.__imagePlaceholder || "/images/image-upload-pending.png";
        img.removeAttribute("data-unit-src");
      });
      pane.classList.remove("hidden");
    }
    const scroller = btn.closest(".unit-tabs-scroller");
    if (scroller) {
      const btnRect = btn.getBoundingClientRect();
      const scrollerRect = scroller.getBoundingClientRect();
      if (btnRect.left < scrollerRect.left || btnRect.right > scrollerRect.right) {
        btn.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" });
      }
    }
  }

  function bindTabScroller() {
    const scroller = document.querySelector(".unit-tabs-scroller");
    if (!scroller) return;
    scroller.addEventListener("wheel", (e) => {
      if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return;
      if (scroller.scrollWidth <= scroller.clientWidth + 1) return;
      e.preventDefault();
      scroller.scrollLeft += e.deltaY;
    }, { passive: false });
  }

  function bindTabs() {
    const tabButtons = document.querySelectorAll("#triplesUnitTabs .nav-tab-btn");
    tabButtons.forEach((btn) => {
      btn.addEventListener("click", () => activateTab(btn));
    });
    bindTabScroller();
    const params = new URLSearchParams(window.location.search);
    const tab = params.get("tab");
    const fromQuery = tab
      ? document.querySelector(`#triplesUnitTabs .nav-tab-btn[data-target="#${CSS.escape(tab)}"]`)
      : null;
    activateTab(fromQuery || tabButtons[0] || null);
  }

  function bindUi() {
    const adminBtn = document.getElementById("unitAdminBtn");
    if (adminBtn) {
      adminBtn.addEventListener("click", async () => {
        if (isAdmin) {
          await apiFetch(API + "/admin/logout", { method: "POST" });
          setToken("");
          isAdmin = false;
          setAdminUi();
          return;
        }
        openLoginModal();
      });
    }

    document.getElementById("unitLoginForm")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("unitLoginError");
      const password = document.getElementById("unitLoginPassword")?.value || "";
      try {
        const res = await apiFetch(API + "/admin/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ password })
        });
        if (!res.ok) throw new Error(await readError(res, "로그인 실패"));
        const data = await res.json();
        setToken(data.token || "");
        isAdmin = true;
        closeModal("unitLoginModal");
        setAdminUi();
      } catch (ex) {
        if (err) { err.hidden = false; err.textContent = ex.message || "로그인 실패"; }
      }
    });

    document.querySelectorAll(".unit-login-close").forEach((el) => el.addEventListener("click", () => closeModal("unitLoginModal")));
    document.querySelectorAll(".unit-edit-close").forEach((el) => el.addEventListener("click", () => closeModal("unitEditModal")));
    document.getElementById("unitAddBtn")?.addEventListener("click", () => openEditModal(null));
    document.querySelectorAll("[data-unit-edit]").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        openEditModal(btn.getAttribute("data-unit-edit"));
      });
    });

    document.getElementById("unitEditForm")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("unitEditError");
      const id = document.getElementById("unitEditId").value;
      const fd = new FormData();
      fd.append("unitName", document.getElementById("unitEditName").value.trim());
      fd.append("smallName", document.getElementById("unitEditSmallName").value.trim());
      fd.append("formationDate", document.getElementById("unitEditFormation").value.trim());
      fd.append("debutDate", document.getElementById("unitEditDebut").value.trim());
      fd.append("visible", document.getElementById("unitEditVisible").checked ? "true" : "false");
      const parentId = document.getElementById("unitEditParentId").value;
      if (parentId) fd.append("parentUnitId", parentId);
      document.querySelectorAll("#unitEditArtists input[type=checkbox]:checked").forEach((cb) => {
        fd.append("artistIds", cb.value);
      });
      const file = document.getElementById("unitEditImage").files?.[0];
      if (file) fd.append("image", file);
      const imageUrl = document.getElementById("unitEditImageUrl")?.value?.trim();
      if (imageUrl) fd.append("imageUrl", imageUrl);
      const clearImage = document.getElementById("unitEditClearImage").checked;
      if (clearImage) fd.append("clearImage", "true");
      if (!document.getElementById("unitEditName").value.trim()) {
        if (err) { err.hidden = false; err.textContent = "유닛 이름을 입력해 주세요."; }
        return;
      }
      if (!window.confirm(clearImage ? "이 유닛을 저장하시겠습니까? 체크한 사진은 삭제됩니다." : (id ? "이 유닛을 수정하시겠습니까?" : "이 유닛을 추가하시겠습니까?"))) return;
      try {
        const url = id ? API + "/" + id : API;
        const res = await apiFetch(url, { method: "POST", body: fd });
        if (!res.ok) throw new Error(await readError(res, "저장 실패"));
        const saved = await res.json();
        const destination = new URL(window.location.href);
        destination.pathname = "/triples/unit";
        destination.hash = "";
        if (saved.visible !== false) {
          destination.searchParams.set("tab", "unit-" + saved.unitId);
        } else {
          destination.searchParams.delete("tab");
        }
        window.location.assign(destination.toString());
      } catch (ex) {
        if (err) { err.hidden = false; err.textContent = ex.message || "저장 실패"; }
      }
    });

    document.getElementById("unitEditDeleteBtn")?.addEventListener("click", async () => {
      const id = document.getElementById("unitEditId").value;
      const err = document.getElementById("unitEditError");
      if (!id) return;
      if (!confirm("이 유닛을 삭제하시겠습니까?\n연결된 앨범·하위 유닛은 삭제되지 않고 연결만 해제됩니다.")) return;
      try {
        const res = await apiFetch(API + "/" + id, { method: "DELETE" });
        if (!res.ok) throw new Error(await readError(res, "삭제 실패"));
        location.href = "/triples/unit";
      } catch (ex) {
        if (err) { err.hidden = false; err.textContent = ex.message || "삭제 실패"; }
        else alert(ex.message || "삭제 실패");
      }
    });
  }

  async function init() {
    bindTabs();
    bindUi();
    try {
      const statusRes = await apiFetch(API + "/admin/status");
      if (statusRes.ok) {
        const status = await statusRes.json();
        adminConfigured = Boolean(status.configured);
        isAdmin = Boolean(status.admin);
        if (!isAdmin) setToken("");
      }
    } catch (_) { /* ignore */ }
    setAdminUi();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
