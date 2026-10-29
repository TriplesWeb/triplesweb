const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const base=path.resolve(__dirname,'../..');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||(fs.existsSync('/usr/bin/chromium')?'/usr/bin/chromium':undefined),args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:390,height:844}});
  let phase=0,requests=0;
  const original=fs.readFileSync(path.join(base,'build/profile-checklist-preview.html'),'utf8');
  await page.addInitScript(()=>{window.refreshDelays=[];const native=setTimeout;window.setTimeout=(fn,delay,...args)=>{window.refreshDelays.push(delay);return native(fn,delay,...args);};});
  await page.route('**/*',async route=>{
   const url=new URL(route.request().url());if(url.origin!=='https://profile.test')return route.abort();
   if(url.pathname==='/cosmo/profile/Fan'){
    requests++;
    if(phase===2)return route.fulfill({status:503,body:'unavailable'});
    let html=phase===1?original.replace(/data-profile-version="[^"]*"/,'data-profile-version="incoming"').replaceAll('profile-checklist-card is-missing','profile-checklist-card is-owned'):original;
    html=html.replace('<head>','<head><link rel="stylesheet" href="/css/gravity.css">');
    return route.fulfill({contentType:'text/html',body:html});
   }
   if(url.pathname.startsWith('/board/')){
    const category=path.basename(url.pathname),file=path.join(base,'build/mobile-pages/board-'+category+'.html');
    return route.fulfill({contentType:'text/html',body:fs.readFileSync(file,'utf8').replace('<head>','<head><link rel="stylesheet" href="/css/gravity.css">')});
   }
   const file=path.resolve(base,'src/main/resources/static','.'+url.pathname);
   if(file.startsWith(path.join(base,'src/main/resources/static')+path.sep)&&fs.existsSync(file)&&fs.statSync(file).isFile())return route.fulfill({path:file,contentType:file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':undefined});
   return route.fulfill({status:404,body:''});
  });
  await page.goto('https://profile.test/cosmo/profile/Fan?tab=members&member=HaYeon');
  assert.equal(await page.locator('.profile-summary-orbit').count(),2);
  assert.equal(await page.locator('.profile-summary-orbit .orbit-value').first().getAttribute('stroke-dasharray'),'50 100');
  await page.locator('.profile-season-fold>summary').click();await page.locator('.profile-checklist-group>summary').click();
  assert.equal(await page.locator('.profile-checklist-card.is-missing').count(),1);
  assert.ok(await page.evaluate(()=>window.refreshDelays.includes(30000)));
  phase=1;await page.locator('[data-profile-refresh]').click();
  await page.waitForFunction(()=>document.querySelector('[data-profile-version]').dataset.profileVersion==='incoming');
  assert.equal(await page.locator('.profile-checklist-card.is-missing').count(),0);
  assert.equal(await page.locator('details[open]').count(),2);
  assert.equal(await page.locator('#profile-member-picker').count(),1);
  phase=2;await page.locator('[data-profile-refresh]').click();
  await page.getByText('갱신 지연 · 기존 목록을 유지하고 다시 확인합니다').waitFor();
  assert.equal(await page.locator('.profile-checklist-card.is-owned').count(),2);
  assert.ok(await page.evaluate(()=>window.refreshDelays.includes(60000)));
  const before=requests;await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>true});document.dispatchEvent(new Event('visibilitychange'));document.querySelector('[data-profile-refresh]').click();});
  assert.equal(requests,before);
  await page.evaluate(()=>Object.defineProperty(document,'hidden',{configurable:true,get:()=>false}));
  fs.mkdirSync('/tmp/objekt-ui',{recursive:true});
  for(const width of [320,390,768,1280]){
   await page.setViewportSize({width,height:900});
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'profile width '+width);
   await page.screenshot({path:`/tmp/objekt-ui/profile-gauges-${width}.png`,fullPage:true});
   for(const category of ['trade','sell','group-orders']){
    await page.goto('https://profile.test/board/'+category);await page.waitForLoadState('load');
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,category+' width '+width);
    if(width<768){
     assert.equal(await page.locator('.article-board-heading').isVisible(),false);
     assert.equal(await page.locator('.article-board-subject').evaluate(n=>getComputedStyle(n).whiteSpace),'normal');
     const author=await page.locator('.article-board-author').boundingBox(),date=await page.locator('.article-board-date').boundingBox();
     assert.ok(author.x+author.width<=date.x+1,'writer/date separate');
    }
    if(category==='group-orders')assert.equal(await page.locator('.article-board-likes').count(),0);
    if(width===390||width===1280)await page.screenshot({path:`/tmp/objekt-ui/board-${category}-${width}.png`,fullPage:true});
   }
   phase=0;await page.goto('https://profile.test/cosmo/profile/Fan?tab=members&member=HaYeon');
  }
  console.log('통과: 보유 목록 자동 갱신·입고 반영·실패 시 목록 유지·펼침 유지·숨긴 탭 중지 / 시즌·클래스 원형 진행도 / 교환·거래·분철 320·390·768·1280px');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
