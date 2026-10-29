const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require('playwright');const base=path.resolve(__dirname,'../..');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||(fs.existsSync('/usr/bin/chromium')?'/usr/bin/chromium':undefined),args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:1280,height:1000}});const calls=[];let failure=true;
  await page.route('**/*',async route=>{
   const u=new URL(route.request().url());if(u.origin!=='https://perf.test')return route.abort();
   if(u.pathname.endsWith('/feeds')){
    calls.push(u.pathname);
    if(u.pathname.includes('/8/')&&failure){failure=false;return route.fulfill({status:503,contentType:'text/html',body:'일시 오류'});}
    await new Promise(resolve=>setTimeout(resolve,150));
    return route.fulfill({contentType:'text/html',body:'<div class="member-activities"><h2>선택한 멤버 콘텐츠</h2><article class="artist-content-card">최근 콘텐츠</article></div>'});
   }
   if(u.pathname.endsWith('/admin/status'))return route.fulfill({contentType:'application/json',body:'{"configured":false,"isAdmin":false}'});
   if(['/triples/artist','/triples/members','/triples/overview','/cosmo/gravity'].includes(u.pathname))return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(base,'build/perf-pages',u.pathname.includes('artist')?'artist.html':u.pathname.includes('members')?'members.html':u.pathname.includes('overview')?'overview.html':'gravity.html'),'utf8')});
   const file=path.resolve(base,'src/main/resources/static','.'+u.pathname);
   if(file.startsWith(path.join(base,'src/main/resources/static')+path.sep)&&fs.existsSync(file)&&fs.statSync(file).isFile())return route.fulfill({path:file,contentType:u.pathname.endsWith('.css')?'text/css':u.pathname.endsWith('.js')?'application/javascript':undefined});
   return route.fulfill({status:404,body:''});
  });
  await page.goto('https://perf.test/triples/artist');
  await page.waitForFunction(()=>typeof window.__artistActivateTab==='function');
  assert.equal(calls.length,0);assert.equal(await page.locator('.member-activities').count(),0);
  assert.equal(await page.locator('#member-7 img[data-member-src]').first().getAttribute('src'),null);
  await page.locator('[data-target="#member-7"]').click();
  await page.evaluate(()=>{window.__artistActivateTab('#member-7');window.__artistActivateTab('#member-7');});
  await page.locator('#member-7 .member-activities').waitFor();
  assert.equal(calls.filter(x=>x.includes('/7/')).length,1);
  assert.equal(await page.locator('.tab-pane:not([hidden])').count(),1);
  assert.equal(await page.locator('#member-6 img[data-member-src]').first().getAttribute('src'),null);
  assert.equal(await page.locator('.artist-tab-frame').first().evaluate(n=>getComputedStyle(n).filter),'none');
  assert.equal(await page.locator('.artist-tab-shine').first().isVisible(),false);
  await page.locator('[data-target="#member-8"]').click();
  await page.locator('#member-8 [data-artist-feed-retry]').waitFor({state:'visible'});
  await page.locator('#member-8 [data-artist-feed-retry]').click();
  await page.locator('#member-8 .member-activities').waitFor();
  await page.locator('[data-target="#member-7"]').click();assert.equal(calls.filter(x=>x.includes('/7/')).length,1);
  await page.locator('[data-target="#member-7"]').focus();await page.keyboard.press('End');
  await page.locator('#member-24 .member-activities').waitFor();
  assert.equal(await page.locator('.nav-tab-btn.is-active').count(),1);
  assert.equal(new URL(page.url()).searchParams.get('tab'),'member-24');
  fs.mkdirSync('/tmp/objekt-ui',{recursive:true});await page.screenshot({path:'/tmp/objekt-ui/artist-desktop.png',fullPage:true});
  await page.goto('https://perf.test/triples/artist?tab=member-3');await page.locator('#member-3 .member-activities').waitFor();
  await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:'/tmp/objekt-ui/artist-mobile.png',fullPage:true});
  await page.goto('https://perf.test/triples/members?tab=member-3');
  await page.locator('#member-3 .member-activities').waitFor();
  assert.equal(await page.locator('#triples-tab').count(),0);assert.equal(await page.locator('[data-member-tab]').count(),24);
  assert.equal(await page.locator('.member-directory-card').count(),0);
  await page.screenshot({path:'/tmp/objekt-ui/members-restored-mobile.png',fullPage:true});
  await page.setViewportSize({width:1280,height:1000});
  await page.screenshot({path:'/tmp/objekt-ui/members-restored-desktop.png',fullPage:true});
  const beforeOverview=calls.length;await page.goto('https://perf.test/triples/overview');
  assert.equal(await page.locator('[data-member-tab]').count(),0);assert.equal(await page.locator('#groupImageDisplay').count(),1);assert.equal(calls.length,beforeOverview);
  await page.goto('https://perf.test/cosmo/gravity');
  const shown=()=>page.locator('#gvGrid .gv-card:not([hidden])');
  await page.getByRole('button',{name:'진행 상태',exact:true}).click();await page.getByRole('option',{name:'진행 중',exact:true}).click();assert.equal(await shown().count(),1);
  await page.locator('#gvReset').click();assert.equal(await shown().count(),3);
  await page.getByRole('button',{name:'정렬',exact:true}).click();await page.getByRole('option',{name:'COMO 많은 순',exact:true}).click();
  assert.deepEqual(await shown().evaluateAll(nodes=>nodes.map(n=>n.dataset.id)),['2','3','1']);
  await page.getByRole('button',{name:'유형',exact:true}).click();await page.getByRole('option',{name:'타이틀',exact:true}).click();assert.equal(await shown().count(),2);
  await page.locator('#gvSearch').fill('3');assert.equal(await shown().count(),1);
  await page.locator('#gvReset').click();assert.equal(await shown().count(),3);assert.match(await page.getByRole('button',{name:'정렬',exact:true}).textContent(),/최신순/);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:'/tmp/objekt-ui/gravity-filters-mobile.png',fullPage:true});
  console.log('통과: 아티스트 초기 콘텐츠 요청 0건 · 선택한 멤버만 조회 · 중복 요청 방지·재시도·탭 이동 · 그래비티 상태/유형/검색/정렬/초기화');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
