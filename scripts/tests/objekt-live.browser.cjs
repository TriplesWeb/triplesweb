const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const html = `<!doctype html><html><head><meta charset="utf-8">
</head><body>
<form id="objekt-search-form" action="/cosmo/objekt" method="get">
<input type="hidden" name="page" value="0">
<input type="hidden" name="group" id="objekt-member-group" value="all">
<div id="member-dropdown">
  <button type="button" id="objekt-member-toggle"></button><span id="member-label"></span>
  <div id="objekt-member-picker"></div>
  <p id="objekt-other-notice" hidden></p>
</div>
<div id="season-dropdown"><span id="season-label"></span><div id="season-menu" class="hidden"></div></div>
<div id="class-dropdown"><span id="class-label"></span><div id="class-menu" class="hidden"></div></div>
<div class="objekt-filter"><input class="objekt-kind-select" name="kind" value="all"><button type="button" id="objekt-kind-toggle"></button><div id="kind-menu" class="hidden"></div></div>
<div class="objekt-filter" id="sort-dropdown">
  <button type="button" id="objekt-sort-toggle"><span id="sort-label">최신순</span></button>
  <label id="sort-native-control"><select name="sort" class="objekt-sort-select"><option value="latest">최신순</option></select></label>
  <div id="sort-menu" class="hidden"></div>
</div>
<input type="search" name="search">
<button type="submit" id="objekt-search-submit">검색</button>
<button type="button" id="objekt-reset">초기화</button>
</form>
<p id="objekt-result-summary" data-result-count="1" data-all-count="1" data-loaded="1">검색 결과 <strong>1</strong>개 <span id="objekt-all-count">1</span> <span id="objekt-loaded-count">1</span></p>
<div id="objekt-empty" class="hidden"></div>
<div id="objekt-card-grid">
  <article class="objekt-card objekt-item" data-objekt-key="old-card">
    <button type="button" class="objekt-open-detail">old</button>
    <p class="objekt-card-supply" data-supply-count>발행량 10개</p>
  </article>
</div>
<div id="objekt-scroll-sentinel" data-has-more="false" data-next-page="1">
  <p id="objekt-scroll-status"></p>
  <div id="objekt-scroll-spinner" class="hidden"></div>
</div>
<div id="objektDetailModal" hidden></div>
<script src="/js/objekt-page.js"></script>
<script src="/js/objekt-issued.js"></script>
<script src="/js/objekt-live.js"></script>
</body></html>`;

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage();
    let catalogSig = '1:old';
    let count = 10;
    const moreCalls = [];
    await page.route('**/*', async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === '/' || url.pathname === '/cosmo/objekt') {
        return route.fulfill({contentType: 'text/html', body: html});
      }
      if (url.pathname === '/cosmo/objekt/supplies') {
        return route.fulfill({contentType: 'application/json', body: JSON.stringify({})});
      }
      if (url.pathname === '/cosmo/objekt/pulse') {
        return route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({
            catalogSig,
            catalogCount: catalogSig === '2:new' ? 2 : 1,
            supplies: {'old-card': {status: 'available', count}},
            dropWindow: true,
            pollMs: 5000,
            forceRefreshMs: 600000,
            historyTtlMs: 15000
          })
        });
      }
      if (url.pathname === '/cosmo/objekt/more') {
        moreCalls.push(true);
        return route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({
            items: [
              {id: 'new-card', publicKey: 'new-card', collectionId: '신규 카드', member: 'HaYeon', mintCount: 1},
              {id: 'old-card', publicKey: 'old-card', collectionId: '기존 카드', member: 'HaYeon', mintCount: count}
            ],
            resultCount: 2,
            allCount: 2,
            hasMore: false,
            nextPage: null
          })
        });
      }
      const file = path.resolve(staticRoot, '.' + url.pathname);
      if (file.startsWith(staticRoot + path.sep) && fs.existsSync(file) && fs.statSync(file).isFile()) {
        return route.fulfill({
          path: file,
          contentType: url.pathname.endsWith('.js') ? 'application/javascript' : 'text/css'
        });
      }
      return route.fulfill({status: 404, body: ''});
    });

    await page.goto('https://live.test/cosmo/objekt', {waitUntil: 'load'});
    await page.waitForResponse((response) => response.url().includes('/cosmo/objekt/pulse') && response.ok());
    assert.equal(moreCalls.length, 0, '첫 pulse는 시그니처만 기억하고 목록을 다시 불러오지 않는다');

    count = 12;
    catalogSig = '2:new';
    await page.waitForFunction(() => document.querySelector('[data-objekt-key="new-card"]'), {timeout: 15000});
    assert.match(await page.locator('[data-objekt-key="old-card"] [data-supply-count]').textContent(), /12/);
    assert.ok(moreCalls.length >= 1);
    console.log('PASS: pulse가 새 오브젝트를 넣고 발행량을 메모리 스냅샷으로 올린다');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
