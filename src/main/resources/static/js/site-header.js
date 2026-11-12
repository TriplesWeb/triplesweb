(() => {
  if (window.__siteHeaderBound) return;
  window.__siteHeaderBound = true;
  let lastScrollTop = 0;
  const header = document.getElementById("top-header");
  const mobileMenuBtn = document.getElementById("mobile-menu-btn");
  const mobileMenu = document.getElementById("mobile-menu");
  const mobileMenuIcon = document.getElementById("mobile-menu-icon");
  const MASCOT_FRAME_COUNT = 54;
  const MASCOT_FRAME_FPS = 24;
  const MASCOT_REST_FRAME = 54; // 1→54 재생 후 최종 고정
  const MASCOT_FRAME_BASE = "/images/sss-mascot-motion/";
  // 페이지를 열 때마다 워드마크 우측 → 홈 인트로 재생

  function mascotFrameSrc(index) {
    const n = String(index).padStart(2, "0");
    return MASCOT_FRAME_BASE + n + ".png";
  }

  function preloadMascotFrames() {
    for (let i = 1; i <= MASCOT_FRAME_COUNT; i++) {
      const img = new Image();
      img.decoding = "async";
      img.src = mascotFrameSrc(i);
    }
  }

  function easeOutCubic(t) {
    return 1 - Math.pow(1 - t, 3);
  }

  function waitWordmarkReady(wordmark) {
    if (!wordmark) return Promise.resolve();
    if (wordmark.complete && wordmark.naturalWidth > 0) return Promise.resolve();
    return new Promise((resolve) => {
      const done = () => resolve();
      wordmark.addEventListener("load", done, { once: true });
      wordmark.addEventListener("error", done, { once: true });
    });
  }

  function measureMascotFlyFromS(wrap, wordmark) {
    if (!wrap || !wordmark) return { x: 96, y: 0 };
    const wr = wrap.getBoundingClientRect();
    const wod = wordmark.getBoundingClientRect();
    // 시작·종료 높이는 동일(워드마크/홈 세로 중앙), 중간만 아래로 우회
    const startX = wod.right + wr.width * 0.55;
    const startY = wod.top + wod.height * 0.5;
    const homeX = wr.left + wr.width * 0.5;
    const homeY = wr.top + wr.height * 0.5;
    return { x: startX - homeX, y: startY - homeY };
  }

  function stopMascotMotion(mascot) {
    if (!mascot) return;
    if (mascot._motionTimer) {
      clearTimeout(mascot._motionTimer);
      mascot._motionTimer = 0;
    }
    mascot.dataset.motionRunning = "0";
  }

  // 인트로 전용: 위치 이동과 프레임 1→54를 같은 진행도로 맞춘 뒤 54 고정
  function runMascotIntroSequence(mascot, options) {
    if (!mascot || mascot.dataset.motionRunning === "1") return;
    mascot.dataset.motionRunning = "1";
    const holdMs = options.holdMs || 0;
    const moveMs = options.moveMs;
    const onUpdate = options.onUpdate;
    const onDone = options.onDone;
    const duration = holdMs + moveMs;
    const started = performance.now();
    const interval = 1000 / MASCOT_FRAME_FPS;
    let lastFrame = 0;

    const tick = () => {
      const now = performance.now();
      const local = Math.max(0, now - started);
      const moving = Math.max(0, local - holdMs);
      // 위치·프레임 같은 선형 진행 → 도착과 동시에 54
      const e = local < holdMs ? 0 : Math.min(1, moving / moveMs);
      let frame = 1;
      if (local >= holdMs) {
        frame = e >= 1
          ? MASCOT_FRAME_COUNT
          : Math.min(MASCOT_FRAME_COUNT, 1 + Math.floor(e * (MASCOT_FRAME_COUNT - 1)));
      }
      if (frame !== lastFrame) {
        lastFrame = frame;
        mascot.src = mascotFrameSrc(frame);
      }
      if (typeof onUpdate === "function") onUpdate(local, e, e, frame);
      if (local >= duration) {
        stopMascotMotion(mascot);
        mascot.src = mascotFrameSrc(MASCOT_REST_FRAME);
        if (typeof onDone === "function") onDone();
        return;
      }
      mascot._motionTimer = setTimeout(tick, interval);
    };
    mascot._motionTimer = setTimeout(tick, 0);
  }

  function playMascotIntro() {
    const wrap = document.getElementById("sss-mascot-wrap");
    const mascot = document.getElementById("sss-mascot");
    const wordmark = document.getElementById("sss-wordmark");
    if (!wrap || !mascot) return;
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    preloadMascotFrames();
    try {
      sessionStorage.removeItem("sss-mascot-intro-seen");
      sessionStorage.removeItem("sss-mascot-intro-seen-v2");
    } catch (_) {}
    if (reduce) {
      wrap.classList.add("is-ready");
      mascot.src = mascotFrameSrc(MASCOT_REST_FRAME);
      return;
    }

    waitWordmarkReady(wordmark).then(() => {
      requestAnimationFrame(() => {
        const fly = measureMascotFlyFromS(wrap, wordmark);
        if (!(fly.x > 8)) {
          const wod = wordmark ? wordmark.getBoundingClientRect() : null;
          const wr = wrap.getBoundingClientRect();
          fly.x = wod ? Math.max(64, wod.width + wr.width * 0.55 + 4) : 96;
          fly.y = 0;
        }
        const holdMs = 450;
        const moveMs = (MASCOT_FRAME_COUNT / MASCOT_FRAME_FPS) * 1000; // 54프레임 1회
        const trails = [];
        const trailMeta = [
          { t: 0.92, delay: holdMs },
          { t: 0.72, delay: holdMs + 70 },
          { t: 0.5, delay: holdMs + 140 },
          { t: 0.3, delay: holdMs + 210 },
          { t: 0.12, delay: holdMs + 280 }
        ];
        for (const step of trailMeta) {
          const ghost = mascot.cloneNode(true);
          ghost.removeAttribute("id");
          ghost.removeAttribute("data-motion-running");
          ghost.className = "sss-mascot-trail";
          ghost.src = mascotFrameSrc(1);
          wrap.insertBefore(ghost, mascot);
          trails.push({ el: ghost, t: step.t, delay: step.delay });
        }

        wrap.classList.add("is-intro");
        wrap.classList.remove("is-ready");
        mascot.src = mascotFrameSrc(1);
        mascot.style.opacity = "1";
        mascot.style.transform = "translate(" + fly.x.toFixed(2) + "px, " + fly.y.toFixed(2) + "px) scale(0.85)";

        runMascotIntroSequence(mascot, {
          holdMs: holdMs,
          moveMs: moveMs,
          onUpdate: (local, _p, e) => {
            const x = fly.x * (1 - e);
            // 시작·종료 Y는 fly.y→0, 중간만 글자 아래로 우회
            const y = fly.y * (1 - e) + Math.sin(e * Math.PI) * 18;
            const scale = 0.85 + 0.15 * e;
            mascot.style.opacity = "1";
            mascot.style.transform = "translate(" + x.toFixed(2) + "px, " + y.toFixed(2) + "px) scale(" + scale.toFixed(3) + ")";

            for (const trail of trails) {
              const tp = Math.min(1, Math.max(0, (local - trail.delay) / 850));
              if (tp <= 0) {
                trail.el.style.opacity = "0";
                continue;
              }
              const te = easeOutCubic(tp);
              const tx = fly.x * trail.t * (1 - te * 0.55);
              const ty = (fly.y * trail.t + Math.sin(trail.t * Math.PI) * 14) * (1 - te * 0.55);
              trail.el.style.opacity = String(0.45 * (1 - te));
              trail.el.style.transform = "translate(" + tx.toFixed(2) + "px, " + ty.toFixed(2) + "px) scale(" + (0.82 + 0.1 * te).toFixed(3) + ")";
            }
          },
          onDone: () => {
            wrap.classList.remove("is-intro");
            wrap.querySelectorAll(".sss-mascot-trail").forEach((el) => el.remove());
            mascot.style.opacity = "";
            mascot.style.transform = "";
            mascot.src = mascotFrameSrc(MASCOT_REST_FRAME);
            wrap.classList.add("is-ready");
          }
        });
      });
    });
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
