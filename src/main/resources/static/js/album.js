(() => {
  const API = "/api/album";
  const auth = window.AboutAdminAuth;
  let adminConfigured = false;
  let isAdmin = false;
  let unitOptions = [];
  let sectionOptions = [];

  function getToken() { return auth ? auth.getToken() : ""; }
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
    const btn = document.getElementById("albumAdminBtn");
    const hint = document.getElementById("albumAdminHint");
    document.querySelectorAll("[data-album-edit], #albumAddBtn, #albumSectionBtn").forEach((control) => {
      control.hidden = !isAdmin;
    });
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
    const err = document.getElementById("albumLoginError");
    const input = document.getElementById("albumLoginPassword");
    if (err) { err.hidden = true; err.textContent = ""; }
    if (input) input.value = "";
    openModal("albumLoginModal");
    if (input) input.focus();
  }

  function trackRowsHtml(tracks) {
    const list = Array.isArray(tracks) && tracks.length ? tracks : [{ trackNum: 1, title: "", duration: "", isTitle: false }];
    return list.map((t, i) => `
      <div class="album-track-row" data-idx="${i}">
        <input type="number" class="track-num" min="1" value="${t.trackNum || i + 1}" title="트랙 번호" aria-label="트랙 번호">
        <input type="text" class="track-title" placeholder="곡 제목" value="${escapeAttr(t.title || "")}" aria-label="곡 제목">
        <input type="text" class="track-duration" placeholder="3:12" value="${escapeAttr(t.duration || "")}" title="재생 시간" aria-label="재생 시간">
        <label class="track-title-toggle" title="타이틀곡">
          <input type="checkbox" class="track-title-flag" ${t.isTitle ? "checked" : ""}>
          <span>T</span>
        </label>
        <button type="button" class="about-link-remove track-remove" title="곡 삭제" aria-label="곡 삭제">
          <span class="material-symbols-outlined" style="font-size:1.05rem">close</span>
        </button>
      </div>`).join("");
  }

  function escapeAttr(s) {
    return String(s).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  }

  function collectTracks() {
    const rows = document.querySelectorAll("#albumEditTracks .album-track-row");
    const out = [];
    rows.forEach((row) => {
      const title = row.querySelector(".track-title")?.value?.trim();
      if (!title) return;
      out.push({
        trackNum: Number(row.querySelector(".track-num")?.value) || out.length + 1,
        title,
        duration: row.querySelector(".track-duration")?.value?.trim() || "",
        isTitle: Boolean(row.querySelector(".track-title-flag")?.checked)
      });
    });
    return out;
  }

  function fillUnitSelect(selectedId) {
    const sel = document.getElementById("albumEditUnitId");
    if (!sel) return;
    sel.innerHTML = '<option value="">완전체 (유닛 없음)</option>' +
      unitOptions.map((u) => `<option value="${u.unitId}" ${String(u.unitId) === String(selectedId || "") ? "selected" : ""}>${escapeAttr(u.unitName)}</option>`).join("");
  }

  function fillSectionSelect(selectedId) {
    const sel = document.getElementById("albumEditSectionId");
    if (!sel) return;
    sel.innerHTML = '<option value="">기타 (구분 없음)</option>' +
      sectionOptions.map((s) => `<option value="${s.sectionId}" ${String(s.sectionId) === String(selectedId || "") ? "selected" : ""}>${escapeAttr(s.name)}</option>`).join("");
  }

  async function loadOptions() {
    if (!unitOptions.length) {
      const res = await apiFetch(API + "/units");
      if (res.ok) unitOptions = await res.json();
    }
    const secRes = await apiFetch(API + "/sections");
    if (secRes.ok) sectionOptions = await secRes.json();
  }

  function renderSectionList() {
    const list = document.getElementById("albumSectionList");
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
    const err = document.getElementById("albumSectionError");
    if (err) { err.hidden = true; err.textContent = ""; }
    const nameInput = document.getElementById("albumSectionNewName");
    if (nameInput) nameInput.value = "";
    await loadOptions();
    renderSectionList();
    openModal("albumSectionModal");
  }

  async function openEditModal(albumId) {
    if (!isAdmin) { openLoginModal(); return; }
    const err = document.getElementById("albumEditError");
    if (err) { err.hidden = true; err.textContent = ""; }
    document.getElementById("albumEditId").value = albumId || "";
    document.getElementById("albumEditTitle").textContent = albumId ? "앨범 수정" : "앨범 추가";
    document.getElementById("albumEditDeleteBtn").classList.toggle("hidden", !albumId);
    document.getElementById("albumEditName").value = "";
    document.getElementById("albumEditReleaseDate").value = "";
    document.getElementById("albumEditInfo").value = "";
    document.getElementById("albumEditType").value = "SUBUNIT";
    document.getElementById("albumEditClearImage").checked = false;
    document.getElementById("albumEditImage").value = "";
    document.getElementById("albumEditImageUrl").value = "";
    const preview = document.getElementById("albumEditImagePreview");
    preview.classList.add("hidden");
    preview.removeAttribute("src");

    await loadOptions();

    if (albumId) {
      const res = await apiFetch(API + "/" + albumId);
      if (!res.ok) {
        alert(await readError(res, "앨범을 불러오지 못했습니다."));
        return;
      }
      const data = await res.json();
      document.getElementById("albumEditName").value = data.albumName || "";
      document.getElementById("albumEditReleaseDate").value = data.releaseDate || "";
      document.getElementById("albumEditInfo").value = data.albumInfo || "";
      document.getElementById("albumEditType").value = data.albumType || "SUBUNIT";
      fillUnitSelect(data.unitId);
      fillSectionSelect(data.sectionId);
      document.getElementById("albumEditTracks").innerHTML = trackRowsHtml(data.tracks);
      if (/^https?:\/\//i.test(data.albumImage || "")) document.getElementById("albumEditImageUrl").value = data.albumImage;
      if (data.albumImage) {
        preview.src = data.albumImage;
        preview.classList.remove("hidden");
      }
    } else {
      fillUnitSelect(null);
      fillSectionSelect(null);
      document.getElementById("albumEditTracks").innerHTML = trackRowsHtml([]);
    }
    openModal("albumEditModal");
  }

  function bindUi() {
    const adminBtn = document.getElementById("albumAdminBtn");
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

    document.getElementById("albumLoginForm")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("albumLoginError");
      const password = document.getElementById("albumLoginPassword")?.value || "";
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
        closeModal("albumLoginModal");
        setAdminUi();
      } catch (ex) {
        if (err) { err.hidden = false; err.textContent = ex.message || "로그인 실패"; }
      }
    });

    document.querySelectorAll(".album-login-close").forEach((el) => {
      el.addEventListener("click", () => closeModal("albumLoginModal"));
    });
    document.querySelectorAll(".album-edit-close").forEach((el) => {
      el.addEventListener("click", () => closeModal("albumEditModal"));
    });
    document.querySelectorAll(".album-section-close").forEach((el) => {
      el.addEventListener("click", () => {
        closeModal("albumSectionModal");
        location.reload();
      });
    });

    document.getElementById("albumAddBtn")?.addEventListener("click", () => openEditModal(null));
    document.getElementById("albumSectionBtn")?.addEventListener("click", () => openSectionModal());
    document.querySelectorAll("[data-album-edit]").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        openEditModal(btn.getAttribute("data-album-edit"));
      });
    });

    document.getElementById("albumAddTrackBtn")?.addEventListener("click", () => {
      const wrap = document.getElementById("albumEditTracks");
      const tmp = document.createElement("div");
      tmp.innerHTML = trackRowsHtml([{ trackNum: wrap.children.length + 1, title: "", duration: "", isTitle: false }]);
      wrap.appendChild(tmp.firstElementChild);
    });

    document.getElementById("albumEditTracks")?.addEventListener("click", (e) => {
      if (e.target.closest(".track-remove")) e.target.closest(".album-track-row")?.remove();
    });

    document.getElementById("albumSectionAddForm")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("albumSectionError");
      const name = document.getElementById("albumSectionNewName")?.value?.trim();
      try {
        const res = await apiFetch(API + "/sections", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name })
        });
        if (!res.ok) throw new Error(await readError(res, "추가 실패"));
        document.getElementById("albumSectionNewName").value = "";
        sectionOptions = await (await apiFetch(API + "/sections")).json();
        renderSectionList();
      } catch (ex) {
        if (err) { err.hidden = false; err.textContent = ex.message || "추가 실패"; }
      }
    });

    document.getElementById("albumSectionList")?.addEventListener("click", async (e) => {
      const row = e.target.closest("[data-section-id]");
      if (!row) return;
      const id = row.getAttribute("data-section-id");
      const err = document.getElementById("albumSectionError");
      try {
        if (e.target.closest(".section-rename")) {
          const name = row.querySelector(".section-name-input")?.value?.trim();
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
          if (!confirm("이 구분을 삭제할까요? 소속 앨범은 「기타」로 이동합니다.")) return;
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

    document.getElementById("albumEditForm")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("albumEditError");
      const id = document.getElementById("albumEditId").value;
      const fd = new FormData();
      fd.append("albumName", document.getElementById("albumEditName").value.trim());
      fd.append("releaseDate", document.getElementById("albumEditReleaseDate").value.trim());
      fd.append("albumInfo", document.getElementById("albumEditInfo").value);
      fd.append("albumType", document.getElementById("albumEditType").value);
      const unitId = document.getElementById("albumEditUnitId").value;
      if (unitId) fd.append("unitId", unitId);
      const sectionId = document.getElementById("albumEditSectionId")?.value;
      if (sectionId) fd.append("sectionId", sectionId);
      fd.append("tracksJson", JSON.stringify(collectTracks()));
      const file = document.getElementById("albumEditImage").files?.[0];
      if (file) fd.append("image", file);
      const imageUrl = document.getElementById("albumEditImageUrl").value.trim();
      if (imageUrl) fd.append("imageUrl", imageUrl);
      if (document.getElementById("albumEditClearImage").checked) fd.append("clearImage", "true");
      try {
        const url = id ? API + "/" + id : API;
        const res = await apiFetch(url, { method: "POST", body: fd });
        if (!res.ok) throw new Error(await readError(res, "저장 실패"));
        location.reload();
      } catch (ex) {
        if (err) { err.hidden = false; err.textContent = ex.message || "저장 실패"; }
      }
    });

    document.getElementById("albumEditDeleteBtn")?.addEventListener("click", async () => {
      const id = document.getElementById("albumEditId").value;
      if (!id || !confirm("이 앨범을 삭제할까요?")) return;
      const res = await apiFetch(API + "/" + id, { method: "DELETE" });
      if (!res.ok) {
        alert(await readError(res, "삭제 실패"));
        return;
      }
      location.href = "/triples/album";
    });
  }

  async function refreshAdmin() {
    isAdmin = false;
    setAdminUi();
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

  function bindSectionTabs() {
    const tabButtons = document.querySelectorAll("#albumSectionTabs .album-section-tab");
    const tabPanes = document.querySelectorAll("#albumSectionTabContent .tab-pane");
    if (!tabButtons.length) return;

    function activateTab(targetId) {
      if (!targetId) return;
      const btn = document.querySelector('#albumSectionTabs .album-section-tab[data-target="' + targetId + '"]');
      const targetPane = document.querySelector(targetId);
      if (!targetPane) return;

      tabButtons.forEach((b) => {
        const img = b.querySelector("img");
        const span = b.querySelector("span");
        if (img) {
          img.classList.remove("border-[#00f5d4]");
          img.classList.add("border-transparent");
        }
        if (span) {
          span.classList.remove("text-[#00f5d4]");
          span.classList.add("text-[#b9cac4]");
        }
      });

      if (btn) {
        const activeImg = btn.querySelector("img");
        const activeSpan = btn.querySelector("span");
        if (activeImg) {
          activeImg.classList.remove("border-transparent");
          activeImg.classList.add("border-[#00f5d4]");
        }
        if (activeSpan) {
          activeSpan.classList.remove("text-[#b9cac4]");
          activeSpan.classList.add("text-[#00f5d4]");
        }
        const scroller = btn.closest("#albumSectionTabs");
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

    const tabsEl = document.getElementById("albumSectionTabs");
    if (tabsEl) {
      tabsEl.addEventListener("wheel", (e) => {
        if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return;
        if (tabsEl.scrollWidth <= tabsEl.clientWidth + 1) return;
        e.preventDefault();
        tabsEl.scrollLeft += e.deltaY;
      }, { passive: false });
    }

    const params = new URLSearchParams(window.location.search);
    const tab = params.get("tab");
    const fromQuery = tab
      ? document.querySelector('#albumSectionTabs .album-section-tab[data-target="#' + CSS.escape(tab) + '"]')
      : null;
    const initial = fromQuery || tabButtons[0];
    if (initial) activateTab(initial.getAttribute("data-target"));
  }

  document.addEventListener("DOMContentLoaded", () => { bindSectionTabs(); bindUi(); refreshAdmin(); });
  window.addEventListener("pageshow", event => { if (event.persisted) refreshAdmin(); });
  window.addEventListener("storage", event => { if (auth && event.key === auth.TOKEN_KEY) refreshAdmin(); });
})();
