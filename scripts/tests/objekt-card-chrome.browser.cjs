const {chromium} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const css = fs.readFileSync(path.join(staticRoot, 'css/objekt-page.css'), 'utf8');
const thumbPath = '/tmp/objekt-card-chrome-thumb.webp';
const thumbUrl = 'https://media.objekt.top/collection-images/thumbnail/cream02-sullin-356a-1793700000838.webp';

(async () => {
  if (!fs.existsSync(thumbPath)) {
    try {
      const res = await fetch(thumbUrl, {signal: AbortSignal.timeout(15000)});
      if (res.ok) fs.writeFileSync(thumbPath, Buffer.from(await res.arrayBuffer()));
    } catch (error) {
      console.warn('썸네일 미리받기를 건너뜁니다:', error.message);
    }
  }
  const html = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
.objekt-card-grid{width:min(22rem,100%);margin:1.5rem auto;padding:0 1rem}
${css}
</style></head><body>
<div class="objekt-card-grid">
<article class="card objekt-card objekt-item group relative border rounded-2xl overflow-hidden">
  <button type="button" class="objekt-open-detail" aria-label="Cream02 Sullin 356A 상세 보기">
    <img src="/preview/sullin.webp" alt="Cream02 Sullin 356A" class="objekt-image">
  </button>
  <div class="card-body objekt-card-body">
    <div class="objekt-card-badges">
      <span class="badge badge-sm objekt-card-season" style="background:#FF8389">Cream02</span>
      <a href="/cosmo/objekt/detail/cream02-sullin-356a" class="objekt-detail-link" title="Cream02 Sullin 356A">Sullin 356A</a>
    </div>
    <div class="objekt-card-foot">
      <a href="/cosmo/objekt/member?name=%EC%84%A4%EB%A6%B0" class="objekt-member-chip" style="--artist-color:#7bba8d">
        <svg class="objekt-member-logo" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path fill="currentColor" d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/></svg>
        <span class="objekt-member-name">설린</span>
      </a>
      <span class="badge badge-sm badge-neutral objekt-card-class">Double</span>
    </div>
    <p class="objekt-card-supply">발행량 1개</p>
  </div>
</article>
</div>
</body></html>`;

  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage({viewport: {width: 420, height: 780}});
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/' || url.pathname === '/preview/card') {
        return route.fulfill({contentType: 'text/html', body: html});
      }
      if (url.pathname === '/preview/sullin.webp' && fs.existsSync(thumbPath)) {
        return route.fulfill({path: thumbPath, contentType: 'image/webp'});
      }
      return route.fulfill({status: 404, body: ''});
    });
    await page.goto('https://preview.test/preview/card', {waitUntil: 'load'});
    assert.equal(await page.locator('.objekt-member-logo').count(), 1);
    assert.equal(await page.locator('.objekt-member-name').textContent(), '설린');
    assert.equal(await page.locator('.objekt-member-name').evaluate(el => getComputedStyle(el).color), 'rgb(244, 246, 251)');
    assert.equal(await page.locator('.objekt-member-logo').evaluate(el => getComputedStyle(el).color), 'rgb(0, 245, 212)');
    fs.mkdirSync('/tmp/objekt-ui', {recursive: true});
    fs.mkdirSync('/opt/cursor/artifacts', {recursive: true});
    await page.screenshot({path: '/tmp/objekt-ui/objekt-card-chrome.png'});
    await page.screenshot({path: '/opt/cursor/artifacts/objekt-card-member-logo.png'});
    await page.setViewportSize({width: 360, height: 720});
    await page.screenshot({path: '/opt/cursor/artifacts/objekt-card-member-logo-mobile.png'});
    console.log('PASS: 오브젝트 카드 멤버 이름 흰색 · 음표 로고');
  } finally {
    await browser.close();
  }
})().catch(error => {
  console.error(error);
  process.exit(1);
});
