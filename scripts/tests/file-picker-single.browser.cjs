const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

// An explicit checkout lets the test run against a read-only integration worktree.
const root = path.resolve(process.argv[2] || path.join(__dirname, '../..'));
const resources = path.join(root, 'src/main/resources');
const origin = 'https://file-picker.test';
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIW2P8z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg==', 'base64');
const artist = { artistId: 1, name: '테스트 멤버', engName: 'Test Member', birthDate: '2005-01-01', nationality: '대한민국', revealDate: '2023-01-01', dormEntryDate: '2023-01-01', profileImage: '/original-profile.png', signatureImage: '/original-sign.png' };
const sticker = { id: 7, kind: 'UNIT', title: '테스트 스티커', eventDate: '2026-11-25', imageUrl: '/original-sticker.png' };

function extract(source, start, end) {
  const first = source.indexOf(start);
  const last = source.indexOf(end, first + start.length);
  assert.ok(first >= 0 && last > first, 'Missing fixture section: ' + start);
  return source.slice(first, last);
}

function fixture(page) {
  const common = '<link rel="stylesheet" href="/css/file-picker.css"><script defer src="/js/file-picker.js"></script>';
  const readTemplate = name => fs.readFileSync(path.join(resources, 'templates', name + '.mustache'), 'utf8');
  let body;
  if (page === '/artist') {
    const modal = extract(readTemplate('triples/artist'), '<div id="artistEditModal"', '<script>')
      .replace('{{>partials/unit-checkbox-chips}}', '');
    body = '<link rel="stylesheet" href="/css/artist.css"><style>.hidden{display:none!important}.flex{display:flex}</style>'
      + '<button type="button" data-artist-edit="1">멤버 수정</button>' + modal
      + '<script>window.SiteFeedback = {confirm: async () => true, beginBusy: () => () => {}, alert: message => {throw new Error(message)}};</script>'
      + '<script defer src="/js/artist.js"></script>';
  } else {
    const template = readTemplate('schedule');
    const modal = extract(template, '<div class="site-modal" id="decorationModal"', '{{/isAdmin}}');
    const manager = extract(template, 'function bindDecorationManager(', '{{/isAdmin}}');
    body = '<link rel="stylesheet" href="/css/site-ui.css"><script defer src="/js/site-ui.js"></script>'
      + '<button id="openDecorationManager" type="button">스티커 관리</button>' + modal
      + '<script>' + manager + '\nfunction scheduleModal(element) { return window.SiteUi.modal(element); }\n'
      + 'document.addEventListener("DOMContentLoaded", () => bindDecorationManager(() => {}));</script>';
  }
  return '<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
    + common + '</head><body>' + body + '</body></html>';
}

function picker(page, id) {
  const input = page.locator('#' + id);
  const field = page.locator('[data-file-drop="single"]').filter({ has: input });
  return {
    input, zone: field.locator('.u432-dz'), preview: field.locator('.u432-preview-img'),
    restore: field.getByRole('button', { name: '되돌리기', exact: true })
  };
}

async function choose(page, field, file) {
  const chooserPromise = page.waitForEvent('filechooser');
  await field.zone.press('Enter');
  await (await chooserPromise).setFiles(file);
}

async function drop(page, field, name) {
  const dataTransfer = await page.evaluateHandle(({ name, bytes }) => {
    const dt = new DataTransfer();
    dt.items.add(new File([Uint8Array.from(bytes)], name, { type: 'image/png' }));
    return dt;
  }, { name, bytes: [...png] });
  try {
    for (const event of ['dragenter', 'dragover', 'drop']) {
      await field.zone.dispatchEvent(event, { dataTransfer });
    }
  } finally {
    await dataTransfer.dispose();
  }
}

async function preview(page, field, expected) {
  await field.preview.waitFor({ state: 'visible' });
  const src = await field.preview.getAttribute('src');
  assert.match(src || '', expected);
  assert.ok(await field.preview.evaluate(img => img.decode().then(() => img.naturalWidth > 0)), 'Preview loads');
}

const selectedFiles = field => field.input.evaluate(input => Array.from(input.files, file => file.name));
const upload = name => ({ name, mimeType: 'image/png', buffer: png });

async function assertMultipart(request, entries) {
  const payload = await new Response(request.postDataBuffer(), {
    headers: { 'content-type': request.headers()['content-type'] }
  }).formData();
  for (const [field, expected] of Object.entries(entries)) {
    const value = payload.get(field);
    if (expected.endsWith('.png')) {
      assert.equal(value?.name, expected, field);
      assert.equal(value.type, 'image/png', field);
      assert.deepEqual(Buffer.from(await value.arrayBuffer()), png, field);
    } else {
      assert.equal(value, expected, field);
    }
  }
}

