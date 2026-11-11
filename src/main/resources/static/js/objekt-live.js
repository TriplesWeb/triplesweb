(() => {
  if (!document.getElementById('objekt-card-grid')) return;
  let timer = 0, catalogSig = null, mintsSig = null, inflight = false;

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
      keys.forEach((key) => query.append('key', key));
      const response = await fetch('/cosmo/objekt/pulse' + (query.toString() ? '?' + query : ''), {
        headers: { Accept: 'application/json' },
        credentials: 'same-origin',
        cache: 'no-store'
      });
      if (!response.ok) throw new Error();
      const data = await response.json();
      const nextSig = String(data.catalogSig || '');
      const nextMints = String(data.mintsSig || '');
      if (catalogSig == null) {
        catalogSig = nextSig;
        mintsSig = nextMints;
      } else if (nextSig && nextSig !== catalogSig) {
        catalogSig = nextSig;
        mintsSig = nextMints;
        await window.ObjektPage?.reloadVisible?.();
      } else if (nextMints && nextMints !== mintsSig) {
        mintsSig = nextMints;
        if (mintSortActive()) await window.ObjektPage?.reloadVisible?.();
      }
      window.ObjektIssued?.applyLive?.(data.supplies);
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
