(() => {
  const API = "/api/album";
  const auth = window.AboutAdminAuth;
  let adminConfigured = false;
  let isAdmin = false;
  let unitOptions = [];

  function getToken() { return auth ? auth.getToken() : ""; }
  function setToken(token) { if (auth) auth.setToken(token); }

  async function apiFetch(url, options) {
    const opts = options ? { ...options } : {};
    opts.headers = auth ? auth.applyAuthHeaders(opts.headers || {}) : new Headers(opts.headers || {});
    opts.credentials = "same-origin";
    return fetch(url, opts);
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

  async function openEditModal(albumId) {
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
    const preview = document.getElementById("albumEditImagePreview");
    preview.classList.add("hidden");
    preview.removeAttribute("src");

    if (!unitOptions.length) {
      const res = await apiFetch(API + "/units");
      if (res.ok) unitOptions = await res.json();
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
      fillUnitSelect(data.unitId);
      document.getElementById("albumEditTracks").innerHTML = trackRowsHtml(data.tracks);
      if (data.albumImage) {
        preview.src = data.albumImage;
        preview.classList.remove("hidden");
      }
    } else {
      fillUnitSelect(null);
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

    document.getElementById("albumAddBtn")?.addEventListener("click", () => openEditModal(null));
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
      fd.append("tracksJson", JSON.stringify(collectTracks()));
      const file = document.getElementById("albumEditImage").files?.[0];
      if (file) fd.append("image", file);
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

  async function init() {
    bindUi();
    try {
      const statusRes = await apiFetch(API + "/admin/status");
      if (statusRes.ok) {
        const status = await statusRes.json();
        adminConfigured = Boolean(status.configured);
        isAdmin = Boolean(status.admin);
        if (!isAdmin && getToken()) {
          // token may still be valid client-side after refresh
          isAdmin = true;
        }
      }
    } catch (_) { /* ignore */ }
    setAdminUi();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
