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
${readCss('objekt-grid-cols.css')}
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
        <button type="button" class="objekt-filter-control" id="collection-season-toggle" aria-haspopup="true" aria-expanded="false" aria-controls="collection-season-menu">
          <span id="collection-season-label" class="truncate">전체</span>
        </button>
        <div id="collection-season-menu" class="objekt-dropdown-menu" hidden>
          <label class="objekt-choice"><input type="checkbox" value="all" class="season-checkbox" checked><span class="objekt-choice-text">전체 시즌</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Cream02" class="season-checkbox"><span class="objekt-choice-text">cream02</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Binary02" class="season-checkbox"><span class="objekt-choice-text">binary02</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Atom02" class="season-checkbox"><span class="objekt-choice-text">atom02</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Ever01" class="season-checkbox"><span class="objekt-choice-text">ever01</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Divine01" class="season-checkbox"><span class="objekt-choice-text">divine01</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Cream01" class="season-checkbox"><span class="objekt-choice-text">cream01</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Binary01" class="season-checkbox"><span class="objekt-choice-text">binary01</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Atom01" class="season-checkbox"><span class="objekt-choice-text">atom01</span></label>
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
<div class="objekt-result-bar collection-result-bar">
  <div class="objekt-cols-control" id="collection-cols-dropdown" data-objekt-cols-filter>
    <button type="button" class="objekt-cols-toggle" data-objekt-cols-toggle aria-label="열 수" aria-expanded="false" aria-controls="collection-cols-menu">
      <span class="objekt-cols-icon" aria-hidden="true"><span></span><span></span><span></span></span>
      <span class="objekt-cols-sr" data-objekt-cols-label>2열</span>
    </button>
    <div id="collection-cols-menu" class="objekt-dropdown-menu" data-objekt-cols-menu hidden>
      <button type="button" class="objekt-kind-option" data-objekt-cols-option="2">2열</button>
      <button type="button" class="objekt-kind-option" data-objekt-cols-option="4">4열</button>
    </div>
  </div>
</div>
<section class="collection-grid" style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.5rem;margin-top:1rem">
  <article class="collection-card" style="height:8rem;background:#191c27;border:1px solid #343b51">카드1</article>
  <article class="collection-card" style="height:8rem;background:#191c27;border:1px solid #343b51">카드2</article>
