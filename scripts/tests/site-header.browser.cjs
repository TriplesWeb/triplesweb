const fs=require("node:fs"),path=require("node:path"),assert=require("node:assert/strict"),{chromium}=require("playwright");
const root=path.resolve(__dirname,"../..");
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||(fs.existsSync("/usr/bin/chromium")?"/usr/bin/chromium":undefined),args:["--no-sandbox"]});
 try{
 const page=await browser.newPage();const errors=[];page.on("pageerror",e=>errors.push(e.message));
 await page.route("**/*",route=>{
 const u=new URL(route.request().url());if(u.origin!=="https://header.test")return route.abort();
 if(u.pathname==="/"){
  let html=fs.readFileSync(path.join(root,"build/mobile-pages/header-auth.html"),"utf8");
  html=html.replace("</head>","<style>main{min-height:1400px}</style></head>").replace("</body>","<script src='/js/v2-site.js'></script></body>");
  return route.fulfill({contentType:"text/html",body:html});
 }
 const base=path.resolve(root,"src/main/resources/static");const file=path.resolve(base,"."+u.pathname);
 if(file.startsWith(base+path.sep)&&fs.existsSync(file))return route.fulfill({path:file,contentType:u.pathname.endsWith(".css")?"text/css":u.pathname.endsWith(".js")?"application/javascript":undefined});
 return route.fulfill({status:404,body:""});
 });
 for(const width of [320,360,390,768,1024,1100,1280,1440]){
  await page.setViewportSize({width,height:900});await page.goto("https://header.test/",{waitUntil:"load"});
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,"header overflow "+width);
  if(width<1080){
   assert.equal(await page.locator("#v2-menu-toggle").isVisible(),true);
   await page.locator("#v2-menu-toggle").click();
   assert.equal(await page.locator("#v2-mobile-menu").isVisible(),true);
   assert.equal(await page.locator('#v2-mobile-menu a[href="/article/trade"]').isVisible(),true);
   assert.equal(await page.locator('#v2-mobile-menu a[href="/triples/overview"]').isVisible(),true);
   assert.equal(await page.locator('#v2-mobile-menu a[href="/mypage"]').isVisible(),true);
   assert.equal(await page.locator('#v2-mobile-menu form[action="/search"]').isVisible(),true);
   await page.keyboard.press("Escape");assert.equal(await page.locator("#v2-mobile-menu").isVisible(),false);
  }else{
   assert.equal(await page.locator(".v2-nav").isVisible(),true);
   assert.equal(await page.locator('.v2-nav a[href="/cosmo/gravity"]').isVisible(),true);
   assert.equal(await page.locator('.v2-nav a[href="/triples/overview"]').first().isVisible(),true);
   assert.equal(await page.locator("#v2-header-nonexistent").count(),0);
   assert.ok(await page.locator(".v2-header").evaluate(n=>n.getBoundingClientRect().height)<=74);
  }
 }
 assert.deepEqual(errors,[]);
 console.log("PASS: v2 header mobile/desktop, keyboard navigation, preserved links and no overflow");
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
