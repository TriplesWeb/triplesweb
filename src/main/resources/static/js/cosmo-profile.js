(() => {
  function applyCompletedFilter() {
    const toggle = document.querySelector('[data-hide-complete]');
    const label = document.querySelector('[data-hide-complete-label]');
    if (label) label.hidden = false;
    document.querySelectorAll('[data-checklist-season]').forEach(season => {
      season.hidden = !!toggle?.checked && season.dataset.complete === 'true';
    });
  }
  document.addEventListener('change', event => {
    if (event.target.matches('[data-hide-complete]')) applyCompletedFilter();
  });
  applyCompletedFilter();
  const main = document.querySelector('[data-profile-version]');
  const button = document.querySelector('[data-profile-refresh]');
  const status = document.querySelector('[data-profile-refresh-status]');
  if (!main?.dataset.profileVersion || !button || !status) return;
  let timer, controller, busy = false;
  const schedule = delay => {
    clearTimeout(timer);
    if (!document.hidden) timer = setTimeout(() => refresh(), delay);
  };
  async function refresh() {
    if (busy || document.hidden) return;
    busy = true; button.disabled = true; button.setAttribute('aria-busy', 'true');
    controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 25000);
    let delay = 30000;
    try {
      const response = await fetch(location.pathname + location.search, {
        headers: { Accept: 'text/html' }, credentials: 'same-origin', cache: 'no-store', signal: controller.signal
      });
      if (!response.ok) throw Error();
      const next = new DOMParser().parseFromString(await response.text(), 'text/html');
      const nextMain = next.querySelector('[data-profile-version]');
      if (!nextMain?.dataset.profileVersion || next.querySelector('.cosmo-profile [role="alert"]')) throw Error();
      let updated = false;
      if (nextMain.dataset.profileVersion !== main.dataset.profileVersion) {
        // Restore the focused summary/card after replacing only the changed regions.
        const regions = [...main.querySelectorAll('[data-profile-live]')];
        const focused = document.activeElement;
        const hadFocus = regions.some(region => region.contains(focused));
        const focusedFold = focused.matches('summary') ? focused.parentElement?.dataset.foldKey : null;
        const focusedHref = focused.closest('.profile-checklist-card')?.getAttribute('href');
        const focusedToggle = focused.matches('[data-hide-complete]');
        const open = new Set([...main.querySelectorAll('details[open][data-fold-key]')].map(node => node.dataset.foldKey));
        const hideComplete = !!main.querySelector('[data-hide-complete]')?.checked;
        const replacements = regions.map(region => [region, nextMain.querySelector(`[data-profile-live="${region.dataset.profileLive}"]`)]);
        if (replacements.some(([, replacement]) => !replacement)) throw Error();
        replacements.forEach(([region, replacement]) => region.replaceChildren(...replacement.childNodes));
        main.querySelectorAll('details[data-fold-key]').forEach(node => node.open = open.has(node.dataset.foldKey));
        const toggle = main.querySelector('[data-hide-complete]');
        if (toggle) toggle.checked = hideComplete;
        applyCompletedFilter();
        if (hadFocus) {
          let target = focusedToggle ? toggle : focusedFold
            ? [...main.querySelectorAll('details[data-fold-key]')].find(node => node.dataset.foldKey === focusedFold)?.querySelector('summary')
            : focusedHref ? [...main.querySelectorAll('.profile-checklist-card')].find(node => node.getAttribute('href') === focusedHref) : null;
          if (!target) { target = main.querySelector('#checklist-heading'); if (target) target.tabIndex = -1; }
          target?.focus({preventScroll:true});
        }
        main.dataset.profileVersion = nextMain.dataset.profileVersion; updated = true;
      }
      status.textContent = (updated ? '보유 변경 반영' : '보유 목록 확인') + ' · ' + new Date().toLocaleTimeString('ko-KR', {hour:'2-digit', minute:'2-digit'});
    } catch (error) {
      if (!document.hidden) status.textContent = '갱신 지연 · 기존 목록을 유지하고 다시 확인합니다';
      delay = 60000;
    } finally {
      clearTimeout(timeout); busy = false; button.disabled = false; button.removeAttribute('aria-busy'); schedule(delay);
    }
  }
  button.addEventListener('click', refresh);
  document.addEventListener('visibilitychange', () => {
    clearTimeout(timer);
    if (document.hidden) controller?.abort(); else refresh();
  });
  window.addEventListener('pagehide', () => {clearTimeout(timer); controller?.abort();});
  schedule(30000);
})();
