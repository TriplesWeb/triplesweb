const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '../..');
const html = `<!doctype html><html><head><meta charset="utf-8">
<style>.is-hidden{display:none}</style>
</head><body class="collection-page">
<main class="gravity-page">
<form class="collection-filter-form" method="get" action="/cosmo/collection" id="collection-filter-form">
  <input type="hidden" name="nickname" value="테스트">
  <input type="hidden" name="page" value="0">
  <input type="hidden" name="group" id="collection-group-input" value="all">
  <input type="hidden" name="member" id="collection-member-input" value="">
  <input type="hidden" name="kind" id="collection-kind-input" value="all">
  <input type="hidden" name="sort" id="collection-sort-input" value="latest">
  <div class="objekt-member-picker-panel" id="collection-member-panel">
    <div class="mpp-toolbar">
      <button type="button" class="objekt-member-toggle" id="collection-member-toggle" aria-expanded="false" aria-controls="collection-member-picker">
        <span><span class="mpp-toolbar-title">멤버</span><span class="mpp-toolbar-meta" id="collection-member-meta">전체 오브젝트</span></span>
      </button>
      <div class="mpp-toolbar-actions">
        <button type="button" class="mpp-tool-btn collection-group-tab" data-objekt-group="all" aria-pressed="true">전체</button>
        <button type="button" class="mpp-tool-btn collection-group-tab" data-objekt-group="members" aria-pressed="false">멤버</button>
        <button type="button" class="mpp-tool-btn collection-group-tab" data-objekt-group="other" aria-pressed="false">기타 옵젝</button>
      </div>
    </div>
    <div class="member-portrait-picker" id="collection-member-picker" role="group" aria-label="멤버 필터">
      <button type="button" class="mpp-item" data-value="윤서연">윤서연</button>
      <button type="button" class="mpp-item" data-value="정하연">정하연</button>
    </div>
    <p class="objekt-other-notice" id="collection-other-notice" hidden>기타</p>
  </div>
  <div class="objekt-filter-bar collection-filter-bar relative">
    <div class="objekt-filter relative" id="collection-season-dropdown">
      <span class="objekt-filter-label">시즌</span>
      <button type="button" class="objekt-filter-control" id="collection-season-toggle" aria-expanded="false" aria-controls="collection-season-menu">
        <span id="collection-season-label" class="truncate">전체</span>
      </button>
      <div id="collection-season-menu" class="objekt-dropdown-menu" hidden>
        <label class="objekt-choice"><input type="checkbox" value="all" class="season-checkbox" checked><span class="objekt-choice-text">전체 시즌</span></label>
        <label class="objekt-choice"><input type="checkbox" name="season" value="Atom01" class="season-checkbox"><span class="objekt-choice-text">Atom01</span></label>
      </div>
    </div>
    <div class="objekt-filter relative" id="collection-class-dropdown">
      <span class="objekt-filter-label">클래스</span>
      <button type="button" class="objekt-filter-control" id="collection-class-toggle" aria-expanded="false" aria-controls="collection-class-menu">
        <span id="collection-class-label" class="truncate">전체</span>
      </button>
      <div id="collection-class-menu" class="objekt-dropdown-menu" hidden>
        <label class="objekt-choice"><input type="checkbox" value="all" class="class-checkbox" checked><span class="objekt-choice-text">전체 클래스</span></label>
        <label class="objekt-choice"><input type="checkbox" name="class" value="Special" class="class-checkbox"><span class="objekt-choice-text">Special</span></label>
      </div>
    </div>
    <div class="objekt-filter relative" id="collection-kind-dropdown">
      <span class="objekt-filter-label">종류</span>
      <div class="objekt-filter-control objekt-kind-combo">
        <input type="text" id="collection-kind-select" class="objekt-kind-select" value="전체" role="combobox" aria-expanded="false" aria-controls="collection-kind-menu">
        <button type="button" class="objekt-kind-toggle" id="collection-kind-toggle" tabindex="-1"></button>
      </div>
      <div id="collection-kind-menu" class="objekt-dropdown-menu hidden" hidden role="listbox">
        <button type="button" class="objekt-kind-option" data-value="all" role="option">전체</button>
        <button type="button" class="objekt-kind-option" data-value="101Z" role="option">101Z</button>
        <p class="objekt-kind-empty" id="collection-kind-empty" hidden>없음</p>
      </div>
    </div>
    <div class="objekt-filter relative" id="collection-sort-dropdown">
      <span class="objekt-filter-label">정렬</span>
      <button type="button" class="objekt-filter-control" id="collection-sort-toggle" aria-haspopup="listbox" aria-expanded="false" aria-controls="collection-sort-menu">
        <span id="collection-sort-label" class="truncate">최신순</span>
      </button>
      <div id="collection-sort-menu" class="objekt-dropdown-menu collection-sort-menu" hidden role="listbox">
        <button type="button" class="objekt-kind-option" data-value="latest" role="option">최신순</button>
        <button type="button" class="objekt-kind-option" data-value="oldest" role="option">오래된순</button>
        <button type="button" class="objekt-kind-option" data-value="last_member" role="option">마지막 멤버</button>
      </div>
    </div>
  </div>
  <div class="objekt-search-row">
    <div class="objekt-search site-search">
      <label><input type="search" name="q" value="" class="objekt-search-input"></label>
      <button type="submit" class="objekt-search-button">검색</button>
    </div>
  </div>
</form>
<section class="ar-grid collection-grid" id="collection-grid" data-loaded="1" data-filtered="5" data-owned="5">
  <article class="card collection-card" data-card="seed">초기카드</article>
</section>
<p class="gv-panel gv-empty is-hidden" id="collection-empty">없음</p>
<div class="collection-scroll-footer" id="collection-scroll-sentinel" data-has-more="false" data-loading="false" data-next-page="1">
  <p id="collection-scroll-status"></p>
  <span id="collection-scroll-spinner" class="is-hidden"></span>
</div>
</main>
<script src="/js/member-portrait-picker.js"></script>
<script src="/js/objekt-kind-combo.js"></script>
SCRIPT_PLACEHOLDER
</body></html>`;

