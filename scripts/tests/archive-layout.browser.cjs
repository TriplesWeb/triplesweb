const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const css = fs.readFileSync(path.join(staticRoot, 'css', 'archive-page.css'), 'utf8');
const site = fs.readFileSync(path.join(staticRoot, 'css', 'site.css'), 'utf8');
const siteChrome = fs.readFileSync(path.join(staticRoot, 'css', 'site-chrome.css'), 'utf8');
const siteUi = fs.readFileSync(path.join(staticRoot, 'css', 'site-ui.css'), 'utf8');
const siteMobile = fs.readFileSync(path.join(staticRoot, 'css', 'site-mobile.css'), 'utf8');
const objektFilter = fs.readFileSync(path.join(staticRoot, 'css', 'objekt-filter.css'), 'utf8');
const browseJs = fs.readFileSync(path.join(staticRoot, 'js', 'archive-browse.js'), 'utf8');
const dateJs = fs.readFileSync(path.join(staticRoot, 'js', 'date-range-filter.js'), 'utf8');

const members = ['윤서연', '정혜린', '이지우', '김채연', '김유연', '김수민', '김나경', '공유빈', '카에데', '서다현', '코토네', '곽연지', '니엔', '박소현', '신위', '마유', '주빈'];

function chip(name) {
  return `<span class="archive-member-chip archive-symbol-chip" style="--member-color:#7bba8d" title="${name}" aria-label="${name}"><img src="/S1.png" alt="${name}"><span class="archive-chip-name">${name}</span></span>`;
}

const html = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
*,*::before,*::after{box-sizing:border-box}
img,video{max-width:100%;height:auto}
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
${site}
${siteChrome}
${css}
${objektFilter}
${siteUi}
${siteMobile}
</style></head>
<body class="site-page">
<main class="archive-page is-browse" data-archive-kind="image" data-date-label="사진 날짜">
  <section class="ar-hero ar-hero-browse">
    <div class="ar-panel ar-headline">
      <div class="ar-headline-body is-copy-only">
        <div class="ar-headline-copy">
          <div class="ar-kicker">Archive</div>
          <h1>아카이브</h1>
          <p>사진 전체</p>
        </div>
      </div>
    </div>
  </section>
  <div class="ar-folder-tabs">
    <nav class="site-section-tabs ar-type-tabs" aria-label="아카이브 종류">
      <a class="site-section-tab" href="/archive" aria-current="page">사진</a>
      <a class="site-section-tab" href="/archive?type=video">영상</a>
    </nav>
    <div class="ar-source-bar">
      <nav class="site-section-tabs ar-source-tabs" aria-label="자료 출처">
        <a class="site-section-tab" href="/archive" aria-current="page">전체</a>
      </nav>
      <div class="ar-source-tools">
        <button type="button" class="site-btn site-btn--ghost site-btn--sm ar-source-manage" data-admin-only id="archiveSourceManageBtn">출처 관리</button>
        <button type="button" class="site-btn site-btn--ghost site-btn--sm ar-source-bulk" data-admin-only id="archiveSourceBulkBtn" aria-pressed="false">출처 바꾸기</button>
        <button type="button" class="site-btn site-btn--primary site-btn--sm ar-submit ar-home-upload" data-admin-only>사진 추가</button>
        <label class="ar-density">
          <select id="archivePageSize" name="size" aria-label="페이지당 개수"><option value="10" selected>10개</option></select>
        </label>
      </div>
    </div>
  </div>
  <section class="ar-panel ar-toolbar">
    <form class="ar-filters" id="archiveFilterForm">
      <div class="ar-filter-tools objekt-filter-bar">
        <div class="objekt-filter ar-sort relative">
          <span class="objekt-filter-label" id="archiveSortLabel">정렬</span>
          <span class="ar-sort-control objekt-filter-control objekt-filter-select-wrap">
            <select id="archiveSort" aria-labelledby="archiveSortLabel"><option value="newest">최신순</option><option value="oldest">오래된 순</option><option value="first-member" data-member-sort>멤버번호 낮은순</option><option value="last-member" data-member-sort>멤버번호 높은순</option></select>
            <span class="material-symbols-outlined objekt-filter-caret">arrow_drop_down</span>
          </span>
        </div>
        <div class="objekt-filter ar-date-filter relative" data-date-filter>
          <span class="objekt-filter-label">날짜</span>
          <button type="button" class="ar-date-toggle objekt-filter-control" data-date-toggle aria-expanded="false"><span class="truncate">전체</span><span class="material-symbols-outlined objekt-filter-caret">arrow_drop_down</span></button>
          <div class="ar-date-popover" data-date-popover hidden>
            <label>사진 날짜 시작<input type="date" name="from" data-date-from lang="ko"></label>
            <label>사진 날짜 끝<input type="date" name="to" data-date-to lang="ko"></label>
            <div class="ar-date-actions">
              <button type="button" class="site-btn site-btn--reset site-btn--sm" data-date-clear>초기화</button>
              <button type="submit" class="site-btn site-btn--primary site-btn--sm ar-date-apply">적용</button>
            </div>
          </div>
        </div>
        <div class="objekt-filter ar-member-filter relative">
          <span class="objekt-filter-label">멤버</span>
          <button type="button" class="ar-member-toggle objekt-filter-control" id="archiveMemberToggle" aria-expanded="false" aria-controls="archiveMemberPanel"><span data-member-toggle-label class="truncate">멤버 선택</span><span class="material-symbols-outlined objekt-filter-caret ar-member-chevron">expand_more</span></button>
        </div>
        <div class="ar-search-row objekt-search-row">
          <div class="ar-search-wrap objekt-search site-search">
            <span class="objekt-search-glyph" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></svg></span>
            <label class="ar-search-field"><input class="ar-search objekt-search-input site-search__input" type="search" name="q" value="심장" placeholder="사진 제목 검색"></label>
          </div>
          <a class="site-btn site-btn--reset ar-reset objekt-reset-button" href="/archive">초기화</a>
        </div>
      </div>
    </form>
    <div class="ar-member-panel" id="archiveMemberPanel" hidden>멤버 패널</div>
  </section>
  <div class="ar-dialog-body">
    <label class="ar-field">사진 날짜<input type="date" name="registeredOn" required><span class="ar-field-hint">실제로 올라온 날짜를 선택하세요.</span></label>
  </div>
  <section class="ar-grid" id="archiveGrid">
    <article class="ar-panel ar-card" data-id="3" data-title="심장마빈즈" data-artists="윤서연,정혜린" data-member-order="0">
      <button type="button" class="ar-photo-link"><img src="/portrait.svg" alt=""></button>
      <div class="ar-body">
        <h3>심장마빈즈</h3>
        <div class="ar-meta" style="--member-count: ${members.length}">${members.map(chip).join('')}</div>
      </div>
    </article>
    <article class="ar-panel ar-card" id="single-card" data-id="1" data-title="지서연" data-artists="지연" data-member-order="23">
      <button type="button" class="ar-photo-link"><img src="/portrait.svg" alt=""></button>
      <div class="ar-body">
        <h3>지서연</h3>
        <div class="ar-meta" style="--member-count: 1">${chip('지연')}</div>
      </div>
    </article>
    <article class="ar-panel ar-card" id="joobin-card" data-id="2" data-title="주빈" data-artists="주빈" data-member-order="17">
      <button type="button" class="ar-photo-link"><img src="/portrait.svg" alt=""></button>
      <div class="ar-body">
        <h3>주빈</h3>
        <div class="ar-meta" style="--member-count: 1">${chip('주빈')}</div>
      </div>
    </article>
  </section>
  <nav class="ar-panel ar-pager">
    <p class="ar-pager-summary">
      <span class="ar-pager-count">총 2장</span>
      <span class="ar-pager-sep" aria-hidden="true">·</span>
      <span>2장 표시</span>
    </p>
    <div class="ar-pager-nav" role="group" aria-label="페이지 번호">
      <a class="ar-pager-step is-disabled" aria-disabled="true">이전</a>
      <div class="ar-pages"><a class="ar-pager-num is-current" aria-current="page">1</a></div>
      <a class="ar-pager-step is-disabled" aria-disabled="true">다음</a>
    </div>
    <form class="ar-page-jump" method="get" action="/archive" aria-label="페이지 이동">
      <span class="ar-page-jump-meta">1<span aria-hidden="true">/</span>1</span>
      <label class="ar-page-jump-field">
        <span class="visually-hidden">이동할 페이지</span>
        <input type="number" name="go" min="1" max="1" value="1" aria-label="이동할 페이지">
      </label>
      <button type="submit">이동</button>
    </form>
  </nav>
