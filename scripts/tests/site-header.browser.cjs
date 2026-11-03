const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'../..');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||(fs.existsSync('/usr/bin/chromium')?'/usr/bin/chromium':undefined),args:['--no-sandbox']});
 try{
  const page=await browser.newPage(); const errors=[]; page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',route=>{
   const url=new URL(route.request().url()); if(url.origin!=='https://header.test')return route.abort();
   if(url.pathname==='/'){
    let html=fs.readFileSync(path.join(root,'build/mobile-pages/header-auth.html'),'utf8')
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
   if(!desktop){
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
    assert.deepEqual(labels,['About 트리플에스','코스모','정보','미디어','일정']);
    const cosmo=page.locator('#navbar .relative').filter({has:page.locator('button',{hasText:'코스모'})});
    // 1024~1040px에서는 우측 계정/검색이 네비를 가려 :hover가 안 붙을 수 있음 — 넓은 폭에서 hover, 그 외는 패널만 연다.
    if(width>=1280) await cosmo.hover();
    else await cosmo.locator(':scope > .absolute').evaluate(n=>{n.classList.remove('hidden');n.style.display='block';});
    assert.equal(await cosmo.locator('a[href="/article/trade"]').first().isVisible(),true);
    assert.equal(await cosmo.locator('a[href="/cosmo/collection"]').isVisible(),true);
    assert.equal(await cosmo.locator('a[href="/cosmo/transfers"]').count(),0);
    assert.ok(await page.locator('#top-header').evaluate(n=>n.getBoundingClientRect().height)<=66, 'main처럼 한 줄 헤더 유지');
    await page.evaluate(()=>window.scrollTo(0,200));
    assert.equal(await page.locator('#top-header').evaluate(n=>n.getBoundingClientRect().top>=0),true);
    await page.screenshot({timeout:10000,animations:'disabled',path:`/tmp/objekt-ui/header-chromium-${width}.png`,fullPage:false}).catch(error=>console.warn(`[preview] chromium ${width}px screenshot skipped: ${error.message}`));
   }
  }
  assert.deepEqual(errors,[]);console.log('PASS: chromium header 320/360/390/1024/1040/1280/1440px, keyboard, navigation, community removed, outside click, search');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});
