const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const css = fs.readFileSync(path.join(staticRoot, 'css', 'archive-page.css'), 'utf8');
const detailJs = fs.readFileSync(path.join(staticRoot, 'js', 'archive-detail.js'), 'utf8');

const names = ['윤서연', '정혜린', '이지우', '김채연', '김유연', '김수민', '김나경', '공유빈', '카에데', '서다현', '코토네', '곽연지', '니엔', '박소현', '신위', '마유', '주빈'];
function albumMembers(list) {
  return list.map((name) => `<a href="/triples/artist/1" class="unit-member-chip" style="--member-color:#f4a261" title="${name}"><img src="/portrait.svg" alt=""><span>${name}</span></a>`).join('');
}
const cards = Array.from({ length: 6 }, (_, index) => {
  const many = index === 0;
  const list = many ? names : ['지연'];
  return `
<article class="ar-panel ar-card" data-id="${index + 1}" data-title="사진 ${index + 1}" data-url="/portrait.svg" data-artists="${list.join(',')}">
  <button type="button" class="ar-photo-link" data-archive-open>
    <img src="/portrait.svg" alt="사진 ${index + 1}" width="320" height="${180 + index * 24}">
  </button>
  <div class="ar-body">
    <h3>사진 ${index + 1}</h3>
    <div class="ar-meta"><span class="archive-member-chip archive-symbol-chip" title="지연" aria-label="지연"><img src="/portrait.svg" alt="지연"><span class="archive-chip-name">지연</span></span></div>
    <div class="ar-card-members" hidden>${albumMembers(list)}</div>
  </div>
</article>`;
}).join('');

