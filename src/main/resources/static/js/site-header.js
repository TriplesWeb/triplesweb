(() => {
  if (window.__siteHeaderBound) return;
  window.__siteHeaderBound = true;
  let lastScrollTop = 0;
  const header = document.getElementById("top-header");
  const mobileMenuBtn = document.getElementById("mobile-menu-btn");
  const mobileMenu = document.getElementById("mobile-menu");
  const mobileMenuIcon = document.getElementById("mobile-menu-icon");
  const INTRO_KEY = "sss-mascot-intro-seen";

  function playMascotIntro() {
    const wrap = document.getElementById("sss-mascot-wrap");
    const mascot = document.getElementById("sss-mascot");
    if (!wrap || !mascot) return;
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let seen = false;
    try { seen = sessionStorage.getItem(INTRO_KEY) === "1"; } catch (_) {}
    if (reduce || seen) {
      wrap.classList.add("is-ready");
      return;
    }
    const trailOffsets = [
      { tx: "86px", ty: "16px", delay: "0ms" },
      { tx: "68px", ty: "20px", delay: "55ms" },
      { tx: "50px", ty: "18px", delay: "110ms" },
      { tx: "32px", ty: "12px", delay: "165ms" },
      { tx: "16px", ty: "6px", delay: "220ms" }
    ];
    for (const step of trailOffsets) {
      const ghost = mascot.cloneNode(true);
      ghost.removeAttribute("id");
      ghost.className = "sss-mascot-trail";
      ghost.style.setProperty("--tx", step.tx);
      ghost.style.setProperty("--ty", step.ty);
      ghost.style.animationDelay = step.delay;
      wrap.insertBefore(ghost, mascot);
    }
    wrap.classList.add("is-intro");
    const finish = () => {
      wrap.classList.remove("is-intro");
      wrap.querySelectorAll(".sss-mascot-trail").forEach((el) => el.remove());
      wrap.classList.add("is-ready");
      try { sessionStorage.setItem(INTRO_KEY, "1"); } catch (_) {}
    };
    mascot.addEventListener("animationend", finish, { once: true });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", playMascotIntro);
  } else {
    playMascotIntro();
  }

  function closeHeaderPopovers() {
    setCosmoIdOpen(false);
    setAlertsOpen(false);
  }

  function setMobileMenuOpen(open) {
    if (!mobileMenu || !mobileMenuBtn || !mobileMenuIcon) return;
    if (open) {
      setHeaderSearchOpen(false);
      closeHeaderPopovers();
    }
    mobileMenu.classList.toggle("hidden", !open);
    mobileMenuBtn.setAttribute("aria-expanded", open ? "true" : "false");
    mobileMenuBtn.setAttribute("aria-label", open ? "메뉴 닫기" : "메뉴 열기");
    mobileMenuIcon.textContent = open ? "close" : "menu";
  }

  if (mobileMenuBtn && mobileMenu) {
    mobileMenuBtn.addEventListener("click", function () {
      const willOpen = mobileMenu.classList.contains("hidden");
      setMobileMenuOpen(willOpen);
    });

    mobileMenu.querySelectorAll(".mobile-nav-toggle").forEach(function (btn) {
      btn.setAttribute("aria-expanded", "false");
      btn.addEventListener("click", function () {
        const group = btn.closest(".mobile-nav-group");
        const panel = group && group.querySelector(".mobile-nav-panel");
        const icon = btn.querySelector(".material-symbols-outlined");
        if (!panel) return;
        const willOpen = panel.classList.contains("hidden");
        panel.classList.toggle("hidden", !willOpen);
        btn.setAttribute("aria-expanded", String(willOpen));
        if (icon) icon.style.transform = willOpen ? "rotate(180deg)" : "";
      });
    });
  }

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && mobileMenu && !mobileMenu.classList.contains("hidden")) {
      setMobileMenuOpen(false);
      mobileMenuBtn.focus();
    }
  });
  window.addEventListener("resize", function () {
    if (window.innerWidth >= 1024) setMobileMenuOpen(false);
  });

  window.addEventListener("scroll", function () {
    if (!header) return;
    let currentScroll = window.pageYOffset || document.documentElement.scrollTop;

    if (mobileMenu && !mobileMenu.classList.contains("hidden")) {
      header.style.transform = "translateY(0)";
      lastScrollTop = currentScroll;
      return;
    }
    if (currentScroll > lastScrollTop && currentScroll > 100) {
      header.style.transform = "translateY(-100%)";
      setMobileMenuOpen(false);
    } else {
      header.style.transform = "translateY(0)";
    }
    lastScrollTop = currentScroll <= 0 ? 0 : currentScroll;
  }, false);

  function headerSearchRoot() {
    return document.querySelector("[data-header-search]");
  }

  let headerSearchIgnoreCloseUntil = 0;

  function setHeaderSearchOpen(open) {
    const headerSearch = headerSearchRoot();
    if (!headerSearch) return;
    if (open) {
      setMobileMenuOpen(false);
      closeHeaderPopovers();
    }
    const searchInput = headerSearch.querySelector("[data-header-search-input]");
    const searchOpen = headerSearch.querySelector("[data-header-search-open]");
    headerSearch.classList.toggle("is-open", open);
    if (searchOpen) {
      searchOpen.setAttribute("aria-expanded", open ? "true" : "false");
      searchOpen.setAttribute("aria-label", open ? "검색 닫기" : "검색 열기");
    }
    if (!searchInput) return;
    if (open) {
      // 같은 제스처의 바깥 pointerdown/click에 바로 닫히지 않게
      headerSearchIgnoreCloseUntil = Date.now() + 500;
      searchInput.removeAttribute("tabindex");
      requestAnimationFrame(function () {
        searchInput.focus();
        try { searchInput.select(); } catch (_) {}
      });
    } else {
      searchInput.setAttribute("tabindex", "-1");
      searchInput.blur();
    }
  }

  (function bindHeaderSearch() {
    const headerSearch = headerSearchRoot();
    if (!headerSearch) return;
    const searchOpen = headerSearch.querySelector("[data-header-search-open]");
    if (!searchOpen) return;
    searchOpen.setAttribute("aria-expanded", "false");

    function openFromToggle(event) {
      if (event.type === "pointerdown" && event.button != null && event.button !== 0) return;
      event.preventDefault();
      event.stopPropagation();
      setHeaderSearchOpen(true);
    }

    // 문서 위임 대신 버튼에 직접 바인딩 (SVG 클릭·캡처 이슈 회피)
    searchOpen.addEventListener("pointerdown", openFromToggle);
    searchOpen.addEventListener("click", openFromToggle);
  })();

  document.addEventListener("keydown", function (event) {
    const headerSearch = headerSearchRoot();
    if (event.key !== "Escape" || !headerSearch || !headerSearch.classList.contains("is-open")) return;
    setHeaderSearchOpen(false);
    const searchOpen = headerSearch.querySelector("[data-header-search-open]");
    if (searchOpen) searchOpen.focus();
  });

  document.addEventListener("pointerdown", function (event) {
    if (Date.now() < headerSearchIgnoreCloseUntil) return;
    const headerSearch = headerSearchRoot();
    if (!headerSearch || !headerSearch.classList.contains("is-open")) return;
    const target = event.target;
    if (target instanceof Node && headerSearch.contains(target)) return;
    const searchInput = headerSearch.querySelector("[data-header-search-input]");
    if (searchInput && searchInput.value.trim()) return;
    setHeaderSearchOpen(false);
  });

  const COSMO_RECENTS_KEY = "cosmo-nickname-recents";
  const ALERTS_CATALOG_KEY = "site-alerts-catalog-sig";
  const ALERTS_NOTICE_KEY = "site-alerts-notice-id";
  const cosmoOpen = document.querySelector("[data-cosmo-id-open]");
  const cosmoPanel = document.querySelector("[data-cosmo-id-panel]");
  const alertsOpen = document.querySelector("[data-alerts-open]");
  const alertsPanel = document.querySelector("[data-alerts-panel]");
  const alertsBadge = document.querySelector("[data-alerts-badge]");
  let alertsCache = null;

  function loadNicknameRecents() {
    try {
      const raw = JSON.parse(localStorage.getItem(COSMO_RECENTS_KEY) || "[]");
      return Array.isArray(raw) ? raw.map(String).filter(Boolean) : [];
    } catch (_) {
      return [];
    }
  }

  function renderCosmoIdPanel() {
    if (!cosmoPanel) return;
    const recents = loadNicknameRecents();
    const mine = cosmoPanel.querySelector("[data-cosmo-id-mine]");
    const list = cosmoPanel.querySelector("[data-cosmo-id-recents]");
    if (mine) {
      if (recents[0]) {
        mine.hidden = false;
        mine.href = "/cosmo/collection?nickname=" + encodeURIComponent(recents[0]);
        mine.textContent = "내 아이디로 보기 · " + recents[0];
      } else {
        mine.hidden = true;
      }
    }
    if (!list) return;
    list.replaceChildren();
    recents.slice(0, 8).forEach((name) => {
      const item = document.createElement("li");
      const link = document.createElement("a");
      link.href = "/cosmo/collection?nickname=" + encodeURIComponent(name);
      link.textContent = name;
      item.appendChild(link);
      list.appendChild(item);
    });
  }

  function setCosmoIdOpen(open) {
    if (!cosmoOpen || !cosmoPanel) return;
    if (open) {
      setMobileMenuOpen(false);
      setHeaderSearchOpen(false);
      setAlertsOpen(false);
      renderCosmoIdPanel();
    }
    cosmoPanel.hidden = !open;
    cosmoOpen.setAttribute("aria-expanded", open ? "true" : "false");
    if (open) {
      const input = cosmoPanel.querySelector("[data-cosmo-id-input]");
      if (input) requestAnimationFrame(() => input.focus());
    }
  }

  function setAlertsOpen(open) {
    if (!alertsOpen || !alertsPanel) return;
    if (open) {
      setMobileMenuOpen(false);
      setHeaderSearchOpen(false);
      setCosmoIdOpen(false);
      loadAlerts(true);
    }
    alertsPanel.hidden = !open;
    alertsOpen.setAttribute("aria-expanded", open ? "true" : "false");
  }

  function markAlertsSeen(data) {
    if (!data) return;
    try {
      if (data.catalogSig) localStorage.setItem(ALERTS_CATALOG_KEY, String(data.catalogSig));
      const noticeId = data.notices && data.notices[0] && data.notices[0].id;
      if (noticeId != null) localStorage.setItem(ALERTS_NOTICE_KEY, String(noticeId));
    } catch (_) {}
    if (alertsBadge) alertsBadge.hidden = true;
  }

  function hasUnreadAlerts(data) {
    if (!data) return false;
    let seenSig = "";
    let seenNotice = "";
    try {
      seenSig = localStorage.getItem(ALERTS_CATALOG_KEY) || "";
      seenNotice = localStorage.getItem(ALERTS_NOTICE_KEY) || "";
    } catch (_) {}
    const latestNotice = data.notices && data.notices[0] && data.notices[0].id != null
      ? String(data.notices[0].id) : "";
    const catalogNew = !!(data.catalogSig && String(data.catalogSig) !== seenSig);
    const noticeNew = !!(latestNotice && latestNotice !== seenNotice);
    return catalogNew || noticeNew;
  }

  function renderAlerts(data) {
    if (!alertsPanel) return;
    const status = alertsPanel.querySelector("[data-alerts-status]");
    const body = alertsPanel.querySelector("[data-alerts-body]");
    if (!body) return;
    body.replaceChildren();
    if (!data) {
      if (status) status.textContent = "알림을 불러오지 못했습니다.";
      return;
    }
    if (status) status.hidden = true;
    if (data.dropWindow) {
      const drop = document.createElement("a");
      drop.className = "site-header-popover__drop";
      drop.href = "/cosmo/objekt";
      drop.textContent = "드롭 시간입니다. 새 오브젝트를 확인하세요.";
      body.appendChild(drop);
    }
    const noticeTitle = document.createElement("p");
    noticeTitle.className = "site-header-popover__heading";
    noticeTitle.textContent = "공지";
    body.appendChild(noticeTitle);
    const notices = Array.isArray(data.notices) ? data.notices : [];
    if (!notices.length) {
      const empty = document.createElement("p");
      empty.className = "site-header-popover__empty";
      empty.textContent = "새 공지가 없습니다.";
      body.appendChild(empty);
    } else {
      const list = document.createElement("ul");
      list.className = "site-header-popover__list";
      notices.forEach((item) => {
        const li = document.createElement("li");
        const link = document.createElement("a");
        link.href = item && item.href ? String(item.href) : "/article/notice";
        link.textContent = item && item.title ? String(item.title) : "공지";
        li.appendChild(link);
        list.appendChild(li);
      });
      body.appendChild(list);
    }
    const objektTitle = document.createElement("p");
    objektTitle.className = "site-header-popover__heading";
    objektTitle.textContent = "새 오브젝트";
    body.appendChild(objektTitle);
    const objekts = Array.isArray(data.objekts) ? data.objekts : [];
    if (!objekts.length) {
      const empty = document.createElement("p");
      empty.className = "site-header-popover__empty";
      empty.textContent = "새로 올라온 오브젝트가 없습니다.";
      body.appendChild(empty);
    } else {
      const list = document.createElement("ul");
      list.className = "site-header-popover__list";
      objekts.forEach((item) => {
        const li = document.createElement("li");
        const link = document.createElement("a");
        link.href = item && item.href ? String(item.href) : "/cosmo/objekt";
        link.textContent = item && item.label ? String(item.label) : "오브젝트";
        li.appendChild(link);
        list.appendChild(li);
      });
      body.appendChild(list);
    }
  }

  function loadAlerts(markSeen) {
    const apply = (data) => {
      alertsCache = data;
      if (alertsBadge) alertsBadge.hidden = !hasUnreadAlerts(data);
      if (markSeen) {
        renderAlerts(data);
        markAlertsSeen(data);
      }
    };
    if (alertsCache && markSeen) {
      apply(alertsCache);
      return;
    }
    fetch("/api/site/alerts", { headers: { Accept: "application/json" } })
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then(apply)
      .catch(() => {
        if (alertsBadge) alertsBadge.hidden = true;
        if (markSeen) renderAlerts(null);
      });
  }

  if (cosmoOpen && cosmoPanel) {
    cosmoOpen.addEventListener("click", function (event) {
      event.preventDefault();
      event.stopPropagation();
      setCosmoIdOpen(cosmoPanel.hidden);
    });
  }
  if (alertsOpen && alertsPanel) {
    alertsOpen.addEventListener("click", function (event) {
      event.preventDefault();
      event.stopPropagation();
      setAlertsOpen(alertsPanel.hidden);
    });
  }

  document.addEventListener("keydown", function (event) {
    if (event.key !== "Escape") return;
    if (cosmoPanel && !cosmoPanel.hidden) {
      setCosmoIdOpen(false);
      if (cosmoOpen) cosmoOpen.focus();
      return;
    }
    if (alertsPanel && !alertsPanel.hidden) {
      setAlertsOpen(false);
      if (alertsOpen) alertsOpen.focus();
    }
  });

  document.addEventListener("pointerdown", function (event) {
    const target = event.target;
    if (!(target instanceof Node)) return;
    if (cosmoPanel && !cosmoPanel.hidden) {
      const wrap = cosmoPanel.closest(".site-header-popover-wrap") || cosmoPanel;
      if (!wrap.contains(target)) setCosmoIdOpen(false);
    }
    if (alertsPanel && !alertsPanel.hidden) {
      const wrap = alertsPanel.closest(".site-header-popover-wrap") || alertsPanel;
      if (!wrap.contains(target)) setAlertsOpen(false);
    }
  });

  loadAlerts(false);
})();
