(() => {
  const API = "/api/album";
  const auth = window.AboutAdminAuth;
  let adminConfigured = false;
  let isAdmin = false;
  let unitOptions = [];
  let artistOptions = [];
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
    const hint = document.getElementById("albumAdminHint");
    document.querySelectorAll("[data-album-edit], #albumAddBtn, #albumSectionBtn").forEach((control) => {
      control.hidden = !isAdmin;
    });
    if (page) page.classList.toggle("is-admin", Boolean(isAdmin));
    if (hint) hint.classList.toggle("hidden", !isAdmin);
  }

  function applyAdminState(state) {
    if (!state) return;
    adminConfigured = Boolean(state.configured);
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

  function trackRowsHtml(tracks) {
    const list = Array.isArray(tracks) && tracks.length
      ? tracks
      : [{ trackNum: 1, title: "", duration: "", isTitle: false, youtubeVideoId: "", melonSongId: "" }];
    return list.map((t, i) => `
      <div class="album-track-row" data-idx="${i}">
        <div class="album-track-main">
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
        </div>
        <div class="album-track-links">
          <label class="album-track-link">
            <span>YouTube</span>
            <input type="text" class="track-youtube-id" placeholder="영상 ID 또는 URL" value="${escapeAttr(t.youtubeVideoId || "")}" title="공식 유튜브 영상 ID 또는 watch URL" aria-label="유튜브 영상 ID">
          </label>
          <label class="album-track-link">
            <span>Melon</span>
            <input type="text" class="track-melon-id" placeholder="곡 ID 또는 URL" value="${escapeAttr(t.melonSongId || "")}" title="멜론 곡 ID 또는 곡 상세 URL" aria-label="멜론 곡 ID">
          </label>
        </div>
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
        isTitle: Boolean(row.querySelector(".track-title-flag")?.checked),
        youtubeVideoId: row.querySelector(".track-youtube-id")?.value?.trim() || "",
        melonSongId: row.querySelector(".track-melon-id")?.value?.trim() || ""
      });
    });
    return out;
  }

  const UNIT_MEMBERS_NONE = "__members__";

  function clearArtistChecks() {
    document.querySelectorAll("#albumEditArtists input[type=checkbox]").forEach((cb) => {
      cb.checked = false;
    });
  }

  function selectedArtistIds() {
    return Array.from(document.querySelectorAll("#albumEditArtists input[type=checkbox]:checked"))
      .map((cb) => cb.value);
  }

  /** 완전체는 항상 옵션으로 유지. 멤버 선택 중일 때만 '없음'을 현재값으로 쓴다. */
  function fillUnitSelect(selectedId, options) {
    const sel = document.getElementById("albumEditUnitId");
    if (!sel) return;
    const forMembers = Boolean(options && options.forMembers);
    let keepId;
    if (forMembers) keepId = UNIT_MEMBERS_NONE;
    else if (selectedId != null && selectedId !== "" && selectedId !== UNIT_MEMBERS_NONE) keepId = String(selectedId);
    else keepId = "";
    sel.innerHTML =
      `<option value="">완전체 (유닛 없음)</option>` +
      (forMembers ? `<option value="${UNIT_MEMBERS_NONE}">없음 (멤버 선택)</option>` : "") +
      unitOptions.map((u) =>
        `<option value="${u.unitId}">${escapeAttr(u.unitName)}</option>`
      ).join("");
    sel.value = keepId;
    if (sel.value !== keepId) sel.value = forMembers ? UNIT_MEMBERS_NONE : "";
    sel.disabled = false;
    sel.title = forMembers
      ? "멤버 선택 중에는 유닛이 비어 있습니다. 유닛/완전체를 고르면 멤버 선택이 해제됩니다."
      : "";
  }

  function fillArtistChecks(selectedIds) {
    const wrap = document.getElementById("albumEditArtists");
    if (!wrap) return;
    const selected = new Set((selectedIds || []).map(String));
    wrap.innerHTML = artistOptions.map((a) => `
      <label class="about-unit-chip">
        <input type="checkbox" value="${a.artistId}" ${selected.has(String(a.artistId)) ? "checked" : ""}>
        <span>${escapeAttr(a.name)}</span>
      </label>`).join("");
  }

  /** 멤버 ↔ 유닛/완전체 상호 배타 동기화. 멤버 선택 시 유형은 기타. */
  function syncParticipationUi() {
    const hasMembers = selectedArtistIds().length > 0;
    const unitSel = document.getElementById("albumEditUnitId");
    const typeSel = document.getElementById("albumEditType");
    if (hasMembers) {
      fillUnitSelect(null, { forMembers: true });
      if (typeSel) typeSel.value = "OTHER";
      return;
    }
    if (unitSel) {
      const current = unitSel.value === UNIT_MEMBERS_NONE ? "" : (unitSel.value || "");
      fillUnitSelect(current || null);
    }
  }

  function selectedUnitIdForSubmit() {
    const raw = document.getElementById("albumEditUnitId")?.value || "";
    if (!raw || raw === UNIT_MEMBERS_NONE) return "";
    return raw;
  }

  function fillSectionSelect(selectedId) {
    const sel = document.getElementById("albumEditSectionId");
    if (!sel) return;
    sel.innerHTML = '<option value="">기타 (구분 없음)</option>' +
      sectionOptions.map((s) => `<option value="${s.sectionId}" ${String(s.sectionId) === String(selectedId || "") ? "selected" : ""}>${escapeAttr(s.name)}</option>`).join("");
  }

  async function loadOptions({ requireArtists = false } = {}) {
    if (!unitOptions.length) {
      const res = await apiFetch(API + "/units");
      if (res.ok) unitOptions = await res.json();
    }
    if (!artistOptions.length) {
      const res = await apiFetch(API + "/artists");
      if (!res.ok) {
        if (requireArtists) {
          throw new Error(await readError(res, "참여 멤버 목록을 불러오지 못했습니다."));
        }
      } else {
        artistOptions = await res.json();
      }
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
    const formatSel = document.getElementById("albumEditFormat");
    if (formatSel) formatSel.value = "";
    document.getElementById("albumEditClearImage").checked = false;
    document.getElementById("albumEditImage").value = "";
    document.getElementById("albumEditImageUrl").value = "";
    const preview = document.getElementById("albumEditImagePreview");
    preview.classList.add("hidden");
    preview.removeAttribute("src");

    try {
      await loadOptions({ requireArtists: true });
    } catch (ex) {
      alert(ex.message || "참여 멤버 목록을 불러오지 못했습니다.");
      return;
    }

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
      if (formatSel) formatSel.value = data.albumFormat || "";
      // 유닛·멤버는 상호 배타: 멤버가 있으면 유닛은 비움(완전체 표기 없음)
      const artistIds = data.artistIds || [];
      fillArtistChecks(artistIds);
      if (artistIds.length) {
        fillUnitSelect(null, { forMembers: true });
        const typeSel = document.getElementById("albumEditType");
        if (typeSel) typeSel.value = "OTHER";
      } else {
        fillUnitSelect(data.unitId);
      }
      fillSectionSelect(data.sectionId);
      document.getElementById("albumEditTracks").innerHTML = trackRowsHtml(data.tracks);
      if (/^https?:\/\//i.test(data.albumImage || "")) document.getElementById("albumEditImageUrl").value = data.albumImage;
      if (data.albumImage) {
        preview.src = data.albumImage;
        preview.classList.remove("hidden");
      }
    } else {
      fillUnitSelect(null);
      fillArtistChecks([]);
      fillSectionSelect(null);
      document.getElementById("albumEditTracks").innerHTML = trackRowsHtml([]);
    }
    openModal("albumEditModal");
  }

  function bindUi() {
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

    document.getElementById("albumEditUnitId")?.addEventListener("change", () => {
      const unitSel = document.getElementById("albumEditUnitId");
      const value = unitSel?.value || "";
      // 유닛 또는 완전체를 고르면 멤버 선택 해제. '없음(멤버)'만 멤버와 함께 허용.
      if (value !== UNIT_MEMBERS_NONE) {
        clearArtistChecks();
        fillUnitSelect(value === "" ? null : value);
      }
    });

    document.getElementById("albumEditArtists")?.addEventListener("change", (e) => {
      if (!(e.target instanceof HTMLInputElement) || e.target.type !== "checkbox") return;
      syncParticipationUi();
    });

    document.getElementById("albumEditType")?.addEventListener("change", () => {
      const typeSel = document.getElementById("albumEditType");
      if (!typeSel) return;
      // 멤버 앨범은 기타만 허용. 다른 유형을 고르면 멤버 선택을 해제한다.
      if (typeSel.value !== "OTHER" && selectedArtistIds().length) {
        clearArtistChecks();
        syncParticipationUi();
      }
    });

    document.getElementById("albumSectionAddForm")?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("albumSectionError");
      const name = document.getElementById("albumSectionNewName")?.value?.trim();
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
          if (!confirm("이 구분을 삭제하시겠습니까? 소속 앨범은 「기타」로 이동합니다.")) return;
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
      const unitId = selectedUnitIdForSubmit();
      const artistIds = selectedArtistIds();
      if (unitId && artistIds.length) {
        if (err) { err.hidden = false; err.textContent = "참여 유닛과 참여 멤버는 동시에 선택할 수 없습니다."; }
        return;
      }
      let albumType = document.getElementById("albumEditType").value;
      // 멤버 지정 시 유형은 기타
      if (artistIds.length) {
        albumType = "OTHER";
        const typeSel = document.getElementById("albumEditType");
        if (typeSel) typeSel.value = "OTHER";
      }
      fd.append("albumType", albumType);
      const albumFormat = document.getElementById("albumEditFormat")?.value || "";
      fd.append("albumFormat", albumFormat);
      if (unitId) fd.append("unitId", unitId);
      artistIds.forEach((id) => fd.append("artistIds", id));
      const sectionId = document.getElementById("albumEditSectionId")?.value;
      if (sectionId) fd.append("sectionId", sectionId);
      fd.append("tracksJson", JSON.stringify(collectTracks()));
      const file = document.getElementById("albumEditImage").files?.[0];
      if (file) fd.append("image", file);
      const imageUrl = document.getElementById("albumEditImageUrl").value.trim();
      if (imageUrl) fd.append("imageUrl", imageUrl);
      const clearImage = document.getElementById("albumEditClearImage").checked;
      if (clearImage) fd.append("clearImage", "true");
      const albumName = document.getElementById("albumEditName").value.trim();
      if (!albumName) {
        if (err) { err.hidden = false; err.textContent = "앨범 이름을 입력해 주세요."; }
        return;
      }
      if (!window.confirm(clearImage ? "이 앨범을 저장하시겠습니까? 체크한 사진은 삭제됩니다." : (id ? "이 앨범을 수정하시겠습니까?" : "이 앨범을 추가하시겠습니까?"))) return;
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
      if (!id || !confirm("이 앨범을 삭제하시겠습니까? 삭제하면 되돌릴 수 없습니다.")) return;
      try {
        const res = await apiFetch(API + "/" + id, { method: "DELETE" });
        if (!res.ok) {
          alert(await readError(res, "삭제에 실패했습니다."));
          return;
        }
        location.href = "/triples/album";
      } catch (_) {
        alert("삭제 요청에 실패했습니다. 네트워크 연결을 확인해 주세요.");
      }
    });
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
        b.classList.remove("is-active");
        b.setAttribute("aria-selected", "false");
      });

      if (btn) {
        btn.classList.add("is-active");
        btn.setAttribute("aria-selected", "true");
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
