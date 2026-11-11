(() => {
  const button = document.querySelector('[data-scroll-top]');
  if (!button) return;
  const threshold = 320;
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const sync = () => {
    const y = window.scrollY || document.documentElement.scrollTop || 0;
    button.hidden = y < threshold;
  };
  button.addEventListener('click', () => {
    window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' });
  });
  window.addEventListener('scroll', sync, { passive: true });
  sync();
})();
