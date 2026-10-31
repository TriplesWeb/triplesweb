const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium,webkit,devices}=require('playwright');
const base=path.resolve(__dirname,'../..'), fixtures=path.join(base,'build/mobile-pages');
const engine=process.env.BROWSER_ENGINE==='webkit'?'webkit':'chromium';
(async()=>{
 const browser=await (engine==='webkit'?webkit:chromium).launch(engine==='webkit'?{headless:true}:{headless:true,executablePath:process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium')?'/usr/bin/chromium':undefined),args:['--no-sandbox']});
 try{
  const page=await browser.newPage({...devices[engine==='webkit'?'iPhone 13':'Pixel 7'],viewport:{width:390,height:844},deviceScaleFactor:2});
  await page.route('**/*',async route=>{
   const url=new URL(route.request().url());if(url.origin!=='https://mobile.test')return route.abort();
   if(url.pathname.startsWith('/preview/')){
    const file=path.basename(url.pathname);let html=fs.readFileSync(path.join(fixtures,file),'utf8').replace(/<script\b[^>]*>[\s\S]*?<\/script>/g,'');
    html=html.replace('<head>',`<head><base href="https://mobile.test/">${file==='header-bare.html'?'':'<link rel="stylesheet" href="/css/gravity.css">'}`);
    html=html.replace('</body>',`<script></script><script src="/js/site-mobile.js"></script><script src="/js/site-header.js"></script></body>`);
    return route.fulfill({contentType:'text/html',body:html});
   }
   const file=path.resolve(base,'src/main/resources/static','.'+url.pathname);
   if(file.startsWith(path.join(base,'src/main/resources/static')+path.sep)&&fs.existsSync(file)&&fs.statSync(file).isFile())return route.fulfill({path:file,contentType:url.pathname.endsWith('.css')?'text/css':url.pathname.endsWith('.js')?'application/javascript':undefined});
   return route.fulfill({status:404,body:''});
  });
  fs.mkdirSync('/tmp/objekt-ui',{recursive:true});
  const files=fs.readdirSync(fixtures).filter(name=>name.endsWith('.html'));
  const failures=[];
  for(const width of [360,390]){
   await page.setViewportSize({width,height:844});
   for(const file of files){
    await page.goto('https://mobile.test/preview/'+file,{waitUntil:'domcontentloaded'});
    await page.waitForLoadState('load');
    const offending=await page.evaluate(()=>[...document.querySelectorAll('main,main *,body>.container,body>.container *')].filter(n=>{
      const r=n.getBoundingClientRect(),s=getComputedStyle(n);
      if(!r.width||!r.height||s.visibility==='hidden'||s.display==='none'||s.position==='absolute'||s.position==='fixed'||s.pointerEvents==='none')return false;
      if(n.closest('.site-table-scroll,.table-responsive,.chem-table-wrap,.fc,[role=grid]'))return false;
      for(let p=n.parentElement;p&&p!==document.body;p=p.parentElement){if(['auto','scroll','hidden','clip'].includes(getComputedStyle(p).overflowX))return false;}
      return r.right>innerWidth+1||r.left < -1;
    }).slice(0,8).map(n=>({tag:n.tagName,cls:n.className,text:n.textContent.slice(0,90),right:n.getBoundingClientRect().right})));
    if(offending.length)failures.push({engine,width,file,offending});
    if(file==='cosmo_profile.mustache.html'){
      assert.equal(await page.locator('.profile-progress-track').count(),0);
      assert.ok(await page.locator('.profile-overall-pie').count()>=1);
      assert.ok(await page.locator('.profile-orbit').count()>=3);
      assert.equal(await page.locator('.profile-overall-pie .orbit-value').first().getAttribute('stroke-dasharray'),'40 100');
      assert.equal(await page.locator('.profile-orbit .orbit-value').first().getAttribute('stroke-dasharray'),'50 100');
    }
    if(file==='article_article.mustache.html'){
      assert.equal(await page.locator('.article-board-heading').isVisible(),false);
      assert.equal(await page.locator('.article-board-row>div:nth-child(2)').first().evaluate(node=>getComputedStyle(node).whiteSpace),'normal');
    }
    if(file==='header-auth.html'||file==='header-bare.html'){
      await page.locator('#mobile-menu-btn').click();assert.equal(await page.locator('#mobile-menu').isVisible(),true);
      await page.locator('.mobile-nav-toggle').first().click();assert.equal(await page.locator('.mobile-nav-panel').first().isVisible(),true);
      await page.keyboard.press('Escape');
      await page.keyboard.press('Escape');assert.equal(await page.locator('#mobile-menu').isVisible(),false);
      await page.locator('#mobile-menu-btn').click();
      assert.equal(await page.locator('.mobile-account a[href="/mypage"]').isVisible(),true);
      await page.keyboard.press('Escape');
      assert.ok(await page.locator('main input').evaluate(n=>parseFloat(getComputedStyle(n).fontSize))>=16);
    }
    if(width===390&&['cosmo_profile.mustache.html','article_article.mustache.html','schedule.mustache.html','shop_shop_detail.mustache.html'].includes(file)){
      // Preview images are optional artifacts; a slow WebKit screenshot must not fail functional assertions.
      try{
        await page.screenshot({path:`/tmp/objekt-ui/${engine}-${file}.png`,fullPage:true,timeout:10000});
      }catch(error){
        console.warn(`[preview] Screenshot skipped (${engine}, ${file}): ${error.message}`);
      }
    }
   }
  }
  for(const width of [320,430]){
    await page.setViewportSize({width,height:844});await page.goto('https://mobile.test/preview/cosmo_profile.mustache.html');
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`${engine} profile ${width}`);
  }
  console.log(`PASS: ${engine} · ${files.length}개 페이지 템플릿 · 360/390px · 프로필 320/430px · 터치 메뉴·입력·원형 그래프`);
  assert.deepEqual(failures,[],JSON.stringify(failures,null,2));
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});
