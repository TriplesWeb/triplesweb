(() => {
  const KEY = 'site-theme';
  const root = document.documentElement;

  function current() {
    return root.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
  }

  function apply(theme) {
    const next = theme === 'light' ? 'light' : 'dark';
    root.setAttribute('data-theme', next);
    root.classList.toggle('dark', next !== 'light');
    root.classList.toggle('theme-light', next === 'light');
    try { localStorage.setItem(KEY, next); } catch {}
    document.querySelectorAll('#site-theme-toggle').forEach((button) => {
      button.setAttribute('aria-pressed', next === 'light' ? 'true' : 'false');
      button.setAttribute('aria-label', next === 'light' ? '다크 모드로 전환' : '라이트 모드로 전환');
    });
  }

  document.addEventListener('click', (event) => {
    const button = event.target.closest('#site-theme-toggle');
    if (!button) return;
    apply(current() === 'light' ? 'dark' : 'light');
  });

  apply(current());
})();
