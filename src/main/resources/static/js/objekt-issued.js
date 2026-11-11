(() => {
  let url='', generation=0, abort, timer;
  const number=value=>Number.isSafeInteger(value)&&value>=0;
  const countEl=()=>document.getElementById('objektIssuedCount');
  function seedCount(value){
    if(typeof value==='string'&&value.trim()==='')return null;
    const count=Number(value);
    return number(count)?count:null;
  }
  function readCount(){
    const digits=String(countEl()?.textContent||'').replace(/[^\d]/g,'');
    if(!digits)return null;
    const count=Number(digits);
    return number(count)?count:null;
  }
  function paint(count){
    const el=countEl();
    if(el&&number(count)) el.textContent=count.toLocaleString('ko-KR')+'개';
  }
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
      if(readCount()!=null)return;
      if(data.status==='loading'&&attempt<8){timer=setTimeout(()=>load(attempt+1),1500);return;}
      if(data.status!=='available'||!number(data.count))throw Error();
      paint(data.count);
    }catch(error){
      if(version===generation&&error.name!=='AbortError'&&readCount()==null){
        const el=countEl();if(el)el.textContent='미확인';
      }
    }
  }
  function show(value, seed){
    close();
    const el=countEl();
    if(!el)return;
    const next=seedCount(seed);
    if(next!=null) paint(next);
    else el.textContent='확인 중';
    url=value||'';
    if(url&&readCount()==null) load();
  }
  function refresh(){if(url&&readCount()==null)load();}
  function applyLive(supplies){
    const openKey=document.getElementById('objektDetailFavorite')?.dataset.key||'';
    Object.entries(supplies||{}).forEach(([key,state])=>{
      document.querySelectorAll('.objekt-card[data-objekt-key="'+CSS.escape(key)+'"]').forEach(card=>{
        const label=card.querySelector('[data-supply-count]');
        if(!label||state?.status!=='available'||!number(state.count))return;
        const prev=Number(card.dataset.supplyCount);
        if(Number.isFinite(prev)&&state.count<prev)return;
        label.textContent='발행량 '+state.count.toLocaleString('ko-KR')+'개';
        card.dataset.supplyDone='true';
        card.dataset.supplyCount=String(state.count);
        card.dataset.supplyAt=String(Date.now());
      });
      if(openKey===key&&state?.status==='available'&&number(state.count)){
        const prev=readCount();
        if(prev==null||state.count>=prev) paint(state.count);
      }
    });
  }
  function hydrate(){}
  window.ObjektIssued={show,close,hydrate,refresh,applyLive};
})();
