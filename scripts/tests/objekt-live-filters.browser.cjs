const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'../..');
const html=`<!doctype html><html><head><meta charset="utf-8">
<style>.hidden{display:none}#objekt-card-grid{min-height:40px}</style>
</head><body>
<form id="objekt-search-form" action="/cosmo/objekt" method="get">
<input type="hidden" name="page" value="0">
<input type="hidden" name="group" id="objekt-member-group" value="all">
<div id="member-dropdown">
  <button type="button" id="objekt-member-toggle" aria-expanded="false">멤버 <span id="member-label">전체 오브젝트</span></button>
  <button type="button" data-objekt-group="all" aria-pressed="true">전체 / 멤버</button>
  <button type="button" data-objekt-group="other" aria-pressed="false">기타 옵젝</button>
  <div class="objekt-member-checks">
    <input type="checkbox" name="member" value="윤서연" class="member-checkbox" data-mpp-value="윤서연">
  </div>
  <div class="member-portrait-picker" id="objekt-member-picker">
    <button type="button" class="mpp-item" data-value="윤서연">윤서연</button>
  </div>
  <p id="objekt-other-notice" hidden>기타</p>
</div>
<div id="season-dropdown">
  <span id="season-label">전체</span>
  <div id="season-menu" class="hidden">
    <label><input type="checkbox" value="all" class="season-checkbox" checked><span class="objekt-choice-text">전체 시즌</span></label>
    <label><input type="checkbox" name="season" value="Atom01" class="season-checkbox"><span class="objekt-choice-text">Atom01</span></label>
  </div>
</div>
<div class="objekt-filter">
  <input class="objekt-kind-select" name="kind" value="all">
  <button type="button" id="objekt-kind-toggle"></button>
  <div id="kind-menu" class="hidden">
    <button type="button" class="objekt-kind-option" data-value="all">전체</button>
    <button type="button" class="objekt-kind-option" data-value="101Z">101Z</button>
  </div>
  <p id="kind-empty" hidden></p>
</div>
<div id="class-dropdown">
  <span id="class-label">전체</span>
  <div id="class-menu" class="hidden">
    <label><input type="checkbox" value="all" class="class-checkbox" checked><span class="objekt-choice-text">전체 클래스</span></label>
    <label><input type="checkbox" name="class" value="Special" class="class-checkbox"><span class="objekt-choice-text">Special</span></label>
  </div>
</div>
<div id="sort-dropdown">
  <button type="button" id="objekt-sort-toggle" hidden><span id="sort-label">최신순</span></button>
  <label id="sort-native-control"><select name="sort" class="objekt-sort-select"><option value="latest">최신순</option><option value="oldest">오래된순</option></select></label>
  <div id="sort-menu" class="hidden"></div>
</div>
<input type="search" name="search" value="">
<button type="submit" id="objekt-search-submit">검색</button>
<button type="button" id="objekt-reset">초기화</button>
</form>
<p id="objekt-result-summary" data-result-count="1" data-all-count="8" data-loaded="1">검색 결과 <strong>1</strong>개 <span>(전체 <span id="objekt-all-count">8</span>개 중 · <span id="objekt-loaded-count">1</span>개 표시)</span></p>
<div id="objekt-empty" class="hidden">없음</div>
<div id="objekt-card-grid"><article class="objekt-card objekt-item" data-objekt-key="first">초기카드</article></div>
<div id="objekt-scroll-sentinel" data-has-more="false" data-next-page="1"><p id="objekt-scroll-status"></p><div id="objekt-scroll-spinner" class="hidden"></div></div>
<script src="/js/season-filters.js"></script>
<script src="/js/member-portrait-picker.js"></script>
<script src="/js/objekt-page.js"></script>
</body></html>`;
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||(fs.existsSync('/usr/bin/chromium')?'/usr/bin/chromium':undefined),args:['--no-sandbox']});
 try{
  const page=await browser.newPage();
  const moreCalls=[];
  await page.route('**/*',route=>{
   const url=new URL(route.request().url());
   if(url.pathname==='/'||url.pathname==='/cosmo/objekt')return route.fulfill({contentType:'text/html',body:html});
   if(url.pathname==='/cosmo/objekt/more'){
    moreCalls.push(Object.fromEntries(url.searchParams));
    const members=url.searchParams.getAll('member');
    const seasons=url.searchParams.getAll('season');
    const group=url.searchParams.get('group')||'all';
    const kind=url.searchParams.get('kind')||'all';
    const label=members[0]||seasons[0]||(group==='other'?'other':kind!=='all'?kind:'filtered');
    return route.fulfill({contentType:'application/json',body:JSON.stringify({
      items:[{id:label,publicKey:label,collectionId:label+' 카드',member:members[0]||'',season:seasons[0]||'',class:'First',collectionNo:'1'}],
      resultCount:3,allCount:8,hasMore:false,nextPage:null
    })});
   }
   const file=path.resolve(root,'src/main/resources/static','.'+url.pathname);
   if(file.startsWith(path.join(root,'src/main/resources/static')+path.sep)&&fs.existsSync(file))
    return route.fulfill({path:file,contentType:url.pathname.endsWith('.js')?'application/javascript':'text/css'});
   return route.fulfill({status:404,body:''});
  });
  await page.goto('https://filters.test/cosmo/objekt',{waitUntil:'load'});
  await page.locator('.mpp-item').click();
  await page.waitForFunction(()=>document.querySelector('[data-objekt-key="윤서연"]'));
  assert.equal(await page.locator('#objekt-card-grid .objekt-item').count(),1);
  assert.equal(await page.locator('#objekt-result-summary strong').textContent(),'3');
  assert.ok(moreCalls.some(call=>call.member==='윤서연'&&call.group==='members'));

  moreCalls.length=0;
  await page.locator('[data-objekt-group="other"]').click();
  await page.waitForFunction(()=>document.querySelector('[data-objekt-key="other"]'));
  assert.ok(moreCalls.some(call=>call.group==='other'));

  moreCalls.length=0;
  await page.locator('.season-checkbox[value="Atom01"]').evaluate(el=>{el.checked=true;el.dispatchEvent(new Event('change',{bubbles:true}));});
  await page.waitForFunction(()=>document.querySelector('[data-objekt-key="Atom01"]'));
  assert.ok(moreCalls.some(call=>call.season==='Atom01'));
  assert.equal(await page.locator('.objekt-card-season').evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(255, 221, 0)');

  moreCalls.length=0;
  const kindWait=page.waitForResponse(res=>{try{return new URL(res.url()).searchParams.get('kind')==='101Z';}catch{return false;}});
  await page.locator('.objekt-kind-option[data-value="101Z"]').evaluate(el=>el.click());
  await kindWait;
  assert.ok(moreCalls.some(call=>call.kind==='101Z'));

  console.log('PASS: 멤버·그룹·시즌·종류 변경 시 검색 버튼 없이 결과 갱신');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});
