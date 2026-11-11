(() => {
  if (!document.getElementById('objekt-card-grid')) return;
  let timer = 0, catalogSig = null, lastForce = 0, inflight = false;

  function schedule(ms) {
    clearTimeout(timer);
    const wait = Number(ms);
    timer = setTimeout(tick, Number.isFinite(wait) ? Math.max(1000, wait) : 20000);
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
      const forceEvery = Math.max(60000, Number(data.forceRefreshMs) || 600000);
      if (catalogSig == null) {
        catalogSig = nextSig;
        lastForce = Date.now();
      } else if (nextSig && nextSig !== catalogSig) {
        catalogSig = nextSig;
        lastForce = Date.now();
        await window.ObjektPage?.reloadVisible?.();
      } else if (data.dropWindow && Date.now() - lastForce >= forceEvery) {
        lastForce = Date.now();
        await window.ObjektPage?.reloadVisible?.();
      }
      window.ObjektIssued?.applyLive?.(data.supplies);
      window.ObjektIssued?.refresh?.();
      window.ObjektSerialLookup?.refresh?.();
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
