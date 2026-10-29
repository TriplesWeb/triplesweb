(() => {
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
})();
