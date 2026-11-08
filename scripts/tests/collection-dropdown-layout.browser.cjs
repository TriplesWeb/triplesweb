const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');

function readCss(name) {
  return fs.readFileSync(path.join(staticRoot, 'css', name), 'utf8');
}

const html = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
.truncate{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
${readCss('site-mobile.css')}
${readCss('cosmo-collection.css')}
${readCss('season-filters.css')}
</style></head>
<body class="site-page collection-page">
<main class="gravity-page" style="padding:1rem">
<section class="collection-filter-panel" aria-label="보유 오브젝트 필터">
<form class="collection-filter-form" id="collection-filter-form">
  <div class="collection-filter-workspace">
    <div class="objekt-filter-bar collection-filter-bar relative">
      <div class="objekt-filter relative" id="collection-season-dropdown">
        <span class="objekt-filter-label">시즌</span>
        <button type="button" class="objekt-filter-control" id="collection-season-toggle" aria-expanded="false" aria-controls="collection-season-menu">
          <span id="collection-season-label" class="truncate">전체</span>
        </button>
        <div id="collection-season-menu" class="objekt-dropdown-menu" hidden>
          <label class="objekt-choice"><input type="checkbox" value="all" class="season-checkbox" checked><span class="objekt-choice-text">전체 시즌</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Atom01" class="season-checkbox"><span class="objekt-choice-text">Atom01</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Binary01" class="season-checkbox"><span class="objekt-choice-text">Binary01</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Cream02" class="season-checkbox"><span class="objekt-choice-text">Cream02</span></label>
        </div>
      </div>
      <div class="objekt-filter relative" id="collection-class-dropdown">
        <span class="objekt-filter-label">클래스</span>
        <button type="button" class="objekt-filter-control" id="collection-class-toggle">전체</button>
        <div id="collection-class-menu" class="objekt-dropdown-menu" hidden>
          <label class="objekt-choice"><input type="checkbox" value="all" class="class-checkbox" checked><span class="objekt-choice-text">전체 클래스</span></label>
          <label class="objekt-choice"><input type="checkbox" name="class" value="Special" class="class-checkbox"><span class="objekt-choice-text">Special</span></label>
          <label class="objekt-choice"><input type="checkbox" name="class" value="Double" class="class-checkbox"><span class="objekt-choice-text">Double</span></label>
        </div>
      </div>
      <div class="objekt-filter relative" id="collection-kind-dropdown">
        <span class="objekt-filter-label">종류</span>
        <button type="button" class="objekt-filter-control" id="collection-kind-toggle">전체</button>
        <div id="collection-kind-menu" class="objekt-dropdown-menu" hidden>
          <button type="button" class="objekt-kind-option">101Z</button>
          <button type="button" class="objekt-kind-option">201Z</button>
        </div>
      </div>
      <div class="objekt-filter relative" id="collection-sort-dropdown">
        <span class="objekt-filter-label">정렬</span>
        <button type="button" class="objekt-filter-control" id="collection-sort-toggle">최신순</button>
        <div id="collection-sort-menu" class="objekt-dropdown-menu collection-sort-menu" hidden>
          <button type="button" class="objekt-kind-option" data-value="latest">최신순</button>
          <button type="button" class="objekt-kind-option" data-value="oldest">오래된순</button>
          <button type="button" class="objekt-kind-option" data-value="last_member">마지막 멤버</button>
        </div>
      </div>
      <div class="objekt-search-row">
        <div class="objekt-search site-search"><input class="objekt-search-input" value=""></div>
      </div>
    </div>
  </div>
</form>
</section>
<section class="collection-grid" style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.5rem;margin-top:1rem">
  <article class="collection-card" style="height:8rem;background:#191c27;border:1px solid #343b51">카드1</article>
  <article class="collection-card" style="height:8rem;background:#191c27;border:1px solid #343b51">카드2</article>
</section>
</main>
<script src="/js/season-filters.js"></script>
<script>
(() => {
  function bind(toggleId, menuId) {
    const toggle = document.getElementById(toggleId);
    const menu = document.getElementById(menuId);
    toggle.addEventListener('click', (event) => {
      event.stopPropagation();
      const open = menu.hasAttribute('hidden');
      document.querySelectorAll('.objekt-dropdown-menu').forEach((other) => { if (other !== menu) other.hidden = true; });
      menu.hidden = !open;
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
      menu.closest('.objekt-filter')?.classList.toggle('is-open', open);
    });
  }
  bind('collection-season-toggle', 'collection-season-menu');
  bind('collection-kind-toggle', 'collection-kind-menu');
  bind('collection-class-toggle', 'collection-class-menu');
  bind('collection-sort-toggle', 'collection-sort-menu');
})();
</script>
</body></html>`;

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
      if (url.pathname === '/' || url.pathname.endsWith('.html') || url.pathname === '/cosmo/collection') {
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

    async function assertMenuVisible(menuId) {
      const info = await page.evaluate((id) => {
        const menu = document.getElementById(id);
        const bar = document.querySelector('.collection-filter-bar');
        const style = getComputedStyle(menu);
        const barStyle = getComputedStyle(bar);
        const box = menu.getBoundingClientRect();
        const sampleX = box.left + Math.min(24, Math.max(8, box.width / 2));
        const sampleY = box.top + Math.min(24, Math.max(8, box.height / 2));
        const top = document.elementFromPoint(sampleX, sampleY);
        return {
          display: style.display,
          visibility: style.visibility,
          overflowX: barStyle.overflowX,
          overflowY: barStyle.overflowY,
          height: box.height,
          width: box.width,
          top: box.top,
          bottom: box.bottom,
          viewportHeight: innerHeight,
          coveredBy: top && !menu.contains(top) && top !== menu ? (top.className || top.tagName) : ''
        };
      }, menuId);
      assert.notEqual(info.display, 'none', menuId + ' hidden by display');
      assert.notEqual(info.visibility, 'hidden', menuId + ' hidden by visibility');
      assert.ok(info.height > 36, menuId + ' too short: ' + info.height);
      assert.ok(info.width > 80, menuId + ' too narrow: ' + info.width);
      assert.ok(info.bottom <= info.viewportHeight + 1, menuId + ' clipped by viewport');
      assert.equal(info.overflowX, 'visible', 'filter bar overflow-x clips dropdowns');
      assert.ok(info.overflowY === 'visible' || info.overflowY === 'auto', 'filter bar overflow-y ' + info.overflowY);
      assert.equal(info.coveredBy, '', menuId + ' covered by ' + info.coveredBy);
      return info;
    }

    async function openMenu(toggleId, menuId) {
      await page.evaluate(() => {
        document.querySelectorAll('.objekt-dropdown-menu').forEach((menu) => { menu.hidden = true; });
        document.querySelectorAll('.objekt-filter').forEach((filter) => filter.classList.remove('is-open'));
      });
      await page.locator('#' + toggleId).click();
      await page.waitForFunction((id) => !document.getElementById(id).hidden, menuId);
    }

    fs.mkdirSync('/tmp/objekt-ui', { recursive: true });
    fs.mkdirSync('/opt/cursor/artifacts', { recursive: true });
    for (const viewport of [{ width: 1280, height: 900, name: 'desktop' }, { width: 390, height: 844, name: 'mobile' }]) {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await page.goto('https://collection-layout.test/cosmo/collection', { waitUntil: 'load' });
      await openMenu('collection-season-toggle', 'collection-season-menu');
      await assertMenuVisible('collection-season-menu');
      const chipCount = await page.locator('#collection-season-menu .season-chip').count();
      assert.ok(chipCount >= 3, 'season chips missing at ' + viewport.width);
      await page.screenshot({ path: `/opt/cursor/artifacts/collection-dropdown-season-${viewport.name}.png` });
      await openMenu('collection-class-toggle', 'collection-class-menu');
      await assertMenuVisible('collection-class-menu');
      await page.screenshot({ path: `/opt/cursor/artifacts/collection-dropdown-class-${viewport.name}.png` });
      await openMenu('collection-sort-toggle', 'collection-sort-menu');
      await assertMenuVisible('collection-sort-menu');
      const fonts = await page.evaluate(() => {
        const kind = document.querySelector('#collection-kind-menu .objekt-kind-option');
        const sort = document.querySelector('#collection-sort-menu .objekt-kind-option');
        const season = document.querySelector('#collection-season-menu .objekt-choice-text');
        return {
          kind: parseFloat(getComputedStyle(kind).fontSize),
          sort: parseFloat(getComputedStyle(sort).fontSize),
          season: parseFloat(getComputedStyle(season).fontSize)
        };
      });
      assert.equal(fonts.sort, fonts.kind, '정렬 항목 글자 크기가 종류와 달라서는 안 된다');
      assert.ok(Math.abs(fonts.sort - fonts.season) < 0.6, '정렬 항목 글자 크기가 시즌과 달라서는 안 된다');
      await page.screenshot({ path: `/opt/cursor/artifacts/collection-dropdown-sort-${viewport.name}.png` });
    }

    console.log('PASS: 컬렉션 드롭다운이 모바일·데스크톱에서 잘리고 가려지지 않음');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
