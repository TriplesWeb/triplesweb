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
${siteUi}
</style></head>
<body class="site-page">
<main class="archive-page" data-archive-kind="image" data-date-label="사진 날짜">
  <section class="ar-hero">
    <div class="ar-panel ar-headline">
      <button type="button" class="ar-stats-toggle" data-stats-toggle aria-expanded="true" aria-controls="archiveStats" aria-label="통계 접기">
        <span data-stats-icon>−</span>
      </button>
      <div>
        <div class="ar-kicker">Archive</div>
        <h1>아카이브</h1>
      </div>
      <nav class="site-section-tabs" aria-label="아카이브 종류">
        <a class="site-section-tab" href="/archive" aria-current="page">사진</a>
        <a class="site-section-tab" href="/archive?type=video">영상</a>
      </nav>
      <div class="ar-stats" id="archiveStats">
        <article class="ar-stat"><div class="ar-stat-label"><span>등록된 사진</span><span class="material-symbols-outlined">image</span></div><strong>2<em>장</em></strong></article>
        <article class="ar-stat"><div class="ar-stat-label"><span>이 페이지</span><span class="material-symbols-outlined">grid_view</span></div><strong>2<em>장</em></strong></article>
        <article class="ar-stat"><div class="ar-stat-label"><span>페이지</span><span class="material-symbols-outlined">layers</span></div><strong>1<em>/ 1</em></strong></article>
        <article class="ar-stat ar-stat-filter" style="--filter-count: 3"><div class="ar-stat-label"><span>검색</span><span class="material-symbols-outlined">search</span></div><div class="ar-filter-summary"><span class="ar-filter-member" style="--member-color:#22aeff">윤서연</span><span class="ar-filter-more">외 1명</span><span class="ar-filter-sep">,</span><strong>심장</strong><span class="ar-filter-sep">,</span><strong>2026-01-01 ~ 2026-12-31</strong></div></article>
      </div>
    </div>
  </section>
  <section class="ar-panel ar-toolbar">
    <form class="ar-filters" id="archiveFilterForm">
      <div class="ar-filter-tools">
        <label class="ar-sort"><span class="ar-sort-label">정렬</span><span class="ar-sort-control"><select id="archiveSort" aria-label="정렬"><option value="newest">최신순</option><option value="oldest">오래된 순</option><option value="title">제목순</option><option value="first-member">첫멤버순</option><option value="last-member">마지막멤버순</option></select><span class="material-symbols-outlined">arrow_drop_down</span></span></label>
        <div class="ar-date-filter" data-date-filter>
          <button type="button" class="ar-date-toggle" data-date-toggle aria-expanded="false"><span>사진 날짜</span></button>
          <div class="ar-date-popover" data-date-popover hidden>
            <label>사진 날짜 시작<input type="date" name="from" data-date-from></label>
            <label>사진 날짜 끝<input type="date" name="to" data-date-to></label>
          </div>
        </div>
        <button type="button" class="ar-member-toggle">멤버 선택</button>
        <div class="ar-search-row">
          <label class="ar-search-wrap"><span class="material-symbols-outlined">search</span><input class="ar-search" type="search" placeholder="사진 제목 검색"></label>
          <button type="submit" class="ar-submit">검색</button>
        </div>
        <a class="site-btn site-btn--reset ar-reset" href="/archive">초기화</a>
      </div>
    </form>
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
    <span class="ar-pager-summary">총 2장 중 2장 표시</span>
    <div class="ar-pager-controls"><a>이전</a><div class="ar-pages"><a class="is-current">1</a></div><a>다음</a></div>
    <form class="ar-page-jump"><label>페이지 <input type="number" name="go" min="1" max="1" value="1"></label><button type="submit">이동</button></form>
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

    const fonts = await page.evaluate(() => [...document.querySelectorAll('.ar-stat-label span:first-child')].map((el) => getComputedStyle(el).fontFamily));
    for (const family of fonts) {
      assert.match(family, /Pretendard/i, '통계 라벨 글꼴이 사이트 본문과 다르다: ' + family);
      assert.doesNotMatch(family, /JetBrains/i, '통계 라벨이 모노스페이스다: ' + family);
    }

    const hero = await page.evaluate(() => {
      const pageRoot = document.querySelector('.archive-page');
      const headline = document.querySelector('.ar-headline');
      const stats = document.getElementById('archiveStats');
      const toggle = document.querySelector('[data-stats-toggle]');
      const headlineBox = headline.getBoundingClientRect();
      const statsBox = stats.getBoundingClientRect();
      const pageBox = pageRoot.getBoundingClientRect();
      const heroBox = document.querySelector('.ar-hero').getBoundingClientRect();
      const padL = parseFloat(getComputedStyle(pageRoot).paddingLeft);
      const padR = parseFloat(getComputedStyle(pageRoot).paddingRight);
      return {
        gutter: padL >= 32 && padR >= 32
          && heroBox.left >= pageBox.left + padL - 1
          && pageBox.right - heroBox.right >= padR - 1,
        statsInside: headline.contains(stats),
        open: toggle.getAttribute('aria-expanded') === 'true',
        icon: toggle.querySelector('[data-stats-icon]').textContent.trim(),
        statsShown: statsBox.height > 20,
        statsNarrower: statsBox.width < headlineBox.width - 80,
        statsCentered: (() => {
          const style = getComputedStyle(headline);
          const left = headlineBox.left + parseFloat(style.paddingLeft);
          const right = headlineBox.right - parseFloat(style.paddingRight);
          return Math.abs((statsBox.left + statsBox.right) / 2 - (left + right) / 2) <= 8;
        })()
      };
    });
    assert.equal(hero.gutter, true, '아카이브 페이지가 좌우를 가득 채운다');
    assert.equal(hero.statsInside, true, '통계가 아카이브 제목과 같은 패널에 없다');
    assert.equal(hero.open, true, '초기 화면에서 통계가 닫혀 있다');
    assert.equal(hero.icon, '−');
    assert.equal(hero.statsShown, true, '초기 화면에서 통계가 안 보인다');
    assert.equal(hero.statsNarrower, true, '통계 카드가 여전히 패널 전체 너비다');
    assert.equal(hero.statsCentered, true, '통계 카드가 가운데 있지 않다');

    await page.click('[data-stats-toggle]');
    const closed = await page.evaluate(() => {
      const stats = document.getElementById('archiveStats');
      const toggle = document.querySelector('[data-stats-toggle]');
      return {
        hidden: stats.hidden,
        icon: toggle.querySelector('[data-stats-icon]').textContent.trim(),
        expanded: toggle.getAttribute('aria-expanded')
      };
    });
    assert.equal(closed.hidden, true, '접기 뒤에도 통계가 남아 있다');
    assert.equal(closed.icon, '+');
    assert.equal(closed.expanded, 'false');
    await page.click('[data-stats-toggle]');
    const reopened = await page.evaluate(() => document.getElementById('archiveStats').hidden);
    assert.equal(reopened, false, '다시 열리지 않는다');

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
    assert.ok(grid.oneImg > 20 && grid.oneImg <= 40, '한 명 카드 배지 크기가 깨졌다: ' + grid.oneImg);
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
      const icon = document.querySelector('.ar-search-wrap > .material-symbols-outlined');
      const go = document.querySelector('.ar-search-row .ar-submit');
      const tools = document.querySelector('.ar-filter-tools');
      const bar = document.querySelector('.ar-toolbar');
      const date = document.querySelector('.ar-date-toggle');
      const member = document.querySelector('.ar-member-toggle');
      const reset = document.querySelector('.ar-reset');
      const trigger = document.querySelector('.ar-sort-trigger');
      const option = document.querySelector('.ar-sort-option[aria-selected="false"]') || document.querySelector('.ar-sort-option');
      const searchBox = search.getBoundingClientRect();
      const iconBox = icon.getBoundingClientRect();
      const goBox = go.getBoundingClientRect();
      const toolsBox = tools.getBoundingClientRect();
      const barBox = bar.getBoundingClientRect();
      const memberBox = member.getBoundingClientRect();
      const resetBox = reset.getBoundingClientRect();
      const memberStyle = getComputedStyle(member);
      const resetStyle = getComputedStyle(reset);
      const triggerStyle = trigger ? getComputedStyle(trigger) : null;
      const optionStyle = option ? getComputedStyle(option) : null;
      const field = document.querySelector('.ar-search');
      return {
        searchW: Math.round(searchBox.width),
        iconLeft: iconBox.left < searchBox.left + 24,
        iconInside: iconBox.left >= searchBox.left - 1 && iconBox.right <= searchBox.right + 2,
        goAfter: goBox.left >= searchBox.right - 2,
        goMint: getComputedStyle(go).backgroundColor,
        radius: getComputedStyle(field).borderRadius,
        searchAfterMember: searchBox.left >= memberBox.right - 8,
        resetAfterSearch: resetBox.left >= goBox.right - 8,
        toolsLeft: Math.abs(toolsBox.left - barBox.left) <= 24,
        dateInTools: tools.contains(date),
        memberInTools: tools.contains(member),
        resetInTools: tools.contains(reset),
        searchInTools: tools.contains(search),
        filterSize: getComputedStyle(document.querySelector('.ar-filter-summary')).fontSize,
        resetClass: reset.className,
        resetFont: resetStyle.fontSize,
        goFont: getComputedStyle(go).fontSize,
        resetColor: resetStyle.color,
        resetAlign: Math.abs(resetBox.top - goBox.top) <= 4,
        sortEnhanced: Boolean(trigger && option),
        sortLabels: [...document.querySelectorAll('.ar-sort-option')].map((el) => el.dataset.value),
        sortMatch: Boolean(triggerStyle && optionStyle
          && triggerStyle.color === optionStyle.color
          && triggerStyle.fontSize === optionStyle.fontSize
          && triggerStyle.fontWeight === optionStyle.fontWeight)
      };
    });
    assert.ok(toolbar.searchW < 420, '검색창이 한 줄을 차지한다: ' + toolbar.searchW);
    assert.equal(toolbar.iconLeft, true, '돋보기가 검색창 왼쪽에 없다');
    assert.equal(toolbar.iconInside, true, '돋보기가 검색창 밖에 있다');
    assert.equal(toolbar.goAfter, true, '검색 버튼이 검색창 오른쪽에 없다');
    assert.ok(toolbar.goMint.includes('0, 245, 212') || toolbar.goMint.includes('00f5d4'), '검색 버튼이 민트 색이 아니다: ' + toolbar.goMint);
    assert.ok(toolbar.radius.includes('12px') || toolbar.radius.includes('0.75rem'), '검색창 모서리가 전역 스타일과 다르다: ' + toolbar.radius);
    assert.equal(toolbar.searchAfterMember, true, '검색창이 멤버 선택 오른쪽에 없다');
    assert.equal(toolbar.resetAfterSearch, true, '초기화가 검색 버튼 오른쪽에 없다');
    assert.deepEqual(toolbar.sortLabels, ['newest', 'oldest', 'title', 'first-member', 'last-member']);
    assert.equal(toolbar.toolsLeft, true, '필터가 왼쪽에 붙어 있지 않다');
    assert.equal(toolbar.dateInTools, true);
    assert.equal(toolbar.memberInTools, true);
    assert.equal(toolbar.resetInTools, true);
    assert.equal(toolbar.searchInTools, true);
    assert.ok(parseFloat(toolbar.filterSize) < 22, '필터 글자가 줄지 않는다: ' + toolbar.filterSize);
    assert.match(toolbar.resetClass, /site-btn--reset/, '초기화가 회색 유리 버튼이 아니다: ' + toolbar.resetClass);
    assert.ok(toolbar.resetColor.includes('255') || toolbar.resetColor.includes('#fff'), '초기화 글자가 흰색이 아니다: ' + toolbar.resetColor);
    assert.equal(toolbar.resetAlign, true, '초기화가 검색 버튼과 세로로 안 맞는다');
    assert.equal(toolbar.sortEnhanced, true, '정렬 드롭다운이 같은 스타일로 열리지 않는다');
    assert.equal(toolbar.sortMatch, true, '정렬 항목 스타일이 닫힌 버튼과 다르다');

    await page.click('.ar-sort-trigger');
    await page.click('.ar-sort-option[data-value="first-member"]');
    const firstMember = await page.evaluate(() => [...document.querySelectorAll('#archiveGrid .ar-card')].map((card) => card.dataset.title));
    assert.deepEqual(firstMember, ['심장마빈즈', '주빈', '지서연'], '첫멤버순이 S1부터 S24로 가지 않는다: ' + firstMember.join(','));
    await page.click('.ar-sort-trigger');
    await page.click('.ar-sort-option[data-value="last-member"]');
    const lastMember = await page.evaluate(() => [...document.querySelectorAll('#archiveGrid .ar-card')].map((card) => card.dataset.title));
    assert.deepEqual(lastMember, ['지서연', '주빈', '심장마빈즈'], '마지막멤버순이 S24부터 S1로 가지 않는다: ' + lastMember.join(','));

    await page.click('[data-date-toggle]');
    const ymd = await page.evaluate(() => {
      const pop = document.querySelector('[data-date-popover]');
      const group = pop?.querySelector('[data-ymd]');
      const labels = [...(group?.querySelectorAll('select') || [])].map((el) => el.getAttribute('aria-label'));
      const boxes = [...(group?.querySelectorAll('select') || [])].map((el) => el.getBoundingClientRect().left);
      return {
        labels,
        ordered: boxes.length === 3 && boxes[0] < boxes[1] && boxes[1] < boxes[2],
        exact: Boolean(pop?.querySelector('[data-date-exact]')),
        hint: pop?.querySelector('.ar-field-hint')?.textContent || '',
        more: Boolean(document.querySelector('.ar-more-link'))
      };
    });
    assert.deepEqual(ymd.labels, ['연도', '월', '일'], '날짜 항목이 연-월-일 순이 아니다: ' + JSON.stringify(ymd.labels));
    assert.equal(ymd.ordered, true, '날짜 선택 칸이 연-월-일 순으로 놓이지 않는다');
    assert.equal(ymd.exact, false, '이 사진 날짜 칸이 남아 있다');
    assert.equal(ymd.hint, '', '날짜 힌트가 남아 있다');
    assert.equal(ymd.more, false, '더보기 버튼이 남아 있다');

    const pager = await page.evaluate(() => {
      const jump = document.querySelector('.ar-page-jump');
      return {
        hasJump: Boolean(jump),
        goName: jump?.querySelector('input[name="go"]')?.name || '',
        label: jump?.querySelector('button')?.textContent.trim() || ''
      };
    });
    assert.equal(pager.hasJump, true, '페이지 이동 UI가 없다');
    assert.equal(pager.goName, 'go');
    assert.equal(pager.label, '이동');

    const tabs = await page.evaluate(() => {
      const current = document.querySelector('.site-section-tab[aria-current="page"]');
      const dateField = document.querySelector('label.ar-field');
      return {
        current: current?.textContent.trim() || '',
        underline: current ? getComputedStyle(current).borderBottomColor : '',
        dateLabel: dateField?.childNodes[0]?.textContent.trim() || '',
        dateHint: dateField?.querySelector('.ar-field-hint')?.textContent.trim() || ''
      };
    });
    assert.equal(tabs.current, '사진');
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

    const artifactDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(artifactDir, { recursive: true });
    await page.screenshot({ path: path.join(artifactDir, 'archive-layout-stats-members.png'), fullPage: true });
    console.log('PASS: 아카이브 통계 접기·검색 버튼·매슨리 그리드');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
