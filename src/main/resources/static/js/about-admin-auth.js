(() => {
  /**
   * About 관리자 인증은 HttpOnly 세션 쿠키만 사용한다.
   * 상태 변경 요청에는 CSRF 쿠키(XSRF-TOKEN) → X-XSRF-TOKEN 헤더를 붙인다.
   * getToken/setToken/clearToken 은 기존 호출처 호환용 no-op 이다.
   */

  function readCookie(name) {
    const encoded = encodeURIComponent(name);
    const parts = document.cookie ? document.cookie.split("; ") : [];
    for (const part of parts) {
      const eq = part.indexOf("=");
      if (eq < 0) continue;
      if (part.slice(0, eq) === name || part.slice(0, eq) === encoded) {
        return decodeURIComponent(part.slice(eq + 1));
      }
    }
    return "";
  }

  function getCsrfToken() {
    return readCookie("XSRF-TOKEN");
  }

  function getToken() {
    return "";
  }

  function setToken() {
    /* session-only: no JS-readable token */
  }

  function clearToken() {
    /* session invalidated server-side on logout */
  }

  function applyAuthHeaders(headers) {
    const h = headers instanceof Headers ? headers : new Headers(headers || {});
    const csrf = getCsrfToken();
    if (csrf) {
      h.set("X-XSRF-TOKEN", csrf);
    }
    return h;
  }

  window.AboutAdminAuth = {
    getToken,
    setToken,
    clearToken,
    getCsrfToken,
    applyAuthHeaders
  };
})();
