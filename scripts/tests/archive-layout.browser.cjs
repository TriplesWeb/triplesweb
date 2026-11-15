const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const css = fs.readFileSync(path.join(staticRoot, 'css', 'archive-page.css'), 'utf8');
const siteUi = fs.readFileSync(path.join(staticRoot, 'css', 'site-ui.css'), 'utf8');

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
${css}
${siteUi}
</style></head>
<body class="site-page">
<main class="archive-page" data-archive-kind="image" data-date-label="사진 날짜">
  <section class="ar-hero">
    <div class="ar-panel ar-headline">
      <div>
        <div class="ar-kicker">Archive</div>
        <h1>아카이브</h1>
      </div>
      <nav class="site-section-tabs" aria-label="아카이브 종류">
        <a class="site-section-tab" href="/archive" aria-current="page">사진</a>
        <a class="site-section-tab" href="/archive?type=video">영상</a>
      </nav>
    </div>
    <div class="ar-stats">
      <article class="ar-panel ar-stat"><div class="ar-stat-label"><span>등록된 사진</span><span class="material-symbols-outlined">image</span></div><strong>2<em>장</em></strong></article>
      <article class="ar-panel ar-stat"><div class="ar-stat-label"><span>이 페이지</span><span class="material-symbols-outlined">grid_view</span></div><strong>2<em>장</em></strong></article>
      <article class="ar-panel ar-stat"><div class="ar-stat-label"><span>페이지</span><span class="material-symbols-outlined">layers</span></div><strong>1<em>/ 1</em></strong></article>
      <article class="ar-panel ar-stat ar-stat-filter" style="--filter-count: 3"><div class="ar-stat-label"><span>검색</span><span class="material-symbols-outlined">search</span></div><div class="ar-filter-summary"><span class="ar-filter-member" style="--member-color:#22aeff">윤서연</span><span class="ar-filter-more">외 1명</span><span class="ar-filter-sep">,</span><strong>심장</strong><span class="ar-filter-sep">,</span><strong>2026-01-01 ~ 2026-12-31</strong></div></article>
    </div>
  </section>
  <section class="ar-panel ar-toolbar">
    <form class="ar-filters" id="archiveFilterForm">
      <label class="ar-sort"><span class="ar-sort-label">정렬</span><span class="ar-sort-control"><select id="archiveSort"><option>최신순</option></select><span class="material-symbols-outlined">arrow_drop_down</span></span></label>
      <div class="ar-date-filter"><button type="button" class="ar-date-toggle"><span>날짜</span></button></div>
      <button type="button" class="ar-member-toggle">멤버 선택</button>
      <div class="ar-search-wrap site-search"><input class="ar-search site-search__input" type="search" placeholder="사진 제목 검색"><button type="submit" class="site-search__submit" aria-label="검색"><span class="material-symbols-outlined">search</span></button></div>
      <a class="ar-text-btn ar-reset" href="/archive">초기화</a>
    </form>
  </section>
  <div class="ar-dialog-body">
    <label class="ar-field">사진 날짜<input type="date" name="registeredOn" required><span class="ar-field-hint">실제로 올라온 날짜를 선택하세요.</span></label>
  </div>
  <section class="ar-block">
    <div class="ar-track" id="archiveTrack">
      <article class="ar-panel ar-highlight" data-archive-ref="1">
        <img src="/portrait.svg" alt="" width="160" height="120">
        <div>
          <span class="ar-id">1</span>
          <h3>심장마빈즈</h3>
          <div class="ar-meta" title="${members.join(', ')}">
            ${chip('윤서연')}
            <span class="archive-member-more" title="${members.slice(1).join(', ')}">외 16명<span class="archive-member-rest">${members.slice(1).join(', ')}</span></span>
          </div>
        </div>
      </article>
      <article class="ar-panel ar-highlight" data-archive-ref="2">
        <img src="/portrait.svg" alt="" width="160" height="120">
        <div>
          <span class="ar-id">2</span>
          <h3>지서연</h3>
          <div class="ar-meta" title="지연">
            ${chip('지연')}
          </div>
        </div>
      </article>
    </div>
  </section>
  <section class="ar-grid" id="archiveGrid">
    <article class="ar-panel ar-card">
      <button type="button" class="ar-photo-link"><img src="/portrait.svg" alt=""></button>
      <div class="ar-body">
        <div class="ar-meta" style="--member-count: ${members.length}"><span class="ar-id">1</span>${members.map(chip).join('')}</div>
        <h3>심장마빈즈</h3>
      </div>
    </article>
    <article class="ar-panel ar-card" id="single-card">
      <button type="button" class="ar-photo-link"><img src="/portrait.svg" alt=""></button>
      <div class="ar-body">
        <div class="ar-meta" style="--member-count: 1"><span class="ar-id">2</span>${chip('지연')}</div>
        <h3>지서연</h3>
      </div>
    </article>
  </section>
