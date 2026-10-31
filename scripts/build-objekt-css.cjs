/* 브라우저에서 Tailwind를 재계산하지 않도록 목록과 동적 카드의 스타일을 미리 만든다. */
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),{execFileSync}=require('node:child_process');
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'objekt-css-'));
try{
 const input=path.join(dir,'input.css'),output=path.join(dir,'output.css');
 fs.writeFileSync(input,'@tailwind base;\n@tailwind components;\n@tailwind utilities;\n');
 execFileSync('npx',['--yes','--package=tailwindcss@3.4.17','tailwindcss','--config','scripts/tailwind-objekt.config.cjs','--input',input,'--output',output,'--minify'],{stdio:'inherit'});
 const base=fs.readFileSync('src/main/resources/static/css/style.css','utf8').replace(/^@charset.*$/igm,'').replace(/^@import.*$/igm,'');
 fs.writeFileSync('src/main/resources/static/css/objekt.css','/* Generated with scripts/build-objekt-css.cjs. */\n'+fs.readFileSync(output,'utf8')+'\n'+base);
}finally{fs.rmSync(dir,{recursive:true,force:true});}
