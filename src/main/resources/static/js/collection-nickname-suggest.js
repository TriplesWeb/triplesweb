(() => {
  const form = document.querySelector('.collection-nickname-form');
  const input = document.getElementById('collection-nickname-input');
  const menu = document.getElementById('collection-nickname-suggest');
  if (!form || !input || !menu) return;

  const STORAGE_KEY = 'cosmo-nickname-recents';
  const MIN_QUERY = 3;
  const saved = (form.dataset.savedNickname || '').trim();

  function loadRecents() {
    try {
      const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
      return Array.isArray(raw) ? raw.map(String).filter(Boolean) : [];
    } catch {
      return [];
    }
  }

  function saveRecent(nickname) {
    const value = (nickname || '').trim();
    if (!value) return;
    const next = [value, ...loadRecents().filter((item) => item.toLowerCase() !== value.toLowerCase())].slice(0, 8);
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(next)); } catch {}
  }

  if (saved) saveRecent(saved);

  function uniqueNames(lists, prefixOnly) {
    const needle = (prefixOnly || '').trim().toLowerCase();
    const seen = new Set();
    const out = [];
    for (const list of lists) {
      for (const item of list) {
        const name = String(item || '').trim();
        if (!name) continue;
        const key = name.toLowerCase();
        if (seen.has(key)) continue;
        if (needle && !key.startsWith(needle)) continue;
        seen.add(key);
        out.push(name);
        if (out.length >= 8) return out;
      }
    }
    return out;
  }

  function mergeNames(preferred, extra) {
    const seen = new Set();
    const out = [];
    for (const item of [...preferred, ...extra]) {
      const name = String(item || '').trim();
      if (!name) continue;
      const key = name.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(name);
      if (out.length >= 8) return out;
    }
    return out;
  }

  function closeMenu() {
    menu.hidden = true;
    menu.classList.add('hidden');
    menu.innerHTML = '';
    input.setAttribute('aria-expanded', 'false');
  }

  function choose(name) {
    input.value = name;
    closeMenu();
    form.requestSubmit();
  }

  function render(items) {
    menu.innerHTML = '';
    if (!items.length) {
      closeMenu();
      return;
    }
    const label = document.createElement('p');
    label.className = 'collection-nickname-suggest-label';
    label.textContent = '연관닉';
    menu.appendChild(label);
    items.forEach((name) => {
      const option = document.createElement('button');
      option.type = 'button';
      option.className = 'collection-nickname-option';
      option.setAttribute('role', 'option');
      option.textContent = name;
      option.addEventListener('mousedown', (event) => {
        event.preventDefault();
        choose(name);
      });
      menu.appendChild(option);
    });
    menu.hidden = false;
    menu.classList.remove('hidden');
    input.setAttribute('aria-expanded', 'true');
  }

  let timer = 0;
  let abort = null;
  let seq = 0;
  function recentsFor(query) {
    const needle = (query || '').trim();
    if (!needle) return loadRecents().slice(0, 8);
    return uniqueNames([loadRecents()], needle);
  }

  async function refresh() {
    const query = input.value.trim();
    const requestId = ++seq;
    const recents = recentsFor(query);
    if (query.length < MIN_QUERY) {
      abort?.abort();
      if (recents.length && document.activeElement === input) render(recents);
      else closeMenu();
      return;
    }
    if (recents.length) render(recents);
    abort?.abort();
    abort = new AbortController();
    let remote = [];
    try {
      const response = await fetch('/cosmo/collection/suggest?q=' + encodeURIComponent(query), {
        headers: { Accept: 'application/json' },
        signal: abort.signal
      });
      if (response.ok) {
        const body = await response.json();
        remote = Array.isArray(body.items) ? body.items : [];
      }
    } catch (error) {
      if (error && error.name === 'AbortError') return;
    }
    if (requestId !== seq || input.value.trim() !== query) return;
    render(mergeNames(remote, recents));
  }

  input.addEventListener('input', () => {
    window.clearTimeout(timer);
    abort?.abort();
    timer = window.setTimeout(refresh, 180);
  });
  input.addEventListener('focus', refresh);
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeMenu();
  });
  document.addEventListener('pointerdown', (event) => {
    if (!form.contains(event.target)) closeMenu();
  });
  document.addEventListener('click', (event) => {
    if (!form.contains(event.target)) closeMenu();
  });
})();
