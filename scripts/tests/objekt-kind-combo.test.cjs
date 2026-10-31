const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const code = fs.readFileSync(path.resolve(__dirname, '../../src/main/resources/static/js/objekt-kind-combo.js'), 'utf8');

function loadCombo() {
  const window = {};
  vm.runInNewContext(code, {window, document: {}});
  return window.ObjektKindCombo;
}

test('kind combo treats all and 전체 as the all sentinel', () => {
  const combo = loadCombo();
  assert.equal(combo.normalizeKindValue(''), 'all');
  assert.equal(combo.normalizeKindValue('all'), 'all');
  assert.equal(combo.normalizeKindValue('ALL'), 'all');
  assert.equal(combo.normalizeKindValue('전체'), 'all');
  assert.equal(combo.displayKindValue('all'), '전체');
  assert.equal(combo.displayKindValue(''), '전체');
});

test('kind combo keeps English letters and digits only', () => {
  const combo = loadCombo();
  assert.equal(combo.sanitizeKind('10-1 가나다'), '101');
  assert.equal(combo.normalizeKindValue('ab12!'), 'AB12');
  assert.equal(combo.normalizeKindValue('@@@'), 'all');
  assert.equal(combo.displayKindValue('101z'), '101Z');
});
