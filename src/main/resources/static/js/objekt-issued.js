(() => {
  const count=document.getElementById('objektIssuedCount');
  let url='', generation=0, abort, timer;
  const number=value=>Number.isSafeInteger(value)&&value>=0;
  function close(){generation++;abort?.abort();clearTimeout(timer);url='';}
  async function load(attempt=0){
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
    }catch(error){if(version===generation&&error.name!=='AbortError')count.textContent='미확인';}
  }
  function show(value){close();if(!count)return;url=value;count.textContent='확인 중';load();}
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
    finally{hydrating=false;clearTimeout(hydrationTimer);if([...document.querySelectorAll('.objekt-card[data-objekt-key]')].some(card=>card.querySelector('[data-supply-count]')&&card.dataset.supplyDone!=='true'&&Number(card.dataset.supplyTries||0)<8))hydrationTimer=setTimeout(hydrate,2000);}
  }
  document.addEventListener('visibilitychange',()=>{if(document.hidden)clearTimeout(hydrationTimer);else hydrate();});
  window.ObjektIssued={show,close,hydrate};hydrate();
})();
