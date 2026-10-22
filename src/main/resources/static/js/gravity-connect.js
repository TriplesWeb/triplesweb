(() => {
  const auth = window.AboutAdminAuth;
  const status = document.getElementById("connectStatus");
  const adminForm = document.getElementById("adminLogin");
  const panel = document.getElementById("cosmoLogin");
  const codeForm = document.getElementById("codeForm");
  function message(value) { status.textContent = value; }
  async function api(path, body) {
    const headers = auth.applyAuthHeaders({ "X-Gravity-Connect": "1" });
    const options = { headers, credentials: "same-origin", cache: "no-store" };
    if (body) {
      options.method = "POST";
      headers.set("Content-Type", "application/json");
      options.body = JSON.stringify(body);
    }
    const response = await fetch("/api/gravity/connect/" + path, options);
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
      if (response.status === 403) { adminForm.hidden = false; panel.hidden = true; }
      throw new Error(result.message || result.detail ||
        (response.status === 429 ? "잠시 후 다시 시도해 주세요." : "요청에 실패했습니다. 입력 내용과 연결 상태를 확인해 주세요."));
    }
    return result;
  }
  async function refresh() {
    const result = await api("status");
    adminForm.hidden = Boolean(result.admin) || !result.configured;
    panel.hidden = !result.admin;
    message(!result.configured ? "사이트 관리자 설정이 필요합니다." : !result.admin
      ? "먼저 사이트 관리자로 로그인하세요." : result.connected
      ? "COSMO 계정이 연결되어 있습니다. 필요하면 이메일로 다시 연결할 수 있습니다."
      : "본인 COSMO 이메일로 최초 인증을 진행하세요.");
  }
  async function submit(event, operation) {
    event.preventDefault();
    const buttons = [...document.querySelectorAll("button")];
    buttons.forEach(button => { button.disabled = true; });
    try { await operation(); } catch (error) { message(error.message); }
    finally { buttons.forEach(button => { button.disabled = false; }); }
  }
  adminForm.addEventListener("submit", event => submit(event, async () => {
    const input = document.getElementById("adminPassword");
    const password = input.value;
    input.value = "";
    const result = await api("admin-login", { password });
    auth.setToken(result.token);
    await refresh();
  }));
  document.getElementById("emailForm").addEventListener("submit", event => submit(event, async () => {
    codeForm.hidden = true;
    await api("email", { email: document.getElementById("cosmoEmail").value.trim() });
    codeForm.hidden = false;
    document.getElementById("cosmoCode").focus();
    message("이메일로 받은 6자리 인증번호를 입력하세요. 재요청은 60초 후 가능합니다.");
  }));
  codeForm.addEventListener("submit", event => submit(event, async () => {
    const input = document.getElementById("cosmoCode");
    const code = input.value;
    input.value = "";
    await api("verify", { code });
    codeForm.hidden = true;
    message("계정 연결이 완료됐습니다. 그래비티 목록에서 실제 정보를 확인하세요.");
  }));
  refresh().catch(error => message(error.message));
})();
