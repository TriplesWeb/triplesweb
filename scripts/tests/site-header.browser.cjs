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
  for(const width of [320,360,390,1024,1280,1440]){
   await page.setViewportSize({width,height:900}); await page.goto('https://header.test/');
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`header overflow ${width}`);
   if(width<1024){
    assert.equal(await page.locator('.header-utilities').isVisible(),false);
    await page.locator('#mobile-menu-btn').focus(); await page.keyboard.press('Enter');
    await page.locator('[aria-controls="mobile-cosmo"]').click();
    await page.locator('#mobile-cosmo summary').click();
    assert.equal(await page.locator('#mobile-cosmo a[href="/cosmo/group-orders"]').isVisible(),true);
    await page.locator('[aria-controls="mobile-community"]').click();
    assert.equal(await page.locator('#mobile-community a[href="/community/suggestions"]').isVisible(),true);
    await page.keyboard.press('Escape'); await page.keyboard.press('Escape');
    assert.equal(await page.locator('#mobile-menu').isVisible(),false);
    assert.equal(await page.locator('#mobile-menu-btn').evaluate(n=>n===document.activeElement),true);
    await page.locator('#header-search-btn').click(); assert.equal(await page.locator('#header-search-panel input').isVisible(),true);
    await page.keyboard.press('Escape'); assert.equal(await page.locator('#header-search-panel').isVisible(),false);
    await page.locator('#mobile-menu-btn').click();
    await page.screenshot({path:`/tmp/objekt-ui/header-${engine}-${width}.png`,fullPage:false});
   }else{
    const labels=await page.locator('#navbar>.header-disclosure>button').allTextContents();
    assert.deepEqual(labels,['About 트리플에스','스케줄·활동','코스모','미디어']);
    const button=page.locator('[aria-controls="desktop-cosmo"]');await button.focus();await page.keyboard.press('Space');
    await page.locator('#desktop-cosmo summary').click();
    assert.equal(await page.locator('#desktop-cosmo a[href="/cosmo/group-orders"]').isVisible(),true);
    await page.evaluate(()=>window.scrollTo(0,200));
    assert.equal(await page.locator('#top-header').evaluate(n=>n.getBoundingClientRect().top>=0),true);
    await page.screenshot({path:`/tmp/objekt-ui/header-${engine}-${width}.png`,fullPage:false});
    await page.keyboard.press('Escape');await page.keyboard.press('Escape');
    assert.equal(await page.locator('#desktop-cosmo').isVisible(),false);
    await button.click();await page.locator('main').click({position:{x:10,y:10}});assert.equal(await page.locator('#desktop-cosmo').isVisible(),false);
   }
  }
  assert.deepEqual(errors,[]);console.log(`PASS: ${engine} header 320/360/390/1024/1280/1440px, keyboard, nested menus, outside click, search`);
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});
