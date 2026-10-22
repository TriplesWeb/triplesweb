(() => {
  const rows = [...document.querySelectorAll(".gravity-rank-row")];
  const amount = row => { const value = Number(row.dataset.como); return Number.isFinite(value) && value >= 0 ? value : null; };
  const maximum = Math.max(0, ...rows.map(amount).filter(value => value !== null));
  const format = new Intl.NumberFormat("ko-KR");
  rows.forEach(row => {
    const value = amount(row);
    if (value === null) return;
    const width = maximum > 0 ? value / maximum * 100 : 0;
    row.querySelector(".gravity-rank-fill").style.width = `${width}%`;
    row.querySelector("strong").textContent = `${format.format(value)} COMO`;
    const track = row.querySelector(".gravity-rank-track");
    track.setAttribute("role", "meter");
    track.setAttribute("aria-label", `${row.querySelector("span").textContent} COMO 소비량`);
    track.setAttribute("aria-valuemin", "0");
    track.setAttribute("aria-valuemax", String(maximum || 1));
    track.setAttribute("aria-valuenow", String(value));
    track.setAttribute("aria-valuetext", `${format.format(value)} COMO`);
  });
})();
