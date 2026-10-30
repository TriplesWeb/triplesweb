(() => {
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

  const header = document.getElementById('top-header');
  if (!header) return;
  const toggles = [...header.querySelectorAll('[data-header-toggle]')];
  const mobile = document.getElementById('mobile-menu-btn');
  const search = document.getElementById('header-search-btn');
  let searchInline = false;
  const wide = () => searchInline;
  function setOpen(button, open) {
    const panel = document.getElementById(button.getAttribute('aria-controls'));
    if (!panel) return;
    button.setAttribute('aria-expanded', String(open));
    panel.hidden = !open;
    panel.classList.toggle('hidden', !open);
    if (button === mobile) button.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
    if (!open) panel.querySelectorAll('details[open]').forEach(item => { item.open = false; });
    if (open) header.style.transform = 'translateY(0)';
  }
  function closeAll(except) {
    toggles.forEach(button => {
      const panel = document.getElementById(button.getAttribute('aria-controls'));
      if (button !== except && !panel?.contains(except) && !(button === search && wide())) setOpen(button, false);
    });
  }
  toggles.forEach(button => button.addEventListener('click', () => {
    const open = button.getAttribute('aria-expanded') !== 'true';
    if (open) closeAll(button);
    setOpen(button, open);
    if (button === search && open) document.querySelector('#header-search-panel input')?.focus();
  }));
  document.addEventListener('click', event => { if (!header.contains(event.target)) closeAll(); });
  document.addEventListener('keydown', event => {
    if (event.key !== 'Escape') return;
    const nested = document.activeElement?.closest('.header-submenu[open]');
    if (nested) { nested.open = false; nested.querySelector('summary').focus(); event.preventDefault(); return; }
    const active = toggles.filter(button => button.getAttribute('aria-expanded') === 'true' && !(button === search && wide()));
    if (!active.length) return;
    const focused = active.find(button => document.getElementById(button.getAttribute('aria-controls'))?.contains(document.activeElement));
    const target = focused || active[active.length - 1];
    setOpen(target, false); target.focus(); event.preventDefault();
  });
  header.addEventListener('focusout', event => {
    const group = event.target.closest('.header-disclosure');
    if (!group || !event.relatedTarget || group.contains(event.relatedTarget)) return;
    const button = group.querySelector('[data-header-toggle]');
    if (button) setOpen(button, false);
  });
  let currentMode = "";
  function resize() {
    if (!search || !mobile) return;
    const inline = getComputedStyle(search).display === 'none';
    const desktop = getComputedStyle(mobile).display === 'none';
    const mode = `${inline}:${desktop}`;
    if (mode === currentMode) return;
    currentMode = mode; searchInline = inline;
    closeAll();
    if (desktop) setOpen(mobile, false);
    setOpen(search, wide());
  }
  window.addEventListener('resize', resize);
  resize();

  let lastScrollTop = 0;
  window.addEventListener("scroll", function () {
    const currentScroll = window.pageYOffset || document.documentElement.scrollTop;
    const menuOpen = mobile && mobile.getAttribute('aria-expanded') === 'true';
    if (menuOpen) {
      header.style.transform = "translateY(0)";
      lastScrollTop = currentScroll;
      return;
    }
    if (currentScroll > lastScrollTop && currentScroll > 100) {
      header.style.transform = "translateY(-100%)";
      if (mobile) setOpen(mobile, false);
    } else {
      header.style.transform = "translateY(0)";
    }
    lastScrollTop = currentScroll <= 0 ? 0 : currentScroll;
  }, { passive: true });
})();
