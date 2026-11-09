(() => {
  const STORAGE_KEY = 'triples-objekt-cols';
  const ALLOWED = new Set(['2', '3', '4', '5']);

  function readCols() {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (ALLOWED.has(stored)) return stored;
    } catch (_) { /* ignore quota / private mode */ }
    return '2';
  }

  function writeCols(value) {
    try { localStorage.setItem(STORAGE_KEY, value); } catch (_) { /* ignore */ }
  }

  function apply(cols) {
    document.querySelectorAll('[data-objekt-grid]').forEach((grid) => {
      grid.setAttribute('data-cols', cols);
    });
    document.querySelectorAll('[data-objekt-cols-label]').forEach((el) => {
      el.textContent = cols + '열';
    });
    document.querySelectorAll('[data-objekt-cols-option]').forEach((btn) => {
      const on = btn.getAttribute('data-objekt-cols-option') === cols;
      btn.classList.toggle('is-active', on);
      btn.setAttribute('aria-selected', on ? 'true' : 'false');
    });
  }

  function setMenuOpen(menu, open) {
    if (!menu) return;
    menu.hidden = !open;
    menu.classList.toggle('hidden', !open);
    const filter = menu.closest('[data-objekt-cols-filter]');
    filter?.classList.toggle('is-open', open);
    filter?.querySelector('[data-objekt-cols-toggle]')?.setAttribute('aria-expanded', open ? 'true' : 'false');
  }

  function closeColsMenus(except) {
    document.querySelectorAll('[data-objekt-cols-menu]').forEach((menu) => {
      if (menu !== except) setMenuOpen(menu, false);
    });
  }

  function closeOtherMenus(keep) {
    document.querySelectorAll('.objekt-dropdown-menu').forEach((menu) => {
      if (menu === keep) return;
      menu.hidden = true;
      menu.classList.add('hidden');
      menu.closest('.objekt-filter')?.classList.remove('is-open');
    });
  }

  function init() {
    apply(readCols());

    document.querySelectorAll('[data-objekt-cols-toggle]').forEach((toggle) => {
      toggle.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        const filter = toggle.closest('[data-objekt-cols-filter]');
        const menu = filter?.querySelector('[data-objekt-cols-menu]');
        if (!menu) return;
        const open = menu.hidden || menu.classList.contains('hidden');
        closeOtherMenus(open ? menu : null);
        setMenuOpen(menu, open);
        if (!open) closeColsMenus();
      });
    });

    document.querySelectorAll('[data-objekt-cols-option]').forEach((btn) => {
      btn.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        const value = btn.getAttribute('data-objekt-cols-option');
        if (!ALLOWED.has(value)) return;
        writeCols(value);
        apply(value);
        closeColsMenus();
      });
    });

    document.addEventListener('click', (event) => {
      if (!event.target.closest('[data-objekt-cols-filter]')) closeColsMenus();
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
