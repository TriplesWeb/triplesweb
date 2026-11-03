// Assert all standalone public views use the v2 shell without removing functional IDs or endpoints.
const fs=require("node:fs"),path=require("node:path"),assert=require("node:assert/strict");
const root=path.resolve("src/main/resources/templates");
const sources=[];
function scan(dir){for(const name of fs.readdirSync(dir)){const file=path.join(dir,name),st=fs.statSync(file);if(st.isDirectory()){scan(file);continue;}if(!file.endsWith(".mustache"))continue;
 const content=fs.readFileSync(file,"utf8"),relative=path.relative(root,file).replaceAll("\\","/");
 if(content.includes("<body")&&relative!=="main.mustache") sources.push({relative,content});
}}scan(root);
assert.ok(sources.length>=30,"Unexpected low page count "+sources.length);
for(const {relative,content} of sources){
 const req=["{{>layout/v2/head-open}}","{{>layout/v2/header}}","{{>layout/v2/footer}}",'v2-body','/css/v2-pages.css'];
 for(const x of req)assert.ok(content.includes(x),relative+" missing "+x);
 assert.ok(!content.includes("{{>layout/header}}"),relative+" still includes legacy header");
 assert.ok(!content.includes("{{>layout/head-open}}"),relative+" still includes legacy head");
}
const load=(file)=>fs.readFileSync(path.join(root,file),"utf8");
const important={
 "cosmo/objekt.mustache":["objekt-card-grid","objektDetailModal","objekt-scroll-sentinel"],
 "cosmo/gravity.mustache":["gvGrid","gvSearch","gvStatus"],
 "cosmo/collection.mustache":["collection-filter-form","collection-nickname-input"],
 "triples/chemistry.mustache":["chemistryTable","chemPairView"],
 "schedule.mustache":["FullCalendar"],
 "article/article_new.mustache":["editorRoot","article-objekt-picker"]
};
for(const [file,tokens]of Object.entries(important)){const s=load(file);for(const token of tokens)assert.ok(s.includes(token),file+" missing "+token)}
const main=load("main.mustache");assert.ok(main.includes("{{>layout/v2/head-open}}")&&main.includes("{{>layout/v2/header}}")&&main.includes("{{>layout/v2/footer}}"));

// No legacy visual CSS imports in v2 pages: only independently authored v2 assets.
for(const {relative,content} of sources){
 const cssTags=[...content.matchAll(/<link\b[^>]*\brel=["']stylesheet["'][^>]*>/gi)].map(x=>x[0]);
 for(const css of cssTags)assert.ok(css.includes("/css/v2-pages.css"),relative+" still loads a legacy stylesheet: "+css);
 assert.ok(!/<style\b[^>]*>[\s\S]*?<\/style>/i.test(content),relative+" has a legacy inline style block");
}
const cssHead=load("layout/v2/head-open.mustache");
for(const f of ["v2-utility.css","v2-structure.css","v2-site.css"])assert.ok(cssHead.includes(f),"new v2 head missing "+f);

console.log("PASS: clean-slate CSS imports, all "+sources.length+" standalone views use v2 shell and major feature contracts remain");
