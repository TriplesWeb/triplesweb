const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');

const html = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:sans-serif}
.objekt-filter-bar{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:.5rem;padding:1rem}
.objekt-filter{position:relative;display:flex;align-items:center;min-height:2.65rem;padding:.25rem .75rem;border:1px solid #343b51;border-radius:.75rem}
.objekt-filter-control{border:0;background:transparent;color:inherit}
.objekt-dropdown-menu{position:absolute;top:100%;left:0;background:#171a24;border:1px solid #343b51}
.objekt-dropdown-menu.hidden,[hidden]{display:none}
.objekt-kind-option{display:block;width:100%;padding:.45rem .7rem;border:0;background:transparent;color:inherit}
.demo-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;padding:1rem}
@media(min-width:768px){.demo-grid{grid-template-columns:repeat(5,minmax(0,1fr))}}
.objekt-card-body,.collection-card-body{padding:.7rem}
.objekt-card-foot,.collection-card-foot{display:flex}
</style>
<link rel="stylesheet" href="/css/objekt-grid-cols.css">
</head>
<body>
<div class="objekt-filter-bar">
  <div class="objekt-search-row">
    <div class="objekt-cols-control" data-objekt-cols-filter>
      <button type="button" class="objekt-cols-toggle" data-objekt-cols-toggle aria-label="열 수" aria-expanded="false">
        <span class="objekt-cols-icon" aria-hidden="true"><span></span><span></span><span></span></span>
        <span class="objekt-cols-sr" data-objekt-cols-label>2열</span>
      </button>
      <div class="objekt-dropdown-menu hidden" data-objekt-cols-menu hidden>
        <button type="button" class="objekt-kind-option" data-objekt-cols-option="2">2열</button>
        <button type="button" class="objekt-kind-option" data-objekt-cols-option="3">3열</button>
        <button type="button" class="objekt-kind-option" data-objekt-cols-option="4">4열</button>
        <button type="button" class="objekt-kind-option" data-objekt-cols-option="5">5열</button>
      </div>
    </div>
  </div>
</div>
<div class="demo-grid" data-objekt-grid data-cols="2">
  <article class="objekt-card"><div class="objekt-card-body"><p>제목</p><div class="objekt-card-foot">멤버</div></article>
  <article class="objekt-card"><div class="objekt-card-body"><p>제목</p><div class="objekt-card-foot">멤버</div></article>
  <article class="objekt-card"><div class="objekt-card-body"><p>제목</p><div class="objekt-card-foot">멤버</div></article>
  <article class="objekt-card"><div class="objekt-card-body"><p>제목</p><div class="objekt-card-foot">멤버</div></article>
</div>
<script src="/js/objekt-grid-cols.js"></script>
</body></html>`;

function columnCount(value) {
  const px = value.match(/[\d.]+px/g);
  if (px && px.length) return px.length;
  return value.trim().split(/\s+/).filter(Boolean).length;
}

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage();
    await page.route('**/*', (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === '/' || url.pathname.endsWith('.html')) {
        return route.fulfill({ contentType: 'text/html', body: html });
      }
      const file = path.resolve(staticRoot, '.' + url.pathname);
      if (file.startsWith(staticRoot + path.sep) && fs.existsSync(file)) {
        return route.fulfill({
          path: file,
          contentType: url.pathname.endsWith('.js') ? 'application/javascript' : 'text/css'
        });
      }
      return route.fulfill({ status: 404, body: '' });
    });

    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('https://objekt-cols.test/', { waitUntil: 'load' });
    assert.equal(await page.locator('[data-objekt-cols-filter]').evaluate((n) => getComputedStyle(n).display !== 'none'), true);
    assert.equal(await page.locator('.objekt-cols-icon span').count(), 3);
    assert.equal(await page.locator('[data-objekt-cols-filter] .objekt-filter-label').count(), 0);
    assert.equal(await page.locator('[data-objekt-grid]').getAttribute('data-cols'), '2');
    assert.equal(columnCount(await page.locator('[data-objekt-grid]').evaluate((n) => getComputedStyle(n).gridTemplateColumns)), 2);

    await page.locator('[data-objekt-cols-toggle]').click();
    await page.locator('[data-objekt-cols-option="4"]').click();
    assert.equal(await page.locator('[data-objekt-grid]').getAttribute('data-cols'), '4');
    assert.equal(await page.locator('[data-objekt-cols-label]').innerText(), '4열');
    assert.equal(columnCount(await page.locator('[data-objekt-grid]').evaluate((n) => getComputedStyle(n).gridTemplateColumns)), 4);
    assert.equal(await page.locator('.objekt-card-foot').first().evaluate((n) => getComputedStyle(n).display), 'none');
    assert.equal(await page.evaluate(() => localStorage.getItem('triples-objekt-cols')), '4');

    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('https://objekt-cols.test/', { waitUntil: 'load' });
    assert.equal(await page.locator('[data-objekt-cols-filter]').evaluate((n) => getComputedStyle(n).display), 'none');
    assert.equal(await page.locator('[data-objekt-grid]').getAttribute('data-cols'), '4');
    assert.equal(columnCount(await page.locator('[data-objekt-grid]').evaluate((n) => getComputedStyle(n).gridTemplateColumns)), 5);
    assert.notEqual(await page.locator('.objekt-card-foot').first().evaluate((n) => getComputedStyle(n).display), 'none');

    console.log('PASS: 모바일 열 수 드롭다운이 2·4열을 바꾸고 데스크톱은 기존 열을 유지한다');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
