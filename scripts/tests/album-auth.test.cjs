const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
async function page(admin, failed=false) {
  let init, token='stale-token';
  const control = () => ({hidden:true,events:{},classList:{add(){},remove(){},toggle(){}},setAttribute(){},addEventListener(name,fn){this.events[name]=fn;}});
  const edit=control(), add=control(), button=control();
  const classes=new Set();
  const document={body:{style:{}},getElementById:id=>({albumAdminBtn:button,albumAddBtn:add})[id]||null,
    querySelector:()=>({classList:{toggle(name,on){on?classes.add(name):classes.delete(name);}}}),
    querySelectorAll:selector=>selector==='[data-album-edit], #albumAddBtn'?[edit,add]:selector==='[data-album-edit]'?[edit]:[],
    addEventListener(name,fn){if(name==='DOMContentLoaded')init=fn;}};
  const auth={getToken:()=>token,setToken:value=>{token=value;},applyAuthHeaders:()=>new Headers()};
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../../src/main/resources/static/js/album.js'),'utf8'),{
    document,Headers,window:{AboutAdminAuth:auth,addEventListener(){}},
    fetch:async()=>{if(failed)throw new Error('offline');return {ok:true,status:200,json:async()=>({configured:true,admin})};}
  });
  init(); await new Promise(setImmediate);
  return {edit,add,classes,token};
}
test('stale browser token cannot expose album editing after server rejects admin status',async()=>{
  const p=await page(false);assert.equal(p.edit.hidden,true);assert.equal(p.add.hidden,true);assert.equal(p.classes.has('is-admin'),false);assert.equal(p.token,'');
});
test('album editing becomes visible only after server confirms administrator',async()=>{
  const p=await page(true);assert.equal(p.edit.hidden,false);assert.equal(p.add.hidden,false);assert.equal(p.classes.has('is-admin'),true);
});
test('status request failure keeps album editing hidden',async()=>{
  const p=await page(false,true);assert.equal(p.edit.hidden,true);assert.equal(p.add.hidden,true);assert.equal(p.classes.has('is-admin'),false);
});
