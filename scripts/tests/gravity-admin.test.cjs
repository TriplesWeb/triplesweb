const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function page(initialAdmin, testOptions = {}) {
  const elements = new Map();
  const ids = ['gravityAdminMessage', 'gravityConnectLink', 'gravityAdminHint', 'gravityLoginOpen'];
  if (testOptions.connectPage) {
    ids.push('sendEmailButton', 'cosmoLogin', 'codeForm', 'emailForm', 'cosmoEmail', 'cosmoCode', 'connectStatus', 'gravityLoginRequired');
  }
  for (const id of ids) {
    elements.set(id, {
      hidden: true, value: '', type: '',
      textContent: '', events: {}, classList: { add() {}, remove() {}, toggle() {} },
      focus() {}, setAttribute() {}, querySelectorAll: () => [],
      addEventListener(event, handler) { this.events[event] = handler; }
    });
  }
  const events = {};
  const document = {
    body: { style: {} },
    getElementById: id => elements.get(id) || null, querySelectorAll: () => [],
    addEventListener(name, handler) { events[name] = handler; }
  };
  let serverAdmin = initialAdmin;
  const requests = [];
  const navigations = [];
  const siteListeners = [];
  let siteState = { configured: true, admin: Boolean(initialAdmin) };
  const notifySite = (state) => {
    siteState = state;
    siteListeners.forEach((fn) => fn({ ...siteState }));
  };
  const context = {
    setInterval: () => 1, clearInterval() {}, document, Headers,
    window: {
      location: { replace(url) { navigations.push(url); } },
      addEventListener(name, handler) { events[name] = handler; },
      SiteAdmin: {
        getState: () => ({ ...siteState }),
        subscribe(fn) {
          siteListeners.push(fn);
          fn({ ...siteState });
          return () => {};
        },
        openLogin() { context._openedLogin = true; },
        async refresh() { return { ...siteState }; },
        async logout() {
          notifySite({ configured: true, admin: false });
          return { ...siteState };
        }
      }
    },
    fetch: async (url, options) => {
      requests.push({ url, ...options });
      if (url.endsWith('/verify') && testOptions.verifyFailure) {
        return { ok: false, status: 503, json: async () => ({ message: '인증 실패' }) };
      }
      if (url.includes('/api/gravity/connect/')) {
        return { ok: true, json: async () => ({ configured: true, admin: serverAdmin, connected: Boolean(testOptions.connected) }) };
      }
      return { ok: true, json: async () => ({ configured: true, admin: serverAdmin }) };
    }
  };
  context.window.AboutAdminAuth = {
    getToken: () => '',
    setToken() {},
    clearToken() {},
    applyAuthHeaders(headers) {
      const h = headers instanceof Headers ? headers : new Headers(headers || {});
      return h;
    }
  };
  vm.createContext(context);
  vm.runInContext(
    fs.readFileSync(path.resolve(__dirname, '../../src/main/resources/static/js/gravity-connect.js'), 'utf8'),
    context
  );
  await new Promise(setImmediate);
  const el = id => elements.get(id);
  return {
    el,
    requests,
    navigations,
    openedLogin: () => Boolean(context._openedLogin),
    setAdmin(admin) {
      serverAdmin = admin;
      notifySite({ configured: true, admin });
    }
  };
}

test('shared SiteAdmin login shows Gravity connection link without a page-local button', async () => {
  const p = await page(true);
  assert.equal(p.el('gravityConnectLink').hidden, false);
  assert.equal(p.el('gravityAdminHint').hidden, false);
});

test('guest hides Gravity admin controls until SiteAdmin reports login', async () => {
  const p = await page(false);
  assert.equal(p.el('gravityConnectLink').hidden, true);
  p.setAdmin(true);
  assert.equal(p.el('gravityConnectLink').hidden, false);
});

test('connect page login CTA opens the global SiteAdmin modal', async () => {
  const p = await page(false, { connectPage: true });
  assert.equal(p.el('gravityLoginRequired').hidden, false);
  await p.el('gravityLoginOpen').events.click();
  assert.equal(p.openedLogin(), true);
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