</section>
</main>
<script src="/js/objekt-grid-cols.js"></script>
<script src="/js/season-filters.js"></script>
<script>
(() => {
  function bind(toggleId, menuId) {
    const toggle = document.getElementById(toggleId);
    const menu = document.getElementById(menuId);
    toggle.addEventListener('click', (event) => {
      event.stopPropagation();
      window.ObjektDropdown.toggle(menu);
      toggle.setAttribute('aria-expanded', window.ObjektDropdown.isClosed(menu) ? 'false' : 'true');
    });
  }
  bind('collection-season-toggle', 'collection-season-menu');
  bind('collection-kind-toggle', 'collection-kind-menu');
  bind('collection-class-toggle', 'collection-class-menu');
  bind('collection-sort-toggle', 'collection-sort-menu');
})();
</script>
</body></html>`;

const objektHtml = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
.truncate{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.hidden{display:none!important}
${readCss('site-mobile.css')}
${readCss('objekt-page.css')}
${readCss('objekt-grid-cols.css')}
${readCss('season-filters.css')}
</style></head>
<body class="site-page">
<main class="main-content w-full max-w-[1440px] mx-auto px-4 sm:px-6 py-6 mb-auto">
<section class="card-box w-full relative z-30">
<form id="objekt-search-form">
  <div class="objekt-workspace">
    <div class="objekt-filter-bar relative">
      <div class="objekt-filter relative" id="season-dropdown">
        <span class="objekt-filter-label">시즌</span>
        <button type="button" class="objekt-filter-control" id="season-toggle" aria-expanded="false" aria-controls="season-menu" aria-label="시즌">
          <span id="season-label" class="truncate">전체</span>
        </button>
        <div id="season-menu" class="objekt-dropdown-menu hidden absolute top-full left-0 mt-2 rounded-xl border z-50 p-2 max-h-60 overflow-y-auto">
          <label class="objekt-choice"><input type="checkbox" value="all" class="season-checkbox" checked><span class="objekt-choice-text">전체 시즌</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Cream02" class="season-checkbox"><span class="objekt-choice-text">cream02</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Binary02" class="season-checkbox"><span class="objekt-choice-text">binary02</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Atom02" class="season-checkbox"><span class="objekt-choice-text">atom02</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Ever01" class="season-checkbox"><span class="objekt-choice-text">ever01</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Divine01" class="season-checkbox"><span class="objekt-choice-text">divine01</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Cream01" class="season-checkbox"><span class="objekt-choice-text">cream01</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Binary01" class="season-checkbox"><span class="objekt-choice-text">binary01</span></label>
          <label class="objekt-choice"><input type="checkbox" name="season" value="Atom01" class="season-checkbox"><span class="objekt-choice-text">atom01</span></label>
        </div>
      </div>
      <div class="objekt-filter relative" id="class-dropdown">
        <span class="objekt-filter-label">클래스</span>
        <button type="button" class="objekt-filter-control">전체</button>
      </div>
    </div>
  </div>
</form>
</section>
</main>
<script src="/js/season-filters.js"></script>
<script>
document.getElementById('season-toggle').addEventListener('click', () => {
  const menu = document.getElementById('season-menu');
  const open = menu.classList.contains('hidden') || menu.hidden;
  menu.classList.toggle('hidden', !open);
  menu.hidden = !open;
  document.getElementById('season-toggle').setAttribute('aria-expanded', open ? 'true' : 'false');
});
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
      if (url.pathname === '/cosmo/objekt') {
        return route.fulfill({ contentType: 'text/html', body: objektHtml });
      }
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
      const seasonGap = await page.evaluate(() => {
        const menu = document.getElementById('collection-season-menu');
        const filter = document.getElementById('collection-season-dropdown');
        return Math.round(menu.getBoundingClientRect().top - filter.getBoundingClientRect().bottom);
      });
      assert.ok(seasonGap >= 0 && seasonGap <= 16, '시즌 메뉴는 시즌 칩 바로 아래에 있어야 한다: ' + seasonGap);
      const chipCount = await page.locator('#collection-season-menu .season-chip').count();
      assert.ok(chipCount >= 3, 'season chips missing at ' + viewport.width);
      if (viewport.name === 'mobile') {
        const wrap = await page.evaluate(() => {
          const list = document.querySelector('#collection-season-menu .season-chip-list');
          const chips = [...document.querySelectorAll('#collection-season-menu .season-chip')];
          const tops = new Set(chips.map((chip) => Math.round(chip.getBoundingClientRect().top)));
          return { count: chips.length, rows: tops.size, width: Math.round(list.getBoundingClientRect().width) };
        });
        assert.ok(wrap.count >= 8, '시즌 칩 수가 부족하다: ' + JSON.stringify(wrap));
        assert.ok(wrap.width > 220, '시즌 패널이 너무 좁아 한 줄에 하나씩 쌓인다: ' + JSON.stringify(wrap));
        assert.ok(wrap.rows < wrap.count, '시즌 칩은 세로 나열이 아니라 행으로 감싸져야 한다: ' + JSON.stringify(wrap));
      }
      await page.screenshot({ path: `/opt/cursor/artifacts/collection-dropdown-season-${viewport.name}.png` });
      await openMenu('collection-class-toggle', 'collection-class-menu');
      await assertMenuVisible('collection-class-menu');
      const classGap = await page.evaluate(() => {
        const menu = document.getElementById('collection-class-menu');
        const filter = document.getElementById('collection-class-dropdown');
        return Math.round(menu.getBoundingClientRect().top - filter.getBoundingClientRect().bottom);
      });
      assert.ok(classGap >= 0 && classGap <= 16, '클래스 메뉴는 클래스 칩 바로 아래에 있어야 한다: ' + classGap);
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
      if (viewport.name === 'mobile') {
        const widths = await page.evaluate(() => {
          const menu = document.getElementById('collection-sort-menu');
          const filter = document.getElementById('collection-sort-dropdown');
          return { menu: menu.getBoundingClientRect().width, filter: filter.getBoundingClientRect().width };
        });
        assert.ok(Math.abs(widths.menu - widths.filter) <= 3, '모바일 드롭다운 너비가 필터와 같아야 한다: ' + JSON.stringify(widths));
        assert.equal(await page.locator('[data-objekt-cols-filter] .objekt-filter-label').count(), 0);
        assert.equal(await page.locator('.objekt-cols-icon span').count(), 3);
        await page.locator('[data-objekt-cols-toggle]').click();
        await page.waitForFunction(() => !document.getElementById('collection-cols-menu').hidden);
        await assertMenuVisible('collection-cols-menu');
        await page.locator('#collection-season-toggle').click();
        await page.waitForFunction(() => {
          const menu = document.getElementById('collection-season-menu');
          return menu && !menu.hidden && !menu.classList.contains('hidden') && getComputedStyle(menu).display !== 'none';
        });
        await assertMenuVisible('collection-season-menu');
        const attached = await page.evaluate(() => {
          const gap = (menuId, filterId) => {
            const menu = document.getElementById(menuId);
            const filter = document.getElementById(filterId);
            return Math.round(menu.getBoundingClientRect().top - filter.getBoundingClientRect().bottom);
          };
          return { season: gap('collection-season-menu', 'collection-season-dropdown') };
        });
        assert.ok(attached.season >= 0 && attached.season <= 16, '시즌 메뉴는 시즌 칩 바로 아래에 있어야 한다: ' + JSON.stringify(attached));
        await page.locator('#collection-class-toggle').click();
        await page.waitForFunction(() => {
          const menu = document.getElementById('collection-class-menu');
          return menu && !menu.hidden && !menu.classList.contains('hidden') && getComputedStyle(menu).display !== 'none';
        });
        const classAttached = await page.evaluate(() => {
          const menu = document.getElementById('collection-class-menu');
          const filter = document.getElementById('collection-class-dropdown');
          return Math.round(menu.getBoundingClientRect().top - filter.getBoundingClientRect().bottom);
        });
        assert.ok(classAttached >= 0 && classAttached <= 16, '클래스 메뉴는 클래스 칩 바로 아래에 있어야 한다: ' + classAttached);
        await openMenu('collection-sort-toggle', 'collection-sort-menu');
        await assertMenuVisible('collection-sort-menu');
        const colsFont = await page.evaluate(() => {
          const cols = document.querySelector('#collection-cols-menu .objekt-kind-option');
          const sort = document.querySelector('#collection-sort-menu .objekt-kind-option');
          return { cols: parseFloat(getComputedStyle(cols).fontSize), sort: parseFloat(getComputedStyle(sort).fontSize) };
        });
        assert.equal(colsFont.cols, colsFont.sort, '열 드롭다운 글자 크기가 정렬과 달라서는 안 된다');
      }
      await page.screenshot({ path: `/opt/cursor/artifacts/collection-dropdown-sort-${viewport.name}.png` });
    }

    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('https://collection-layout.test/cosmo/objekt', { waitUntil: 'load' });
    await page.locator('#season-toggle').click();
    await page.waitForFunction(() => {
      const list = document.querySelector('#season-menu .season-chip-list');
      if (!list) return false;
      const box = list.getBoundingClientRect();
      return box.width > 0 && box.height > 0;
    });
    await page.setViewportSize({ width: 390, height: 844 });
    const objektWrap = await page.evaluate(() => {
      const list = document.querySelector('#season-menu .season-chip-list');
      const chips = [...document.querySelectorAll('#season-menu .season-chip')];
      const tops = new Set(chips.map((chip) => Math.round(chip.getBoundingClientRect().top)));
      return {
        count: chips.length,
        rows: tops.size,
        width: Math.round(list.getBoundingClientRect().width),
        scroll: document.documentElement.scrollWidth,
        view: innerWidth
      };
    });
    assert.ok(objektWrap.count >= 8, '오브젝트 시즌 칩 수가 부족하다: ' + JSON.stringify(objektWrap));
    assert.ok(objektWrap.width > 220, '오브젝트 시즌 패널이 너무 좁다: ' + JSON.stringify(objektWrap));
    assert.ok(objektWrap.rows < objektWrap.count, '오브젝트 시즌 칩은 행으로 감싸져야 한다: ' + JSON.stringify(objektWrap));
    assert.ok(objektWrap.scroll <= objektWrap.view, '오브젝트 시즌 패널이 뷰포트를 넘치면 안 된다: ' + JSON.stringify(objektWrap));
    await page.screenshot({ path: `/opt/cursor/artifacts/objekt-season-chips-mobile.png` });

    console.log('PASS: 컬렉션 드롭다운이 모바일·데스크톱에서 잘리고 가려지지 않음');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
