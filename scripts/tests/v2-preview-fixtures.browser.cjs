// Build deterministic screenshots from MobilePageTemplatesTest fixtures. No production server needed.
const fs=require("node:fs"),path=require("node:path"),{chromium}=require("playwright");
const root=path.resolve(__dirname,"../.."),publicDir=path.join(root,"src/main/resources/static");
const fixtures=path.join(root,"build/mobile-pages"),output=path.join(root,"build/v2-preview");
const cases=[
["main","홈"],["triples_artist","멤버"],["triples_unit","DIMENSION"],["triples_album","앨범"],["triples_chemistry","케미씃트리"],
["cosmo_objekt","Objekt 도감"],["cosmo_objekt_favorites","찜 목록"],["cosmo_collection","컬렉션 조회"],["cosmo_profile","프로필"],
["cosmo_gravity","Gravity"],["cosmo_gravity-detail","Gravity 상세"],["cosmo_transfers","전송 내역"],
["schedule","일정"],["archive_media","공식 미디어"],["archive_image_archive","사진 아카이브"],["archive_video_archive","영상 아카이브"],
["article_article","게시판"],["article_article_new","글 작성"],["search","검색"],["shop_shop","굿즈리스트"]
];
const htmlEscape=(s)=>String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
(async()=>{
 if(!fs.existsSync(fixtures))throw Error("Run ./gradlew test --tests '*MobilePageTemplatesTest' first");
 fs.mkdirSync(output,{recursive:true});
 const browser=await chromium.launch({headless:true});
 const manifest=[];
 try{
  for(const [key,label] of cases){
   const htmlPath=path.join(fixtures,key+".mustache.html");
   if(!fs.existsSync(htmlPath)){manifest.push({key,label,missing:true});continue;}
   for(const [width,device] of [[390,"mobile"],[1280,"desktop"]]){
    const page=await browser.newPage({viewport:{width,height:850},deviceScaleFactor:1});
    const errors=[];page.on("pageerror",e=>errors.push(e.message));
    await page.route("**/*",route=>{
      const req=route.request(),u=new URL(req.url());
      if(u.origin!=="https://preview.local")return route.abort();
      if(u.pathname==="/"+key+".html")return route.fulfill({contentType:"text/html",body:fs.readFileSync(htmlPath,"utf8")});
      const candidate=path.resolve(publicDir,"."+u.pathname);
      if(candidate.startsWith(publicDir+path.sep)&&fs.existsSync(candidate)&&fs.statSync(candidate).isFile()){
        const ext=path.extname(candidate).toLowerCase();
        const type={".css":"text/css",".js":"text/javascript",".jpg":"image/jpeg",".jpeg":"image/jpeg",".png":"image/png",".webp":"image/webp",".svg":"image/svg+xml",".ttf":"font/ttf"}[ext];
        return route.fulfill({path:candidate,contentType:type||"application/octet-stream"});
      }
      return route.fulfill({status:404,body:""});
    });
    try{
     await page.goto("https://preview.local/"+key+".html",{waitUntil:"domcontentloaded",timeout:20000});
     await page.waitForTimeout(250);
     const file=key+"-"+device+".png";
     await page.screenshot({path:path.join(output,file),animations:"disabled"});
     const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
     manifest.push({key,label,device,file,overflow,errors:errors.slice(0,6)});
    }catch(e){manifest.push({key,label,device,error:String(e).slice(0,400)})}
    finally{await page.close()}
   }
  }
 }finally{await browser.close()}
 fs.writeFileSync(path.join(output,"manifest.json"),JSON.stringify(manifest,null,2));
 const cards=cases.map(([key,label])=>{
 const shots=manifest.filter(x=>x.key===key&&x.file);
 if(!shots.length)return "<section><h2>"+htmlEscape(label)+"</h2><p>이 화면의 fixture가 없습니다.</p></section>";
 return "<section><h2>"+htmlEscape(label)+"</h2><div class='screens'>"+shots.map(x=>"<figure><img src='"+x.file+"' alt='"+htmlEscape(label)+" "+x.device+"'><figcaption>"+x.device+" "+(x.overflow?"· 가로 오버플로 점검 필요":"")+"</figcaption></figure>").join("")+"</div></section>";
 }).join("\n");
 fs.writeFileSync(path.join(output,"index.html"),"<!doctype html><html lang='ko'><meta charset='utf-8'><meta name='viewport' content='width=device-width'><title>tripleS Web V2 화면 모음</title><style>body{font:14px system-ui;background:#f4f5f8;color:#20232b;margin:0 auto;padding:30px;max-width:1440px}header{margin-bottom:32px}section{background:white;padding:20px;margin:24px 0;border-radius:14px}.screens{display:flex;gap:20px;align-items:start;flex-wrap:wrap}figure{margin:0;flex:1 1 280px;min-width:0}img{width:100%;border:1px solid #ddd}figcaption{padding:8px;color:#5a5f70}h2{font-size:18px}</style><header><h1>tripleS Web V2 — 전 페이지 프리뷰</h1><p>정적 테스트 데이터 기반 캡처입니다. 운영 데이터와 실제 API 동작을 나타내지 않습니다.</p></header>"+cards+"</html>");
 console.log(JSON.stringify({views:cases.length,captures:manifest.filter(x=>x.file).length,missing:manifest.filter(x=>x.missing||x.error),output},null,2));
 if(!manifest.some(x=>x.file))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1});