async function verifyArtist(page, posts) {
  await page.goto(origin + '/artist');
  const open = page.getByRole('button', { name: '멤버 수정' });
  const modal = page.locator('#artistEditModal');
  const profile = picker(page, 'artistEditImage');
  const sign = picker(page, 'artistEditSignatureImage');
  const clearProfile = page.locator('#artistEditClearImage');
  const clearSign = page.locator('#artistEditClearSignatureImage');
  await open.click();
  await modal.waitFor();
  await preview(page, profile, /^\/original-profile\.png$/);
  await preview(page, sign, /^\/original-sign\.png$/);
  await clearProfile.check();
  await clearSign.check();
  await choose(page, profile, upload('profile.png'));
  await preview(page, profile, /^blob:/);
  assert.equal(await clearProfile.isChecked(), false);
  assert.equal(await clearSign.isChecked(), true);
  await profile.restore.click();
  await preview(page, profile, /^\/original-profile\.png$/);
  assert.deepEqual(await selectedFiles(profile), []);

  await drop(page, sign, 'signature.png');
  await preview(page, sign, /^blob:/);
  assert.equal(await clearSign.isChecked(), false);
  await modal.getByRole('button', { name: '취소', exact: true }).click();
  await modal.waitFor({ state: 'hidden' });
  assert.deepEqual(await selectedFiles(sign), []);
  await open.click();
  await modal.waitFor();
  await preview(page, sign, /^\/original-sign\.png$/);
  await preview(page, profile, /^\/original-profile\.png$/);
  assert.equal(posts.length, 0);

  await choose(page, profile, upload('profile.png'));
  await drop(page, sign, 'signature.png');
  await modal.getByRole('button', { name: '저장', exact: true }).click();
  await modal.locator('#artistEditError').waitFor();
  assert.equal(posts.length, 1);
  await assertMultipart(posts[0], { image: 'profile.png', signatureImage: 'signature.png', clearImage: 'false', clearSignatureImage: 'false' });
}

async function verifySchedule(page, posts) {
  await page.goto(origin + '/schedule');
  const open = page.getByRole('button', { name: '스티커 관리' });
  const modal = page.locator('#decorationModal');
  const field = picker(page, 'decorationImage');
  const clear = page.locator('#decorationClearImage');
  await open.click();
  await modal.getByRole('button', { name: '수정', exact: true }).click();
  await preview(page, field, /^\/original-sticker\.png$/);
  await clear.check();
  await choose(page, field, { name: 'large.png', mimeType: 'image/png', buffer: Buffer.alloc(10 * 1024 * 1024 + 1) });
  await page.getByText('이미지는 10MB 이하만 가능합니다.', { exact: true }).waitFor();
  assert.deepEqual(await selectedFiles(field), []);
  assert.equal(await clear.isChecked(), true);
  await preview(page, field, /^\/original-sticker\.png$/);

  await drop(page, field, 'sticker.png');
  await preview(page, field, /^blob:/);
  assert.equal(await clear.isChecked(), false);
  await field.restore.click();
  await preview(page, field, /^\/original-sticker\.png$/);
  await choose(page, field, upload('sticker.png'));
  await modal.getByRole('button', { name: '닫기', exact: true }).click();
  await modal.waitFor({ state: 'hidden' });
  assert.deepEqual(await selectedFiles(field), []);
  await open.click();
  await preview(page, field, /^\/original-sticker\.png$/);
  assert.equal(posts.length, 1);

  await clear.check();
  await drop(page, field, 'sticker.png');
  await modal.getByRole('button', { name: '저장', exact: true }).click();
  await page.waitForFunction(() => document.querySelector('#decorationTitle').value === '');
  assert.equal(posts.length, 2);
  await assertMultipart(posts[1], { image: 'sticker.png', clearImage: 'false', title: sticker.title });
}

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 375, height: 800 } });
    const posts = [];
    const errors = [];
    page.setDefaultTimeout(10000);
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', async route => {
      const request = route.request();
      const url = new URL(request.url());
      if (url.origin !== origin) return route.abort();
      if (request.method() === 'POST') {
        assert.ok(['/api/artist/1', '/api/calendar-decorations/7'].includes(url.pathname));
        posts.push(request);
        if (url.pathname === '/api/artist/1') return route.fulfill({ status: 400, json: { message: 'Captured by fixture' } });
        return route.fulfill({ json: sticker });
      }
      if (request.method() !== 'GET') return route.abort();
      if (url.pathname === '/artist' || url.pathname === '/schedule') return route.fulfill({ contentType: 'text/html; charset=utf-8', body: fixture(url.pathname) });
      if (url.pathname === '/api/artist/admin/status') return route.fulfill({ json: { configured: true, admin: true } });
      if (url.pathname === '/api/artist/1') return route.fulfill({ json: artist });
      if (url.pathname === '/api/calendar-decorations') return route.fulfill({ json: [sticker] });
      if (/^\/original-(profile|sign|sticker)\.png$/.test(url.pathname)) return route.fulfill({ contentType: 'image/png', body: png });
      if (/^\/(js|css)\/[\w-]+\.(js|css)$/.test(url.pathname)) {
        return route.fulfill({ path: path.join(resources, 'static', url.pathname) });
      }
      return route.abort();
    });
    await verifyArtist(page, posts);
    console.log('PASS: artist profile and signature upload');
    await verifySchedule(page, posts);
    console.log('PASS: schedule sticker upload and 10MB limit');
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
