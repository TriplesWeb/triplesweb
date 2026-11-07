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

  function setMobileMenuOpen(open) {
    if (!mobileMenu || !mobileMenuBtn || !mobileMenuIcon) return;
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
})();
