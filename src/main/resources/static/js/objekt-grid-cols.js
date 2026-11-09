(() => {
  const STORAGE_KEY = 'triples-objekt-cols';
  const ALLOWED = new Set(['2', '3', '4', '5']);

  function isClosed(menu) {
    if (!menu) return true;
    return menu.hasAttribute('hidden') || menu.classList.contains('hidden');
  }

  function place(menu) {
    if (!menu || isClosed(menu)) return;
    const host = menu.closest('.objekt-filter, [data-objekt-cols-filter]');
    if (!host) return;
    const bar = menu.closest('.objekt-filter-bar, .collection-filter-bar');
    const hostBox = host.getBoundingClientRect();
    let bottom = hostBox.bottom;
    if (bar && host.classList.contains('objekt-filter')) {
      const chips = [...bar.querySelectorAll(':scope > .objekt-filter')];
      if (chips.length) {
        bottom = Math.max(...chips.map((el) => el.getBoundingClientRect().bottom));
      }
    }
    menu.style.top = Math.max(8, Math.round(bottom - hostBox.top + 8)) + 'px';
  }

  function setOpen(menu, open) {
    if (!menu) return;
    const show = Boolean(open);
    menu.hidden = !show;
    menu.toggleAttribute('hidden', !show);
    menu.classList.toggle('hidden', !show);
    const host = menu.closest('.objekt-filter, [data-objekt-cols-filter]');
    host?.classList.toggle('is-open', show);
    const toggles = [];
    if (menu.id) {
      document.querySelectorAll('[aria-controls="' + menu.id + '"]').forEach((el) => toggles.push(el));
    }
    host?.querySelectorAll('[data-objekt-cols-toggle], .objekt-kind-toggle').forEach((el) => toggles.push(el));
    toggles.forEach((el) => el.setAttribute('aria-expanded', show ? 'true' : 'false'));
    if (show) {
      place(menu);
      requestAnimationFrame(() => place(menu));
    } else {
      menu.style.removeProperty('top');
    }
  }

  function closeAll(except) {
    document.querySelectorAll('.objekt-dropdown-menu').forEach((menu) => {
      if (menu !== except) setOpen(menu, false);
    });
  }

  function toggle(menu) {
    const open = isClosed(menu);
    closeAll(open ? menu : null);
    setOpen(menu, open);
    return open;
  }

  window.ObjektDropdown = { isClosed, setOpen, closeAll, toggle, place };

  window.addEventListener('resize', () => {
    document.querySelectorAll('.objekt-dropdown-menu').forEach(place);
  });

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

  function init() {
    apply(readCols());

    document.querySelectorAll('[data-objekt-cols-toggle]').forEach((btn) => {
      btn.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        const filter = btn.closest('[data-objekt-cols-filter]');
        const menu = filter?.querySelector('[data-objekt-cols-menu]');
        if (!menu) return;
        toggle(menu);
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
        closeAll();
      });
    });

    document.addEventListener('click', (event) => {
      if (!event.target.closest('[data-objekt-cols-filter]')) {
        document.querySelectorAll('[data-objekt-cols-menu]').forEach((menu) => setOpen(menu, false));
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
