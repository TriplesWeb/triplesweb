(() => {
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
  // Safari can blur a button to the document when clicking a native summary.
  // Only an explicit keyboard focus destination outside this disclosure closes it;
  // pointer navigation is handled by the outside-click listener above.
  header.addEventListener('focusout', event => {
    const group = event.target.closest('.header-disclosure');
    if (!group || !event.relatedTarget || group.contains(event.relatedTarget)) return;
    const button = group.querySelector('[data-header-toggle]');
    if (button) setOpen(button, false);
  });
  let currentMode = "";
  function resize() {
    // Read the rendered CSS mode: WebKit's matchMedia width can differ by a scrollbar.
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
})();
