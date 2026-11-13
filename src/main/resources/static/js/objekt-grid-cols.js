(() => {
  const STORAGE_KEY = 'triples-objekt-cols';
  const MOBILE_MQ = '(max-width: 767px)';

  function isMobile() {
    return window.matchMedia(MOBILE_MQ).matches;
  }

  function maxCols() {
    return isMobile() ? 4 : 8;
  }

  function defaultCols() {
    return isMobile() ? '2' : '5';
  }

  function clampCols(value) {
    const n = Number.parseInt(String(value || ''), 10);
    if (!Number.isFinite(n)) return defaultCols();
    const max = maxCols();
    const min = 2;
    return String(Math.min(max, Math.max(min, n)));
  }

  // 공통 API는 objekt-dropdown-core.js가 먼저 로드되면 그대로 쓰고, 없으면 여기서 보정한다.
  if (!window.ObjektDropdown) {
    function isClosed(menu) {
      if (!menu) return true;
      return menu.hasAttribute('hidden') || menu.classList.contains('hidden');
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
    window.ObjektDropdown = { isClosed, setOpen, closeAll, toggle };
  }
  const { isClosed, setOpen, closeAll, toggle } = window.ObjektDropdown;

  function readCols() {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored != null) return clampCols(stored);
    } catch (_) { /* ignore quota / private mode */ }
    return defaultCols();
  }

  function writeCols(value) {
    try { localStorage.setItem(STORAGE_KEY, value); } catch (_) { /* ignore */ }
  }

  function syncOptionVisibility() {
    const max = maxCols();
    document.querySelectorAll('[data-objekt-cols-option]').forEach((btn) => {
      const value = Number.parseInt(btn.getAttribute('data-objekt-cols-option') || '', 10);
      const allowed = Number.isFinite(value) && value >= 2 && value <= max;
      btn.hidden = !allowed;
      btn.toggleAttribute('hidden', !allowed);
    });
  }

  function apply(cols) {
    const next = clampCols(cols);
    document.querySelectorAll('[data-objekt-grid]').forEach((grid) => {
      grid.setAttribute('data-cols', next);
    });
    document.querySelectorAll('[data-objekt-cols-label]').forEach((el) => {
      el.textContent = next + '열';
    });
    document.querySelectorAll('[data-objekt-cols-option]').forEach((btn) => {
      const on = btn.getAttribute('data-objekt-cols-option') === next;
      btn.classList.toggle('is-active', on);
      btn.setAttribute('aria-selected', on ? 'true' : 'false');
    });
    syncOptionVisibility();
    return next;
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
        const raw = Number.parseInt(btn.getAttribute('data-objekt-cols-option') || '', 10);
        if (!Number.isFinite(raw) || raw < 2 || raw > maxCols()) return;
        const next = String(raw);
        writeCols(next);
        apply(next);
        closeAll();
      });
    });

    document.addEventListener('click', (event) => {
      if (!event.target.closest('[data-objekt-cols-filter]')) {
        document.querySelectorAll('[data-objekt-cols-menu]').forEach((menu) => setOpen(menu, false));
      }
    });

    const mq = window.matchMedia(MOBILE_MQ);
    const onViewportChange = () => apply(readCols());
    if (typeof mq.addEventListener === 'function') mq.addEventListener('change', onViewportChange);
    else if (typeof mq.addListener === 'function') mq.addListener(onViewportChange);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