(async () => {
  const mustache = fs.readFileSync(path.join(root, 'src/main/resources/templates/cosmo/collection.mustache'), 'utf8');
  const scriptMatch = mustache.match(/<script>\s*\(\(\) => \{[\s\S]*?\}\)\(\);\s*<\/script>/);
  assert.ok(scriptMatch, 'collection.mustache inline filter script missing');
  const pageHtml = html.replace('SCRIPT_PLACEHOLDER', scriptMatch[0]);
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage();
    const moreCalls = [];
    await page.route('**/*', (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === '/' || url.pathname === '/cosmo/collection') {
        return route.fulfill({ contentType: 'text/html', body: pageHtml });
      }
      if (url.pathname === '/cosmo/collection/more') {
        const params = Object.fromEntries(url.searchParams);
        moreCalls.push({
          ...params,
          season: url.searchParams.getAll('season'),
          class: url.searchParams.getAll('class')
        });
        const member = url.searchParams.get('member') || '';
        const season = url.searchParams.get('season') || '';
        const group = url.searchParams.get('group') || 'all';
        const kind = url.searchParams.get('kind') || 'all';
        const sort = url.searchParams.get('sort') || 'latest';
        const klass = url.searchParams.get('class') || '';
        const query = url.searchParams.get('q') || '';
        const label = [
          group !== 'all' ? group : '',
          member,
          season,
          klass,
          kind !== 'all' ? kind : '',
          sort !== 'latest' ? sort : '',
          query
        ].filter(Boolean).join('|') || 'filtered';
        return route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({
            items: [{
              collectionId: label + ' 카드',
              cardTitle: label,
              member: member || '멤버',
              season: season || 'Cream02',
              objektClass: klass || 'First',
              collectionNo: '1',
              hasSerial: true,
              serial: '9',
              hasImage: false,
              hasDetail: false
            }],
            filteredCount: 3,
            ownedCount: 5,
            hasMore: false,
            loading: false,
            nextPage: null
          })
        });
      }
      const file = path.resolve(root, 'src/main/resources/static', '.' + url.pathname);
      if (file.startsWith(path.join(root, 'src/main/resources/static') + path.sep) && fs.existsSync(file)) {
        return route.fulfill({
          path: file,
          contentType: url.pathname.endsWith('.js') ? 'application/javascript' : 'text/css'
        });
      }
      return route.fulfill({ status: 404, body: '' });
    });

    await page.goto('https://collection-filters.test/cosmo/collection?nickname=%ED%85%8C%EC%8A%A4%ED%8A%B8', { waitUntil: 'load' });

    async function waitForMore(predicate) {
      await page.waitForFunction((expected) => {
        const grid = document.querySelector('#collection-grid');
        return Boolean(grid && grid.textContent && grid.textContent.includes(expected));
      }, predicate);
    }

    moreCalls.length = 0;
    await page.locator('.mpp-item[data-value="윤서연"]').click();
    await waitForMore('윤서연');
    assert.ok(moreCalls.some((call) => call.member === '윤서연' && call.group === 'members'));
    assert.equal(await page.locator('#collection-member-input').inputValue(), '윤서연');

    moreCalls.length = 0;
    await page.locator('.collection-group-tab[data-objekt-group="other"]').click();
    await waitForMore('other');
    assert.ok(moreCalls.some((call) => call.group === 'other'));

    moreCalls.length = 0;
    const seasonWait = page.waitForResponse((res) => {
      try { return new URL(res.url()).searchParams.getAll('season').includes('Atom01'); } catch { return false; }
    });
    await page.locator('.season-checkbox[value="Atom01"]').evaluate((el) => {
      el.checked = true;
      el.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await seasonWait;
    await waitForMore('Atom01');
    assert.ok(moreCalls.some((call) => (call.season || []).includes('Atom01')));

    moreCalls.length = 0;
    const classWait = page.waitForResponse((res) => {
      try { return new URL(res.url()).searchParams.getAll('class').includes('Special'); } catch { return false; }
    });
    await page.locator('.class-checkbox[value="Special"]').evaluate((el) => {
      el.checked = true;
      el.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await classWait;
    await waitForMore('Special');
    assert.ok(moreCalls.some((call) => (call.class || []).includes('Special')));

    moreCalls.length = 0;
    const kindWait = page.waitForResponse((res) => {
      try { return new URL(res.url()).searchParams.get('kind') === '101Z'; } catch { return false; }
    });
    await page.locator('.objekt-kind-option[data-value="101Z"]').evaluate((el) => el.click());
    await kindWait;
    await waitForMore('101Z');
    assert.ok(moreCalls.some((call) => call.kind === '101Z'));

    moreCalls.length = 0;
    const sortWait = page.waitForResponse((res) => {
      try { return new URL(res.url()).searchParams.get('sort') === 'oldest'; } catch { return false; }
    });
    await page.locator('#collection-sort-menu .objekt-kind-option[data-value="oldest"]').evaluate((el) => el.click());
    await sortWait;
    await waitForMore('oldest');
    assert.ok(moreCalls.some((call) => call.sort === 'oldest'));

    moreCalls.length = 0;
    const lastMemberWait = page.waitForResponse((res) => {
      try { return new URL(res.url()).searchParams.get('sort') === 'last_member'; } catch { return false; }
    });
    await page.locator('#collection-sort-toggle').click();
    await page.locator('#collection-sort-menu .objekt-kind-option[data-value="last_member"]').dispatchEvent('pointerdown', { button: 0 });
    await page.locator('#collection-sort-menu .objekt-kind-option[data-value="last_member"]').click();
    await lastMemberWait;
    await waitForMore('last_member');
    assert.ok(moreCalls.some((call) => call.sort === 'last_member'));

    moreCalls.length = 0;
    const searchWait = page.waitForResponse((res) => {
      try { return new URL(res.url()).searchParams.get('q') === 'cream'; } catch { return false; }
    });
    await page.locator('input[name="q"]').fill('cream');
    await searchWait;
    await waitForMore('cream');
    assert.ok(moreCalls.some((call) => call.q === 'cream'));

    console.log('PASS: 컬렉션 멤버·그룹·시즌·클래스·종류·정렬·검색 변경 시 검색 버튼 없이 결과 갱신');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
