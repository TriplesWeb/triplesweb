(() => {
  const userButton=document.querySelector('[data-user-menu-toggle]');
  const userMenu=userButton?.closest('.site-user-menu');
  function closeUser(){userMenu?.classList.remove('site-user-menu-open');userButton?.setAttribute('aria-expanded','false');}
  userButton?.addEventListener('click',()=>{const open=!userMenu.classList.contains('site-user-menu-open');userMenu.classList.toggle('site-user-menu-open',open);userButton.setAttribute('aria-expanded',String(open));});
  document.addEventListener('click',event=>{if(userMenu&&!userMenu.contains(event.target))closeUser();});
  document.addEventListener('keydown',event=>{if(event.key==='Escape'&&userMenu?.classList.contains('site-user-menu-open')){closeUser();userButton.focus();}});
  document.querySelectorAll('table').forEach(table => {
    if (table.closest('.site-table-scroll,.table-responsive,.chem-table-wrap,.fc,[role=grid]')) return;
    const wrap = document.createElement('div'); wrap.className = 'site-table-scroll';
    wrap.tabIndex = 0; wrap.setAttribute('role','region');
    wrap.setAttribute('aria-label',table.getAttribute('aria-label') || '표 · 좌우로 스크롤하여 보기');
    table.before(wrap); wrap.append(table);
  });
})();
