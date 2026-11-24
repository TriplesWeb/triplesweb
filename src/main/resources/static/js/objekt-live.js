(() => {
  const grid = document.getElementById('objekt-card-grid');
  if (!grid) return;
  let timer = 0, catalogSig = grid.dataset.catalogSig || null;
  let mintsSig = grid.dataset.mintsSig || null, inflight = false;

  function schedule(ms) {
    clearTimeout(timer);
    const wait = Number(ms);
    timer = setTimeout(tick, Number.isFinite(wait) ? Math.max(1000, wait) : 20000);
  }

  function mintSortActive() {
    const sort = document.querySelector('.objekt-sort-select')?.value || '';
    return sort === 'mint_low' || sort === 'mint_high';
  }

  async function tick() {
    if (document.hidden || inflight) {
      schedule(document.hidden ? 30000 : 10000);
      return;
    }
    inflight = true;
    try {
      const keys = window.ObjektPage?.visibleKeys?.() || [];
      const query = new URLSearchParams();
      const openKey = document.getElementById('objektDetailFavorite')?.dataset.key;
      const watchedKeys = openKey ? [...new Set([openKey, ...keys])].slice(0, 24) : keys;
      watchedKeys.forEach((key) => query.append('key', key));
      const response = await fetch('/cosmo/objekt/pulse' + (query.toString() ? '?' + query : ''), {
        headers: { Accept: 'application/json' },
        credentials: 'same-origin',
        cache: 'no-store',
        siteFeedbackSilent: true
      });
      if (!response.ok) throw new Error();
      const data = await response.json();
      const nextSig = String(data.catalogSig || '');
      const nextMints = String(data.mintsSig || '');
      const catalogChanged = catalogSig != null && nextSig && nextSig !== catalogSig;
      const mintSortChanged = mintsSig != null && nextMints && nextMints !== mintsSig && mintSortActive();
      const reloaded = (catalogChanged || mintSortChanged) ? await window.ObjektPage?.reloadVisible?.() : true;
      if (reloaded !== false) {
        catalogSig = nextSig;
        mintsSig = nextMints;
      }
      window.ObjektIssued?.applyLive?.(data.supplies, data.chainMints === true);
      schedule(Math.max(5000, Number(data.pollMs) || 20000));
    } catch {
      schedule(20000);
    } finally {
      inflight = false;
    }
  }

  document.addEventListener('visibilitychange', () => {
    if (document.hidden) clearTimeout(timer);
    else tick();
  });
  schedule(3000);
})();
