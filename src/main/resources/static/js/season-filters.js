/* 시즌 컨트롤을 열면 색상 버튼만 표시한다. 기존 폼 값과 change 계약은 유지한다. */
(() => {
  const sources = new WeakMap(), controllers = new Set();
  const selectSelector = 'select[data-season],select[name="season"],#objektFilterSeason,select[aria-label*="시즌"]';
  const checkSelector = 'input.season-checkbox';
  const palette = {atom:'#FFDD00',binary:'#00FF00',cream:'#FF7477',divine:'#B400FF',ever:'#33ECFD'};
  const second={atom:'#FFD13E',binary:'#83FF00',cream:'#FF8389'};
  const aliases={aa:'atom02',bb:'binary02',cc:'cream02'};
  let sequence = 0;
  function colorFor(label) {
    let key=String(label).toLowerCase().replace(/[\s_-]/g,'');key=aliases[key]||key;
    const match=key.match(/^([a-z]+)(\d+)$/);
    const base=match&&palette[match[1]];
    if(!base)return '#A4B7B4';
    if(Number(match[2])===2&&second[match[1]])return second[match[1]];
    const amount=Math.min(.1,Math.max(0,Number(match[2])-1)*.035);
    if(!amount)return base;
    return '#'+base.slice(1).match(/../g).map(hex=>Math.round(parseInt(hex,16)*(1-amount)+255*amount).toString(16).padStart(2,'0')).join('').toUpperCase();
  }
  function enhance(source, checkbox) {
    if(sources.has(source))return;
    // 게시글 작성/수정 오브젝트 픽커는 멤버·클래스와 같은 네이티브 select를 유지한다.
    if(source.closest('.objekt-picker-filter'))return;
    const control=source.closest('.objekt-filter,.objekt-picker-filter,label')||source.parentElement;
    if(!control)return;
    control.classList.add("season-control");
    let menu, native;
    if(checkbox){
      menu=source;
      const toggle=control.querySelector('button');const usesHidden=menu.hasAttribute('hidden');
      source.__seasonClose=()=>{if(usesHidden)menu.hidden=true;else menu.classList.add('hidden');toggle?.setAttribute('aria-expanded','false');};
      menu.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();event.stopPropagation();source.__seasonClose();toggle?.focus();}});
      native=document.createElement('div');native.className='season-native-options';native.hidden=true;
      while(menu.firstChild)native.append(menu.firstChild);
      menu.append(native);
    }else{
      // Prevent the generic dropdown enhancer from creating a second control.
      source.removeAttribute('data-objekt-dropdown');source.hidden=true;native=source;
      control.querySelectorAll('[id^="objekt-select-menu-"]').forEach(node=>node.remove());
      control.querySelectorAll('button[aria-haspopup="listbox"]').forEach(node=>node.remove());
      const toggle=document.createElement('button');toggle.type='button';toggle.className='objekt-filter-control season-toggle';
      toggle.setAttribute('aria-label',source.getAttribute('aria-label')||'시즌 선택');
      menu=document.createElement('div');menu.id='season-choice-'+(++sequence);menu.className='objekt-dropdown-menu';menu.hidden=true;
      toggle.setAttribute('aria-expanded','false');toggle.setAttribute('aria-controls',menu.id);
      const text=document.createElement('span'),caret=document.createElement('span');caret.className='objekt-dropdown-caret';caret.setAttribute('aria-hidden','true');toggle.append(text,caret);
      const close=()=>{menu.hidden=true;toggle.setAttribute('aria-expanded','false');};
      toggle.addEventListener('click',()=>{const open=menu.hidden;controllers.forEach(c=>c.close?.());menu.hidden=!open;toggle.setAttribute('aria-expanded',String(open));});
      document.addEventListener('click',event=>{if(!control.contains(event.target))close();});
      menu.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();event.stopPropagation();close();toggle.focus();}});
      control.append(toggle,menu);
      source.__seasonToggleText=text;source.__seasonClose=close;
    }
    menu.classList.add('season-choice-panel');
    control.addEventListener('click',()=>queueMicrotask(()=>{
      if(menu.hidden||menu.classList.contains('hidden'))return;
      menu.style.left='0';menu.style.right='auto';
      if(menu.getBoundingClientRect().right>innerWidth-12){menu.style.left='auto';menu.style.right='0';}
      if(menu.getBoundingClientRect().left<12){menu.style.right='auto';menu.style.left=(12-control.getBoundingClientRect().left)+'px';}
    }));
    const row=document.createElement('div');row.className='season-quick-filters';row.setAttribute('role','group');row.setAttribute('aria-label','시즌 선택');
    const list=document.createElement('div');list.className='season-chip-list';row.append(list);menu.append(row);
    const options=()=>checkbox?[...native.querySelectorAll(checkSelector)].filter(input=>input.value!=='all').map(input=>({value:input.value,label:input.closest('label')?.querySelector('.objekt-choice-text')?.textContent.trim()||input.value,disabled:input.disabled}))
      :[...source.options].filter(option=>option.value).map(option=>({value:option.value,label:option.textContent.trim(),disabled:option.disabled}));
    const selected=()=>checkbox?[...native.querySelectorAll(checkSelector)].filter(input=>input.value!=='all'&&input.checked).map(input=>input.value):(source.value?[source.value]:[]);
    function sync(){
      const chosen=selected();list.querySelectorAll('button').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.value?chosen.includes(button.dataset.value):chosen.length===0)));
      if(source.__seasonToggleText)source.__seasonToggleText.textContent=source.selectedOptions[0]?.textContent||'전체';
    }
    function choose(value){
      if(checkbox){
        const boxes=[...native.querySelectorAll(checkSelector)],target=boxes.find(input=>input.value===(value||'all'));
        if(target){target.checked=value?!target.checked:true;target.dispatchEvent(new Event('change',{bubbles:true}));}
        else if(!value){boxes.forEach(input=>input.checked=false);boxes[0]?.dispatchEvent(new Event('change',{bubbles:true}));}
      }else{source.value=value;source.dispatchEvent(new Event('change',{bubbles:true}));source.__seasonClose();}
      sync();
    }
    function render(){
      list.replaceChildren();for(const choice of [{value:'',label:'전체'},...options()]){
        const button=document.createElement('button');button.type='button';button.className='season-chip';button.textContent=choice.label;
        button.dataset.value=choice.value;button.setAttribute('aria-label','시즌 '+choice.label);button.disabled=!!choice.disabled;
        button.style.setProperty('--season-color',colorFor(choice.label));button.addEventListener('click',()=>choose(choice.value));list.append(button);
      }sync();
    }
    // Observe only the original options, never the generated button subtree.
    new MutationObserver(render).observe(native,{childList:true,subtree:true,attributes:true,attributeFilter:['selected','disabled']});
    source.addEventListener('change',sync);sources.set(source,true);controllers.add({source,sync,close:source.__seasonClose});render();
  }
  function init(root=document){
    const nodes=selector=>[...(root.matches?.(selector)?[root]:[]),...root.querySelectorAll(selector)];
    nodes(selectSelector).forEach(select=>enhance(select,false));
    nodes(checkSelector).forEach(input=>{const menu=input.closest('#season-menu,#collection-season-menu,[data-season-options],.objekt-dropdown-menu');if(menu)enhance(menu,true);});
  }
  function syncAll(){controllers.forEach(c=>{if(c.source.isConnected)c.sync();else controllers.delete(c);});}
  function start(){init();document.addEventListener('reset',()=>requestAnimationFrame(syncAll));document.addEventListener('click',event=>{const button=event.target.closest('button');if(button&&(button.matches('.objekt-reset-button,[data-reset],#gvReset')||/reset|clear/i.test(button.id)||button.textContent.includes('초기화')))queueMicrotask(syncAll);});}
  window.SeasonChips={init,colorFor};
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start);else start();
})();
