(() => {
  const toggle = document.querySelector('[data-hide-complete]');
  const label = document.querySelector('[data-hide-complete-label]');
  if (!toggle || !label) return;
  label.hidden = false;
  toggle.addEventListener('change', () => {
    document.querySelectorAll('[data-checklist-season]').forEach(season => {
      season.hidden = toggle.checked && season.dataset.complete === 'true';
    });
  });
})();
