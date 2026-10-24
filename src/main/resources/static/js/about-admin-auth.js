(() => {
  const TOKEN_KEY = "aboutAdminToken";
  const HEADER = "X-About-Admin-Token";
  const LEGACY_KEYS = [
    "chemistryAdminToken",
    "artistAdminToken",
    "albumAdminToken",
    "unitAdminToken"
  ];

  function readStorage(storage, key) {
    try {
      return storage.getItem(key) || "";
    } catch (_) {
      return "";
    }
  }

  function writeStorage(storage, key, value) {
    try {
      if (value) storage.setItem(key, value);
      else storage.removeItem(key);
    } catch (_) { /* private mode */ }
  }

  function getToken() {
    const current = readStorage(localStorage, TOKEN_KEY) || readStorage(sessionStorage, TOKEN_KEY);
    if (current) return current;
    for (const key of LEGACY_KEYS) {
      const legacy = readStorage(localStorage, key) || readStorage(sessionStorage, key);
      if (legacy) {
        setToken(legacy);
        return legacy;
      }
    }
    return "";
  }

  function syncCookie(token) {
    const maxAge = token ? 60 * 60 * 12 : 0;
    document.cookie = "about_admin_token=" + encodeURIComponent(token || "")
      + "; Path=/; Max-Age=" + maxAge + "; SameSite=Lax";
  }

  function setToken(token) {
    writeStorage(localStorage, TOKEN_KEY, token || "");
    writeStorage(sessionStorage, TOKEN_KEY, "");
    for (const key of LEGACY_KEYS) {
      writeStorage(localStorage, key, token || "");
      writeStorage(sessionStorage, key, "");
    }
    syncCookie(token || "");
  }

  function clearToken() {
    setToken("");
  }

  function applyAuthHeaders(headers) {
    const h = headers instanceof Headers ? headers : new Headers(headers || {});
    const token = getToken();
    if (token) h.set(HEADER, token);
    return h;
  }

  window.AboutAdminAuth = {
    TOKEN_KEY,
    HEADER,
    getToken,
    setToken,
    clearToken,
    applyAuthHeaders
  };
  syncCookie(getToken());
})();
