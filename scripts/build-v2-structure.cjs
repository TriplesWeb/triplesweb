// tripleS Web 2.0: preserve layout/interactions, not legacy colors, fonts or ornamentation.
// The structural layer is generated from legacy selectors only during migration.
// Delete this adapter after all feature widgets have independent v2 implementations.
"use strict";
const fs = require("node:fs"), path = require("node:path");
const root = path.resolve(__dirname,"..");
const templateRoot=path.join(root,"src/main/resources/templates");
const cssRoot=path.join(root,"src/main/resources/static/css");
const out=path.join(cssRoot,"v2-structure.css");
const excluded=new Set(["site.css","triples.css","objekt.css","gravity.css","style.css",
"site-header.css","site-mobile.css","v2-site.css","v2-pages.css","v2-utility.css","v2-structure.css"]);
const allowed=new Set(["display","position","float","clear","inset","top","bottom","left","right","width","height","min-width","min-height","max-width","max-height","aspect-ratio","box-sizing","gap","row-gap","column-gap","grid","grid-template-columns","grid-template-rows","grid-template-areas","grid-auto-columns","grid-auto-rows","grid-auto-flow","grid-column","grid-row","grid-area","flex","flex-direction","flex-wrap","flex-grow","flex-shrink","flex-basis","align-items","align-content","align-self","justify-items","justify-content","justify-self","place-items","place-content","order","padding","padding-top","padding-bottom","padding-left","padding-right","padding-block","padding-inline","margin","margin-top","margin-bottom","margin-left","margin-right","margin-block","margin-inline","overflow","overflow-x","overflow-y","overflow-wrap","overflow-anchor","overscroll-behavior","overscroll-behavior-x","overscroll-behavior-y","white-space","word-break","text-overflow","object-fit","object-position","z-index","vertical-align","table-layout","border-collapse","border-spacing","list-style","list-style-type","visibility","opacity","cursor","pointer-events","touch-action","user-select","isolation","scrollbar-width","scroll-snap-type","scroll-snap-align","scroll-margin","scroll-padding","clip","clip-path","content"]);
function allFiles(dir,ext){let acc=[];for(const f of fs.readdirSync(dir,{withFileTypes:true})){const name=path.join(dir,f.name);if(f.isDirectory())acc.push(...allFiles(name,ext));else if(name.endsWith(ext))acc.push(name)}return acc}
const references=new Set(fs.readdirSync(cssRoot).filter(file=>file.endsWith(".css")&&!excluded.has(file)));
const raw=[...references].sort().map(file=>({file,body:fs.readFileSync(path.join(cssRoot,file),"utf8")}));
function parse(input){
 input=input.replace(/\/\*[\s\S]*?\*\//g,"");
 let pos=0,output=[];
 function blockEnd(start){
  let depth=1,quote="",escape=false;
  for(let i=start;i<input.length;i++){const c=input[i];if(quote){if(escape)escape=false;else if(c==="\\")escape=true;else if(c===quote)quote="";continue}
   if(c==="'"||c==='"'){quote=c;continue}
   if(c==="{")depth++;else if(c==="}"&&!--depth)return i;
  }return input.length;
 }
 while(pos<input.length){
  let open=input.indexOf("{",pos);if(open<0)break;
  let selector=input.slice(pos,open).trim();
  const semicolon=selector.lastIndexOf(";");
  if(semicolon>=0)selector=selector.slice(semicolon+1).trim();
  const close=blockEnd(open+1);
  const interior=input.slice(open+1,close);
  pos=close+1;
  if(!selector||selector.startsWith("@keyframes")||selector.startsWith("@-webkit-keyframes")||selector.startsWith("@font-face")||selector.startsWith("@property"))continue;
  if(selector.startsWith("@media")||selector.startsWith("@supports")||selector.startsWith("@container")){
   const nested=parse(interior);if(nested.trim())output.push(selector+"{"+nested+"}");continue;
  }
  if(selector.startsWith("@")||selector.includes(":root")||selector.startsWith("html{"))continue;
  // Individual declarations are flattened; nested CSS selectors use their own rules.
  const props=[];
  for(const pair of interior.split(";")){
   if(pair.includes("{")||pair.includes("}"))continue;
   const cut=pair.indexOf(":");if(cut<=0)continue;
   const key=pair.slice(0,cut).trim().toLowerCase();let value=pair.slice(cut+1).trim();
   if(!allowed.has(key)||!value||value.includes("javascript:"))continue;
   if(key==="content"&&value.length>120)continue;
   if((key==="clip-path"||key==="content")&&value.includes("url("))continue;
   props.push(key+":"+value);
  }
  if(props.length)output.push(selector+"{"+props.join(";")+"}");
 }
 return output.join("\n");
}
const parts=["/* Structural-only feature compatibility. Generated: visuals are v2-only. */"];
let rules=0;
for(const item of raw){const generated=parse(item.body);if(!generated)continue;parts.push("/* "+item.file+" */",generated);rules+=generated.match(/\{/g)?.length||0}
fs.writeFileSync(out,parts.join("\n")+"\n");
console.log("Generated v2 structural adapter",references.size,"files",rules,"rules",fs.statSync(out).size,"bytes");
