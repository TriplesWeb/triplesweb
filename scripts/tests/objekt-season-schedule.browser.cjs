const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const base=path.resolve(__dirname,'../..'),staticRoot=path.join(base,'src/main/resources/static');
const urls={
 calendar:'https://cdn.jsdelivr.net/npm/fullcalendar@6.1.10/index.global.min.js'
};
(async()=>{
 const libraries={};
 await Promise.all(Object.entries(urls).map(async([key,url])=>{
   const response=await fetch(url,{signal:AbortSignal.timeout(30000)});
   assert.ok(response.ok,'검사 라이브러리 다운로드: '+key);libraries[url]=await response.text();
 }));
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||undefined,args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:1280,height:900}});
  await page.addInitScript(()=>{window.tailwind={};});
  const posts=[], pageErrors=[];
  page.on('pageerror',error=>pageErrors.push(error.message));
  await page.context().addCookies([{name:'XSRF-TOKEN',value:'fixture-csrf',domain:'filters.test',path:'/'}]);
  page.on('dialog',dialog=>dialog.accept());
  await page.route('**/*',async route=>{
   const request=route.request(),url=new URL(request.url());
   if(libraries[request.url()])return route.fulfill({body:libraries[request.url()],contentType:request.url().endsWith('.css')?'text/css':'application/javascript'});
   if(url.origin!=='https://filters.test')return route.fulfill({body:'',contentType:url.pathname.endsWith('.css')?'text/css':'application/javascript'});
   if(url.pathname==='/api/artist/admin/status')return route.fulfill({json:{configured:true,admin:true}});
   if(request.method()==='POST'&&(url.pathname==='/schedule/add'||url.pathname==='/edit/42')){
    const params=new URLSearchParams(request.postData());posts.push({params,headers:request.headers()});
    const category=params.get('category'),title=params.get('title'),start=params.get('start')||params.get('startDate');
    const color=category==='ANNIVERSARY'?'#22aeff':'#f87171',categoryLabel=category==='ANNIVERSARY'?'기념일':'발매';
    return route.fulfill({json:{id:posts.length===3?'43':'42',title,displayTitle:(category==='ANNIVERSARY'?'🎂 ':'')+title,start,end:null,rawEnd:null,artistNames:'윤서연',category,categoryLabel,color}});
   }
   if(url.pathname.startsWith('/fixture/')){
    const name=url.pathname.slice('/fixture/'.length);
    if(name==='native')return route.fulfill({contentType:'text/html',body:'<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/css/season-filters.css"><script defer src="/js/season-filters.js"></script></head><body style="margin:16px;background:#101216;color:white"><form><div class="tr-facets"><div class="objekt-filter"><select data-season aria-label="시즌 필터"><option value="">전체</option><option>Atom01</option><option>Binary01</option></select></div></div><button type="reset">초기화</button><output></output></form><script>document.querySelector("select").addEventListener("change",event=>document.querySelector("output").textContent=event.target.value);</script></body></html>'});
    const file=name==='schedule'?path.join(base,'build/filter-pages/schedule.html'):path.join(base,'build/mobile-pages','cosmo_'+name+'.mustache.html');
    return route.fulfill({contentType:'text/html',body:fs.readFileSync(file,'utf8')});
   }
   const file=path.resolve(staticRoot,'.'+url.pathname);
   if(file.startsWith(staticRoot+path.sep)&&fs.existsSync(file)&&fs.statSync(file).isFile())
    return route.fulfill({path:file,contentType:url.pathname.endsWith('.js')?'application/javascript':url.pathname.endsWith('.css')?'text/css':undefined});
   if(url.pathname.startsWith('/api/')||url.pathname.includes('objekt-options'))return route.fulfill({json:{items:[],objekts:[],hasMore:false}});
   return route.fulfill({status:404,body:''});
  });
  for(const name of ['objekt','collection']){
   await page.setViewportSize({width:1280,height:900});
   await page.goto('https://filters.test/fixture/'+name);
   assert.equal(await page.locator('.season-quick-filters').isVisible(),false,'시즌 버튼은 열었을 때만 표시');
   await page.locator(name==='objekt'?'#season-dropdown>button':'#collection-season-toggle').click();
   const row=page.getByRole('group',{name:'시즌 선택',exact:true});
   await row.locator('button[data-value="Atom01"]').waitFor({timeout:10000}).catch(async error=>{
    console.log('시즌 버튼 진단',name,JSON.stringify(await page.evaluate(()=>({
      api:typeof window.SeasonChips,
      source:[...document.querySelectorAll('.season-checkbox')].map(el=>({value:el.value,parent:el.closest('.objekt-dropdown-menu')?.id})),
      rows:[...document.querySelectorAll('.season-quick-filters')].map(el=>el.outerHTML),
      assets:[...document.scripts].map(el=>el.src).filter(src=>src.includes('season'))
    }))),JSON.stringify(pageErrors));
    throw error;
   });
   assert.equal(await page.locator('.season-native-options').isVisible(),false,'기존 체크 표시 UI 숨김');
   assert.equal(await row.locator('.season-chip-list').evaluate(el=>getComputedStyle(el).flexWrap),'wrap',name+' 웹에서 시즌 전체 펼침');
   assert.equal(await row.locator('.season-chip-list').evaluate(el=>el.scrollWidth<=el.clientWidth),true,name+' 웹 가로 스크롤 없음');
   const atom=row.locator('button[data-value="Atom01"]'),binary=row.locator('button[data-value="Binary01"]');
   assert.notEqual(await atom.evaluate(el=>getComputedStyle(el).borderColor),await binary.evaluate(el=>getComputedStyle(el).borderColor));
   await atom.click();await binary.click();
   assert.equal(await page.locator('input.season-checkbox[value="Atom01"]').isChecked(),true);
   assert.equal(await page.locator('input.season-checkbox[value="Binary01"]').isChecked(),true);
   assert.equal(await atom.getAttribute('aria-pressed'),'true');
   await row.locator('button[data-value=""]').click();
   assert.equal(await page.locator('input.season-checkbox[value="Atom01"]').isChecked(),false);
   const input=page.locator('input.season-checkbox[value="Cream02"]');
   await input.evaluate(el=>{el.checked=true;el.dispatchEvent(new Event('change',{bubbles:true}));});
   assert.equal(await row.locator('button[data-value="Cream02"]').getAttribute('aria-pressed'),'true');
   await page.setViewportSize({width:390,height:844});
   if(!await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))console.log('시즌 배치 진단',await page.evaluate(()=>({w:innerWidth,scroll:document.documentElement.scrollWidth,items:[...document.querySelectorAll('.season-control,.season-choice-panel,.season-chip-list')].map(n=>({cls:n.className,rect:JSON.stringify(n.getBoundingClientRect()),min:getComputedStyle(n).minWidth,max:getComputedStyle(n).maxWidth,box:getComputedStyle(n).boxSizing}))})));
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,name+' 시즌 버튼 모바일 넘침');
   assert.equal(await row.locator('.season-chip-list').evaluate(el=>getComputedStyle(el).flexWrap),'wrap',name+' 모바일도 메뉴 안에서 버튼 줄바꿈');
  }
  await page.goto('https://filters.test/fixture/native');
  assert.equal(await page.evaluate(()=>document.characterSet),'UTF-8');
  const nativeRow=page.locator('.season-quick-filters');
  await page.locator('.season-toggle').click();
  await nativeRow.locator('button[data-value="Binary01"]').click();
  assert.equal(await page.locator('output').textContent(),'Binary01');
  await page.locator('select').evaluate(el=>{el.value='Atom01';el.dispatchEvent(new Event('change',{bubbles:true}));});
  assert.equal(await nativeRow.locator('button[data-value="Atom01"]').getAttribute('aria-pressed'),'true');
  await page.locator('select').evaluate(el=>el.add(new Option('Ever02','Ever02')));
  await page.locator('.season-toggle').click();
  await nativeRow.locator('button[data-value="Ever02"]').waitFor();
  await nativeRow.locator('button[data-value="Ever02"]').click();
  assert.equal(await page.locator('select').inputValue(),'Ever02');
  await page.getByRole('button',{name:'초기화',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('.season-chip[data-value=""]').getAttribute('aria-pressed')==='true');
  await page.locator('.season-toggle').click();
  await nativeRow.locator('button[data-value="Atom01"]').focus();await page.keyboard.press('Enter');
  assert.equal(await page.locator('select').inputValue(),'Atom01');
  assert.deepEqual(await page.evaluate(()=>Object.fromEntries(['Atom01','Binary01','Cream01','Divine01','Ever01','Atom02','Binary02','Cream02'].map(k=>[k,window.SeasonChips.colorFor(k)]))),{Atom01:'#FFDD00',Binary01:'#00FF00',Cream01:'#FF7477',Divine01:'#B400FF',Ever01:'#33ECFD',Atom02:'#FFD13E',Binary02:'#83FF00',Cream02:'#FF8389'});
  fs.mkdirSync('/tmp/objekt-ui',{recursive:true});
  await page.screenshot({path:'/tmp/objekt-ui/season-chips-mobile.png',fullPage:true,timeout:15000});
  await page.setViewportSize({width:1280,height:1000});
  await page.goto('https://filters.test/fixture/schedule');
  await page.addStyleTag({content:'.material-symbols-outlined{font-size:0!important;width:20px;flex:none}'});
  await page.locator('#calendar .fc-view').waitFor();
  assert.equal(await page.locator('#deleteForm input[name="_csrf"]').inputValue(),'fixture-csrf');
  const purple=page.locator('.fc-event:visible').filter({hasText:'팬사인회'}).first();
  if(!await purple.count())await page.locator('.fc-more-link').first().click();
  await purple.waitFor();
  assert.equal(await purple.evaluate(el=>getComputedStyle(el).borderTopColor),'rgb(167, 139, 250)');
  await page.locator('[onclick="showAddModal()"]').click();
  await page.locator('#titleInput').fill('새 기념일');
  const day=new Date().toISOString().slice(0,10);
  await page.locator('#startInput').fill(day);
  await page.locator('#addScheduleCategory').selectOption('ANNIVERSARY');
  await page.locator('#addArtistNames').selectOption({label:'윤서연'});
  assert.equal(await page.locator('#addSchedulePreview').textContent(),'🎂 새 기념일');
  assert.equal(await page.locator('#addSchedulePreview').evaluate(el=>getComputedStyle(el).borderLeftColor),'rgb(34, 174, 255)');
  await page.locator('#addScheduleForm button[type=submit]').click();
  await page.waitForFunction(()=>!document.getElementById('addScheduleModal').classList.contains('show'));
  assert.equal(posts[0].params.get('category'),'ANNIVERSARY');
  assert.equal(posts[0].headers['x-xsrf-token'],'fixture-csrf');
  async function openEvent(title){
   const event=page.locator('.fc-event:visible').filter({hasText:title}).first();
   try {await event.waitFor({state:'visible',timeout:3000});}
   catch (_) {
    const more=page.locator('.fc-more-link:visible').first();
    if(await more.count())await more.click();
    await event.waitFor({state:'visible',timeout:5000}).catch(async error=>{
      console.log('일정 렌더링 진단',title,JSON.stringify(await page.locator('.fc-event').allTextContents()),JSON.stringify(pageErrors));
      throw error;
    });
   }
   await event.click();
  }
  await openEvent('🎂 새 기념일');
  await page.locator('#editBtn').click();
  await page.locator('#editTitleInput').waitFor({state:'visible'});
  assert.equal(await page.locator('#editTitleInput').inputValue(),'새 기념일');
  assert.equal(await page.locator('#editScheduleCategory').inputValue(),'ANNIVERSARY');
  await page.locator('#editScheduleCategory').selectOption('RELEASE');
  await page.locator('#editTitleInput').fill('새 음원 발매');
  await page.locator('#editScheduleForm button[type=submit]').click();
  await page.waitForFunction(()=>!document.getElementById('editScheduleModal').classList.contains('show'));
  assert.equal(posts[1].params.get('category'),'RELEASE');
  assert.equal(posts[1].headers['x-xsrf-token'],'fixture-csrf');
  await openEvent('새 음원 발매');
  assert.equal(await page.locator('#modalScheduleTitle').textContent(),'새 음원 발매');
  assert.equal(await page.locator('#modalScheduleCategory').textContent(),'발매');
  await page.locator('#viewScheduleModal button[data-site-dismiss=modal]').first().click();
  // 실제 등록으로 FullCalendar가 긴 제목의 높이와 배치를 다시 계산하게 한다.
  const longTitle='윤서연 온라인 팬사인회 및 새 앨범 발매 기념 행사';
  await page.locator('[onclick="showAddModal()"]').click();
  await page.locator('#titleInput').fill(longTitle);
  await page.locator('#startInput').fill(day.slice(0,8)+'01');
  await page.locator('#addScheduleCategory').selectOption('FANSIGN');
  await page.locator('#addScheduleForm button[type=submit]').click();
  await page.waitForFunction(()=>!document.getElementById('addScheduleModal').classList.contains('show'));
  const title=page.locator('#calendar .fc-event-title').filter({hasText:longTitle}).first();
  await title.waitFor({state:'visible'});
  for(const width of [1280,390,360]){
    await page.setViewportSize({width,height:1000});
    await page.waitForTimeout(250);
    const metrics=await title.evaluate(el=>({whiteSpace:getComputedStyle(el).whiteSpace,scroll:el.scrollWidth,width:el.clientWidth,height:el.getBoundingClientRect().height}));
    assert.equal(metrics.whiteSpace,'normal',width+'px 달력 제목 줄바꿈');
    assert.ok(metrics.width>0&&metrics.scroll<=metrics.width+1,width+'px 제목 가로 잘림 없음');
    assert.ok(metrics.height>20,width+'px 긴 제목 여러 줄 표시');
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,width+'px 달력 가로 넘침 없음');
  }
  await page.setViewportSize({width:1280,height:1000});
  await page.screenshot({path:'/tmp/objekt-ui/schedule-categories-desktop.png',fullPage:true,timeout:15000});
  await page.setViewportSize({width:390,height:844});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'일정 모바일 넘침');
  await page.screenshot({path:'/tmp/objekt-ui/schedule-categories-mobile.png',fullPage:true,timeout:15000});
  console.log('통과: 시즌 버튼 색상·다중/단일 선택·동기화·동적 옵션·초기화·키보드·모바일 / 실제 FullCalendar·SiteUi 일정 분류·멤버색·케이크·CSRF·저장 후 수정');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
