(() => {
  const grid = document.getElementById('archiveGrid');
  if (!grid) return;

  const KEY = 'triples-archive-cols';
  const filter = document.querySelector('[data-archive-cols-filter]');
  const menu = filter?.querySelector('[data-archive-cols-menu]');
  const toggle = filter?.querySelector('[data-archive-cols-toggle]');
  const label = filter?.querySelector('[data-archive-cols-label]');
  const seen = new WeakSet();
  let frame = 0;
  let laying = false;
  let pending = false;
  let contentDirty = true;
  let lastKey = '';

  function storedCols() {
    try {
      const value = localStorage.getItem(KEY);
      if (value === '2' || value === '3' || value === '4' || value === '5') return Number(value);
    } catch (_) { /* private mode */ }
    return 0;
  }

  function cssPx(name, fallback) {
    const raw = getComputedStyle(grid).getPropertyValue(name).trim() || fallback;
    if (raw.endsWith('px')) return parseFloat(raw) || 0;
    const probe = document.createElement('div');
    probe.style.cssText = 'position:absolute;visibility:hidden;width:' + raw;
    grid.appendChild(probe);
    const px = probe.getBoundingClientRect().width;
    probe.remove();
    return px || 0;
  }

  function columnCount(width) {
    const picked = storedCols();
    if (picked) return picked;
    const gap = cssPx('--ar-pack-gap', '0.9rem');
    const min = cssPx('--ar-col-min', '14rem');
    const count = Math.floor((width + gap) / (min + gap));
    return Math.min(5, Math.max(2, count || 2));
  }

  function visibleCards() {
    return [...grid.querySelectorAll(':scope > .ar-card')].filter((card) => !card.classList.contains('ar-off'));
  }

  function clearPacked() {
    grid.classList.remove('is-packed');
    grid.style.height = '';
    grid.querySelectorAll(':scope > .ar-card').forEach((card) => {
      card.style.position = '';
      card.style.left = '';
      card.style.top = '';
      card.style.width = '';
    });
  }

  function syncLabel(cols) {
    if (label) label.textContent = cols + '열';
    filter?.querySelectorAll('[data-archive-cols-option]').forEach((btn) => {
      const on = btn.getAttribute('data-archive-cols-option') === String(cols);
      btn.classList.toggle('is-active', on);
      btn.setAttribute('aria-selected', on ? 'true' : 'false');
    });
  }

  function bindImages() {
    grid.querySelectorAll('img').forEach((img) => {
      if (seen.has(img) || img.complete) {
        seen.add(img);
        return;
      }
      seen.add(img);
      img.addEventListener('load', () => {
        contentDirty = true;
        schedule();
      }, { once: true });
    });
  }

  function layout() {
    if (laying) {
      pending = true;
      return;
    }
    const carousel = grid.classList.contains('is-photo-carousel');
    const width = Math.round(grid.clientWidth);
    const cards = carousel ? [] : visibleCards();
    const cols = carousel || !width ? 0 : columnCount(width);
    const key = (carousel ? 'carousel' : 'pack') + ':' + width + ':' + cols + ':' + cards.map((card) => card.dataset.id || '').join(',');
    if (!contentDirty && key === lastKey) return;
    contentDirty = false;
    lastKey = key;
    if (carousel || !cards.length || !width) {
      clearPacked();
      if (cols) syncLabel(cols);
      return;
    }

    laying = true;
    const gap = cssPx('--ar-pack-gap', '0.9rem');
    const colWidth = (width - gap * (cols - 1)) / cols;
    grid.classList.add('is-packed');
    grid.dataset.cols = String(cols);
    cards.forEach((card) => {
      card.style.position = 'absolute';
      card.style.width = colWidth + 'px';
    });
    const heights = Array(cols).fill(0);
    cards.forEach((card) => {
      let col = 0;
      for (let i = 1; i < cols; i += 1) {
        if (heights[i] < heights[col]) col = i;
      }
      card.style.left = (col * (colWidth + gap)) + 'px';
      card.style.top = heights[col] + 'px';
      heights[col] += card.offsetHeight + gap;
    });
    grid.style.height = Math.max(0, Math.max(...heights) - gap) + 'px';
    syncLabel(cols);
    bindImages();
    laying = false;
    if (pending) {
      pending = false;
      contentDirty = true;
      schedule();
    }
  }

  function schedule() {
    if (frame) return;
    frame = requestAnimationFrame(() => {
      frame = 0;
      layout();
    });
  }

  function setOpen(open) {
    if (!menu || !toggle || !filter) return;
    menu.hidden = !open;
    toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    filter.classList.toggle('is-open', open);
  }

  toggle?.addEventListener('click', (event) => {
    event.preventDefault();
    event.stopPropagation();
    const willOpen = Boolean(menu?.hidden);
    window.closeArchiveOverlays?.(toggle);
    setOpen(willOpen);
  });

  filter?.querySelectorAll('[data-archive-cols-option]').forEach((btn) => {
    btn.addEventListener('click', (event) => {
      event.preventDefault();
      event.stopPropagation();
      const value = btn.getAttribute('data-archive-cols-option');
      if (value !== '2' && value !== '3' && value !== '4' && value !== '5') return;
      try { localStorage.setItem(KEY, value); } catch (_) { /* ignore */ }
      contentDirty = true;
      setOpen(false);
      schedule();
    });
  });

  grid.addEventListener('archive:updated', () => {
    contentDirty = true;
    schedule();
  });
  window.addEventListener('resize', () => {
    contentDirty = true;
    schedule();
  });
  if (typeof ResizeObserver === 'function') {
    const observer = new ResizeObserver(() => schedule());
    observer.observe(grid);
  }
  const classObserver = new MutationObserver(() => {
    contentDirty = true;
    schedule();
  });
  classObserver.observe(grid, { attributes: true, attributeFilter: ['class'] });
  document.fonts?.ready.then(() => {
    contentDirty = true;
    schedule();
  }).catch(() => {});
  schedule();
})();
