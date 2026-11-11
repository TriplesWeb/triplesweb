const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'../..');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||(fs.existsSync('/usr/bin/chromium')?'/usr/bin/chromium':undefined),args:['--no-sandbox']});
 try{
  const page=await browser.newPage(); const errors=[]; page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',route=>{
   const url=new URL(route.request().url()); if(url.origin!=='https://header.test')return route.abort();
   if(url.pathname==='/api/site/alerts'){
    return route.fulfill({contentType:'application/json',body:JSON.stringify({
      catalogSig:'sig-1',catalogCount:2,dropWindow:true,
      notices:[{id:9,title:'공지 테스트',href:'/article/notice/9',at:'2026-11-10'}],
      objekts:[{label:'Cream02 323 드롭',href:'/cosmo/objekt?search=323',collectionId:'cc323',createdAt:'2026-11-10'}]
    })});
   }
   if(url.pathname==='/search'){
    let html=fs.readFileSync(path.join(root,'build/mobile-pages','search-tabs.html'),'utf8')
      .replace(/<script\b[^>]*>[\s\S]*?<\/script>/g,'');
    html=html.replace('</body>','<script src="/js/site-header.js"></script><script src="/js/site-mobile.js"></script></body>');
    return route.fulfill({contentType:'text/html',body:html});
   }
   if(url.pathname==='/'||url.pathname==='/home'){
    const fixture=url.pathname==='/home'?'header-home.html':'header-auth.html';
    let html=fs.readFileSync(path.join(root,'build/mobile-pages',fixture),'utf8')
      .replace(/<script\b[^>]*>[\s\S]*?<\/script>/g,'');
    html=html.replace('</head>','<style>main{min-height:1400px}</style></head>');
    // header.mustache already embeds site-header.js; strip then inject once so the menu toggle is not double-bound.
    html=html.replace('</body>','<script src="/js/site-header.js"></script><script src="/js/site-mobile.js"></script></body>');
    return route.fulfill({contentType:'text/html',body:html});
   }
   const file=path.resolve(root,'src/main/resources/static','.'+url.pathname);
   if(file.startsWith(path.join(root,'src/main/resources/static')+path.sep)&&fs.existsSync(file))return route.fulfill({path:file,contentType:url.pathname.endsWith('.css')?'text/css':url.pathname.endsWith('.js')?'application/javascript':undefined});
   return route.fulfill({status:404,body:''});
  });
  fs.mkdirSync('/tmp/objekt-ui',{recursive:true});
  for(const width of [320,360,390,1024,1040,1280,1440]){
   await page.setViewportSize({width,height:900});
   await page.goto('https://header.test/',{waitUntil:'load'});
   await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`header overflow ${width}`);
   const desktop=await page.locator('#navbar').evaluate(n=>getComputedStyle(n).display!=='none');
   const jump=page.locator('.header-objekt-jump');
   assert.equal(await jump.count(),1,`모든 페이지에 오브젝트 바로가기가 있다 ${width}`);
   assert.equal(await jump.getAttribute('href'),'/cosmo/objekt');
   assert.equal(await jump.getAttribute('aria-label'),'오브젝트');
   assert.equal(await jump.isVisible(),true,`오브젝트 아이콘이 보인다 ${width}`);
   if(!desktop){
    assert.equal(await page.locator('[data-header-search-open]').isVisible(),false,`모바일 헤더 돋보기는 없다 ${width}`);
    assert.equal(await page.locator('[data-header-search]').evaluate(n=>getComputedStyle(n).display==='none'),true,`모바일 헤더 검색칸 숨김 ${width}`);
    const cosmoId=page.locator('[data-cosmo-id-open]');
    const jumpBox=await jump.boundingBox();
    const cosmoBox=await cosmoId.boundingBox();
    assert.ok(jumpBox&&cosmoBox&&jumpBox.x<cosmoBox.x,`오브젝트 아이콘이 코스모 아이디 왼쪽 ${width}`);
    assert.equal(await cosmoId.isVisible(),true,`모바일 코스모 아이디 ${width}`);
    assert.equal(await page.locator('[data-alerts-open]').isVisible(),true,`모바일 종 ${width}`);
    await page.evaluate(()=>localStorage.setItem('cosmo-nickname-recents',JSON.stringify(['myid'])));
    await page.locator('[data-cosmo-id-open]').click();
    await page.waitForFunction(()=>!document.querySelector('[data-cosmo-id-panel]').hidden);
    assert.equal(await page.locator('[data-cosmo-id-mine]').isVisible(),true);
    assert.ok((await page.locator('[data-cosmo-id-mine]').getAttribute('href')).includes('myid'));
    await page.keyboard.press('Escape');
    await page.waitForFunction(()=>document.querySelector('[data-cosmo-id-panel]').hidden);
    await page.locator('[data-alerts-open]').click();
    await page.getByText('공지 테스트').waitFor();
    assert.equal(await page.getByText('드롭 시간입니다').isVisible(),true);
    assert.equal(await page.getByText('공지 테스트').isVisible(),true);
    assert.equal(await page.getByText('Cream02 323 드롭').isVisible(),true);
    await page.keyboard.press('Escape');
    await page.locator('#mobile-menu-btn').click();
    await page.waitForFunction(()=>!document.getElementById('mobile-menu').classList.contains('hidden'));
    assert.equal(await page.locator('#mobile-menu').evaluate(n=>getComputedStyle(n).display!=='none'),true);
    const cosmoToggle=page.locator('.mobile-nav-toggle').filter({hasText:'코스모'});
    await cosmoToggle.click();
    assert.equal(await page.locator('.mobile-nav-panel a[href="/article/trade"]').first().isVisible(),true);
    assert.equal(await page.locator('.mobile-nav-panel summary').count(),0);
    assert.equal(await page.locator('a[href="/community"]').count(),0);
    await page.screenshot({timeout:10000,animations:'disabled',path:`/tmp/objekt-ui/flat-menu-chromium-${width}.png`}).catch(error=>console.warn(`[preview] chromium ${width}px screenshot skipped: ${error.message}`));
    await page.keyboard.press('Escape');
    await page.waitForFunction(()=>document.getElementById('mobile-menu').classList.contains('hidden'));
    assert.equal(await page.locator('#mobile-menu-btn').evaluate(n=>n===document.activeElement),true);
    await page.locator('#mobile-menu-btn').click();
    await page.waitForFunction(()=>!document.getElementById('mobile-menu').classList.contains('hidden'));
    assert.equal(await page.locator('#mobile-menu form[action="/search"] input').isVisible(),true);
    assert.equal(await page.locator('.mobile-account a[href="/mypage"]').isVisible(),true);
    await page.screenshot({timeout:10000,animations:'disabled',path:`/tmp/objekt-ui/header-chromium-${width}.png`,fullPage:false}).catch(error=>console.warn(`[preview] chromium ${width}px screenshot skipped: ${error.message}`));
   }else{
    const labels=await page.locator('#navbar > a.nav-item-link, #navbar > .relative > button.nav-item-link').evaluateAll(nodes=>
      nodes.map(n=>n.childNodes[0]?.textContent?.trim() || n.textContent.replace(/expand_more/g,'').trim())
    );
    assert.deepEqual(labels,['About 트리플에스','코스모','미디어','정보','일정']);
    const schedule=page.locator('#navbar > a.nav-item-link').filter({hasText:/^일정$/});
    const scheduleBox=await schedule.boundingBox();
    const jumpBox=await jump.boundingBox();
    const searchBox=await page.locator('[data-header-search]').boundingBox();
    const cosmoIdBox=await page.locator('[data-cosmo-id-open]').boundingBox();
    assert.ok(scheduleBox&&jumpBox&&jumpBox.x>=scheduleBox.x+scheduleBox.width-1,`오브젝트 아이콘이 일정 오른쪽 ${width}`);
    assert.ok(searchBox&&jumpBox&&searchBox.x+searchBox.width<=jumpBox.x+1,`검색칸이 오브젝트 바로가기 왼쪽 ${width}`);
    assert.ok(cosmoIdBox&&jumpBox&&cosmoIdBox.x+cosmoIdBox.width<=jumpBox.x+1,`코스모 아이디가 오브젝트 바로가기 왼쪽 ${width}`);
    if(width>=1280){
      assert.ok(searchBox.width>=300,`검색칸이 왼쪽으로 더 온다 ${width} ${searchBox.width}`);
      assert.ok(searchBox.width<=520,`검색칸이 네비까지 붙지 않는다 ${width} ${searchBox.width}`);
      assert.ok(searchBox.x>=scheduleBox.x+scheduleBox.width+12,`일정과 검색 사이 간격 ${width}`);
    }
    assert.equal(await page.locator('#navbar').evaluate(n=>!!n.parentElement.querySelector('.header-objekt-jump')),false,`오브젝트 아이콘이 로고·네비 div 밖 ${width}`);
    assert.ok(await jump.evaluate(n=>n.closest('.ml-auto')!=null),`오브젝트 아이콘이 우측 도구 줄 ${width}`);
    const logoBox=await page.locator('.header-brand-cluster').boundingBox();
    const aboutBox=await page.locator('#navbar > .relative > button.nav-item-link').filter({hasText:'About'}).boundingBox();
    assert.ok(logoBox&&aboutBox&&aboutBox.x>=logoBox.x+logoBox.width+20,`About이 로고에서 떨어져 있다 ${width}`);
    const cosmo=page.locator('#navbar .relative').filter({has:page.locator('button',{hasText:'코스모'})});
    // 1024~1040px에서는 우측 계정/검색이 네비를 가려 :hover가 안 붙을 수 있음 — 넓은 폭에서 hover, 그 외는 패널만 연다.
    if(width>=1280) await cosmo.hover();
    else await cosmo.locator(':scope > .absolute').evaluate(n=>{n.classList.remove('hidden');n.style.display='block';});
    assert.equal(await cosmo.locator('a[href="/article/trade"]').first().isVisible(),true);
    assert.equal(await cosmo.locator('a[href="/cosmo/collection"]').isVisible(),true);
    assert.equal(await cosmo.locator('a[href="/cosmo/transfers"]').count(),0);
    assert.equal(await page.locator('[data-cosmo-id-open]').isVisible(),true,`데스크톱 코스모 아이디 ${width}`);
    assert.equal(await page.locator('[data-alerts-open]').isVisible(),true,`데스크톱 종 ${width}`);
    assert.ok(await page.locator('#top-header').evaluate(n=>n.getBoundingClientRect().height)<=66, 'main처럼 한 줄 헤더 유지');
    const search=page.locator('[data-header-search]');
    assert.equal(await search.locator('[data-header-search-input]').isVisible(),true,`데스크톱 검색 입력칸 ${width}`);
    assert.equal(await page.locator('[data-header-search-open]').evaluate(n=>getComputedStyle(n).display==='none'),true,`데스크톱은 검색 토글이 숨겨진다 ${width}`);
    assert.ok(await search.locator('[data-header-search-input]').evaluate(n=>getComputedStyle(n).opacity==='1'));
    await page.evaluate(()=>window.scrollTo(0,200));
    assert.equal(await page.locator('#top-header').evaluate(n=>n.getBoundingClientRect().top>=0),true);
    await page.screenshot({timeout:10000,animations:'disabled',path:`/tmp/objekt-ui/header-chromium-${width}.png`,fullPage:false}).catch(error=>console.warn(`[preview] chromium ${width}px screenshot skipped: ${error.message}`));
   }
  }
  for(const width of [320,390,1024]){
    await page.setViewportSize({width,height:900});
    await page.goto('https://header.test/home',{waitUntil:'load'});
    const homeJump=page.locator('.header-objekt-jump');
    assert.equal(await homeJump.getAttribute('href'),'/cosmo/objekt');
    assert.equal(await homeJump.isVisible(),true,`홈 오브젝트 바로가기 ${width}`);
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`home header overflow ${width}`);
  }
  await page.setViewportSize({width:390,height:900});
  await page.goto('https://header.test/search',{waitUntil:'load'});
  assert.equal(await page.locator('.search-tabs').count(),0);
  assert.equal(await page.locator('.search-section').count(),5);
  assert.equal(await page.locator('.search-members').count(),1);
  assert.equal(await page.getByRole('heading',{name:'멤버 검색 결과'}).isVisible(),true);
  assert.equal(await page.locator('a.search-member').filter({hasText:'지연'}).isVisible(),true);
  const memberTop=await page.locator('.search-members').evaluate(n=>n.getBoundingClientRect().top);
  const sectionTop=await page.locator('.search-section').first().evaluate(n=>n.getBoundingClientRect().top);
  assert.ok(memberTop<sectionTop,'멤버 검색 결과는 분류 섹션 위에 있어야 한다');
  assert.equal(await page.getByText('HaYeon 323').isVisible(),true);
  assert.equal(await page.getByText('코스모에서 더 보기').isVisible(),true);
  assert.ok(await page.getByText('이 분류에 없음').count()>=1);
  assert.deepEqual(errors,[]);console.log('PASS: chromium header 320/360/390/1024/1040/1280/1440px, keyboard, navigation, community removed, outside click, search, alerts, cosmo id');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});
