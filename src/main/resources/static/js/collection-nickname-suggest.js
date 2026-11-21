(() => {
  const form = document.querySelector('.collection-nickname-form');
  const input = document.getElementById('collection-nickname-input');
  const menu = document.getElementById('collection-nickname-suggest');
  if (!form || !input || !menu) return;

  const MIN_QUERY = 3;

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

  async function refresh() {
    const query = input.value.trim();
    const requestId = ++seq;
    if (query.length < MIN_QUERY) {
      abort?.abort();
      closeMenu();
      return;
    }
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
    render(remote.map((item) => String(item || '').trim()).filter(Boolean).slice(0, 8));
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