const html = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
*,*::before,*::after{box-sizing:border-box}
img{max-width:100%;height:auto}
body{margin:0;background:#0d0e12;color:#e3e2e8}
${css}
</style></head>
<body>
<main class="archive-page" data-archive-kind="image">
  <section class="ar-grid" id="archiveGrid">${cards}</section>
</main>
<div class="ar-dialog" id="archiveDetail" hidden>
  <button type="button" class="ar-dialog-backdrop" data-ar-close></button>
  <div class="ar-dialog-panel ar-detail-panel">
    <div class="ar-detail-photo"><img id="archiveDetailImage" alt=""></div>
    <div class="ar-detail-side">
      <div class="ar-dialog-head"><h2 id="archiveDetailTitle">사진</h2></div>
      <div class="ar-detail-body">
        <p class="ar-detail-desc" id="archiveDetailDescription" hidden></p>
        <div class="ar-detail-meta" id="archiveDetailMeta" hidden>
          <p class="ar-detail-date" id="archiveDetailDate" hidden>
            <span class="ar-detail-kicker" data-detail-date-label></span>
            <time data-detail-date-value></time>
          </p>
          <div class="ar-source-badge" id="archiveDetailSource" hidden></div>
        </div>
        <section class="ar-detail-cast" aria-label="출연 멤버" hidden>
          <p class="ar-detail-kicker">출연 멤버</p>
          <div class="ar-detail-members" id="archiveDetailMembers"></div>
        </section>
      </div>
      <div class="ar-dialog-foot ar-detail-actions" data-admin-only>
        <button type="button" data-archive-edit id="archiveDetailEdit">수정</button>
        <button type="button" data-archive-delete id="archiveDetailDelete">삭제</button>
      </div>
    </div>
  </div>
</div>
<script>${detailJs}</script>
</body></html>`;

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage({ viewport: { width: 1100, height: 900 } });
    await page.route('**/*', (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === '/' || url.pathname.endsWith('.html')) {
        return route.fulfill({ contentType: 'text/html', body: html });
      }
      if (url.pathname === '/portrait.svg') {
        return route.fulfill({
          contentType: 'image/svg+xml',
          body: '<svg xmlns="http://www.w3.org/2000/svg" width="320" height="240"><rect width="320" height="240" fill="#244"/></svg>'
        });
      }
      return route.fulfill({ status: 404, body: '' });
    });
    await page.goto('https://archive-carousel.test/', { waitUntil: 'load' });

    const layout = await page.evaluate(() => {
      const grid = document.getElementById('archiveGrid');
      const cards = [...grid.querySelectorAll('.ar-card')];
      const firstTitle = cards[0].querySelector('h3').getBoundingClientRect();
      const firstMeta = cards[0].querySelector('.ar-meta').getBoundingClientRect();
      return {
        carousel: grid.classList.contains('is-photo-carousel'),
        columns: getComputedStyle(grid).gridTemplateColumns.split(' ').filter(Boolean).length,
        visible: cards.filter((card) => card.getBoundingClientRect().height > 40).length,
        titleBefore: firstTitle.bottom <= firstMeta.top + 1
      };
    });
    assert.equal(layout.carousel, false, '캐러셀이 켜져 있다');
    assert.ok(layout.columns >= 2, '사진이 여러 열로 배치되지 않는다: ' + layout.columns);
    assert.ok(layout.visible >= 4, '여러 장이 한 화면에 보이지 않는다: ' + layout.visible);
    assert.equal(layout.titleBefore, true, '제목이 멤버 로고보다 아래에 있다');

    await page.locator('#archiveGrid .ar-card').nth(1).locator('[data-archive-open]').click();
    const detail = await page.evaluate(() => ({
      open: !document.getElementById('archiveDetail').hidden,
      title: document.getElementById('archiveDetailTitle').textContent,
      description: document.getElementById('archiveDetailDescription').textContent,
      descriptionHidden: document.getElementById('archiveDetailDescription').hidden,
      blank: document.querySelector('.ar-card [data-archive-open]')?.tagName,
      original: Boolean(document.getElementById('archiveDetailOriginal')),
      member: document.querySelector('#archiveDetailMembers .unit-member-chip span')?.textContent || '',
      portrait: document.querySelector('#archiveDetailMembers .unit-member-chip img')?.getAttribute('src') || '',
      cols: new Set([...document.querySelectorAll('#archiveDetailMembers .unit-member-chip')].map((el) => Math.round(el.getBoundingClientRect().left))).size,
      size: Math.round(document.querySelector('#archiveDetailMembers .unit-member-chip img')?.getBoundingClientRect().width || 0),
      outside: !document.getElementById('archiveDetailMembers').contains(document.getElementById('archiveDetailDate'))
    }));
    assert.equal(detail.open, true, '상세 모달이 열리지 않는다');
    assert.equal(detail.title, '사진');
    assert.equal(detail.description, '사진 2');
    assert.equal(detail.descriptionHidden, false);
    assert.equal(detail.blank, 'BUTTON');
    assert.equal(detail.original, false, '원본 열기가 남아 있다');
    assert.equal(detail.member, '지연');
    assert.equal(detail.portrait, '/portrait.svg');
    assert.equal(detail.cols, 1, '한 명일 때도 여러 열이다: ' + detail.cols);
    assert.ok(detail.size >= 32, '한 명 칩이 너무 작다: ' + detail.size);
    assert.equal(detail.outside, true, '날짜가 멤버 영역에 있다');

    await page.evaluate(() => {
      document.getElementById('archiveDetail').hidden = true;
      document.body.style.overflow = '';
    });
    await page.locator('#archiveGrid .ar-card').nth(0).locator('[data-archive-open]').click();
    const crowded = await page.evaluate(() => {
      const box = document.getElementById('archiveDetailMembers');
      const chips = [...box.querySelectorAll('.unit-member-chip')];
      const img = box.querySelector('.unit-member-chip img')?.getBoundingClientRect();
      return {
        count: chips.length,
        cols: new Set(chips.map((el) => Math.round(el.getBoundingClientRect().left))).size,
        size: Math.round(img?.width || 0),
        rows: new Set(chips.map((el) => Math.round(el.getBoundingClientRect().top))).size
      };
    });
    assert.equal(crowded.count, 17, '많은 멤버가 복사되지 않는다: ' + crowded.count);
    assert.ok(crowded.cols >= 3, '멤버가 많을 때 여러 열이 아니다: ' + crowded.cols);
    assert.ok(crowded.size >= 32, '멤버 칩이 너무 작다: ' + crowded.size);
    assert.ok(crowded.rows >= 4, '멤버가 여러 줄로 안 나뉜다: ' + crowded.rows);

    const artifactDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(artifactDir, { recursive: true });
    await page.screenshot({ path: path.join(artifactDir, 'archive-masonry-detail.png') });
    console.log('PASS: 아카이브 매슨리 목록·상세 모달');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
