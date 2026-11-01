(() => {
  const auth = window.AboutAdminAuth;
  if (!auth || document.body.classList.contains("artist-page")) return;

  const CACHE_KEY = "site-admin-status-v1";
  const CACHE_TTL_MS = 60_000;
  const protectedSelector = "[data-admin-only], .admin-only";
  const style = document.createElement("style");
  style.textContent = `
    ${protectedSelector} { display: none !important; }
    body.site-admin-enabled [data-admin-only], body.site-admin-enabled .admin-only { display: revert !important; }
    body.site-admin-enabled .ar-actions[data-admin-only] { display: flex !important; }
    body.site-admin-enabled a[data-admin-only], body.site-admin-enabled button[data-admin-only] { display: inline-flex !important; }
    body.site-admin-enabled form[data-admin-only] { display: block !important; }
  `;
  document.head.appendChild(style);

  const headers = (extra) => auth.applyAuthHeaders(extra || {});

  function applyState(state) {
    const configured = Boolean(state && state.configured);
    const admin = configured && Boolean(state.admin);
    document.body.classList.toggle("site-admin-enabled", admin);
    document.querySelectorAll(protectedSelector).forEach((el) => {
      if (admin) el.removeAttribute("aria-hidden");
      else el.setAttribute("aria-hidden", "true");
    });
    return { configured, admin };
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

  async function refresh() {
    try {
      const res = await fetch("/api/artist/admin/status", { credentials: "same-origin", headers: headers() });
      const state = res.ok ? await res.json() : { configured: false, admin: false };
      writeCache(state);
      return applyState(state);
    } catch (_) {
      document.body.classList.remove("site-admin-enabled");
      return { configured: false, admin: false };
    }
  }

  function scheduleRefresh() {
    const cached = readCache();
    if (cached) applyState(cached);
    const run = () => { refresh(); };
    if (typeof requestIdleCallback === "function") {
      requestIdleCallback(run, { timeout: 2500 });
    } else {
      setTimeout(run, 200);
    }
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

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", scheduleRefresh);
  } else {
    scheduleRefresh();
  }
})();
