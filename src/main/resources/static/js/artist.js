(() => {
  const auth = window.AboutAdminAuth;
  let adminConfigured = false;
  let isAdmin = false;

  function getAdminToken() {
    return auth ? auth.getToken() : "";
  }

  function setAdminToken(token) {
    if (auth) auth.setToken(token);
  }

  async function artistFetch(url, options) {
    const opts = options ? { ...options } : {};
    opts.headers = auth ? auth.applyAuthHeaders(opts.headers || {}) : new Headers(opts.headers || {});
    opts.credentials = "same-origin";
    return fetch(url, opts);
  }

  async function readError(res, fallback) {
    try {
      const data = await res.json();
      if (data && (data.detail || data.message || data.error)) {
        return data.detail || data.message || data.error;
      }
      if (typeof data === "string") return data;
    } catch (_) {
      /* ignore */
    }
    try {
      const text = await res.text();
      if (text) return text;
    } catch (_) {
      /* ignore */
    }
    if (res.status === 403) return "관리자 로그인이 필요합니다. 다시 로그인해 주세요.";
    return fallback || "요청에 실패했습니다.";
  }

  function setAdminUi() {
    const page = document.querySelector(".artist-page");
    const btn = document.getElementById("artistAdminBtn");
    const hint = document.getElementById("artistAdminHint");
    // 관리자 인증 전에는 소개/멤버 편집 버튼을 표시하지 않는다.
    document.querySelectorAll(".artist-edit-inline").forEach((control) => {
      control.hidden = !isAdmin;
    });
    if (page) page.classList.toggle("is-admin", Boolean(isAdmin));
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
  }

  function setLoginPasswordVisible(visible) {
    const input = document.getElementById("artistLoginPassword");
    const toggle = document.getElementById("artistLoginPasswordToggle");
    if (!input || !toggle) return;
    input.type = visible ? "text" : "password";
    toggle.setAttribute("aria-pressed", visible ? "true" : "false");
    toggle.setAttribute("aria-label", visible ? "비밀번호 숨기기" : "비밀번호 보기");
    const icon = toggle.querySelector(".material-symbols-outlined");
    if (icon) icon.textContent = visible ? "visibility_off" : "visibility";
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
    const anyOpen = document.querySelector(".artist-modal.flex");
    if (!anyOpen) document.body.style.overflow = "";
  }

  function openLoginModal() {
    const err = document.getElementById("artistLoginError");
    const input = document.getElementById("artistLoginPassword");
    if (err) {
      err.hidden = true;
      err.textContent = "";
    }
    if (input) input.value = "";
    setLoginPasswordVisible(false);
    openModal("artistLoginModal");
    if (input) input.focus();
  }

  function bindMemberTabFrames() {
    document.querySelectorAll(".nav-tab-btn[data-member-tab]").forEach((btn, index) => {
      const frame = btn.querySelector(".artist-tab-frame");
      if (!frame) return;
      const n = index + 1;
      if (n > 24) {
        frame.hidden = true;
        return;
      }
      frame.src = "/uploads/images/artist-tab-frames/" + n + ".png";
      frame.hidden = false;
    });
  }

  function bindTabs() {
    const tabButtons = document.querySelectorAll(".nav-tab-btn");
    const tabPanes = document.querySelectorAll(".tab-pane");
    bindMemberTabFrames();

    function activateTab(targetId) {
      if (!targetId) return;
      const btn = document.querySelector('.nav-tab-btn[data-target="' + targetId + '"]');
      const targetPane = document.querySelector(targetId);
      if (!targetPane) return;

      tabButtons.forEach((b) => b.classList.remove("is-active"));
      if (btn) btn.classList.add("is-active");

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

    const params = new URLSearchParams(window.location.search);
    const tab = params.get("tab");
    const hash = window.location.hash;
    if (tab && document.getElementById(tab)) {
      activateTab("#" + tab);
    } else if (hash && document.querySelector(hash)) {
      activateTab(hash);
    } else {
      activateTab("#triples");
    }

    window.__artistActivateTab = activateTab;
  }

  function setUnitChecks(selectedIds) {
    const selected = new Set((selectedIds || []).map(String));
    document.querySelectorAll('#artistEditUnits input[type="checkbox"]').forEach((cb) => {
      cb.checked = selected.has(String(cb.value));
    });
  }

  function createLinkRow(title, url) {
    const row = document.createElement("div");
    row.className = "artist-link-row";
    row.innerHTML =
      '<input type="text" class="artist-link-title" placeholder="표시 제목" autocomplete="off">' +
      '<input type="url" class="artist-link-url" placeholder="https://" autocomplete="off">' +
      '<button type="button" class="artist-link-remove" aria-label="링크 삭제">' +
      '<span class="material-symbols-outlined" style="font-size:18px">close</span></button>';
    row.querySelector(".artist-link-title").value = title || "";
    row.querySelector(".artist-link-url").value = url || "";
    row.querySelector(".artist-link-remove").addEventListener("click", () => {
      const box = row.parentElement;
      row.remove();
      if (box && !box.querySelector(".artist-link-row")) {
        box.appendChild(createLinkRow("", ""));
      }
    });
    return row;
  }

  function renderLinkRows(containerId, links) {
    const box = document.getElementById(containerId);
    if (!box) return;
    box.innerHTML = "";
    const list = Array.isArray(links) ? links.filter((l) => l && (l.title || l.url)) : [];
    if (!list.length) {
      box.appendChild(createLinkRow("", ""));
      return;
    }
    list.forEach((link) => box.appendChild(createLinkRow(link.title || "", link.url || "")));
  }

  function collectLinksJson(containerId) {
    const box = document.getElementById(containerId);
    const out = [];
    if (!box) return "[]";
    box.querySelectorAll(".artist-link-row").forEach((row) => {
      const title = (row.querySelector(".artist-link-title")?.value || "").trim();
      const url = (row.querySelector(".artist-link-url")?.value || "").trim();
      if (!title && !url) return;
      out.push({ title: title || url, url });
    });
    return JSON.stringify(out);
  }

  async function refreshAdminStatus() {
    try {
      const res = await artistFetch("/api/artist/admin/status");
      if (!res.ok) {
        adminConfigured = false;
        isAdmin = false;
        setAdminUi();
        return false;
      }
      const status = await res.json();
      adminConfigured = Boolean(status.configured);
      isAdmin = Boolean(status.admin);
      if (!isAdmin) setAdminToken("");
      setAdminUi();
      return isAdmin;
    } catch (_) {
      adminConfigured = false;
      isAdmin = false;
      setAdminUi();
      return false;
    }
  }

  async function requireAdminOrLogin() {
    if (await refreshAdminStatus()) return true;
    openLoginModal();
    return false;
  }

  async function openMemberEdit(artistId) {
    if (!(await requireAdminOrLogin())) return;
    const err = document.getElementById("artistEditError");
    if (err) {
      err.hidden = true;
      err.textContent = "";
    }
    try {
      const res = await artistFetch("/api/artist/" + artistId);
      if (!res.ok) throw new Error(await readError(res, "멤버 정보를 불러오지 못했습니다."));
      const data = await res.json();
      document.getElementById("artistEditId").value = data.artistId;
      document.getElementById("artistEditName").value = data.name || "";
      document.getElementById("artistEditEngName").value = data.engName || "";
      document.getElementById("artistEditBirthDate").value = data.birthDate || "";
      document.getElementById("artistEditBornPlace").value = data.bornPlace || "";
      document.getElementById("artistEditBirthPlace").value = data.birthPlace || "";
      document.getElementById("artistEditRealName").value = data.realName || "";
      document.getElementById("artistEditMbti").value = data.mbti || "";
      document.getElementById("artistEditNationality").value = data.nationality || "";
      document.getElementById("artistEditDebutDate").value = data.debutDate || "";
      document.getElementById("artistEditColor").value = data.color || "#00f5d4";
      renderLinkRows("artistEditSoloLinks", data.soloLinks || []);
      renderLinkRows("artistEditBroadcastLinks", data.broadcastLinks || []);
      document.getElementById("artistEditClearImage").checked = false;
      document.getElementById("artistEditImage").value = "";
      const preview = document.getElementById("artistEditImagePreview");
      if (preview) {
        if (data.profileImage) {
          preview.src = data.profileImage;
          preview.classList.remove("hidden");
        } else {
          preview.removeAttribute("src");
          preview.classList.add("hidden");
        }
      }
      document.getElementById("artistEditClearSignatureImage").checked = false;
      document.getElementById("artistEditSignatureImage").value = "";
      const sigPreview = document.getElementById("artistEditSignaturePreview");
      if (sigPreview) {
        if (data.signatureImage) {
          sigPreview.src = data.signatureImage;
          sigPreview.classList.remove("hidden");
        } else {
          sigPreview.removeAttribute("src");
          sigPreview.classList.add("hidden");
        }
      }
      setUnitChecks(data.unitIds || []);
      document.getElementById("artistEditTitle").textContent = "멤버 수정 — " + (data.name || "");
      openModal("artistEditModal");
    } catch (e) {
      alert(e.message || "멤버 정보를 불러오지 못했습니다.");
    }
  }

  function openGroupEdit() {
    const err = document.getElementById("artistGroupError");
    if (err) {
      err.hidden = true;
      err.textContent = "";
    }
    const titleEl = document.getElementById("groupTitleDisplay");
    const bodyEl = document.getElementById("groupBodyDisplay");
    const imgEl = document.getElementById("groupImageDisplay");
    const tabImgEl = document.getElementById("groupTabImageDisplay");
    document.getElementById("artistGroupTitle").value = (titleEl && titleEl.textContent) || "";
    document.getElementById("artistGroupBody").value = (bodyEl && bodyEl.textContent) || "";
    document.getElementById("artistGroupClearImage").checked = false;
    document.getElementById("artistGroupClearTabImage").checked = false;
    document.getElementById("artistGroupImage").value = "";
    document.getElementById("artistGroupTabImage").value = "";
    const preview = document.getElementById("artistGroupImagePreview");
    const tabPreview = document.getElementById("artistGroupTabImagePreview");
    if (preview && imgEl) {
      preview.src = imgEl.getAttribute("src") || "";
      preview.classList.toggle("hidden", !preview.src);
    }
    if (tabPreview && tabImgEl) {
      tabPreview.src = tabImgEl.getAttribute("src") || "";
      tabPreview.classList.toggle("hidden", !tabPreview.src);
    }
    openModal("artistGroupModal");
  }

  function bindAdminUi() {
    const adminBtn = document.getElementById("artistAdminBtn");
    if (adminBtn) {
      adminBtn.addEventListener("click", async () => {
        if (isAdmin) {
          await artistFetch("/api/artist/admin/logout", { method: "POST" });
          isAdmin = false;
          setAdminToken("");
          setAdminUi();
          return;
        }
        openLoginModal();
      });
    }

    const loginForm = document.getElementById("artistLoginForm");
    if (loginForm) {
      loginForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const err = document.getElementById("artistLoginError");
        const password = document.getElementById("artistLoginPassword").value;
        try {
          const res = await artistFetch("/api/artist/admin/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ password })
          });
          if (!res.ok) {
            if (err) {
              err.textContent = await readError(res, "비밀번호가 올바르지 않습니다.");
              err.hidden = false;
            }
            return;
          }
          const data = await res.json();
          setAdminToken(data && data.token ? data.token : "");
          isAdmin = true;
          closeModal("artistLoginModal");
          setAdminUi();
        } catch (_) {
          if (err) {
            err.textContent = "로그인에 실패했습니다.";
            err.hidden = false;
          }
        }
      });
    }

    const passwordToggle = document.getElementById("artistLoginPasswordToggle");
    if (passwordToggle) {
      passwordToggle.addEventListener("click", () => {
        const input = document.getElementById("artistLoginPassword");
        setLoginPasswordVisible(Boolean(input && input.type === "password"));
      });
    }

    document.querySelectorAll(".artist-login-close").forEach((btn) => {
      btn.addEventListener("click", () => closeModal("artistLoginModal"));
    });
    document.querySelectorAll(".artist-edit-close").forEach((btn) => {
      btn.addEventListener("click", () => closeModal("artistEditModal"));
    });
    document.querySelectorAll(".artist-group-close").forEach((btn) => {
      btn.addEventListener("click", () => closeModal("artistGroupModal"));
    });

    document.querySelectorAll("[data-artist-edit]").forEach((btn) => {
      btn.addEventListener("click", () => {
        openMemberEdit(btn.getAttribute("data-artist-edit"));
      });
    });

    const addSolo = document.getElementById("artistAddSoloLink");
    if (addSolo) {
      addSolo.addEventListener("click", () => {
        const box = document.getElementById("artistEditSoloLinks");
        if (box) box.appendChild(createLinkRow("", ""));
      });
    }
    const addBroadcast = document.getElementById("artistAddBroadcastLink");
    if (addBroadcast) {
      addBroadcast.addEventListener("click", () => {
        const box = document.getElementById("artistEditBroadcastLinks");
        if (box) box.appendChild(createLinkRow("", ""));
      });
    }

    const groupEditBtn = document.getElementById("artistGroupEditBtn");
    if (groupEditBtn) {
      groupEditBtn.addEventListener("click", async () => {
        if (!(await requireAdminOrLogin())) return;
        openGroupEdit();
      });
    }

    const editForm = document.getElementById("artistEditForm");
    if (editForm) {
      editForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        if (!(await requireAdminOrLogin())) return;
        const err = document.getElementById("artistEditError");
        const id = document.getElementById("artistEditId").value;
        const fd = new FormData();
        fd.append("name", document.getElementById("artistEditName").value);
        fd.append("engName", document.getElementById("artistEditEngName").value);
        fd.append("birthDate", document.getElementById("artistEditBirthDate").value);
        fd.append("bornPlace", document.getElementById("artistEditBornPlace").value);
        fd.append("birthPlace", document.getElementById("artistEditBirthPlace").value);
        fd.append("realName", document.getElementById("artistEditRealName").value);
        fd.append("mbti", document.getElementById("artistEditMbti").value);
        fd.append("nationality", document.getElementById("artistEditNationality").value);
        fd.append("debutDate", document.getElementById("artistEditDebutDate").value);
        fd.append("color", document.getElementById("artistEditColor").value);
        fd.append("soloContent", collectLinksJson("artistEditSoloLinks"));
        fd.append("broadcastContent", collectLinksJson("artistEditBroadcastLinks"));
        document.querySelectorAll('#artistEditUnits input[type="checkbox"]:checked').forEach((cb) => {
          fd.append("unitIds", cb.value);
        });
        const clearChecked = Boolean(document.getElementById("artistEditClearImage")?.checked);
        fd.append("clearImage", clearChecked ? "true" : "false");
        const file = document.getElementById("artistEditImage").files[0];
        if (file && !clearChecked) fd.append("image", file);
        const clearSig = Boolean(document.getElementById("artistEditClearSignatureImage")?.checked);
        fd.append("clearSignatureImage", clearSig ? "true" : "false");
        const sigFile = document.getElementById("artistEditSignatureImage").files[0];
        if (sigFile && !clearSig) fd.append("signatureImage", sigFile);
        try {
          const res = await artistFetch("/api/artist/" + id, { method: "POST", body: fd });
          if (!res.ok) {
            if (err) {
              err.textContent = await readError(res, "저장에 실패했습니다.");
              err.hidden = false;
            }
            if (res.status === 403) {
              isAdmin = false;
              setAdminToken("");
              setAdminUi();
            }
            return;
          }
          const tab = "member-" + id;
          window.location.href = "/triples/artist?tab=" + encodeURIComponent(tab);
        } catch (_) {
          if (err) {
            err.textContent = "저장에 실패했습니다.";
            err.hidden = false;
          }
        }
      });
    }

    const groupForm = document.getElementById("artistGroupForm");
    if (groupForm) {
      groupForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        if (!(await requireAdminOrLogin())) return;
        const err = document.getElementById("artistGroupError");
        const fd = new FormData();
        fd.append("title", document.getElementById("artistGroupTitle").value);
        fd.append("body", document.getElementById("artistGroupBody").value);
        fd.append("clearImage", document.getElementById("artistGroupClearImage").checked ? "true" : "false");
        fd.append("clearTabImage", document.getElementById("artistGroupClearTabImage").checked ? "true" : "false");
        const image = document.getElementById("artistGroupImage").files[0];
        const tabImage = document.getElementById("artistGroupTabImage").files[0];
        if (image && !document.getElementById("artistGroupClearImage").checked) fd.append("image", image);
        if (tabImage && !document.getElementById("artistGroupClearTabImage").checked) fd.append("tabImage", tabImage);
        try {
          const res = await artistFetch("/api/artist/group", { method: "POST", body: fd });
          if (!res.ok) {
            if (err) {
              err.textContent = await readError(res, "저장에 실패했습니다.");
              err.hidden = false;
            }
            if (res.status === 403) {
              isAdmin = false;
              setAdminToken("");
              setAdminUi();
            }
            return;
          }
          window.location.href = "/triples/artist?tab=triples";
        } catch (_) {
          if (err) {
            err.textContent = "저장에 실패했습니다.";
            err.hidden = false;
          }
        }
      });
    }
  }

  async function initAdmin() {
    await refreshAdminStatus();
    bindAdminUi();
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "visible") refreshAdminStatus();
    });
  }

  document.addEventListener("DOMContentLoaded", () => {
    bindTabs();
    initAdmin();
  });
})();
