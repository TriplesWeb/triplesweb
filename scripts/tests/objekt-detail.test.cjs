const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const code = fs.readFileSync(path.resolve(__dirname, '../../src/main/resources/static/js/objekt-detail.js'), 'utf8');

function setup(fetch) {
  const button = {disabled: false, attrs: {}, setAttribute(k, v) { this.attrs[k] = v; }};
  const input = {value: 'true'};
  const status = {};
  const form = {action: '/favorite', elements: {namedItem: () => input},
    querySelector: s => s === 'button' ? button : status,
    addEventListener: (type, fn) => { form.submit = fn; }};
  vm.runInNewContext(code, {document: {querySelector: s => s === '[data-objekt-favorite]' ? form : null}, fetch, URLSearchParams});
  return {form, button, input, status, submit: () => form.submit({preventDefault() {}})};
}

test('duplicate submits send one desired state, then allow explicit removal', async () => {
  let finish, calls = [];
  const ui = setup((url, options) => {
    calls.push(options);
    return new Promise(resolve => { finish = resolve; });
  });
  const pending = ui.submit();
  await ui.submit();
  assert.equal(calls.length, 1);
  assert.equal(calls[0].method, 'PUT');
  assert.equal(String(calls[0].body), 'favorite=true');
  finish({ok: true, json: async () => ({favorite: true})});
  await pending;
  assert.equal(ui.input.value, 'false');
  assert.equal(ui.button.attrs['aria-pressed'], 'true');
  const remove = ui.submit();
  assert.equal(String(calls[1].body), 'favorite=false');
  finish({ok: true, json: async () => ({favorite: false})});
  await remove;
  assert.equal(ui.input.value, 'true');
  assert.equal(ui.button.attrs['aria-pressed'], 'false');
  assert.equal(ui.button.disabled, false);
});

test('failed save keeps desired state for a safe retry and restores button', async () => {
  const ui = setup(async () => ({ok: false}));
  await ui.submit();
  assert.equal(ui.input.value, 'true');
  assert.equal(ui.button.disabled, false);
  assert.match(ui.status.textContent, /다시/);
});

test('owner lookup renders only serial and nickname even if internal fields are returned', async () => {
  const result = {children: [], replaceChildren() { this.children = []; }, append(node) { this.children.push(node); }};
  const button = {disabled: false};
  const form = {action: '/owner', reportValidity: () => true, elements: {namedItem: () => ({value: '3'})},
    querySelector: () => button, setAttribute() {}, removeAttribute() {}, addEventListener(type, fn) { this.submit = fn; }};
  vm.runInNewContext(code, {document: {
    querySelector: s => s === '[data-objekt-owner]' ? form : s === '[data-owner-result]' ? result : null,
    createElement: tag => ({tag})
  }, URLSearchParams, fetch: async () => ({ok: true, json: async () => ({status: 'found', serial: 3,
    nickname: 'TVTwe', address: '0xprivate', tokenId: '30569938', verifiedAt: 'time', explorerUrl: 'https://abscan.org/token/private'})})});
  await form.submit({preventDefault() {}});
  assert.deepEqual(result.children.map(p => p.textContent), ['#3 · TVTwe']);
  assert.equal(button.disabled, false);
});
