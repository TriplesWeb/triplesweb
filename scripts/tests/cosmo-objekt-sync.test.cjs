const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function page(fetcher) {
  const button = {disabled:false,addEventListener(name, callback){this[name]=callback;}};
  const status = {textContent:''};
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../../src/main/resources/static/js/cosmo-objekt-sync.js'),'utf8'), {
    document:{getElementById:id=>id==='cosmoObjektSync'?button:status},
    window:{AboutAdminAuth:{applyAuthHeaders:()=>new Headers({'X-XSRF-TOKEN':'csrf-test'})}}, fetch:fetcher
  });
  return {button,status};
}
test('sync sends a CSRF-protected POST with no COSMO credentials and renders counts',async()=>{
  const p=page(async(url,options)=>{
    assert.equal(url,'/api/cosmo/objekts/sync'); assert.equal(options.method,'POST');
    assert.equal(options.headers.get('X-XSRF-TOKEN'),'csrf-test'); assert.equal(options.headers.has('Authorization'),false);
    assert.equal(options.body,undefined);
    return {ok:true,json:async()=>({collections:4,objekts:4,changed:0})};
  });
  await p.button.click(); assert.match(p.status.textContent,/4종류 · 4장.*0건/); assert.equal(p.button.disabled,false);
});
test('a pending sync cannot be submitted twice',async()=>{
  let finish,calls=0; const p=page(()=>{calls++; return new Promise(resolve=>finish=resolve);});
  const pending=p.button.click(); await p.button.click(); assert.equal(calls,1);
  finish({ok:true,json:async()=>({collections:0,objekts:0,changed:0})}); await pending;
});
test('server response bodies are not exposed on failure',async()=>{
  const p=page(async()=>({ok:false,status:503,json:async()=>({message:'credential-secret'})}));
  await p.button.click(); assert.match(p.status.textContent,/기존 DB/); assert.doesNotMatch(p.status.textContent,/credential-secret/);
});
test('expired administrator session prompts login and enables retry',async()=>{
  const p=page(async()=>({ok:false,status:403})); await p.button.click();
  assert.match(p.status.textContent,/관리자 로그인/); assert.equal(p.button.disabled,false);
});
