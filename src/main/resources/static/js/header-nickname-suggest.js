(() => {
  const panel = document.querySelector('[data-cosmo-id-panel]');
  const input = panel?.querySelector('[data-cosmo-id-input]');
  const form = input?.form;
  const menu = document.getElementById('site-cosmo-id-suggest');
  if (!panel || !input || !form || !menu) return;

  let timer = 0;
  let request = null;
  let generation = 0;
  let activeIndex = -1;

  function close() {
    menu.hidden = true;
    menu.replaceChildren();
    input.setAttribute('aria-expanded', 'false');
    input.removeAttribute('aria-activedescendant');
    activeIndex = -1;
  }

  function cancel() {
    generation += 1;
    clearTimeout(timer);
    request?.abort();
    request = null;
    close();
  }

  function recentMatches(query) {
    try {
      const names = JSON.parse(localStorage.getItem('cosmo-nickname-recents') || '[]');
      return Array.isArray(names) ? names.filter((name) =>
        typeof name === 'string' && name.toLowerCase().startsWith(query.toLowerCase())) : [];
    } catch (_) {
      return [];
    }
  }

  function choose(name) {
    input.value = name;
    cancel();
    form.requestSubmit();
  }

  function render(names) {
    menu.replaceChildren();
    activeIndex = -1;
    input.removeAttribute('aria-activedescendant');
    if (!names.length) return close();
    names.forEach((name, index) => {
      const option = document.createElement('button');
      option.type = 'button';
      option.id = `site-cosmo-id-option-${index}`;
      option.className = 'site-header-nickname-option';
      option.setAttribute('role', 'option');
      option.setAttribute('aria-selected', 'false');
      option.textContent = name;
      // Keep focus in the combobox until the click chooses the option.
      option.addEventListener('mousedown', (event) => event.preventDefault());
      option.addEventListener('click', () => choose(name));
      menu.appendChild(option);
    });
    menu.hidden = false;
    input.setAttribute('aria-expanded', 'true');
  }

  function select(index) {
    const options = Array.from(menu.querySelectorAll('[role="option"]'));
    if (!options.length) return;
    activeIndex = (index + options.length) % options.length;
    options.forEach((option, i) => option.setAttribute('aria-selected', String(i === activeIndex)));
    input.setAttribute('aria-activedescendant', options[activeIndex].id);
    options[activeIndex].scrollIntoView({ block: 'nearest' });
  }

  async function refresh(query, current) {
    request = new AbortController();
    let remote = [];
    try {
      const response = await fetch('/cosmo/collection/suggest?q=' + encodeURIComponent(query), {
        headers: { Accept: 'application/json' }, signal: request.signal
      });
      if (response.ok) {
        const body = await response.json();
        if (Array.isArray(body.items)) remote = body.items;
      }
    } catch (error) {
      if (error?.name === 'AbortError') return;
    }
    if (current !== generation || panel.hidden || document.activeElement !== input || input.value.trim() !== query) return;
    const seen = new Set();
    const names = [];
    for (const item of [...remote, ...recentMatches(query)]) {
      if (typeof item !== 'string') continue;
      const name = item.trim();
      const key = name.toLowerCase();
      if (!name || seen.has(key)) continue;
      seen.add(key);
      names.push(name);
      if (names.length === 8) break;
    }
    render(names);
  }

  function schedule() {
    cancel();
    const query = input.value.trim();
    if (query.length < 3 || query.length > 40 || panel.hidden) return;
    const current = generation;
    timer = setTimeout(() => refresh(query, current), 180);
  }

  input.addEventListener('input', schedule);
  input.addEventListener('focus', schedule);
  input.addEventListener('keydown', (event) => {
    if (menu.hidden) return;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      select(activeIndex < 0 && event.key === 'ArrowUp' ? -1 : activeIndex + (event.key === 'ArrowDown' ? 1 : -1));
    } else if (event.key === 'Enter' && activeIndex >= 0) {
      event.preventDefault();
      choose(menu.children[activeIndex].textContent);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      cancel();
    }
  });
  form.addEventListener('submit', cancel);
  document.addEventListener('pointerdown', (event) => {
    if (!panel.contains(event.target)) cancel();
  });
  new MutationObserver(() => { if (panel.hidden) cancel(); })
    .observe(panel, { attributes: true, attributeFilter: ['hidden'] });
})();
