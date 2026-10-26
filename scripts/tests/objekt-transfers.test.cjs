const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const code = fs.readFileSync(require('node:path').join(__dirname, '../../src/main/resources/static/js/objekt-transfers.js'), 'utf8');

function setup(fetch, account = '') {
  const status = {};
  const list = {children: [], replaceChildren() { this.children = []; }, append(row) { this.children.push(row); }};
  const more = {hidden: true, addEventListener(_, fn) { this.click = fn; }};
  const submit = {};
  const input = {value: '3'};
  const form = {elements: {namedItem: () => input}, reportValidity: () => true,
    querySelector: () => submit, addEventListener(_, fn) { this.submit = fn; }};
  const root = {dataset: {account, url: '/history'}, setAttribute() {}, removeAttribute() {},
    querySelector: s => ({'[data-transfer-status]': status, '[data-transfer-rows]': list,
      '[data-transfer-more]': more, form})[s]};
  vm.runInNewContext(code, {document: {querySelector: () => root, createElement: tag => ({tag})},
    URLSearchParams, Date, fetch});
  return {status, list, more, input, submit: () => form.submit({preventDefault() {}})};
}
const reply = data => ({ok: true, json: async () => data});
const row = {timestamp: '2026-10-25T10:00:00Z', nickname: '<img src=x onerror=alert(1)>'};

test('card history loads only on submit and renders untrusted names as text', async () => {
  const calls = [];
  const ui = setup(async url => { calls.push(url); return reply({status: 'available', transfers: [row], hasNext: false}); });
  assert.equal(calls.length, 0);
  await ui.submit();
  assert.equal(calls[0], '/history?serial=3');
  assert.match(ui.list.children[0].textContent, /<img/);
  assert.equal(ui.list.children[0].innerHTML, undefined);
});
test('failed next page preserves rows and cursor for retry', async () => {
  const calls = [];
  const ui = setup(async url => {
    calls.push(url);
    if (calls.length === 2) throw new Error('offline');
    return reply({status: 'available', transfers: [row], hasNext: calls.length === 1, page: calls.length === 1 ? 0 : 1});
  });
  await ui.submit(); await ui.more.click();
  assert.equal(ui.list.children.length, 1);
  assert.equal(ui.more.hidden, false);
  assert.equal(ui.more.disabled, false);
  await ui.more.click();
  assert.equal(calls[1], calls[2]);
  assert.equal(ui.list.children.length, 2);
});
test('visibility changes clear all previously displayed history', async () => {
  let n = 0;
  const ui = setup(async () => reply(++n === 1 ? {status: 'available', transfers: [row], hasNext: true, page: 0} : {status: 'hidden'}));
  await ui.submit(); await ui.more.click();
  assert.equal(ui.list.children.length, 0);
  assert.equal(ui.more.hidden, true);
});
test('new serial query resets pagination and duplicate submissions are suppressed', async () => {
  let finish, calls = [];
  const ui = setup(url => { calls.push(url); return new Promise(resolve => { finish = resolve; }); });
  const pending = ui.submit(); await ui.submit();
  assert.equal(calls.length, 1);
  finish(reply({status: 'available', transfers: [], hasNext: false})); await pending;
  ui.input.value = '9';
  const second = ui.submit();
  assert.equal(calls[1], '/history?serial=9');
  finish(reply({status: 'available', transfers: [], hasNext: false})); await second;
});
test('malformed next-page payload does not append partial rows', async () => {
  let n = 0;
  const ui = setup(async () => reply(++n === 1
    ? {status: 'available', transfers: [row], hasNext: true, page: 0}
    : {status: 'available', transfers: [row, {timestamp: 'invalid'}], hasNext: false}));
  await ui.submit(); await ui.more.click();
  assert.equal(ui.list.children.length, 1);
  assert.equal(ui.more.hidden, false);
});
test('account page loads independently and forwards the opaque cursor', async () => {
  const calls = [];
  const ui = setup(async url => { calls.push(url); return reply({status: 'available', transfers: [{...row,
    direction: '받음', collectionId: 'Cream02', fromNickname: 'A', toNickname: 'B'}],
    hasNext: calls.length === 1, cursorTimestamp: '2026-10-25T10:00:00Z', cursorId: 'opaque+id'}); }, 'A+B');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(new URLSearchParams(calls[0].split('?')[1]).get('nickname'), 'A+B');
  await ui.more.click();
  assert.equal(new URLSearchParams(calls[1].split('?')[1]).get('cursorId'), 'opaque+id');
  assert.equal(ui.list.children.length, 2);
});
