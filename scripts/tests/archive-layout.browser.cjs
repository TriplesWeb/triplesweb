const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const css = fs.readFileSync(path.join(staticRoot, 'css', 'archive-page.css'), 'utf8');

const members = ['윤서연', '정혜린', '이지우', '김채연', '김유연', '김수민', '김나경', '공유빈', '카에데', '서다현', '코토네', '곽연지', '니엔', '박소현', '신위', '마유', '주빈'];

function chip(name) {
  return `<span class="archive-member-chip" style="--member-color:#7bba8d"><img src="/portrait.svg" alt=""><span>${name}</span></span>`;
}

const html = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
${css}
</style></head>
<body class="site-page">
<main class="archive-page" data-archive-kind="image">
  <section class="ar-hero">
    <div class="ar-stats">
      <article class="ar-panel ar-stat"><div class="ar-stat-label"><span>등록된 사진</span><span class="material-symbols-outlined">image</span></div><strong>2<em>장</em></strong></article>
      <article class="ar-panel ar-stat"><div class="ar-stat-label"><span>이 페이지</span><span class="material-symbols-outlined">grid_view</span></div><strong>2<em>장</em></strong></article>
      <article class="ar-panel ar-stat"><div class="ar-stat-label"><span>페이지</span><span class="material-symbols-outlined">layers</span></div><strong>1<em>/ 1</em></strong></article>
      <article class="ar-panel ar-stat"><div class="ar-stat-label"><span>검색</span><span class="material-symbols-outlined">search</span></div><strong>전체</strong></article>
    </div>
  </section>
  <section class="ar-block">
    <div class="ar-track" id="archiveTrack">
      <article class="ar-panel ar-highlight" data-archive-ref="1">
        <img src="/portrait.svg" alt="" width="160" height="120">
        <div>
          <span class="ar-id">1</span>
          <h3>심장마빈즈</h3>
          <div class="ar-meta" title="${members.join(', ')}">
            ${chip('윤서연')}
            <span class="archive-member-more">외 16명</span>
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
      <a class="ar-photo-link"><img src="/portrait.svg" alt=""></a>
      <div class="ar-body">
        <div class="ar-meta" style="--member-count: ${members.length}"><span class="ar-id">1</span>${members.map(chip).join('')}</div>
        <h3>심장마빈즈</h3>
      </div>
    </article>
    <article class="ar-panel ar-card" id="single-card">
      <a class="ar-photo-link"><img src="/portrait.svg" alt=""></a>
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
      if (url.pathname === '/portrait.svg') {
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
        more: many.querySelector('.archive-member-more')?.textContent.trim() || '',
        manyHeight: Math.round(many.getBoundingClientRect().height),
        oneHeight: Math.round(one.getBoundingClientRect().height),
        align: getComputedStyle(document.querySelector('.ar-track')).alignItems
      };
    });
    assert.equal(highlight.chips, 1, '최근 사진은 첫 멤버만 보여야 한다');
    assert.equal(highlight.more, '외 16명');
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
      return {
        manyImg: manyImg.height,
        oneImg: oneImg.height,
        manyMeta: Math.round(many.querySelector('.ar-meta').getBoundingClientRect().height),
        rows: tops.size,
        count: chips.length
      };
    });
    assert.ok(grid.oneImg > 20 && grid.oneImg <= 40, '한 명 카드 배지 크기가 깨졌다: ' + grid.oneImg);
    assert.ok(grid.manyImg < grid.oneImg - 4, '멤버가 많을 때 배지가 작아지지 않는다: ' + JSON.stringify(grid));
    assert.ok(grid.rows < grid.count, '그리드 멤버가 한 줄에 하나씩 쌓인다: ' + JSON.stringify(grid));
    assert.ok(grid.manyMeta < 280, '그리드 멤버 영역이 너무 높다: ' + grid.manyMeta);

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
