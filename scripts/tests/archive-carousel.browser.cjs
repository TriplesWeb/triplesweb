const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const css = fs.readFileSync(path.join(staticRoot, 'css', 'archive-page.css'), 'utf8');
const carouselJs = fs.readFileSync(path.join(staticRoot, 'js', 'archive-photo-carousel.js'), 'utf8');
const detailJs = fs.readFileSync(path.join(staticRoot, 'js', 'archive-detail.js'), 'utf8');

const cards = Array.from({ length: 6 }, (_, index) => `
<article class="ar-panel ar-card" data-id="${index + 1}" data-title="사진 ${index + 1}" data-url="/portrait.svg" data-artists="지연">
  <button type="button" class="ar-photo-link" data-archive-open>
    <img src="/portrait.svg" alt="사진 ${index + 1}" width="320" height="240">
  </button>
  <div class="ar-body"><h3>사진 ${index + 1}</h3></div>
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
  <div class="ar-gallery-bar">
    <div class="ar-gallery-controls" id="archiveGalleryControls" hidden>
      <div class="ar-gallery-navigation">
        <button type="button" class="ar-icon-btn" id="archiveGalleryPrev">이전</button>
        <span class="ar-gallery-position" id="archiveGalleryPosition"></span>
        <button type="button" class="ar-icon-btn" id="archiveGalleryNext">다음</button>
      </div>
    </div>
    <a class="ar-more-link" href="/archive/all">더보기</a>
  </div>
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
        <div class="ar-meta ar-detail-members" id="archiveDetailMembers"></div>
        <a id="archiveDetailOriginal" href="#"></a>
      </div>
      <div class="ar-dialog-foot ar-detail-actions" data-admin-only>
        <button type="button" data-archive-edit id="archiveDetailEdit">수정</button>
        <button type="button" data-archive-delete id="archiveDetailDelete">삭제</button>
      </div>
    </div>
  </div>
</div>
<script>${carouselJs}</script>
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
    await page.waitForSelector('.is-photo-carousel');

    const first = await page.evaluate(() => ({
      carousel: document.getElementById('archiveGrid').classList.contains('is-photo-carousel'),
      position: document.getElementById('archiveGalleryPosition').textContent.trim(),
      current: document.querySelector('.is-current-photo')?.dataset.title || ''
    }));
    assert.equal(first.carousel, true, '사진이 늘어도 캐러셀이 켜지지 않는다');
    assert.equal(first.position, '1 / 6');
    assert.equal(first.current, '사진 1');

    await page.click('#archiveGalleryNext');
    await page.waitForTimeout(400);
    const second = await page.evaluate(() => ({
      position: document.getElementById('archiveGalleryPosition').textContent.trim(),
      current: document.querySelector('.is-current-photo')?.dataset.title || ''
    }));
    assert.equal(second.position, '2 / 6');
    assert.equal(second.current, '사진 2');

    await page.locator('.is-current-photo [data-archive-open]').click();
    const detail = await page.evaluate(() => ({
      open: !document.getElementById('archiveDetail').hidden,
      title: document.getElementById('archiveDetailTitle').textContent,
      blank: document.querySelector('.ar-card [data-archive-open]')?.tagName
    }));
    assert.equal(detail.open, true, '상세 모달이 열리지 않는다');
    assert.equal(detail.title, '사진 2');
    assert.equal(detail.blank, 'BUTTON');

    const artifactDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(artifactDir, { recursive: true });
    await page.screenshot({ path: path.join(artifactDir, 'archive-carousel-detail.png') });
    console.log('PASS: 아카이브 캐러셀 넘김·상세 모달');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
