(() => {
  const widgets = [];
  document.querySelectorAll('select[data-objekt-dropdown]').forEach((select, index) => {
    const root = select.closest('.objekt-filter');
    if (!root) return;
    const button = document.createElement('button'); button.type = 'button'; button.className = 'objekt-filter-control';
    button.setAttribute('aria-label', select.getAttribute('aria-label') || '필터');
    button.setAttribute('aria-haspopup', 'listbox'); button.setAttribute('aria-expanded', 'false');
    const label = document.createElement('span'), caret = document.createElement('span');
    label.id = `objekt-select-value-${index}`; button.setAttribute('aria-describedby',label.id);
    caret.className = 'objekt-dropdown-caret'; caret.setAttribute('aria-hidden','true'); button.append(label, caret);
    const menu = document.createElement('div'); menu.className = 'objekt-dropdown-menu'; menu.hidden = true;
    menu.id = `objekt-select-menu-${index}`; menu.setAttribute('role','listbox'); menu.setAttribute('aria-label',select.getAttribute('aria-label') || '필터');
    button.setAttribute('aria-controls',menu.id);
    function close() {menu.hidden = true; button.setAttribute('aria-expanded','false');}
    const widget = {close,root}; widgets.push(widget);
    function sync() {
      menu.replaceChildren();
      Array.from(select.options).forEach(option => {
        const item = document.createElement('button'); item.type = 'button'; item.className = 'objekt-choice'; item.setAttribute('role','option');
        const selected = option.value === select.value; item.classList.toggle('is-selected',selected); item.setAttribute('aria-selected',String(selected));
        const mark = document.createElement('span'); mark.className='objekt-choice-mark'; mark.setAttribute('aria-hidden','true');
        const text = document.createElement('span'); text.className='objekt-choice-text'; text.textContent=option.textContent; item.append(mark,text);
        item.addEventListener('click',()=>{select.value=option.value; select.dispatchEvent(new Event('change',{bubbles:true})); close(); button.focus();});
        menu.append(item);
      });
      label.textContent=select.selectedOptions[0]?.textContent || '전체'; root.classList.toggle('is-filtered',Boolean(select.value));
    }
    function open(focus) {
      widgets.forEach(other=>other.close()); sync(); menu.hidden=false; button.setAttribute('aria-expanded','true');
      menu.style.left='0'; menu.style.right='auto'; if(menu.getBoundingClientRect().right>innerWidth-8){menu.style.left='auto';menu.style.right='0';}
      if(focus) menu.querySelector('[aria-selected="true"]')?.focus();
    }
    button.addEventListener('click',()=>menu.hidden?open(false):close());
    button.addEventListener('keydown',event=>{if(event.key==='ArrowDown'){event.preventDefault();open(true);}});
    menu.addEventListener('keydown',event=>{
      if(event.key==='Escape'){event.preventDefault();close();button.focus();return;}
      const items=Array.from(menu.querySelectorAll('[role=option]')), at=items.indexOf(document.activeElement);
      let next=null;
      if(event.key==='ArrowDown')next=(at+1)%items.length;
      if(event.key==='ArrowUp')next=(at-1+items.length)%items.length;
      if(event.key==='Home')next=0;
      if(event.key==='End')next=items.length-1;
      if(next!==null && items.length){event.preventDefault();items[next].focus();}
    });
    root.append(button,menu); select.hidden=true; sync(); select.addEventListener('change',sync);
    new MutationObserver(sync).observe(select,{childList:true,subtree:true});
    root.addEventListener('focusout',event=>{if(!root.contains(event.relatedTarget))close();});
  });
  document.addEventListener('click',event=>widgets.forEach(widget=>{if(!widget.root.contains(event.target))widget.close();}));
  document.addEventListener('keydown',event=>{if(event.key==='Escape')widgets.forEach(widget=>widget.close());});
})();
