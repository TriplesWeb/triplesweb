(() => {
  const auth = window.AboutAdminAuth;
  const byId = id => document.getElementById(id);
  const adminButton = byId("gravityAdminBtn");
  const modal = byId("gravityLoginModal");
  const password = byId("gravityAdminPassword");
  const loginError = byId("gravityLoginError");
  const panel = byId("cosmoLogin");
  const codeForm = byId("codeForm");
  const notice = byId("connectStatus") || byId("gravityAdminMessage");
  let isAdmin = false, configured = false, returnFocus;
  const sendButton = byId("sendEmailButton");
  let resendUntil = 0, resendTimer;
  function updateResend() {
    if (!sendButton) return;
    const seconds = Math.max(0, Math.ceil((resendUntil - Date.now()) / 1000));
    sendButton.disabled = seconds > 0;
    sendButton.textContent = seconds ? `재전송 (${seconds}초)` : "인증번호 받기";
    if (!seconds && resendTimer) { clearInterval(resendTimer); resendTimer = undefined; }
  }
  function startResend(seconds) {
    if (!sendButton) return;
    resendUntil = Date.now() + Math.min(600, Math.max(1, Number(seconds) || 60)) * 1000;
    if (resendTimer) clearInterval(resendTimer);
    resendTimer = setInterval(updateResend, 1000);
    updateResend();
  }

  function message(value) { if (notice) { notice.textContent = value; notice.hidden = false; } }
  function render() {
    if (adminButton) {
      adminButton.hidden = !configured;
      adminButton.textContent = isAdmin ? "관리자 로그아웃" : "관리자";
      adminButton.classList.toggle("is-active", isAdmin);
      adminButton.setAttribute("aria-pressed", String(isAdmin));
    }
    if (byId("gravityConnectLink")) byId("gravityConnectLink").hidden = !isAdmin || Boolean(panel);
    if (byId("gravityAdminHint")) byId("gravityAdminHint").hidden = !isAdmin;
    if (byId("gravityLoginRequired")) byId("gravityLoginRequired").hidden = isAdmin || !configured;
    if (panel) panel.hidden = !isAdmin;
    if (!isAdmin && codeForm) codeForm.hidden = true;
  }
  function openLogin() {
    if (!configured || !modal) return;
    returnFocus = document.activeElement;
    password.value = "";
    password.type = "password";
    byId("gravityPasswordToggle").textContent = "보기";
    byId("gravityPasswordToggle").setAttribute("aria-label", "비밀번호 보기");
    byId("gravityPasswordToggle").setAttribute("aria-pressed", "false");
    loginError.hidden = true;
    modal.hidden = false;
    modal.classList.add("flex");
    document.body.style.overflow = "hidden";
    password.focus();
  }
  function closeLogin() {
    if (!modal) return;
    modal.hidden = true;
    modal.classList.remove("flex");
    password.value = "";
    document.body.style.overflow = "";
    returnFocus?.focus();
  }
  async function api(path, body) {
    const headers = auth.applyAuthHeaders({ "X-Gravity-Connect": "1" });
    const options = { headers, credentials: "same-origin", cache: "no-store" };
    if (body) { options.method = "POST"; headers.set("Content-Type", "application/json"); options.body = JSON.stringify(body); }
    let response;
    try {
      response = await fetch("/api/gravity/connect/" + path, options);
    } catch (_) {
      throw new Error("서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
      if (path === "email" && response.status === 429 && result.retryAfterSeconds) startResend(result.retryAfterSeconds);
      if (response.status === 401) throw new Error("비밀번호가 올바르지 않습니다.");
      if (response.status === 403) { auth.clearToken(); isAdmin = false; render(); openLogin(); }
      const fallback = response.status === 401 ? "비밀번호가 올바르지 않습니다."
        : response.status === 403 ? "관리자 로그인이 만료되었습니다. 다시 로그인해 주세요."
        : response.status === 429 ? "잠시 후 다시 시도해 주세요." : "요청에 실패했습니다. 입력 내용과 연결 상태를 확인해 주세요.";
      throw new Error(result.message || result.detail || fallback);
    }
    return result;
  }
  async function refresh() {
    try {
      const result = await api("status");
      configured = Boolean(result.configured);
      isAdmin = Boolean(result.admin);
      if (!isAdmin) auth.clearToken();
      render();
      if (panel) message(!configured ? "사이트 관리자 설정이 필요합니다." : !isAdmin
        ? "케미명 페이지와 같은 관리자 계정으로 로그인하세요." : result.connected
        ? "COSMO 계정이 연결되어 있습니다. 필요하면 이메일로 다시 연결할 수 있습니다."
        : "본인 COSMO 이메일로 최초 인증을 진행하세요.");
    } catch (error) {
      isAdmin = false;
      render();
      message(error.message);
    }
  }
  async function submit(event, operation, errorTarget) {
    event.preventDefault();
    const buttons = [...event.currentTarget.querySelectorAll("button")];
    buttons.forEach(button => { button.disabled = true; });
    try { await operation(); } catch (error) {
      if (errorTarget) { errorTarget.textContent = error.message; errorTarget.hidden = false; }
      else message(error.message);
    } finally { buttons.forEach(button => { button.disabled = false; }); updateResend(); }
  }
  adminButton?.addEventListener("click", async () => {
    if (!isAdmin) { openLogin(); return; }
    try {
      await api("admin-logout", {});
      auth.clearToken();
      isAdmin = false;
      render();
      message("관리자 로그아웃되었습니다. 케미명 페이지와 로그인 상태가 함께 변경됩니다.");
    } catch (error) { message(error.message); }
  });
  byId("gravityLoginOpen")?.addEventListener("click", openLogin);
  document.querySelectorAll("[data-gravity-login-close]").forEach(button => button.addEventListener("click", closeLogin));
  modal?.addEventListener("click", event => { if (event.target === modal) closeLogin(); });
  document.addEventListener("keydown", event => {
    if (!modal || modal.hidden) return;
    if (event.key === "Escape") closeLogin();
    if (event.key === "Tab") {
      const fields = [...modal.querySelectorAll("button:not([disabled]),input:not([disabled])")];
      const first = fields[0], last = fields[fields.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
  });
  byId("gravityPasswordToggle")?.addEventListener("click", event => {
    const showing = password.type === "password";
    password.type = showing ? "text" : "password";
    event.currentTarget.textContent = showing ? "숨기기" : "보기";
    event.currentTarget.setAttribute("aria-label", showing ? "비밀번호 숨기기" : "비밀번호 보기");
    event.currentTarget.setAttribute("aria-pressed", String(showing));
  });
  byId("gravityAdminLoginForm")?.addEventListener("submit", event => submit(event, async () => {
    const value = password.value;
    password.value = "";
    await api("admin-login", { password: value });
    closeLogin();
    await refresh();
  }, loginError));
  byId("emailForm")?.addEventListener("submit", event => submit(event, async () => {
    codeForm.hidden = true;
    await api("email", { email: byId("cosmoEmail").value.trim() });
    startResend(60);
    codeForm.hidden = false;
    byId("cosmoCode").focus();
    message("이메일로 받은 6자리 인증번호를 입력하세요. 재요청은 60초 후 가능합니다.");
  }));
  codeForm?.addEventListener("submit", event => submit(event, async () => {
    const code = byId("cosmoCode").value;
    byId("cosmoCode").value = "";
    await api("verify", { code });
    codeForm.hidden = true;
    message("계정 연결이 완료됐습니다. 그래비티 목록으로 이동합니다.");
    window.location.replace("/cosmo/gravity");
  }));
  window.addEventListener("storage", event => { if (event.key === auth.TOKEN_KEY) refresh(); });
  window.addEventListener("pageshow", event => { if (event.persisted) refresh(); });
  refresh();
})();