</main>
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

    const highlight = await page.evaluate(() => {
      const many = document.querySelector('[data-archive-ref="1"]');
      const one = document.querySelector('[data-archive-ref="2"]');
      return {
        chips: many.querySelectorAll('.archive-member-chip').length,
        more: many.querySelector('.archive-member-more')?.childNodes[0]?.textContent.trim() || '',
        rest: many.querySelector('.archive-member-rest')?.textContent.trim() || '',
        moreWidth: Math.round(many.querySelector('.archive-member-more').getBoundingClientRect().width),
        moreInside: (() => {
          const card = many.getBoundingClientRect();
          const badge = many.querySelector('.archive-member-more').getBoundingClientRect();
          return badge.right <= card.right + 1 && badge.bottom <= card.bottom + 1;
        })(),
        avatar: Math.round(many.querySelector('.archive-member-chip img').getBoundingClientRect().height),
        photo: Math.round(many.querySelector(':scope > img').getBoundingClientRect().height),
        manyHeight: Math.round(many.getBoundingClientRect().height),
        oneHeight: Math.round(one.getBoundingClientRect().height),
        align: getComputedStyle(document.querySelector('.ar-track')).alignItems
      };
    });
    assert.equal(highlight.chips, 1, '최근 사진은 첫 멤버만 보여야 한다');
    assert.equal(highlight.more, '외 16명');
    assert.equal(highlight.rest, members.slice(1).join(', '));
    assert.ok(highlight.moreWidth < 140, '나머지 멤버 이름이 배지를 늘린다: ' + highlight.moreWidth);
    assert.equal(highlight.moreInside, true, '외 n명 배지가 카드 밖으로 잘린다');
    assert.ok(highlight.avatar >= 20 && highlight.avatar <= 40, '최근 사진 멤버 이미지가 사진 높이에 끌려간다: ' + highlight.avatar);
    assert.ok(highlight.photo > 100, '최근 사진 원본이 줄어들었다: ' + highlight.photo);
    assert.equal(highlight.align, 'flex-start');
    assert.ok(highlight.manyHeight < 420, '최근 사진 카드가 너무 길다: ' + highlight.manyHeight);
    assert.ok(Math.abs(highlight.oneHeight - highlight.manyHeight) < 80, '짧은 카드가 긴 카드에 맞춰 늘어난다: ' + JSON.stringify(highlight));

    const grid = await page.evaluate(() => {
      const many = document.querySelector('#archiveGrid .ar-card');
      const one = document.getElementById('single-card');
      const manyImg = many.querySelector('.archive-member-chip img').getBoundingClientRect();
      const oneImg = one.querySelector('.archive-member-chip img').getBoundingClientRect();
      const chips = [...many.querySelectorAll('.archive-member-chip')];
      const tops = new Set(chips.map((chip) => Math.round(chip.getBoundingClientRect().top)));
      const name = chips[0].querySelector('.archive-chip-name');
      const nameBox = name.getBoundingClientRect();
      return {
        manyImg: manyImg.height,
        oneImg: oneImg.height,
        manyMeta: Math.round(many.querySelector('.ar-meta').getBoundingClientRect().height),
        rows: tops.size,
        count: chips.length,
        nameHidden: nameBox.width <= 1 && nameBox.height <= 1,
        name: name.textContent,
        logo: chips[0].classList.contains('archive-symbol-chip'),
        photoH: Math.round(many.querySelector('.ar-photo-link img').getBoundingClientRect().height)
      };
    });
    assert.ok(grid.oneImg > 20 && grid.oneImg <= 40, '한 명 카드 배지 크기가 깨졌다: ' + grid.oneImg);
    assert.ok(grid.manyImg < grid.oneImg - 4, '멤버가 많을 때 배지가 작아지지 않는다: ' + JSON.stringify(grid));
    assert.equal(grid.nameHidden, true, '상징로고 칩에 이름이 드러난다: ' + JSON.stringify(grid));
    assert.equal(grid.logo, true, '상징로고 칩 클래스가 없다');
    assert.ok(grid.rows < grid.count, '그리드 멤버가 한 줄에 하나씩 쌓인다: ' + JSON.stringify(grid));
    assert.ok(grid.manyMeta < 280, '그리드 멤버 영역이 너무 높다: ' + grid.manyMeta);
    assert.ok(grid.photoH <= 250, '목록 사진 행이 너무 높다: ' + grid.photoH);

    const toolbar = await page.evaluate(() => {
      const search = document.querySelector('.ar-search-wrap');
      const date = document.querySelector('.ar-date-toggle');
      const member = document.querySelector('.ar-member-toggle');
      const reset = document.querySelector('.ar-reset');
      const submit = document.querySelector('.site-search__submit');
      const searchBox = search.getBoundingClientRect();
      const dateBox = date.getBoundingClientRect();
      const memberBox = member.getBoundingClientRect();
      const resetBox = reset.getBoundingClientRect();
      const submitBox = submit.getBoundingClientRect();
      return {
        searchW: Math.round(searchBox.width),
        dateBeforeSearch: dateBox.left < searchBox.left,
        memberSameRow: Math.abs(memberBox.top - searchBox.top) < 24,
        resetAfterSearch: resetBox.left > searchBox.right - 8,
        submitInside: submitBox.left >= searchBox.left && submitBox.right <= searchBox.right + 1,
        filterSize: getComputedStyle(document.querySelector('.ar-filter-summary')).fontSize
      };
    });
    assert.ok(toolbar.searchW < 420, '검색창이 한 줄을 차지한다: ' + toolbar.searchW);
    assert.equal(toolbar.dateBeforeSearch, true, '날짜 버튼이 검색창 왼쪽에 없다');
    assert.equal(toolbar.memberSameRow, true, '멤버 선택이 다른 줄에 있다');
    assert.equal(toolbar.resetAfterSearch, true, '초기화가 검색창 오른쪽에 없다');
    assert.equal(toolbar.submitInside, true, '검색 버튼이 검색창 밖에 있다');
    assert.ok(parseFloat(toolbar.filterSize) < 22, '필터 글자가 줄지 않는다: ' + toolbar.filterSize);

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

    const artifactDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(artifactDir, { recursive: true });
    await page.screenshot({ path: path.join(artifactDir, 'archive-layout-stats-members.png'), fullPage: true });
    console.log('PASS: 아카이브 통계 글꼴·최근 사진 요약·그리드 멤버 축소');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
