(() => {
  const auth = window.AboutAdminAuth;
  if (!auth) return;

  const CACHE_KEY = "site-admin-status-v1";
  const CACHE_TTL_MS = 60_000;
  const STATUS_URL = "/api/artist/admin/status";
  const LOGIN_URL = "/api/artist/admin/login";
  const LOGOUT_URL = "/api/artist/admin/logout";
  const protectedSelector = "[data-admin-only], .admin-only";
  const listeners = new Set();
  let currentState = { configured: false, admin: false };
  let returnFocus = null;

  const style = document.createElement("style");
  style.textContent = `
    ${protectedSelector} { display: none !important; }
    body.site-admin-enabled [data-admin-only], body.site-admin-enabled .admin-only { display: revert !important; }
    body.site-admin-enabled .ar-actions[data-admin-only] { display: flex !important; }
    body.site-admin-enabled a[data-admin-only], body.site-admin-enabled button[data-admin-only] { display: inline-flex !important; }
    body.site-admin-enabled form[data-admin-only] { display: block !important; }
    #siteAdminBtn {
      appearance: none;
      display: none;
      align-items: center;
      justify-content: center;
      box-sizing: border-box;
      width: 2.5rem;
      height: 2.5rem;
      min-width: 2.5rem;
      min-height: 2.5rem;
      flex: 0 0 auto;
      padding: 0;
      border: 1px solid #3a4a4680;
      border-radius: .75rem;
      background: #191b22;
      color: #e3e2e8;
      cursor: pointer;
      transition: color .15s ease;
    }
    #siteAdminBtn.is-ready,
    #siteAdminBtn.is-ready[hidden] { display: inline-flex !important; }
    #siteAdminBtn svg { width: 1.25rem; height: 1.25rem; flex: none; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
    #siteAdminBtn [data-site-admin-icon="logout"],
    #siteAdminBtn.is-active [data-site-admin-icon="admin"] { display: none; }
    #siteAdminBtn.is-active [data-site-admin-icon="logout"] { display: block; }
    #siteAdminBtn:hover,
    #siteAdminBtn.is-active { color: #8decd9; border-color: #8decd9; }
    #siteAdminLoginModal {
      position: fixed;
      inset: 0;
      z-index: 90;
      display: none;
      align-items: center;
      justify-content: center;
      padding: 1rem;
      background: rgba(0, 0, 0, 0.62);
    }
    #siteAdminLoginModal.is-open { display: flex; }
    #siteAdminLoginModal .site-admin-login-panel {
      position: relative;
      width: min(92vw, 420px);
      border-radius: 1rem;
      border: 1px solid rgba(58, 74, 70, 0.55);
      background: #16181f;
      padding: 1.25rem;
      box-shadow: 0 18px 48px rgba(0, 0, 0, 0.45);
      color: #e3e2e8;
    }
    #siteAdminLoginModal .site-admin-login-close {
      position: absolute;
      top: 0.75rem;
      right: 0.75rem;
      appearance: none;
      border: 0;
      background: transparent;
      color: #b9cac4;
      cursor: pointer;
      padding: 0.25rem;
      line-height: 0;
    }
    #siteAdminLoginModal .site-admin-login-title {
      margin: 0 2rem 1rem 0;
      font-size: 1.1rem;
      font-weight: 700;
    }
    #siteAdminLoginModal .site-admin-login-label {
      display: flex;
      flex-direction: column;
      gap: 0.45rem;
      font-size: 0.85rem;
      color: #b9cac4;
    }
    #siteAdminLoginModal .site-admin-password-field {
      display: flex;
      align-items: center;
      gap: 0.35rem;
    }
    #siteAdminLoginModal input[type="password"],
    #siteAdminLoginModal input[type="text"] {
      flex: 1;
      min-width: 0;
      border-radius: 0.6rem;
      border: 1px solid rgba(58, 74, 70, 0.55);
      background: #12141a;
      color: #e3e2e8;
      padding: 0.65rem 0.75rem;
      font-size: 0.95rem;
    }
    #siteAdminLoginModal .site-admin-password-toggle {
      appearance: none;
      border: 1px solid rgba(58, 74, 70, 0.55);
      background: #12141a;
      color: #b9cac4;
      border-radius: 0.6rem;
      padding: 0.55rem 0.65rem;
      cursor: pointer;
      line-height: 0;
    }
    #siteAdminLoginModal .site-admin-login-error {
      margin: 0.75rem 0 0;
      color: #ff8a8a;
      font-size: 0.85rem;
    }
    #siteAdminLoginModal .site-admin-login-actions {
      margin-top: 1rem;
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
    }
    #siteAdminLoginModal .site-admin-login-submit {
      appearance: none;
      border: 1px solid rgba(0, 245, 212, 0.65);
      background: #00f5d4;
      color: #0d0e12;
      border-radius: 0.6rem;
      padding: 0.55rem 1rem;
      font-size: 0.85rem;
      font-weight: 700;
      cursor: pointer;
    }
  `;
  document.head.appendChild(style);

  const headers = (extra) => auth.applyAuthHeaders(extra || {});

  function byId(id) {
    return document.getElementById(id);
  }

  function applyProtected(state) {
    const configured = Boolean(state && state.configured);
    const admin = configured && Boolean(state.admin);
    document.body.classList.toggle("site-admin-enabled", admin);
    document.querySelectorAll(protectedSelector).forEach((el) => {
      if (admin) el.removeAttribute("aria-hidden");
      else el.setAttribute("aria-hidden", "true");
    });
    return { configured, admin };
  }

  function updateHeaderButton(state) {
    const configured = Boolean(state.configured);
    const admin = Boolean(state.admin);
    const label = admin ? "관리자 로그아웃" : "관리자 로그인";
    document.querySelectorAll("[data-site-admin-open]").forEach((btn) => {
      btn.classList.toggle("is-ready", configured);
      btn.hidden = !configured;
      btn.setAttribute("aria-label", label);
      btn.setAttribute("title", label);
      btn.setAttribute("aria-pressed", admin ? "true" : "false");
      btn.classList.toggle("is-active", admin);
      const text = btn.querySelector("[data-site-admin-mobile-label]");
      if (text) text.textContent = label;
    });
  }

  function notify(state) {
    currentState = applyProtected(state);
    updateHeaderButton(currentState);
    listeners.forEach((fn) => {
      try { fn({ ...currentState }); } catch (_) {}
    });
    document.dispatchEvent(new CustomEvent("site-admin:change", { detail: { ...currentState } }));
    return currentState;
  }

  function readCache() {
    try {
      const raw = sessionStorage.getItem(CACHE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed.expiresAt !== "number" || parsed.expiresAt < Date.now()) return null;
      return parsed.state;
    } catch (_) {
      return null;
    }
  }

  function writeCache(state) {
    try {
      sessionStorage.setItem(CACHE_KEY, JSON.stringify({
        expiresAt: Date.now() + CACHE_TTL_MS,
        state
      }));
    } catch (_) {}
  }

  function clearCache() {
    try { sessionStorage.removeItem(CACHE_KEY); } catch (_) {}
  }

  async function refresh() {
    try {
      const res = await fetch(STATUS_URL, { credentials: "same-origin", headers: headers(), cache: "no-store" });
      const state = res.ok ? await res.json() : { configured: false, admin: false };
      writeCache(state);
      return notify(state);
    } catch (_) {
      const fallback = { configured: false, admin: false };
      return notify(fallback);
    }
  }

  function setPasswordVisible(visible) {
    const input = byId("siteAdminPassword");
    const toggle = byId("siteAdminPasswordToggle");
    if (!input || !toggle) return;
    input.type = visible ? "text" : "password";
    toggle.setAttribute("aria-pressed", visible ? "true" : "false");
    toggle.setAttribute("aria-label", visible ? "비밀번호 숨기기" : "비밀번호 보기");
    const icon = toggle.querySelector(".material-symbols-outlined");
    if (icon) icon.textContent = visible ? "visibility_off" : "visibility";
  }

  function openLogin() {
    const modal = byId("siteAdminLoginModal");
    const err = byId("siteAdminLoginError");
    const input = byId("siteAdminPassword");
    if (!modal) return;
    returnFocus = document.activeElement;
    if (err) {
      err.hidden = true;
      err.textContent = "";
    }
    if (input) input.value = "";
    setPasswordVisible(false);
    modal.classList.add("is-open");
    modal.removeAttribute("hidden");
    document.body.style.overflow = "hidden";
    if (input) input.focus();
  }

  function closeLogin() {
    const modal = byId("siteAdminLoginModal");
    const input = byId("siteAdminPassword");
    if (!modal) return;
    modal.classList.remove("is-open");
    modal.setAttribute("hidden", "");
    if (input) input.value = "";
    document.body.style.overflow = "";
    if (returnFocus && typeof returnFocus.focus === "function") returnFocus.focus();
    returnFocus = null;
  }

  async function logout() {
    const res = await fetch(LOGOUT_URL, {
      method: "POST",
      credentials: "same-origin",
      headers: headers()
    });
    if (!res.ok) throw new Error("로그아웃에 실패했습니다.");
    auth.clearToken();
    clearCache();
    return notify({ configured: currentState.configured, admin: false });
  }

  async function login(password) {
    const res = await fetch(LOGIN_URL, {
      method: "POST",
      credentials: "same-origin",
      headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify({ password })
    });
    if (!res.ok) {
      let message = "비밀번호가 올바르지 않습니다.";
      try {
        const data = await res.json();
        message = data.detail || data.message || data.error || message;
      } catch (_) {
        if (res.status === 429) message = "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.";
      }
      throw new Error(message);
    }
    const data = await res.json().catch(() => ({}));
    if (data && data.token) auth.setToken(data.token);
    clearCache();
    return notify({ configured: true, admin: true });
  }

  function bindUi() {
    const modal = byId("siteAdminLoginModal");
    const form = byId("siteAdminLoginForm");

    document.querySelectorAll("[data-site-admin-open]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const mobileMenu = document.getElementById("mobile-menu");
        const mobileMenuBtn = document.getElementById("mobile-menu-btn");
        if (mobileMenu && !mobileMenu.classList.contains("hidden")) {
          mobileMenu.classList.add("hidden");
          if (mobileMenuBtn) {
            mobileMenuBtn.setAttribute("aria-expanded", "false");
            mobileMenuBtn.setAttribute("aria-label", "메뉴 열기");
          }
        }
        if (currentState.admin) {
          try {
            await logout();
          } catch (_) {
            alert("로그아웃에 실패했습니다. 네트워크 연결을 확인해 주세요.");
          }
          return;
        }
        openLogin();
      });
    });

    form?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const err = byId("siteAdminLoginError");
      const input = byId("siteAdminPassword");
      const password = input ? input.value : "";
      const submit = form.querySelector('button[type="submit"]');
      if (submit) submit.disabled = true;
      try {
        await login(password);
        closeLogin();
      } catch (error) {
        if (err) {
          err.textContent = error.message || "로그인에 실패했습니다.";
          err.hidden = false;
        }
      } finally {
        if (submit) submit.disabled = false;
      }
    });

    byId("siteAdminPasswordToggle")?.addEventListener("click", () => {
      const input = byId("siteAdminPassword");
      setPasswordVisible(Boolean(input && input.type === "password"));
    });

    document.querySelectorAll("[data-site-admin-close]").forEach((el) => {
      el.addEventListener("click", closeLogin);
    });

    modal?.addEventListener("click", (event) => {
      if (event.target === modal) closeLogin();
    });

    document.addEventListener("keydown", (event) => {
      if (!modal || !modal.classList.contains("is-open")) return;
      if (event.key === "Escape") closeLogin();
    });
  }

  document.addEventListener("submit", (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || form.hasAttribute("data-admin-fetch")) return;
    const message = form.getAttribute("data-confirm");
    if (message && !window.confirm(message)) event.preventDefault();
  }, true);

  document.addEventListener("submit", async (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || !form.hasAttribute("data-admin-fetch")) return;
    const confirmMessage = form.getAttribute("data-confirm");
    if (confirmMessage && !window.confirm(confirmMessage)) {
      event.preventDefault();
      return;
    }
    event.preventDefault();
    const busy = event.submitter instanceof HTMLButtonElement ? event.submitter : form.querySelector("button[type='submit']");
    if (busy) busy.disabled = true;
    try {
      const response = await fetch(form.action, {
        method: (form.getAttribute("method") || "POST").toUpperCase(),
        body: new FormData(form),
        credentials: "same-origin",
        headers: auth.applyAuthHeaders()
      });
      if (response.ok) {
        window.location.href = response.redirected ? response.url : window.location.href;
        return;
      }
      let detail = "";
      try { detail = (await response.text()).replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim(); } catch (_) {}
      if (detail.length > 180) detail = detail.slice(0, 180);
      alert(response.status === 403
        ? "관리자 로그인 후 다시 시도해 주세요."
        : (detail || "저장하지 못했습니다. 입력값을 확인하고 다시 시도해 주세요."));
    } catch (_) {
      alert("요청에 실패했습니다. 네트워크 연결을 확인해 주세요.");
    } finally {
      if (busy) busy.disabled = false;
    }
  });

  window.SiteAdmin = {
    getState: () => ({ ...currentState }),
    subscribe(fn) {
      if (typeof fn !== "function") return () => {};
      listeners.add(fn);
      fn({ ...currentState });
      return () => listeners.delete(fn);
    },
    refresh,
    openLogin,
    closeLogin,
    logout,
    login
  };

  function boot() {
    bindUi();
    const cached = readCache();
    if (cached) notify(cached);
    const run = () => { refresh(); };
    if (typeof requestIdleCallback === "function") {
      requestIdleCallback(run, { timeout: 2500 });
    } else {
      setTimeout(run, 200);
    }
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "visible") refresh();
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
