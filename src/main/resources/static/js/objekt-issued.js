(() => {
  let url='', generation=0, abort, timer;
  const number=value=>Number.isSafeInteger(value)&&value>=0;
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
        const count=countEl();
        if(count)count.textContent=state.count.toLocaleString('ko-KR')+'개';
      }
    });
  }
  function hydrate(){}
  window.ObjektIssued={show,close,hydrate,refresh,applyLive};
})();
