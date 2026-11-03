// Deterministic full-site visual comparison: main versus clean-slate v2.
// It renders two isolated Mustache fixture trees, never connects to production databases.
"use strict";
const fs=require("node:fs"),path=require("node:path"),{chromium}=require("playwright");
const v2=path.resolve(__dirname,"../..");
const main=path.resolve(process.env.MAIN_CHECKOUT||path.join(v2,"baseline"));
const out=path.resolve(process.env.OUTPUT_DIR||path.join(v2,"build/design-comparison"));
const routes=[
 ["main","홈"],["triples_artist","멤버"],["triples_unit","DIMENSION"],["triples_album","앨범"],["triples_album_detail","앨범 상세"],
 ["triples_chemistry","케미씃트리"],["triples_chemistry-en","케미 영문"],
 ["cosmo_objekt","Objekt 도감"],["cosmo_objekt_favorites","찜한 Objekt"],["cosmo_collection","컬렉션 검색"],
 ["cosmo_profile","COSMO 프로필"],["cosmo_transfers","전송 이력"],["cosmo_gravity","Gravity"],
 ["cosmo_gravity-detail","Gravity 상세"],["cosmo_gravity-connect","관리자 연결"],
 ["archive_media","공식 미디어"],["archive_image_archive","사진 아카이브"],["archive_video_archive","영상 아카이브"],["archive_media_new","미디어 등록"],
 ["article_article","게시판"],["article_article_detail","게시글 상세"],["article_article_new","게시글 작성"],["article_article_edit","게시글 수정"],
 ["schedule","일정"],["shop_shop","굿즈리스트"],["shop_shop_detail","굿즈 상세"],["shop_shop_cart","장바구니"],["shop_shop_new","굿즈 등록"],["search","검색"]
];
function esc(v){return String(v).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]))}
function mime(p){const ext=path.extname(p).toLowerCase();return {".css":"text/css",".js":"text/javascript",".svg":"image/svg+xml",".webp":"image/webp",".png":"image/png",".jpg":"image/jpeg",".jpeg":"image/jpeg",".ttf":"font/ttf",".woff2":"font/woff2"}[ext]||"application/octet-stream"}
(async()=>{
 const browser=await chromium.launch({headless:true,args:["--no-sandbox"]});fs.mkdirSync(out,{recursive:true});
 let report=[];
 try{
  for(const [key,label]of routes)for(const [width,device]of [[390,"mobile"],[1280,"desktop"]]){
   for(const [variant,root] of [["main",main],["v2",v2]]){
    const fixture=path.join(root,"build/mobile-pages",key+".mustache.html");
    if(!fs.existsSync(fixture)){report.push({key,label,device,variant,error:"fixture missing"});continue}
    const staticRoot=path.join(root,"src/main/resources/static");
    const page=await browser.newPage({viewport:{width,height:860},deviceScaleFactor:1});
    const errors=[];page.on("pageerror",e=>errors.push(e.message));
    await page.route("**/*",route=>{
      let u;try{u=new URL(route.request().url())}catch(_){return route.abort()}
      if(u.origin!=="https://design.test")return route.abort();
      if(u.pathname==="/"+key+".html")return route.fulfill({contentType:"text/html",body:fs.readFileSync(fixture,"utf8")});
      const useUpload=u.pathname.startsWith("/uploads/");
      const rootDir=useUpload?root:staticRoot;
      const file=path.resolve(rootDir,"."+u.pathname);
      if(file.startsWith(rootDir+path.sep)&&fs.existsSync(file)&&fs.statSync(file).isFile())return route.fulfill({contentType:mime(file),path:file});
      return route.fulfill({status:404,body:""});
    });
    try{
     await page.goto("https://design.test/"+key+".html",{waitUntil:"domcontentloaded",timeout:20000});
     await page.waitForTimeout(180);
     const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
     const file=key+"-"+variant+"-"+device+".png";
     await page.screenshot({path:path.join(out,file),animations:"disabled",timeout:20000,fullPage:true});
     report.push({key,label,device,variant,file,overflow,pageErrors:errors.slice(0,5)});
    }catch(e){report.push({key,label,device,variant,error:String(e).slice(0,320)})}finally{await page.close()}
   }
  }
 }finally{await browser.close()}
 fs.writeFileSync(path.join(out,"report.json"),JSON.stringify(report,null,2));
 const cards=routes.map(([key,label])=>"<section><h2>"+esc(label)+"</h2>"+["desktop","mobile"].map(device=>{
   const shots=["main","v2"].map(variant=>report.find(x=>x.key===key&&x.device===device&&x.variant===variant));
   return "<h3>"+device+"</h3><div class='comparison'>"+shots.map(x=>"<figure><figcaption>"+esc(x?.variant||"?")+"</figcaption>"+(x?.file?"<img src='"+x.file+"' alt='"+esc(label+" "+x.variant)+"'>":"<p>"+esc(x?.error||"Missing")+"</p>")+"</figure>").join("")+"</div>";
 }).join("")+"</section>").join("");
 const html="<!doctype html><html lang='ko'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'><title>tripleS Web — main vs V2</title><style>*{box-sizing:border-box}body{font:14px system-ui;background:#efeff2;color:#202128;max-width:1600px;margin:auto;padding:24px}header{padding:30px 10px}h1{font-size:30px}p{color:#4b5060}section{padding:20px;background:white;border-radius:15px;margin-bottom:24px}h2{font-size:21px;margin:0 0 18px}h3{font-size:14px;color:#545767}.comparison{display:grid;grid-template-columns:1fr 1fr;gap:15px}figure{margin:0;min-width:0}figcaption{font-size:13px;font-weight:700;margin:0 0 8px}img{width:100%;height:auto;border:1px solid #dedee5;object-position:top}a{color:#6543bf}@media(max-width:700px){.comparison{grid-template-columns:1fr}}</style></head><body><header><h1>tripleS Web — main vs V2</h1><p>동일한 테스트 픽스처로 생성한 화면 비교입니다. 왼쪽 main, 오른쪽 V2. 실제 데이터/계정/작성 기능은 포함되지 않습니다.</p><a href='report.json'>비교 리포트</a></header>"+cards+"</body></html>";
 fs.writeFileSync(path.join(out,"index.html"),html);
 const screenshots=report.filter(x=>x.file).length;const errors=report.filter(x=>x.error);
 console.log(JSON.stringify({routes:routes.length,screenshots,missing:errors,overflows:report.filter(x=>x.overflow).map(x=>({key:x.key,device:x.device,variant:x.variant})),out},null,2));
 if(errors.length)process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1});
