const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function page(initialAdmin, testOptions = {}) {
  const storage = new Map([['aboutAdminToken', initialAdmin ? 'shared-admin-token' : 'expired']]);
  const elements = new Map();
  const ids = ['gravityAdminBtn', 'gravityLoginModal', 'gravityAdminPassword', 'gravityLoginError',
    'gravityAdminMessage', 'gravityConnectLink', 'gravityAdminHint', 'gravityPasswordToggle', 'gravityAdminLoginForm'];
  if (testOptions.connectPage) ids.push('sendEmailButton', 'cosmoLogin', 'codeForm', 'emailForm', 'cosmoEmail', 'cosmoCode', 'connectStatus', 'gravityLoginRequired');
  for (const id of ids) elements.set(id, {
    hidden: true, value: '', type: id === 'gravityAdminPassword' ? 'password' : '',
    textContent: '', events: {}, classList: { add() {}, remove() {}, toggle() {} },
    focus() {}, setAttribute() {}, querySelectorAll: () => [],
    addEventListener(event, handler) { this.events[event] = handler; }
  });
  const events = {};
  const document = {
    body: { style: {} }, activeElement: elements.get('gravityAdminBtn'),
    getElementById: id => elements.get(id) || null, querySelectorAll: () => [],
    addEventListener(name, handler) { events[name] = handler; }
  };
  const localStorage = {
    getItem: key => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key)
  };
  let serverAdmin = initialAdmin;
  const requests = [];
  const navigations = [];
  const context = {
    setInterval: () => 1, clearInterval() {}, document, localStorage, sessionStorage: { getItem: () => null, setItem() {}, removeItem() {} }, Headers,
    window: { location: { replace(url) { navigations.push(url); } }, addEventListener(name, handler) { events[name] = handler; } },
    fetch: async (url, options) => {
      requests.push({ url, ...options });
      if (url.endsWith('/verify') && testOptions.verifyFailure) return { ok: false, status: 503, json: async () => ({ message: '인증 실패' }) };
      if (url.endsWith('admin-logout')) serverAdmin = false;
      if (url.endsWith('admin-login')) {
        if (testOptions.networkFailure) throw new TypeError('Failed to fetch');
        const good = JSON.parse(options.body).password === 'test-password';
        if (!good) return { ok: false, status: 401, json: async () => ({}) };
        serverAdmin = true;
        return { ok: true, json: async () => ({ token: 'shared-admin-token' }) };
      }
      return { ok: true, json: async () => ({ configured: true, admin: serverAdmin, connected: false }) };
    }
  };
  vm.createContext(context);
  for (const file of ['about-admin-auth.js', 'gravity-connect.js']) {
    vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../../src/main/resources/static/js', file), 'utf8'), context);
  }
  await new Promise(setImmediate);
  const el = id => elements.get(id);
  async function login(password) {
    el('gravityAdminPassword').value = password;
    await el('gravityAdminLoginForm').events.submit({ preventDefault() {}, currentTarget: el('gravityAdminLoginForm') });
  }
  return { el, storage, login, requests, navigations };
}

test('Chemistry shared authentication exposes Gravity connection without another password prompt', async () => {
  const p = await page(true);
  assert.equal(p.el('gravityConnectLink').hidden, false);
  assert.equal(p.el('gravityAdminBtn').textContent, '관리자 로그아웃');
  assert.equal(p.el('gravityLoginModal').hidden, true);
});

test('guest must enter the common administrator password in a modal', async () => {
  const p = await page(false);
  assert.equal(p.storage.has('aboutAdminToken'), false);
  assert.equal(p.el('gravityConnectLink').hidden, true);
  await p.el('gravityAdminBtn').events.click();
  assert.equal(p.el('gravityLoginModal').hidden, false);
  assert.equal(p.el('gravityAdminPassword').type, 'password');
  await p.login('wrong-password');
  assert.equal(p.el('gravityLoginModal').hidden, false);
  assert.match(p.el('gravityLoginError').textContent, /비밀번호/);
  await p.login('test-password');
  assert.equal(p.el('gravityLoginModal').hidden, true);
  assert.equal(p.el('gravityConnectLink').hidden, false);
  assert.equal(p.storage.get('aboutAdminToken'), 'shared-admin-token');
});

test('Gravity logout clears the same browser token used by Chemistry', async () => {
  const p = await page(true);
  await p.el('gravityAdminBtn').events.click();
  assert.equal(p.storage.has('aboutAdminToken'), false);
  assert.equal(p.el('gravityConnectLink').hidden, true);
  assert.equal(p.el('gravityAdminBtn').textContent, '관리자');
});

test('network failure has a separate Korean message and is not called a wrong password', async () => {
  const p = await page(false, { networkFailure: true });
  await p.el('gravityAdminBtn').events.click();
  await p.login('test-password');
  assert.match(p.el('gravityLoginError').textContent, /서버에 연결하지 못했습니다/);
  assert.doesNotMatch(p.el('gravityLoginError').textContent, /Failed to fetch|비밀번호가 올바르지/);
});

test('email and OTP forms send authenticated JSON POST requests and prevent browser GET navigation', async () => {
  const p = await page(true, { connectPage: true });
  p.el('cosmoEmail').value = 'owner@example.com';
  let prevented = false;
  await p.el('emailForm').events.submit({ preventDefault() { prevented = true; }, currentTarget: p.el('emailForm') });
  assert.equal(prevented, true);
  const emailRequest = p.requests.find(request => request.url.endsWith('/email'));
  assert.equal(emailRequest.method, 'POST');
  assert.equal(emailRequest.headers.get('Content-Type'), 'application/json');
  assert.equal(emailRequest.headers.get('X-Gravity-Connect'), '1');
  assert.equal(emailRequest.credentials, 'same-origin');
  assert.equal(p.el('codeForm').hidden, false);
  assert.equal(p.el('sendEmailButton').disabled, true);
  assert.match(p.el('sendEmailButton').textContent, /60초/);
  p.el('cosmoCode').value = '123456';
  await p.el('codeForm').events.submit({ preventDefault() {}, currentTarget: p.el('codeForm') });
  assert.equal(p.requests.find(request => request.url.endsWith('/verify')).method, 'POST');
  assert.equal(p.el('codeForm').hidden, true);
  assert.deepEqual(p.navigations, ['/cosmo/gravity']);
});

 test('failed verification stays on the connection page', async () => {
  const p = await page(true, { connectPage: true, verifyFailure: true });
  p.el('codeForm').hidden = false;
  p.el('cosmoCode').value = '123456';
  await p.el('codeForm').events.submit({ preventDefault() {}, currentTarget: p.el('codeForm') });
  assert.deepEqual(p.navigations, []);
  assert.equal(p.el('codeForm').hidden, false);
  assert.match(p.el('connectStatus').textContent, /인증 실패/);
});
