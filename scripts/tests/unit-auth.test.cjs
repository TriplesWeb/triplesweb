const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { runInNewContext } = require('node:vm');

async function page(serverAdmin, options = {}) {
  const elements = new Map();
  function element(id) {
    if (!elements.has(id)) elements.set(id, {
      value: '', checked: false, files: [], hidden: false, textContent: '',
      events: {}, classes: new Set(),
      classList: {
        add(name) { elements.get(id).classes.add(name); },
        remove(name) { elements.get(id).classes.delete(name); },
        toggle() {}
      },
      setAttribute() {}, removeAttribute() {}, closest: () => null,
      querySelectorAll: () => [],
      getAttribute(name) { return name === 'data-target' ? '#unit-' + id : null; },
      addEventListener(type, handler) { this.events[type] = handler; }
    });
    return elements.get(id);
  }
  let init, token = 'stale-browser-token', reloaded = false, assigned;
  let current = new URL(options.url || 'https://example.test/triples/unit');
  const tabs = options.tabs ? options.tabs.map(id => element(String(id))) : [];
  const location = {
    get href() { return current.href; },
    get search() { return current.search; },
    assign(value) { assigned = value; reloaded = true; }
  };
  const document = {
    body: { style: {} },
    querySelectorAll: selector => selector === '#triplesUnitTabs .nav-tab-btn' ? tabs : [],
    querySelector: selector => {
      const pane = selector.match(/^#unit-(\d+)$/);
      if (pane) {
        const target = element('pane-' + pane[1]);
        target.querySelectorAll = () => (options.images || {})[pane[1]] || [];
        return target;
      }
      const match = selector.match(/data-target="#unit-(\d+)"/);
      return match ? tabs.find(tab => tab === elements.get(match[1])) || null : null;
    },
    getElementById: element,
    addEventListener(type, handler) { if (type === 'DOMContentLoaded') init = handler; }
  };
  const auth = {
    getToken: () => token, setToken: value => { token = value; },
    applyAuthHeaders: value => new Headers(value)
  };
  runInNewContext(readFileSync(resolve(__dirname, '../../src/main/resources/static/js/unit.js'), 'utf8'), {
    document, window: {
      AboutAdminAuth: auth, location,
      confirm: () => options.confirm !== false,
      history: { replaceState(_state, _unused, value) { current = new URL(value, current); } }
    },
    Headers, FormData, URLSearchParams, URL, CSS: { escape: value => value },
    location: { reload() { reloaded = true; } },
    fetch: async url => url.endsWith('/admin/status')
      ? { ok: true, status: 200, json: async () => ({ configured: true, admin: serverAdmin }) }
      : options.saved
        ? { ok: true, status: 200, json: async () => options.saved }
        : { ok: false, status: 403, json: async () => ({ error: 'Forbidden' }) }
  });
  await init();
  element('unitEditName').value = 'Test unit';
  return { element, token: () => token, reloaded: () => reloaded,
    url: () => current.href, assigned: () => assigned };
}

test('a stale browser token does not override server rejection', async () => {
  const p = await page(false);
  assert.equal(p.token(), '');
  assert.equal(p.element('unitAdminBtn').textContent, '관리자');
});

test('403 during image-only save resets auth and explains how to sign in again', async () => {
  const p = await page(true);
  p.element('unitEditId').value = '14';
  p.element('unitEditName').value = 'Acid Eyes';
  p.element('unitEditSmallName').value = 'acideyes';
  p.element('unitEditFormation').value = '2022-12-26';
  p.element('unitEditClearImage').checked = true;
  await p.element('unitEditForm').events.submit({ preventDefault() {} });
  assert.equal(p.token(), '');
  assert.equal(p.element('unitAdminBtn').textContent, '관리자');
  assert.match(p.element('unitEditError').textContent, /관리자 로그인이 만료/);
  assert.equal(p.element('unitEditError').hidden, false);
  assert.equal(p.reloaded(), false);
});

test('selecting a unit persists that tab through an ordinary refresh', async () => {
  const p = await page(true, { tabs: [1, 14] });
  p.element('14').events.click();
  assert.equal(new URL(p.url()).searchParams.get('tab'), 'unit-14');
  const refreshed = await page(true, { tabs: [1, 14], url: p.url() });
  assert.equal(refreshed.element('14').classes.has('is-active'), true);
  assert.equal(refreshed.element('1').classes.has('is-active'), false);
});

test('saving a unit returns to the saved unit rather than the first tab', async () => {
  const p = await page(true, { tabs: [1, 14], saved: { unitId: 14, visible: true } });
  p.element('unitEditId').value = '14';
  await p.element('unitEditForm').events.submit({ preventDefault() {} });
  assert.equal(new URL(p.assigned()).searchParams.get('tab'), 'unit-14');
});

test('newly created units use the ID returned by the server', async () => {
  const p = await page(true, { tabs: [1], saved: { unitId: 27, visible: true } });
  await p.element('unitEditForm').events.submit({ preventDefault() {} });
  assert.equal(new URL(p.assigned()).searchParams.get('tab'), 'unit-27');
});

test('cancelling the save confirmation keeps the editor open without navigation', async () => {
  const p = await page(true, { confirm: false, saved: { unitId: 27, visible: true } });
  await p.element('unitEditForm').events.submit({ preventDefault() {} });
  assert.equal(p.assigned(), undefined);
  assert.equal(p.reloaded(), false);
});

test('hiding a unit returns to the list without an invalid tab target', async () => {
  const p = await page(true, { tabs: [1, 14],
    url: 'https://example.test/triples/unit?tab=unit-14', saved: { unitId: 14, visible: false } });
  p.element('unitEditId').value = '14';
  await p.element('unitEditForm').events.submit({ preventDefault() {} });
  assert.equal(new URL(p.assigned()).searchParams.has('tab'), false);
});

test('only images in the selected unit are loaded', async () => {
  const image = url => ({ getAttribute: () => url, removeAttribute() {} });
  const first = image('/first.jpg'), second = image('/second.jpg');
  const p = await page(true, { tabs: [1, 14], images: { 1: [first], 14: [second] } });
  assert.equal(first.src, '/first.jpg');
  assert.equal(second.src, undefined);
  p.element('14').events.click();
  assert.equal(second.src, '/second.jpg');
});
