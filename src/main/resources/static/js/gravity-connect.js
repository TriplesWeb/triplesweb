(() => {
  // Resolve at call time: deferred header auth must not be captured as undefined at parse.
  const auth = () => {
    const api = window.AboutAdminAuth;
    if (!api) throw new Error("관리자 인증 스크립트를 불러오지 못했습니다.");
    return api;
  };
  const byId = id => document.getElementById(id);
  const panel = byId("cosmoLogin");
  const codeForm = byId("codeForm");
  const notice = byId("connectStatus") || byId("gravityAdminMessage");
  let isAdmin = false, configured = false;
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
    if (byId("gravityConnectLink")) byId("gravityConnectLink").hidden = !isAdmin || Boolean(panel);
    if (byId("gravityAdminHint")) byId("gravityAdminHint").hidden = !isAdmin;
    if (byId("gravityLoginRequired")) byId("gravityLoginRequired").hidden = isAdmin || !configured;
    if (panel) panel.hidden = !isAdmin;
    if (!isAdmin && codeForm) codeForm.hidden = true;
  }

  function openLogin() {
    if (window.SiteAdmin && typeof window.SiteAdmin.openLogin === "function") {
      window.SiteAdmin.openLogin();
    }
  }

  function applyAdminState(state) {
    if (!state) return;
    configured = Boolean(state.configured);
    isAdmin = Boolean(state.admin);
    if (!isAdmin) auth().clearToken();
    render();
    if (panel) {
      message(!configured ? "사이트 관리자 설정이 필요합니다." : !isAdmin
        ? "헤더의 관리자 버튼으로 로그인해 주세요." : "본인 COSMO 이메일로 최초 인증을 진행하세요.");
    }
  }

  async function api(path, body) {
    const headers = auth().applyAuthHeaders({ "X-Gravity-Connect": "1" });
    const options = { headers, credentials: "same-origin", cache: "no-store" };
    if (body) { options.method = "POST"; headers.set("Content-Type": "application/json"); options.body = JSON.stringify(body); }
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
      if (response.status === 403) {
        auth().clearToken();
        isAdmin = false;
        render();
        openLogin();
      }
      const fallback = response.status === 401 ? "비밀번호가 올바르지 않습니다."
        : response.status === 403 ? "관리자 로그인이 만료되었습니다. 다시 로그인해 주세요."
        : response.status === 429 ? "잠시 후 다시 시도해 주세요." : "요청에 실패했습니다. 입력 내용과 연결 상태를 확인해 주세요.";
      throw new Error(result.message || result.detail || fallback);
    }
    return result;
  }

  async function refreshConnectStatus() {
    if (!panel) return;
    try {
      const result = await api("status");
      configured = Boolean(result.configured);
      isAdmin = Boolean(result.admin);
      if (!isAdmin) auth().clearToken();
      render();
      message(!configured ? "사이트 관리자 설정이 필요합니다." : !isAdmin
        ? "헤더의 관리자 버튼으로 로그인해 주세요." : result.connected
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

  byId("gravityLoginOpen")?.addEventListener("click", openLogin);

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

  function bindSiteAdmin() {
    if (window.SiteAdmin && typeof window.SiteAdmin.subscribe === "function") {
      window.SiteAdmin.subscribe((state) => {
        applyAdminState(state);
        if (panel && state.admin) refreshConnectStatus();
      });
      return;
    }
    document.addEventListener("site-admin:change", (event) => {
      applyAdminState(event.detail);
      if (panel && event.detail && event.detail.admin) refreshConnectStatus();
    });
    refreshConnectStatus();
  }

  window.addEventListener("pageshow", event => { if (event.persisted) refreshConnectStatus(); });
  bindSiteAdmin();
})();
