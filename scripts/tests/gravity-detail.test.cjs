const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const code = fs.readFileSync(path.resolve(__dirname,'../../src/main/resources/static/js/gravity-detail.js'),'utf8');
function render(amounts) {
  const rows = amounts.map(value => {
    const fill = {style:{}}, strong = {}, track = { attrs:{}, setAttribute(k,v){this.attrs[k]=v;} };
    return {dataset:{como:String(value)}, fill, strong, track, querySelector(selector){return {'.gravity-rank-fill':fill,strong,'.gravity-rank-track':track,span:{textContent:'후보'}}[selector];}};
  });
  vm.runInNewContext(code, {document:{querySelectorAll:()=>rows}, Intl});
  return rows;
}
test('official ranking preserves order and scales to the maximum, not the sum',()=>{
  const rows = render([100,400,200]);
  assert.deepEqual(rows.map(row=>row.fill.style.width),['25%','100%','50%']);
  assert.equal(rows[0].track.attrs['aria-valuenow'],'100');
});
test('zero totals and malformed values do not invent data or invalid bar sizes',()=>{
  const rows=render([0,0,'unknown',-1]);
  assert.equal(rows[0].fill.style.width,'0%');
  assert.equal(rows[2].fill.style.width,undefined);
  assert.equal(rows[3].fill.style.width,undefined);
});
