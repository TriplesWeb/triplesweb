/* 기존 시즌 필터와 연동하는 작은 빠른 선택 버튼. 필터 동작은 원래 change 처리기를 사용한다. */
(() => {
  const sources = new WeakMap(), controllers = new Set();
  const selectSelector = 'select[data-season],select[name="season"],#objektFilterSeason,select[aria-label*="시즌"]';
  const checkSelector = 'input.season-checkbox';
  const familyHue = {atom:52,binary:91,cream:173,divine:285,ever:18};
  function colorFor(label) {
    const match = String(label).toLowerCase().replace(/[\s_-]/g,'').match(/^([a-z]+)(\d+)$/);
    if (match && familyHue[match[1]] !== undefined) {
      const hue = (familyHue[match[1]] + (Number(match[2])-1)*17)%360;
      return 'hsl('+hue+' 76% 62%)';
    }
    let hash=0; for(const char of String(label)) hash=(hash*31+char.charCodeAt(0))>>>0;
    return 'hsl('+(hash%360)+' 65% 70%)';
  }
  function enhance(source, checkbox) {
    if (sources.has(source)) return;
    const row=document.createElement('div'); row.className='season-quick-filters';
    row.setAttribute('role','group'); row.setAttribute('aria-label','시즌 빠른 선택');
    const list=document.createElement('div');list.className='season-chip-list';row.append(list);
    const control=source.closest('.objekt-filter,.objekt-picker-filter,label') || source;
    const bar=control.closest('.objekt-filter-bar,.collection-filter-bar,.tr-facets,.objekt-picker-filter-bar');
    const stack=document.createElement('div');stack.className='season-filter-stack';
    control.before(stack);stack.append(control,row);
    bar?.classList.add('has-season-stack');
    const options=()=>checkbox
      ? [...source.querySelectorAll(checkSelector)].filter(input=>input.value!=='all').map(input=>({
          value:input.value,label:input.closest('label')?.querySelector('.objekt-choice-text')?.textContent.trim()||input.value,input,disabled:input.disabled}))
      : [...source.options].filter(option=>option.value).map(option=>({value:option.value,label:option.textContent.trim(),disabled:option.disabled}));
    const selected=()=>checkbox ? [...source.querySelectorAll(checkSelector)].filter(input=>input.value!=='all'&&input.checked).map(input=>input.value)
      : (source.value?[source.value]:[]);
    function sync() {
      const chosen=selected();
      list.querySelectorAll('button').forEach(button=>{
        const active=button.dataset.value?chosen.includes(button.dataset.value):chosen.length===0;
        button.setAttribute('aria-pressed',String(active));
      });
    }
    function choose(value) {
      if(checkbox) {
        const boxes=[...source.querySelectorAll(checkSelector)];
        const target=value?boxes.find(input=>input.value===value):boxes.find(input=>input.value==='all');
        if(target) {target.checked=value?!target.checked:true;target.dispatchEvent(new Event('change',{bubbles:true}));}
        else if(!value) {
          boxes.forEach(input=>{input.checked=false;});
          boxes[0]?.dispatchEvent(new Event('change',{bubbles:true}));
        }
      } else {
        source.value=value;source.dispatchEvent(new Event('change',{bubbles:true}));
      }
      sync();
    }
    function render() {
      const values=options();row.hidden=values.length===0;list.replaceChildren();
      for(const choice of [{value:'',label:'전체'},...values]) {
        const button=document.createElement('button');button.type='button';button.className='season-chip';
        button.textContent=choice.label;button.setAttribute('aria-label','시즌 '+choice.label);button.dataset.value=choice.value;button.disabled=!!choice.disabled;
        button.style.setProperty('--season-color',choice.value?colorFor(choice.label):'#a4b7b4');
        button.addEventListener('click',()=>choose(choice.value));list.appendChild(button);
      }
      sync();
    }
    source.addEventListener('change',sync);
    new MutationObserver(render).observe(source,{childList:true,subtree:true,attributes:true,attributeFilter:['checked','selected','disabled']});
    sources.set(source,true);controllers.add({source,sync});render();
  }
  function init(root=document) {
    const nodes=(selector)=>[...(root.matches?.(selector)?[root]:[]),...root.querySelectorAll(selector)];
    nodes(selectSelector).forEach(select=>enhance(select,false));
    nodes(checkSelector).forEach(input=>{
      const group=input.closest('#season-menu,#collection-season-menu,[data-season-options],.objekt-dropdown-menu');
      if(group) enhance(group,true);
    });
  }
  function syncAll(){controllers.forEach(controller=>{if(controller.source.isConnected)controller.sync();else controllers.delete(controller);});}
  function start(){
    init();
    document.addEventListener('reset',()=>requestAnimationFrame(syncAll));
    document.addEventListener('click',event=>{
      const button=event.target.closest('button');
      if(button&&(button.matches('.objekt-reset-button,[data-reset],#gvReset')||/reset|clear/i.test(button.id)||button.textContent.includes('초기화')))queueMicrotask(syncAll);
    });
    // 선택지 변경은 각 시즌 컨트롤만 관찰한다. 카드/달력 전체 DOM은 감시하지 않는다.

  }
  window.SeasonChips={init,colorFor};
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start);else start();
})();
