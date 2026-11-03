// Design branch contract: functional wiring remains, appearance is independent.
const fs=require("node:fs");
const assert=require("node:assert/strict");
const root="src/main/resources/";
const page=fs.readFileSync(root+"templates/main.mustache","utf8");
const head=fs.readFileSync(root+"templates/layout/v2/head-open.mustache","utf8");
const nav=fs.readFileSync(root+"templates/layout/v2/header.mustache","utf8");
const css=fs.readFileSync(root+"static/css/v2-site.css","utf8");
const js=fs.readFileSync(root+"static/js/v2-site.js","utf8");
assert.ok(page.includes("{{>layout/v2/head-open}}"));
for(const token of ["{{albumHref}}","{{#noticePosts}}","{{#recentPhotos}}","{{#recentVideos}}","{{{schedulesJson}}}"])assert.ok(page.includes(token),"home data contract missing: "+token);
for(const path of ["/cosmo/objekt","/cosmo/gravity","/cosmo/collection","/triples/unit","/triples/members","/article/trade","/archive/image_archive","/archive/video_archive","/schedule"])assert.ok(nav.includes(path),"navigation path missing: "+path);
assert.ok(nav.includes("/triples/overview"),"reserved future overview path must remain");
assert.ok(page.includes('id="home-yt-facade"')&&page.includes('id="miniCalendar"'));
assert.ok(!page.includes("main-fade-up")&&!page.includes("material-symbols-outlined"));
assert.ok(head.includes("/css/v2-site.css")&&!head.includes("/css/site.css")&&!head.includes("cdn."));
assert.ok(!css.includes("@import")&&!css.includes(".site-header"));
assert.ok(js.includes("IntersectionObserver")&&js.includes("youtube.com/embed")&&js.includes("document.createElement"));
console.log("PASS: clean-slate v2 home, retained routes/data and lazy rendering");
