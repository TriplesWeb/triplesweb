const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const css = fs.readFileSync(path.join(staticRoot, 'css', 'archive-page.css'), 'utf8');
const detailJs = fs.readFileSync(path.join(staticRoot, 'js', 'archive-detail.js'), 'utf8');

const cards = Array.from({ length: 6 }, (_, index) => `
<article class="ar-panel ar-card" data-id="${index + 1}" data-title="사진 ${index + 1}" data-url="/portrait.svg" data-artists="지연">
  <button type="button" class="ar-photo-link" data-archive-open>
    <img src="/portrait.svg" alt="사진 ${index + 1}" width="320" height="${180 + index * 24}">
  </button>
  <div class="ar-body">
    <h3>사진 ${index + 1}</h3>
    <div class="ar-meta"><span class="archive-member-chip archive-symbol-chip" title="지연" aria-label="지연"><img src="/portrait.svg" alt="지연"><span class="archive-chip-name">지연</span></span></div>
    <div class="ar-card-members" hidden><a href="/triples/artist/24" class="ar-album-member" style="--member-color:#f4a261" title="지연"><img src="/portrait.svg" alt=""><span>지연</span></a></div>
  </div>
</article>`).join('');

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
      <div class="ar-dialog-head"><h2 id="archiveDetailTitle"></h2></div>
      <div class="ar-detail-body">
        <p class="ar-detail-date" id="archiveDetailDate" hidden></p>
        <div class="ar-detail-members" id="archiveDetailMembers"></div>
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
      blank: document.querySelector('.ar-card [data-archive-open]')?.tagName,
      original: Boolean(document.getElementById('archiveDetailOriginal')),
      member: document.querySelector('#archiveDetailMembers .ar-album-member span')?.textContent || '',
      portrait: document.querySelector('#archiveDetailMembers .ar-album-member img')?.getAttribute('src') || ''
    }));
    assert.equal(detail.open, true, '상세 모달이 열리지 않는다');
    assert.equal(detail.title, '사진 2');
    assert.equal(detail.blank, 'BUTTON');
    assert.equal(detail.original, false, '원본 열기가 남아 있다');
    assert.equal(detail.member, '지연');
    assert.equal(detail.portrait, '/portrait.svg');

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