</main>
<script>${browseJs}</script>
<script>${dateJs}</script>
</body></html>`;

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    await page.route('**/*', (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === '/' || url.pathname.endsWith('.html')) {
        return route.fulfill({ contentType: 'text/html', body: html });
      }
      if (url.pathname === '/portrait.svg' || url.pathname === '/S1.png') {
        return route.fulfill({
          contentType: 'image/svg+xml',
          body: '<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><circle cx="40" cy="40" r="40" fill="#7bba8d"/></svg>'
        });
      }
      const file = path.resolve(staticRoot, '.' + url.pathname);
      if (file.startsWith(staticRoot + path.sep) && fs.existsSync(file)) {
        return route.fulfill({ path: file });
      }
      return route.fulfill({ status: 404, body: '' });
    });
    await page.goto('https://archive-layout.test/', { waitUntil: 'load' });

    const hero = await page.evaluate(() => {
      const pageRoot = document.querySelector('.archive-page');
      const headline = document.querySelector('.ar-headline');
      const pageBox = pageRoot.getBoundingClientRect();
      const heroBox = document.querySelector('.ar-hero').getBoundingClientRect();
      const padL = parseFloat(getComputedStyle(pageRoot).paddingLeft);
      const padR = parseFloat(getComputedStyle(pageRoot).paddingRight);
      return {
        gutter: padL >= 32 && padR >= 32
          && heroBox.left >= pageBox.left + padL - 1
          && pageBox.right - heroBox.right >= padR - 1,
        noStats: !document.getElementById('archiveStats'),
        noToggle: !document.querySelector('[data-stats-toggle]'),
        bodyCopyOnly: headline.querySelector('.ar-headline-body')?.classList.contains('is-copy-only') || false
      };
    });
    assert.equal(hero.gutter, true, '아카이브 페이지가 좌우를 가득 채운다');
    assert.equal(hero.noStats, true, '브라우즈에 통계가 남아 있다');
    assert.equal(hero.noToggle, true, '브라우즈에 통계 접기가 남아 있다');
    assert.equal(hero.bodyCopyOnly, true, '브라우즈 헤드라인이 제목 전용이 아니다');

    assert.equal(await page.locator('#archiveTrack').count(), 0, '최근 사진 트랙이 남아 있다');

    const grid = await page.evaluate(() => {
      const many = document.querySelector('#archiveGrid .ar-card');
      const one = document.getElementById('single-card');
      const manyImg = many.querySelector('.archive-member-chip img').getBoundingClientRect();
      const oneImg = one.querySelector('.archive-member-chip img').getBoundingClientRect();
      const chips = [...many.querySelectorAll('.archive-member-chip')];
      const tops = new Set(chips.map((chip) => Math.round(chip.getBoundingClientRect().top)));
      const name = chips[0].querySelector('.archive-chip-name');
      const nameBox = name.getBoundingClientRect();
      const title = many.querySelector('h3').getBoundingClientRect();
      const meta = many.querySelector('.ar-meta').getBoundingClientRect();
      return {
        manyImg: manyImg.height,
        oneImg: oneImg.height,
        manyMeta: Math.round(many.querySelector('.ar-meta').getBoundingClientRect().height),
        rows: tops.size,
        count: chips.length,
        nameHidden: nameBox.width <= 1 && nameBox.height <= 1,
        name: name.textContent,
        logo: chips[0].classList.contains('archive-symbol-chip'),
        titleBefore: title.bottom <= meta.top + 1,
        numbers: document.querySelectorAll('#archiveGrid .ar-id').length,
        columns: getComputedStyle(document.getElementById('archiveGrid')).gridTemplateColumns.split(' ').filter(Boolean).length,
        carousel: document.getElementById('archiveGrid').classList.contains('is-photo-carousel'),
        chipBorder: getComputedStyle(one.querySelector('.archive-symbol-chip img')).borderWidth
      };
    });
    assert.ok(grid.oneImg > 20 && grid.oneImg <= 26, '한 명 카드 로고 크기가 깨졌다: ' + grid.oneImg);
    assert.ok(grid.manyImg < grid.oneImg - 4, '멤버가 많을 때 배지가 작아지지 않는다: ' + JSON.stringify(grid));
    assert.equal(grid.nameHidden, true, '상징로고 칩에 이름이 드러난다: ' + JSON.stringify(grid));
    assert.equal(grid.logo, true, '상징로고 칩 클래스가 없다');
    assert.ok(grid.rows < grid.count, '그리드 멤버가 한 줄에 하나씩 쌓인다: ' + JSON.stringify(grid));
    assert.ok(grid.manyMeta < 280, '그리드 멤버 영역이 너무 높다: ' + grid.manyMeta);
    assert.equal(grid.titleBefore, true, '제목이 멤버 로고 아래에 있다');
    assert.equal(grid.numbers, 0, '목록에 번호가 보인다');
    assert.equal(grid.carousel, false, '캐러셀이 남아 있다');
    assert.ok(grid.columns >= 2, '사진이 여러 열로 배치되지 않는다: ' + grid.columns);
    assert.equal(grid.chipBorder, '0px', '상징로고에 테두리가 남아 있다: ' + grid.chipBorder);

    const toolbar = await page.evaluate(() => {
      const search = document.querySelector('.ar-search-wrap');
      const icon = document.querySelector('.ar-search-wrap .objekt-search-glyph');
      const tools = document.querySelector('.ar-filter-tools');
      const bar = document.querySelector('.ar-toolbar');
      const date = document.querySelector('.ar-date-toggle');
      const member = document.querySelector('.ar-member-toggle');
      const reset = document.querySelector('.ar-reset');
      const trigger = document.querySelector('.ar-sort-trigger');
      const option = document.querySelector('.ar-sort-option[aria-selected="false"]') || document.querySelector('.ar-sort-option');
      const searchBox = search.getBoundingClientRect();
      const iconBox = icon.getBoundingClientRect();
      const toolsBox = tools.getBoundingClientRect();
      const barBox = bar.getBoundingClientRect();
      const memberBox = member.getBoundingClientRect();
      const resetBox = reset.getBoundingClientRect();
      const resetStyle = getComputedStyle(reset);
      const triggerStyle = trigger ? getComputedStyle(trigger) : null;
      const optionStyle = option ? getComputedStyle(option) : null;
      const field = document.querySelector('.ar-search');
      const filterShell = document.querySelector('.ar-filter-tools .objekt-filter');
      return {
        searchW: Math.round(searchBox.width),
        iconLeft: iconBox.left < searchBox.left + 24,
        iconInside: iconBox.left >= searchBox.left - 1 && iconBox.right <= searchBox.right + 2,
        searchButton: Boolean(document.querySelector('.ar-search-row .ar-submit')),
        radius: getComputedStyle(search).borderRadius,
        searchBelowFilters: searchBox.top >= memberBox.bottom - 2,
        resetAfterSearch: resetBox.left >= searchBox.right - 8,
        toolsFill: Math.abs(toolsBox.width - (barBox.width - parseFloat(getComputedStyle(bar).paddingLeft) - parseFloat(getComputedStyle(bar).paddingRight))) <= 8,
        filterLikeObjekt: Boolean(filterShell) && getComputedStyle(filterShell).borderRadius.includes('999'),
        dateInTools: tools.contains(date),
        memberInTools: tools.contains(member),
        resetInTools: tools.contains(reset),
        searchInTools: tools.contains(search),
        resetClass: reset.className,
        resetFont: resetStyle.fontSize,
        resetColor: resetStyle.color,
        resetAlign: Math.abs((resetBox.top + resetBox.bottom) / 2 - (searchBox.top + searchBox.bottom) / 2) <= 6,
        resetHeight: Math.round(resetBox.height),
        searchHeight: Math.round(searchBox.height),
        memberHeight: Math.round(member.closest('.objekt-filter').getBoundingClientRect().height),
        resetWrap: resetStyle.whiteSpace,
        resetOneLine: reset.scrollWidth <= reset.clientWidth + 1,
        sortEnhanced: Boolean(trigger && option),
        sortLabels: [...document.querySelectorAll('.ar-sort-option')].map((el) => el.dataset.value),
        sortMatch: Boolean(triggerStyle && optionStyle
          && triggerStyle.color === optionStyle.color
          && triggerStyle.fontSize === optionStyle.fontSize
          && String(parseInt(triggerStyle.fontWeight, 10) || triggerStyle.fontWeight)
            === String(parseInt(optionStyle.fontWeight, 10) || optionStyle.fontWeight)),
        fieldTransparent: getComputedStyle(field).backgroundColor.includes('0, 0, 0, 0') || getComputedStyle(field).backgroundColor === 'transparent'
      };
    });
    assert.ok(toolbar.searchW > 420, '검색창이 오브젝트처럼 넓지 않다: ' + toolbar.searchW);
    assert.equal(toolbar.iconLeft, true, '돋보기가 검색창 왼쪽에 없다');
    assert.equal(toolbar.iconInside, true, '돋보기가 검색창 밖에 있다');
    assert.equal(toolbar.searchButton, false, '검색 버튼이 남아 있다');
    assert.ok(toolbar.radius.includes('12px') || toolbar.radius.includes('0.75rem'), '검색창 모서리가 전역 스타일과 다르다: ' + toolbar.radius);
    assert.equal(toolbar.searchBelowFilters, true, '검색창이 필터 아래에 없다');
    assert.equal(toolbar.resetAfterSearch, true, '초기화가 검색창 오른쪽에 없다');
    assert.deepEqual(toolbar.sortLabels, ['newest', 'oldest', 'first-member', 'last-member']);
    assert.equal(toolbar.toolsFill, true, '필터 바가 툴바 폭을 채우지 않는다');
    assert.equal(toolbar.filterLikeObjekt, true, '필터가 오브젝트 알약 스타일이 아니다');
    assert.equal(toolbar.dateInTools, true);
    assert.equal(toolbar.memberInTools, true);
    assert.equal(toolbar.resetInTools, true);
    assert.equal(toolbar.searchInTools, true);
    assert.match(toolbar.resetClass, /site-btn--reset/, '초기화가 공통 reset 버튼이 아니다: ' + toolbar.resetClass);
    assert.ok(
      /b9cac4|141,\s*236,\s*217|0,\s*245,\s*212|185,\s*202,\s*196/i.test(toolbar.resetColor)
        || toolbar.resetColor.includes('185')
        || toolbar.resetColor.includes('#b9'),
      '초기화 글자색이 outline 톤이 아니다: ' + toolbar.resetColor
    );
    assert.equal(toolbar.resetAlign, true, '초기화가 검색창과 세로로 안 맞는다');
    assert.equal(toolbar.resetWrap, 'nowrap', '초기화 글자가 한 줄이 아니다');
    assert.equal(toolbar.resetOneLine, true, '초기화 글자가 버튼 안에서 잘린다');
    assert.ok(Math.abs(toolbar.resetHeight - toolbar.searchHeight) <= 4, '초기화 높이가 검색창과 다르다: ' + JSON.stringify({ reset: toolbar.resetHeight, search: toolbar.searchHeight }));
    assert.equal(toolbar.sortEnhanced, true, '정렬 드롭다운이 같은 스타일로 열리지 않는다');
    assert.equal(toolbar.sortMatch, true, '정렬 항목 스타일이 닫힌 버튼과 다르다');
    assert.equal(toolbar.fieldTransparent, true, '검색 입력이 오브젝트처럼 투명 배경이 아니다');

    await page.click('.ar-sort-trigger');
    await page.click('.ar-sort-option[data-value="first-member"]');
    const firstMember = await page.evaluate(() => [...document.querySelectorAll('#archiveGrid .ar-card')].map((card) => card.dataset.title));
    assert.deepEqual(firstMember, ['심장마빈즈', '주빈', '지서연'], '첫멤버순이 S1부터 S24로 가지 않는다: ' + firstMember.join(','));
    await page.click('.ar-sort-trigger');
    await page.click('.ar-sort-option[data-value="last-member"]');
    const lastMember = await page.evaluate(() => [...document.querySelectorAll('#archiveGrid .ar-card')].map((card) => card.dataset.title));
    assert.deepEqual(lastMember, ['지서연', '주빈', '심장마빈즈'], '마지막멤버순이 S24부터 S1로 가지 않는다: ' + lastMember.join(','));

    await page.click('.ar-sort-trigger');
    await page.click('[data-date-toggle]');
    let overlays = await page.evaluate(() => ({
      sortOpen: document.querySelector('.ar-sort-menu')?.hidden === false,
      dateOpen: document.querySelector('[data-date-popover]')?.hidden === false,
      memberOpen: document.getElementById('archiveMemberPanel')?.hidden === false
    }));
    assert.equal(overlays.sortOpen, false, '날짜를 열어도 정렬 메뉴가 남아 있다');
    assert.equal(overlays.dateOpen, true, '날짜 팝오버가 열리지 않는다');
    await page.click('#archiveMemberToggle');
    overlays = await page.evaluate(() => ({
      sortOpen: document.querySelector('.ar-sort-menu')?.hidden === false,
      dateOpen: document.querySelector('[data-date-popover]')?.hidden === false,
      memberOpen: document.getElementById('archiveMemberPanel')?.hidden === false
    }));
    assert.equal(overlays.dateOpen, false, '멤버를 열어도 날짜 팝오버가 남아 있다');
    assert.equal(overlays.memberOpen, true, '멤버 패널이 열리지 않는다');
    await page.click('.ar-search');
    overlays = await page.evaluate(() => ({
      sortOpen: document.querySelector('.ar-sort-menu')?.hidden === false,
      dateOpen: document.querySelector('[data-date-popover]')?.hidden === false,
      memberOpen: document.getElementById('archiveMemberPanel')?.hidden === false
    }));
    assert.equal(overlays.sortOpen, false, '검색을 눌러도 정렬이 열려 있다');
    assert.equal(overlays.dateOpen, false, '검색을 눌러도 날짜가 열려 있다');
    assert.equal(overlays.memberOpen, false, '검색을 눌러도 멤버 패널이 열려 있다');

    await page.click('[data-date-toggle]');
    const ymd = await page.evaluate(() => {
      const pop = document.querySelector('[data-date-popover]');
      const dates = [...(pop?.querySelectorAll('input[type="date"]') || [])];
      const cals = [...(pop?.querySelectorAll('.ar-cal') || [])];
      return {
        dateCount: dates.length,
        calCount: cals.length,
        selects: pop?.querySelectorAll('select').length || 0,
        names: dates.map((el) => el.name),
        visible: dates.every((el) => el.getBoundingClientRect().width > 40),
        indicator: dates.every((el) => getComputedStyle(el, '::-webkit-calendar-picker-indicator').display !== 'none'),
        exact: Boolean(pop?.querySelector('[data-date-exact]')),
        hint: pop?.querySelector('.ar-field-hint')?.textContent || '',
        more: Boolean(document.querySelector('.ar-more-link'))
      };
    });
    assert.equal(ymd.dateCount, 2, '시작/끝 날짜 칸이 없다: ' + JSON.stringify(ymd));
    assert.equal(ymd.calCount, 2, '달력 스타일 날짜 칸이 없다: ' + JSON.stringify(ymd));
    assert.equal(ymd.selects, 0, '연-월-일 셀렉트가 남아 있다');
    assert.deepEqual(ymd.names, ['from', 'to']);
    assert.equal(ymd.visible, true, '날짜 달력이 숨겨져 있다');
    assert.equal(ymd.exact, false, '이 사진 날짜 칸이 남아 있다');
    assert.equal(ymd.hint, '', '날짜 힌트가 남아 있다');
    assert.equal(ymd.more, false, '더보기 버튼이 남아 있다');
    const yearFrom = page.locator('.ar-date-popover [data-part="year"]').nth(0);
    const yearTo = page.locator('.ar-date-popover [data-part="year"]').nth(1);
    assert.equal(await page.locator('.ar-date-popover .ar-cal-part').count(), 6, '연·월·일 칸이 없다');
    await yearFrom.click();
    await page.keyboard.type('abcd20261116');
    await yearTo.click();
    await page.keyboard.type('20261202');
    const typed = await page.evaluate(() => {
      const from = document.querySelector('[data-date-from]');
      const to = document.querySelector('[data-date-to]');
      const values = (root) => [...root.querySelectorAll('.ar-cal-part')].map((el) => el.value);
      const fromWrap = document.querySelector('[data-date-from]')?.closest('.ar-cal');
      const toWrap = document.querySelector('[data-date-to]')?.closest('.ar-cal');
      return { from: from?.value || '', to: to?.value || '', fromParts: values(fromWrap), toParts: values(toWrap) };
    });
    assert.equal(typed.from, '2026-11-16', '시작 날짜를 숫자로 넣지 못한다: ' + JSON.stringify(typed));
    assert.equal(typed.to, '2026-12-02', '끝 날짜를 숫자로 넣지 못한다: ' + JSON.stringify(typed));
    assert.deepEqual(typed.fromParts, ['2026', '11', '16']);
    assert.deepEqual(typed.toParts, ['2026', '12', '02']);
    await page.locator('.ar-date-popover .ar-cal-open').nth(0).click();
    const calKo = await page.evaluate(() => {
      const board = document.querySelector('.ar-cal-board:not([hidden])');
      return {
        title: board?.querySelector('.ar-cal-board-title')?.textContent || '',
        week: [...(board?.querySelectorAll('.ar-cal-board-week span') || [])].map((el) => el.textContent).join(''),
        popOpen: document.querySelector('[data-date-popover]')?.hidden === false,
        days: board?.querySelectorAll('.ar-cal-day').length || 0
      };
    });
    assert.match(calKo.title.replace(/\s+/g, ''), /2026년11월/, '달력 제목이 한국어가 아니다: ' + calKo.title);
    assert.equal(calKo.week, '일월화수목금토', '달력 요일이 한국어가 아니다: ' + calKo.week);
    assert.equal(calKo.popOpen, true, '달력을 열면 날짜 팝오버가 닫힌다');
    assert.ok(calKo.days >= 28, '달력 날짜 칸이 없다');
    const dateShotDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(dateShotDir, { recursive: true });
    await page.waitForTimeout(400);
    const calStill = await page.evaluate(() => {
      const board = document.querySelector('.ar-cal-board:not([hidden])');
      const pop = document.querySelector('[data-date-popover]');
      const box = board?.getBoundingClientRect();
      return {
        popOpen: pop?.hidden === false,
        days: board?.querySelectorAll('.ar-cal-day').length || 0,
        visible: Boolean(box && box.width > 40 && box.height > 40)
      };
    });
    assert.equal(calStill.popOpen, true, '달력 확인 전에 날짜 팝오버가 닫힌다');
    assert.ok(calStill.days >= 28 && calStill.visible, '달력이 다시 숨겨졌다: ' + JSON.stringify(calStill));
    await page.locator('.ar-cal-board:not([hidden])').screenshot({ path: path.join(dateShotDir, 'archive-date-calendar-ko.png') });
    await page.locator('[data-date-popover]').screenshot({ path: path.join(dateShotDir, 'archive-date-calendar.png') });
    await page.locator('.ar-cal-board:not([hidden]) .ar-cal-day').filter({ hasText: /^16$/ }).click();
    const picked = await page.evaluate(() => document.querySelector('[data-date-from]')?.value || '');
    assert.equal(picked, '2026-11-16', '한국어 달력에서 날짜를 고르지 못한다: ' + picked);
    await page.locator('.ar-date-popover .ar-cal-open').nth(0).click();
    await page.locator('.ar-cal-board:not([hidden]) .ar-cal-pick').nth(0).click();
    const yearMenu = await page.locator('.ar-cal-menu:not([hidden]) button').allTextContents();
    assert.ok(yearMenu.some((text) => text === '2024년'), '연도 목록이 없다: ' + yearMenu.slice(0, 4).join(','));
    await page.locator('.ar-cal-menu button', { hasText: '2024년' }).click();
    await page.locator('.ar-cal-board:not([hidden]) .ar-cal-pick').nth(1).click();
    const monthMenu = await page.locator('.ar-cal-menu:not([hidden]) button').allTextContents();
    assert.equal(monthMenu.length, 12, '월 목록이 12개가 아니다: ' + monthMenu.join(','));
    await page.locator('.ar-cal-menu button', { hasText: '3월' }).click();
    const movedTitle = await page.locator('.ar-cal-board-title').innerText();
    assert.match(movedTitle.replace(/\s+/g, ''), /2024년3월/, '연·월을 고르지 못한다: ' + movedTitle);
    await page.locator('.ar-cal-board:not([hidden])').screenshot({ path: path.join(dateShotDir, 'archive-date-year-month.png') });
    await page.evaluate(() => document.querySelectorAll('.ar-cal-board').forEach((board) => { board.hidden = true; }));

    const pager = await page.evaluate(() => {
      const root = document.querySelector('.ar-pager');
      const summary = document.querySelector('.ar-pager-summary');
      const nav = document.querySelector('.ar-pager-nav');
      const jump = document.querySelector('.ar-page-jump');
      const meta = jump?.querySelector('.ar-page-jump-meta');
      const next = [...(nav?.querySelectorAll('a') || [])].find((el) => el.textContent.trim() === '다음');
      const pageLink = nav?.querySelector('.ar-pages a:last-child');
      const rootBox = root?.getBoundingClientRect();
      const summaryBox = summary?.getBoundingClientRect();
      const navBox = nav?.getBoundingClientRect();
      const jumpBox = jump?.getBoundingClientRect();
      const nextBox = next?.getBoundingClientRect();
      const pageBox = pageLink?.getBoundingClientRect();
      const goBox = jump?.querySelector('input[name="go"]')?.getBoundingClientRect();
      const moveBox = jump?.querySelector('button')?.getBoundingClientRect();
      const mid = rootBox ? rootBox.left + rootBox.width / 2 : 0;
      return {
        hasJump: Boolean(jump),
        hasNav: Boolean(nav),
        hasSummary: Boolean(summary),
        hasMeta: Boolean(meta && /1\s*\/\s*1/.test(meta.textContent || '')),
        noControls: !document.querySelector('.ar-pager-controls'),
        goName: jump?.querySelector('input[name="go"]')?.name || '',
        label: jump?.querySelector('button')?.textContent.trim() || '',
        afterNext: Boolean(next && jump && (next.compareDocumentPosition(jump) & Node.DOCUMENT_POSITION_FOLLOWING)),
        afterPage: Boolean(pageLink && jump && (pageLink.compareDocumentPosition(jump) & Node.DOCUMENT_POSITION_FOLLOWING)),
        summaryLeft: Boolean(summaryBox && rootBox && summaryBox.left <= rootBox.left + 24),
        navCentered: Boolean(navBox && rootBox && Math.abs((navBox.left + navBox.width / 2) - mid) <= 48),
        jumpRight: Boolean(jumpBox && rootBox && jumpBox.right >= rootBox.right - 24),
        aligned: nextBox && jumpBox ? Math.abs(nextBox.top - jumpBox.top) <= 8 : false,
        goWidth: goBox ? Math.round(goBox.width) : 0,
        moveHeight: moveBox ? Math.round(moveBox.height) : 0,
        pageGap: pageBox && jumpBox ? jumpBox.left - pageBox.right : 999
      };
    });
    assert.equal(pager.hasJump, true, '페이지 이동 UI가 없다');
    assert.equal(pager.hasNav, true, '이전/번호/다음 묶음이 없다');
    assert.equal(pager.hasSummary, true, '요약 문구가 없다');
    assert.equal(pager.hasMeta, true, '현재/전체 페이지 표시가 없다');
    assert.equal(pager.noControls, true, '구 controls 래퍼가 남아 있다');
    assert.equal(pager.afterNext, true, '바로가기가 다음 버튼 뒤에 없다');
    assert.equal(pager.afterPage, true, '바로가기가 페이지 번호 뒤에 없다');
    assert.equal(pager.goName, 'go');
    assert.equal(pager.label, '이동');
    assert.equal(pager.summaryLeft, true, '요약이 왼쪽에 없다');
    assert.equal(pager.navCentered, true, '페이지 번호가 가운데에 없다');
    assert.equal(pager.jumpRight, true, '바로가기가 오른쪽에 없다');
    assert.equal(pager.aligned, true, '바로가기가 다음과 세로로 안 맞는다');
    assert.ok(pager.pageGap >= 8, '바로가기가 번호에 너무 붙어 있다: ' + pager.pageGap);
    assert.ok(pager.goWidth >= 40 && pager.goWidth <= 90, '페이지 숫자 칸 크기가 달라졌다: ' + pager.goWidth);
    assert.ok(pager.moveHeight >= 28 && pager.moveHeight <= 40, '이동 버튼 크기가 달라졌다: ' + pager.moveHeight);
    await page.locator('.ar-pager').screenshot({ path: path.join(dateShotDir, 'archive-pager-jump.png') });

    const tabs = await page.evaluate(() => {
      const current = document.querySelector('.site-section-tab[aria-current="page"]');
      const dateField = document.querySelector('label.ar-field');
      return {
        current: current?.textContent.trim() || '',
        underline: current ? getComputedStyle(current).borderBottomColor : '',
        typeSize: current ? parseFloat(getComputedStyle(current).fontSize) : 0,
        typeWeight: current ? Number(getComputedStyle(current).fontWeight) : 0,
        dateLabel: dateField?.childNodes[0]?.textContent.trim() || '',
        dateHint: dateField?.querySelector('.ar-field-hint')?.textContent.trim() || ''
      };
    });
    assert.equal(tabs.current, '사진');
    assert.ok(tabs.typeSize >= 16, '사진·영상 탭 글자가 작다: ' + tabs.typeSize);
    assert.ok(tabs.typeWeight >= 700, '사진·영상 탭이 굵지 않다: ' + tabs.typeWeight);
    assert.ok(tabs.underline.includes('0, 245, 212') || tabs.underline.includes('rgb(0, 245, 212)'), '사진 탭 underline이 없다: ' + tabs.underline);
    assert.equal(tabs.dateLabel, '사진 날짜');
    assert.equal(tabs.dateHint, '실제로 올라온 날짜를 선택하세요.');

    const registeredYmd = await page.evaluate(() => {
      const field = document.querySelector('label.ar-field');
      const wrap = field?.querySelector('[data-ymd]');
      const labels = [...(wrap?.querySelectorAll('select') || [])].map((el) => el.getAttribute('aria-label'));
      const native = field?.querySelector('input[type="date"][name="registeredOn"]');
      const nativeBox = native?.getBoundingClientRect();
      return {
        labels,
        hidden: Boolean(native) && nativeBox.width <= 1 && nativeBox.height <= 1,
        extra: Boolean(document.querySelector('[name="extraArtists"]'))
      };
    });
    assert.deepEqual(registeredYmd.labels, [], '등록 날짜가 다시 연-월-일 셀렉트다: ' + JSON.stringify(registeredYmd.labels));
    assert.equal(registeredYmd.hidden, false, '등록 날짜 네이티브 입력이 숨겨져 있다');
    assert.equal(registeredYmd.extra, false, '멤버 외 출연자 입력이 남아 있다');

    await page.setViewportSize({ width: 412, height: 900 });
    const mobile = await page.evaluate(() => {
      const upload = document.querySelector('.ar-home-upload').getBoundingClientRect();
      const manage = document.querySelector('.ar-source-manage').getBoundingClientRect();
      const density = document.querySelector('.ar-density').getBoundingClientRect();
      const sourceTools = document.querySelector('.ar-source-tools').getBoundingClientRect();
      const tools = document.querySelector('.ar-filter-tools').getBoundingClientRect();
      const date = document.querySelector('.ar-date-toggle').getBoundingClientRect();
      const member = document.querySelector('.ar-member-toggle').getBoundingClientRect();
      const reset = document.querySelector('.ar-reset').getBoundingClientRect();
      const sort = document.querySelector('.ar-sort-control').getBoundingClientRect();
      return {
        inSourceTools: Boolean(document.querySelector('.ar-source-tools .ar-home-upload')),
        uploadHeight: Math.round(upload.height),
        manageHeight: Math.round(manage.height),
        densityHeight: Math.round(density.height),
        toolsAligned: Math.abs(upload.top - manage.top) <= 4 && Math.abs(upload.top - density.top) <= 4,
        uploadInsideTools: upload.right <= sourceTools.right + 1 && upload.left >= sourceTools.left - 1,
        dateWidth: Math.round(date.width),
        memberWidth: Math.round(member.width),
        sameRow: Math.abs(date.top - member.top) <= 8,
        sideBySide: member.left >= date.right - 2,
        resetWidth: Math.round(reset.width),
        resetInside: reset.right <= tools.right + 1 && reset.left >= tools.left - 1,
        sortWidth: Math.round(sort.width),
        toolsWidth: Math.round(tools.width)
      };
    });
    assert.equal(mobile.inSourceTools, true, '사진 추가가 출처 도구 줄에 없다');
    assert.equal(mobile.toolsAligned, true, '사진 추가·출처 관리·개수가 한 줄이 아니다: ' + JSON.stringify(mobile));
    assert.ok(Math.abs(mobile.uploadHeight - mobile.densityHeight) <= 2, '사진 추가와 개수 드롭다운 높이가 다르다: ' + JSON.stringify(mobile));
    assert.ok(Math.abs(mobile.manageHeight - mobile.densityHeight) <= 2, '출처 관리와 개수 드롭다운 높이가 다르다: ' + JSON.stringify(mobile));
    assert.equal(mobile.uploadInsideTools, true, '사진 추가가 출처 도구 밖에 있다');
    assert.equal(mobile.sameRow, true, '날짜와 멤버 버튼이 한 줄이 아니다');
    assert.equal(mobile.sideBySide, true, '날짜와 멤버 버튼이 나란히 있지 않다');
    assert.ok(mobile.dateWidth < mobile.toolsWidth * 0.7, '날짜 버튼이 한 줄을 다 차지한다: ' + mobile.dateWidth);
    assert.ok(mobile.resetWidth < 140 && mobile.resetInside, '초기화 버튼이 잘리거나 길다: ' + JSON.stringify(mobile));
    assert.ok(mobile.sortWidth < mobile.toolsWidth * 0.8, '정렬 칸이 한 줄을 다 차지한다: ' + mobile.sortWidth);
    await page.evaluate(() => {
      document.querySelectorAll('[data-date-popover], .ar-cal-board').forEach((node) => { node.hidden = true; });
    });
    await page.locator('.ar-toolbar').screenshot({ path: path.join(dateShotDir, 'archive-mobile-filters.png') });
    await page.locator('.ar-home-upload').screenshot({ path: path.join(dateShotDir, 'archive-mobile-upload.png') });
    await page.setViewportSize({ width: 1280, height: 900 });

    await page.evaluate(() => {
      document.body.style.minHeight = '1800px';
      window.scrollTo(0, 420);
      const input = document.querySelector('.ar-search');
      input.value = '지연';
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await page.waitForTimeout(700);
    const stayed = await page.evaluate(() => window.scrollY);
    assert.ok(stayed >= 360, '필터 적용이 페이지를 위로 올렸다: ' + stayed);

    const artifactDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(artifactDir, { recursive: true });
    await page.locator('.ar-filter-tools').screenshot({ path: path.join(artifactDir, 'archive-reset-search-size.png') });
    await page.screenshot({ path: path.join(artifactDir, 'archive-layout-stats-members.png'), fullPage: true });
    for (const width of [320, 360, 390, 430]) {
      await page.setViewportSize({ width, height: 844 });
      await page.goto('https://archive-layout.test/', { waitUntil: 'load' });
      await page.evaluate(() => {
        const toggle = document.getElementById('archiveMemberToggle');
        toggle.innerHTML = '<span data-member-toggle-label>아주 긴 멤버와 케미 이름을 선택한 상태</span>';
        document.querySelectorAll('[data-date-filter] input[type="date"]').forEach(input => { input.value = ''; });
        const input = document.querySelector('.ar-search');
        input.value = '';
        input.dispatchEvent(new Event('input', { bubbles: true }));
      });
      assert.equal(await page.locator('.ar-reset').isHidden(), true, '기본 상태 초기화 버튼: ' + width);
      const before = await page.locator('.ar-search').boundingBox();
      await page.evaluate(() => {
        const input = document.querySelector('.ar-search');
        input.value = '지연';
        input.dispatchEvent(new Event('input', { bubbles: true }));
      });
      assert.equal(await page.locator('.ar-reset').isVisible(), true, '검색 중 초기화 버튼: ' + width);
      const after = await page.locator('.ar-search').boundingBox();
      assert.ok(before.width > after.width, '초기화가 숨겨지면 검색창이 넓어져야 한다: ' + width);
      const metrics = await page.evaluate(() => ({
        overflow: document.documentElement.scrollWidth > innerWidth + 1,
        targets: ['.ar-date-toggle', '.ar-member-toggle', '.ar-reset', '.ar-search'].map(selector => document.querySelector(selector).getBoundingClientRect().height)
      }));
      assert.equal(metrics.overflow, false, '모바일 가로 넘침: ' + width);
      assert.ok(metrics.targets.every(height => height >= 44), '모바일 터치 높이: ' + JSON.stringify(metrics));
      await page.locator('[data-date-toggle]').click();
      const pop = await page.locator('[data-date-popover]').boundingBox();
      assert.ok(pop.x >= 0 && pop.x + pop.width <= width + 1, '날짜 팝업이 화면을 벗어난다: ' + width);
      await page.screenshot({ path: path.join(artifactDir, 'archive-mobile-' + width + '.png') });
    }
    console.log('PASS: 아카이브 검색 버튼·매슨리 그리드');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
