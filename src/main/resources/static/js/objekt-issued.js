(() => {
  const ranges=document.getElementById('objektIssuedRanges'),count=document.getElementById('objektIssuedCount');
  const more=document.getElementById('objektIssuedMore'),time=document.getElementById('objektIssuedTime');
  let url='',generation=0,abort,timer,nextOffset=0,busy=false;
  const number=value=>Number.isSafeInteger(value)&&value>=0;
  const format=value=>'#'+String(value).padStart(5,'0');
  function close(){generation++;abort?.abort();clearTimeout(timer);url='';busy=false;}
  async function load(append=false,attempt=0){
    if(!url||busy)return;
    const version=generation;busy=true;more.disabled=true;abort=new AbortController();
    try{
      const target=new URL(url,location.origin);target.searchParams.set('offset',String(append?nextOffset:0));
      const response=await fetch(target.pathname+target.search,{headers:{Accept:'application/json'},credentials:'same-origin',cache:'no-store',signal:abort.signal});
      if(!response.ok)throw Error();const data=await response.json();if(version!==generation)return;
      if(data.status==='loading'&&attempt<8){busy=false;timer=setTimeout(()=>load(append,attempt+1),1500);return;}
      if(data.status!=='available'||!number(data.count)||!Array.isArray(data.ranges)||!number(data.nextOffset))throw Error();
      const nodes=data.ranges.map(range=>{if(!number(range.first)||range.first<1||!number(range.last)||range.last<range.first)throw Error();const node=document.createElement('span');node.textContent=range.first===range.last?format(range.first):`${format(range.first)}–${format(range.last)}`;return node;});
      if(!append)ranges.replaceChildren();nodes.forEach(node=>ranges.append(node));
      if(!data.count)ranges.textContent='확인된 발행 시리얼이 없습니다.';
      count.textContent=data.count.toLocaleString('ko-KR')+'개';
      time.textContent=(data.stale?'마지막 확인 · ':'시리얼 목록 확인 · ')+new Date(data.observedAt).toLocaleString('ko-KR');
      nextOffset=data.nextOffset;more.hidden=!data.hasMore;
      if(!append&&data.ranges.length){const input=document.querySelector('[data-serial-lookup] input[name="serial"]');if(input&&input.value==='1'&&document.activeElement!==input)input.value=String(data.ranges[0].first);}
    }catch(error){if(version===generation&&error.name!=='AbortError'){if(!append){ranges.textContent='발행 시리얼을 확인하지 못했습니다.';count.textContent='미확인';}time.textContent='잠시 후 다시 열어 확인해 주세요.';}}
    finally{if(version===generation){busy=false;more.disabled=false;}}
  }
  function show(value){close();if(!ranges)return;url=value;ranges.textContent='실제 발행된 시리얼 목록을 확인하고 있습니다.';count.textContent='';time.textContent='';more.hidden=true;load();}
  more?.addEventListener('click',()=>load(true));

  let hydrationTimer,hydrating=false;
  async function hydrate(){
    if(hydrating||document.hidden)return;
    const cards=[...document.querySelectorAll('.objekt-card[data-objekt-key]')].filter(card=>card.querySelector('[data-supply-count]')&&card.dataset.supplyDone!=='true'&&Number(card.dataset.supplyTries||0)<8).slice(0,24);
    if(!cards.length)return;hydrating=true;
    try{
      const query=new URLSearchParams();cards.forEach(card=>{query.append('key',card.dataset.objektKey);card.dataset.supplyTries=String(Number(card.dataset.supplyTries||0)+1);});
      const response=await fetch('/cosmo/objekt/supplies?'+query,{headers:{Accept:'application/json'},credentials:'same-origin',cache:'no-store'});
      if(!response.ok)throw Error();const data=await response.json();
      cards.forEach(card=>{const state=data[card.dataset.objektKey],label=card.querySelector('[data-supply-count]');if(state?.status==='available'&&number(state.count)){label.textContent='발행량 '+state.count.toLocaleString('ko-KR')+'개';label.title='시리얼 목록 확인 · '+new Date(state.observedAt).toLocaleString('ko-KR');card.dataset.supplyDone='true';}else if(state?.status==='unavailable'||Number(card.dataset.supplyTries)>=8)label.textContent='발행량 미확인';});
    }catch{cards.forEach(card=>{if(Number(card.dataset.supplyTries)>=8)card.querySelector('[data-supply-count]').textContent='발행량 미확인';});}
    finally{hydrating=false;clearTimeout(hydrationTimer);if(cards.some(card=>card.dataset.supplyDone!=='true'&&Number(card.dataset.supplyTries)<8))hydrationTimer=setTimeout(hydrate,2000);}
  }
  document.addEventListener('visibilitychange',()=>{if(document.hidden)clearTimeout(hydrationTimer);else hydrate();});
  window.ObjektIssued={show,close,hydrate};hydrate();
})();
