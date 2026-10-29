const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium,webkit}=require('playwright');
const root=path.resolve(__dirname,'../..');
const engine=process.env.BROWSER_ENGINE==='webkit'?'webkit':'chromium';
(async()=>{
 const browser=await (engine==='webkit'?webkit:chromium).launch(engine==='webkit'?{headless:true}:{headless:true,executablePath:process.env.CHROMIUM_PATH||(fs.existsSync('/usr/bin/chromium')?'/usr/bin/chromium':undefined),args:['--no-sandbox']});
 try{
  const page=await browser.newPage(); const errors=[]; page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',route=>{
   const url=new URL(route.request().url()); if(url.origin!=='https://header.test')return route.abort();
   if(url.pathname==='/'){
    let html=fs.readFileSync(path.join(root,'build/mobile-pages/header-auth.html'),'utf8');
    html=html.replace('</head>','<style>main{min-height:1400px}</style></head>');
    html=html.replace('</body>','<script src="/js/site-header.js"></script></body>');
    return route.fulfill({contentType:'text/html',body:html});
   }
   const file=path.resolve(root,'src/main/resources/static','.'+url.pathname);
   if(file.startsWith(path.join(root,'src/main/resources/static')+path.sep)&&fs.existsSync(file))return route.fulfill({path:file,contentType:url.pathname.endsWith('.css')?'text/css':url.pathname.endsWith('.js')?'application/javascript':undefined});
   return route.fulfill({status:404,body:''});
  });
  fs.mkdirSync('/tmp/objekt-ui',{recursive:true});
  for(const width of [320,360,390,1024,1040,1280,1440]){
   await page.setViewportSize({width,height:900}); await page.goto('https://header.test/');
   // WebKit applies scrollbar-dependent media queries on the next layout frame.
   await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`header overflow ${width}`);
   const desktop=await page.locator('#navbar').isVisible();
   if(!desktop){
    assert.equal(await page.locator('.header-utilities').isVisible(),false);
    await page.locator('#mobile-menu-btn').focus(); await page.keyboard.press('Enter');
    await page.locator('[aria-controls="mobile-cosmo"]').click();
    assert.equal(await page.locator('#mobile-cosmo summary').count(),0);
    assert.equal(await page.locator('#mobile-cosmo a[href="/article/group-orders"]').isVisible(),true);
    assert.equal(await page.locator('#mobile-cosmo .header-link-group').count(),2);
    const headingStyles=await page.locator('#mobile-cosmo .header-group-heading').evaluateAll(nodes=>nodes.map(node=>{const s=getComputedStyle(node);return [s.display,s.padding,s.fontSize,s.lineHeight];}));
    assert.deepEqual(headingStyles[0],headingStyles[1]);
    const marketLinks=page.locator('#mobile-cosmo .header-link-group').nth(1).locator('.header-related-links a');
    const boxes=await Promise.all([0,1,2].map(i=>marketLinks.nth(i).boundingBox()));
    for(let i=1;i<boxes.length;i++)assert.ok(boxes[i].y>=boxes[i-1].y+boxes[i-1].height+3);
    await page.screenshot({timeout:10000,animations:'disabled',path:`/tmp/objekt-ui/flat-menu-${engine}-${width}.png`}).catch(error=>console.warn(`[preview] ${engine} ${width}px screenshot skipped: ${error.message}`));
    assert.equal(await page.locator('[aria-controls="mobile-community"]').count(),0);
    await page.keyboard.press('Escape'); await page.keyboard.press('Escape');
    assert.equal(await page.locator('#mobile-menu').isVisible(),false);
    assert.equal(await page.locator('#mobile-menu-btn').evaluate(n=>n===document.activeElement),true);
    await page.locator('#header-search-btn').click(); await page.setViewportSize({width,height:760}); assert.equal(await page.locator('#header-search-panel input').isVisible(),true);
    await page.keyboard.press('Escape'); assert.equal(await page.locator('#header-search-panel').isVisible(),false);
    await page.locator('#mobile-menu-btn').click();
    await page.screenshot({timeout:10000,animations:'disabled',path:`/tmp/objekt-ui/header-${engine}-${width}.png`,fullPage:false}).catch(error=>console.warn(`[preview] ${engine} ${width}px screenshot skipped: ${error.message}`));
   }else{
    const labels=await page.locator('#navbar>.header-disclosure>button,#navbar>.header-nav-link').allTextContents();
    assert.deepEqual(labels,['About 트리플에스','코스모','정보','미디어','일정']);
    const button=page.locator('[aria-controls="desktop-cosmo"]');await button.focus();await page.keyboard.press('Space');
    assert.equal(await page.locator('#desktop-cosmo summary').count(),0);
    assert.equal(await page.locator('#desktop-cosmo a[href="/article/group-orders"]').isVisible(),true);
    assert.equal(await page.locator('#desktop-cosmo .header-link-group').count(),2);
    const headingStyles=await page.locator('#desktop-cosmo .header-group-heading').evaluateAll(nodes=>nodes.map(node=>{const s=getComputedStyle(node);return [s.display,s.padding,s.fontSize,s.lineHeight];}));
    assert.deepEqual(headingStyles[0],headingStyles[1]);
    const marketLinks=page.locator('#desktop-cosmo .header-link-group').nth(1).locator('.header-related-links a');
    const boxes=await Promise.all([0,1,2].map(i=>marketLinks.nth(i).boundingBox()));
    for(let i=1;i<boxes.length;i++)assert.ok(boxes[i].y>=boxes[i-1].y+boxes[i-1].height+3);
    await page.screenshot({timeout:10000,animations:'disabled',path:`/tmp/objekt-ui/flat-menu-${engine}-${width}.png`}).catch(error=>console.warn(`[preview] ${engine} ${width}px screenshot skipped: ${error.message}`));
    assert.equal(await page.locator('#desktop-cosmo a[href="/cosmo/transfers"]').count(),0);
    assert.equal(await page.locator('#desktop-cosmo a[href="/cosmo/collection"]').isVisible(),true);
    assert.equal(await page.locator('.header-utilities [aria-controls="desktop-community"]').isVisible(),true);
    assert.ok(await page.locator('#top-header').evaluate(n=>n.getBoundingClientRect().height)<=66, 'main처럼 한 줄 헤더 유지');
    await page.evaluate(()=>window.scrollTo(0,200));
    assert.equal(await page.locator('#top-header').evaluate(n=>n.getBoundingClientRect().top>=0),true);
    await page.screenshot({timeout:10000,animations:'disabled',path:`/tmp/objekt-ui/header-${engine}-${width}.png`,fullPage:false}).catch(error=>console.warn(`[preview] ${engine} ${width}px screenshot skipped: ${error.message}`));
    await page.keyboard.press('Escape');await page.keyboard.press('Escape');
    assert.equal(await page.locator('#desktop-cosmo').isVisible(),false);
    await button.click();await page.locator('main').click({position:{x:10,y:10}});assert.equal(await page.locator('#desktop-cosmo').isVisible(),false);
   }
  }
  assert.deepEqual(errors,[]);console.log(`PASS: ${engine} header 320/360/390/1024/1040/1280/1440px, keyboard, grouped links, mobile community hidden, outside click, search`);
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});
