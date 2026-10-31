(() => {
  document.querySelectorAll('table').forEach(table => {
    if (table.closest('.site-table-scroll,.table-responsive,.chem-table-wrap,.fc,[role=grid]')) return;
    const wrap = document.createElement('div'); wrap.className = 'site-table-scroll';
    wrap.tabIndex = 0; wrap.setAttribute('role','region');
    wrap.setAttribute('aria-label',table.getAttribute('aria-label') || '표 · 좌우로 스크롤하여 보기');
    table.before(wrap); wrap.append(table);
  });
})();
