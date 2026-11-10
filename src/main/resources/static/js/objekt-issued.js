(() => {
  let url='', generation=0, abort, timer;
  const number=value=>Number.isSafeInteger(value)&&value>=0;
  const REFRESH_MS=30_000;
  const countEl=()=>document.getElementById('objektIssuedCount');
  function close(){generation++;abort?.abort();clearTimeout(timer);url='';}
  async function load(attempt=0){
    const count=countEl();
    if(!url||!count)return;
    const version=generation;abort=new AbortController();
    try{
      const response=await fetch(url,{headers:{Accept:'application/json'},credentials:'same-origin',cache:'no-store',signal:abort.signal});
      if(!response.ok)throw Error();
      const data=await response.json();
      if(version!==generation)return;
      if(data.status==='loading'&&attempt<8){timer=setTimeout(()=>load(attempt+1),1500);return;}
      if(data.status!=='available'||!number(data.count))throw Error();
      count.textContent=data.count.toLocaleString('ko-KR')+'개';
    }catch(error){if(version===generation&&error.name!=='AbortError'){const el=countEl();if(el)el.textContent='미확인';}}
  }
  function show(value){close();const count=countEl();if(!count)return;url=value;count.textContent='확인 중';load();}
  function refresh(){if(url)load();}
  function applyLive(supplies){
    Object.entries(supplies||{}).forEach(([key,state])=>{
      const card=document.querySelector('.objekt-card[data-objekt-key="'+CSS.escape(key)+'"]');
      const label=card?.querySelector('[data-supply-count]');
      if(!label||state?.status!=='available'||!number(state.count))return;
      const prev=Number(card.dataset.supplyCount);
      if(Number.isFinite(prev)&&state.count<prev)return;
      label.textContent='발행량 '+state.count.toLocaleString('ko-KR')+'개';
      card.dataset.supplyDone='true';
      card.dataset.supplyCount=String(state.count);
      card.dataset.supplyAt=String(Date.now());
    });
  }
  let hydrationTimer,hydrating=false;
  function needsLoad(card){
    const label=card.querySelector('[data-supply-count]');
    if(!label)return false;
    const done=card.dataset.supplyDone==='true';
    const tries=Number(card.dataset.supplyTries||0);
    if(done)return false;
    return tries<8;
  }
  async function hydrate(){
    if(hydrating||document.hidden)return;
    const cards=[...document.querySelectorAll('.objekt-card[data-objekt-key]')].filter(needsLoad).slice(0,24);
    if(!cards.length){
      clearTimeout(hydrationTimer);
      hydrationTimer=setTimeout(hydrate,REFRESH_MS);
      return;
    }
    hydrating=true;
    try{
      const query=new URLSearchParams();
      cards.forEach(card=>{
        query.append('key',card.dataset.objektKey);
        if(card.dataset.supplyDone!=='true')card.dataset.supplyTries=String(Number(card.dataset.supplyTries||0)+1);
      });
      const response=await fetch('/cosmo/objekt/supplies?'+query,{headers:{Accept:'application/json'},credentials:'same-origin',cache:'no-store'});
      if(!response.ok)throw Error();const data=await response.json();
      cards.forEach(card=>{
        const state=data[card.dataset.objektKey],label=card.querySelector('[data-supply-count]');
        if(!label)return;
        if(state?.status==='available'&&number(state.count)){
          label.textContent='발행량 '+state.count.toLocaleString('ko-KR')+'개';
          label.title='시리얼 목록 확인 · '+new Date(state.observedAt).toLocaleString('ko-KR');
          card.dataset.supplyDone='true';
          card.dataset.supplyAt=String(Date.now());
          card.dataset.supplyTries='0';
        }else if(card.dataset.supplyDone!=='true'&&(state?.status==='unavailable'||Number(card.dataset.supplyTries)>=8)){
          label.textContent='발행량 미확인';
        }
      });
    }catch{
      cards.forEach(card=>{
        if(card.dataset.supplyDone!=='true'&&Number(card.dataset.supplyTries)>=8){
          const label=card.querySelector('[data-supply-count]');
          if(label)label.textContent='발행량 미확인';
        }
      });
    }finally{
      hydrating=false;clearTimeout(hydrationTimer);
      const pending=[...document.querySelectorAll('.objekt-card[data-objekt-key]')].some(card=>
        card.querySelector('[data-supply-count]')&&card.dataset.supplyDone!=='true'&&Number(card.dataset.supplyTries||0)<8);
      hydrationTimer=setTimeout(hydrate,pending?2000:REFRESH_MS);
    }
  }
  document.addEventListener('visibilitychange',()=>{if(document.hidden)clearTimeout(hydrationTimer);else hydrate();});
  window.ObjektIssued={show,close,hydrate,refresh,applyLive};hydrate();
})();
