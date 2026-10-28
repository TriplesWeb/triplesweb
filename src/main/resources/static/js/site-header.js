(() => {
  const header = document.getElementById('top-header');
  if (!header) return;
  const toggles = [...header.querySelectorAll('[data-header-toggle]')];
  const mobile = document.getElementById('mobile-menu-btn');
  const search = document.getElementById('header-search-btn');
  const wide = () => window.innerWidth >= 1280;
  function setOpen(button, open) {
    const panel = document.getElementById(button.getAttribute('aria-controls'));
    if (!panel) return;
    button.setAttribute('aria-expanded', String(open));
    panel.hidden = !open;
    // Keep the former hidden class in sync for integrations that inspect the mobile menu.
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
  // Close a desktop dropdown when keyboard focus leaves it.
  header.addEventListener('focusout', () => requestAnimationFrame(() => {
    header.querySelectorAll('.header-disclosure').forEach(group => {
      if (!group.contains(document.activeElement)) {
        const button = group.querySelector('[data-header-toggle]');
        if (button) setOpen(button, false);
      }
    });
  }));
  function resize() {
    closeAll();
    if (window.innerWidth >= 1024) setOpen(mobile, false);
    setOpen(search, wide());
  }
  window.addEventListener('resize', resize);
  resize();
  let lastScroll = window.scrollY;
  let scheduled = false;
  window.addEventListener('scroll', () => {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(() => {
      const current = Math.max(0, window.scrollY);
      const menuOpen = toggles.some(button => button.getAttribute('aria-expanded') === 'true' && !(button === search && wide()));
      header.style.transform = menuOpen || header.contains(document.activeElement) || current <= lastScroll || current < 100 ? 'translateY(0)' : 'translateY(-100%)';
      lastScroll = current; scheduled = false;
    });
  }, { passive: true });
})();
