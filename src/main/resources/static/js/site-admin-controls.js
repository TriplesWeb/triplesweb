(() => {
  const auth = window.AboutAdminAuth;
  if (!auth || document.body.classList.contains("artist-page")) return;

  const protectedSelector = "[data-admin-only], .admin-only";
  const style = document.createElement("style");
  style.textContent = `
    ${protectedSelector} { display: none !important; }
    body.site-admin-enabled [data-admin-only], body.site-admin-enabled .admin-only { display: revert !important; }
    #siteAdminControls { position: fixed; left: 1rem; bottom: 1rem; z-index: 10000; }
    #siteAdminButton { border: 1px solid rgba(0,245,212,.5); border-radius: 999px; padding: .7rem 1rem; color: #00f5d4; background: #16181f; box-shadow: 0 8px 30px #0008; font-weight: 700; }
    #siteAdminModal[hidden] { display:none !important; }
    #siteAdminModal { position:fixed; inset:0; z-index:10001; display:flex; align-items:center; justify-content:center; background:#000b; padding:1rem; }
    #siteAdminModal .panel { width:min(100%,420px); border:1px solid #3a4a46; border-radius:1rem; background:#16181f; color:#e3e2e8; padding:1.5rem; }
    #siteAdminModal input { width:100%; margin-top:.5rem; padding:.75rem; border:1px solid #3a4a46; border-radius:.6rem; background:#0d0e12; color:white; }
    #siteAdminModal button { border-radius:.6rem; padding:.65rem 1rem; }
  `;
  document.head.appendChild(style);

  const controls = document.createElement("div");
  controls.id = "siteAdminControls";
  controls.innerHTML = `
    <button id="siteAdminButton" type="button">관리자 로그인</button>
    <div id="siteAdminModal" hidden role="dialog" aria-modal="true" aria-labelledby="siteAdminTitle">
      <div class="panel">
        <h2 id="siteAdminTitle" style="font-size:1.2rem;font-weight:800;margin-bottom:1rem">테스트 관리자</h2>
        <form id="siteAdminLoginForm">
          <label for="siteAdminPassword">ABOUT 트리플에스와 동일한 관리자 비밀번호</label>
          <input id="siteAdminPassword" type="password" autocomplete="current-password" required>
          <p id="siteAdminError" role="alert" style="color:#fca5a5;margin-top:.75rem" hidden></p>
          <div style="display:flex;justify-content:flex-end;gap:.5rem;margin-top:1rem">
            <button id="siteAdminCancel" type="button" style="background:#292d35;color:#e3e2e8">취소</button>
            <button type="submit" style="background:#00f5d4;color:#0d0e12;font-weight:800">로그인</button>
          </div>
        </form>
      </div>
    </div>`;
  document.body.appendChild(controls);

  const button = controls.querySelector("#siteAdminButton");
  const modal = controls.querySelector("#siteAdminModal");
  const form = controls.querySelector("#siteAdminLoginForm");
  const password = controls.querySelector("#siteAdminPassword");
  const error = controls.querySelector("#siteAdminError");
  const headers = (extra) => auth.applyAuthHeaders(extra || {});

  async function refresh() {
    try {
      const res = await fetch("/api/artist/admin/status", { credentials: "same-origin", headers: headers() });
      const state = res.ok ? await res.json() : { configured: false, admin: false };
      const configured = Boolean(state.configured);
      const admin = configured && Boolean(state.admin);
      button.hidden = !configured;
      button.textContent = admin ? "관리자 로그아웃" : "테스트 관리자 로그인";
      document.body.classList.toggle("site-admin-enabled", admin);
      document.querySelectorAll(protectedSelector).forEach((el) => {
        if (admin) el.removeAttribute("aria-hidden");
        else el.setAttribute("aria-hidden", "true");
      });
      if (!configured) {
        const hint = document.getElementById("siteAdminNotConfigured");
        if (hint) hint.hidden = false;
      }
      return { configured, admin };
    } catch (_) {
      button.hidden = true;
      document.body.classList.remove("site-admin-enabled");
      return { configured: false, admin: false };
    }
  }

  button.addEventListener("click", async () => {
    const state = await refresh();
    if (state.admin) {
      try {
        await fetch("/api/artist/admin/logout", { method: "POST", credentials: "same-origin", headers: headers() });
        auth.clearToken();
      } finally {
        window.location.reload();
      }
    } else {
      error.hidden = true;
      error.textContent = "";
      password.value = "";
      modal.hidden = false;
      password.focus();
    }
  });
  controls.querySelector("#siteAdminCancel").addEventListener("click", () => { modal.hidden = true; });
  modal.addEventListener("click", (event) => { if (event.target === modal) modal.hidden = true; });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    error.hidden = true;
    try {
      const res = await fetch("/api/artist/admin/login", {
        method: "POST",
        credentials: "same-origin",
        headers: headers({ "Content-Type": "application/json" }),
        body: JSON.stringify({ password: password.value })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        error.textContent = data.detail || data.message || "로그인할 수 없습니다. 관리자 비밀번호 설정을 확인하세요.";
        error.hidden = false;
        return;
      }
      auth.setToken(data.token || "");
      modal.hidden = true;
      window.location.reload();
    } catch (_) {
      error.textContent = "로그인 요청에 실패했습니다.";
      error.hidden = false;
    }
  });

  document.addEventListener("DOMContentLoaded", refresh);
})();