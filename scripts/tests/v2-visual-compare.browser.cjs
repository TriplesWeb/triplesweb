const fs=require("node:fs"),path=require("node:path"),{chromium}=require("playwright");
const urls=[["/","home"],["/triples/members","members"],["/triples/unit","unit"],["/triples/album","album"],["/triples/chemistry","chemistry"],["/cosmo/objekt","objekt"],["/cosmo/collection","collection"],["/cosmo/gravity","gravity"],["/schedule","schedule"],["/media","media"],["/archive/image_archive","photos"],["/archive/video_archive","videos"],["/article/trade","trade"],["/article/sell","sell"],["/shop","shop"],["/search?keyword=tripleS","search"]];
const roots=[["main",process.env.MAIN_URL||"http://127.0.0.1:8090"],["v2",process.env.V2_URL||"http://127.0.0.1:8091"]];
const output=path.resolve(process.env.SCREENSHOT_DIR||"build/v2-compare");
(async()=>{fs.mkdirSync(output,{recursive:true});const browser=await chromium.launch({headless:true});const report=[];
try{for(const width of [390,1280])for(const [route,label] of urls)for(const [kind,url] of roots){
 const page=await browser.newPage({viewport:{width,height:900}}),errors=[];
 page.on("pageerror",e=>errors.push(e.message));
 try{const res=await page.goto(url.replace(/\/$/,"")+route,{waitUntil:"domcontentloaded",timeout:20000});
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
 await page.screenshot({path:path.join(output,label+"-"+kind+"-"+width+".png"),animations:"disabled"});
 report.push({route,kind,width,status:res?.status(),overflow,errors});
 }catch(e){report.push({route,kind,width,error:String(e),errors})}finally{await page.close()}
}}finally{await browser.close()}
fs.writeFileSync(path.join(output,"report.json"),JSON.stringify(report,null,2));
const issues=report.filter(x=>x.error||x.status!==200||x.overflow||x.errors.length);
console.log(JSON.stringify({screenshots:report.length,issues:issues.length,details:issues},null,2));if(issues.length)process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1});
