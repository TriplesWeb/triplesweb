const {chromium} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const logoRoot = path.join(staticRoot, 'images/objekt-member-logos');
const css = fs.readFileSync(path.join(staticRoot, 'css/objekt-page.css'), 'utf8');
const thumbPath = '/tmp/objekt-card-chrome-thumb.webp';
const thumbUrl = 'https://media.objekt.top/collection-images/thumbnail/cream02-sullin-356a-1793700000838.webp';

const members = [
  ['윤서연', 'SeoYeon', '#22aeff', 's01'],
  ['정혜린', 'HyeRin', '#9200ff', 's02'],
  ['이지우', 'JiWoo', '#fff800', 's03'],
  ['김채연', 'ChaeYeon', '#98f21d', 's04'],
  ['김유연', 'YooYeon', '#db0c74', 's05'],
  ['김수민', 'SooMin', '#fc83a4', 's06'],
  ['김나경', 'NaKyoung', '#6799a0', 's07'],
  ['공유빈', 'YuBin', '#ffe3e2', 's08'],
  ['카에데', 'Kaede', '#ffc935', 's09'],
  ['서다현', 'DaHyun', '#ff9ad6', 's10'],
  ['코토네', 'Kotone', '#ffde00', 's11'],
  ['곽연지', 'YeonJi', '#5974ff', 's12'],
  ['니엔', 'Nien', '#ff953f', 's13'],
  ['박소현', 'SoHyun', '#1222b5', 's14'],
  ['신위', 'XinYu', '#d51313', 's15'],
  ['마유', 'Mayu', '#fe8e76', 's16'],
  ['린', 'Lynn', '#ac62b7', 's17'],
  ['주빈', 'JooBin', '#b7f54c', 's18'],
  ['정하연', 'HaYeon', '#52d9bb', 's19'],
  ['박시온', 'ShiOn', '#ff428a', 's20'],
  ['김채원', 'ChaeWon', '#c7a3e0', 's21'],
  ['설린', 'Sullin', '#7bba8d', 's22'],
  ['서아', 'SeoAh', '#cff3ff', 's23'],
  ['지연', 'JiYeon', '#ffab62', 's24']
];

function cardHtml({member, english, color, logo, klass, season, title}) {
  const mark = logo
    ? `<img class="objekt-member-logo" src="/images/objekt-member-logos/${logo}.png" alt="" aria-hidden="true">`
    : '<span class="objekt-member-logo" aria-hidden="true"></span>';
  return `<article class="card objekt-card objekt-item group relative border rounded-2xl overflow-hidden" data-member="${member}">
  <button type="button" class="objekt-open-detail" aria-label="Cream02 ${english} 356A 상세 보기">
    <img src="/preview/sullin.webp" alt="Cream02 ${english} 356A" class="objekt-image">
  </button>
  <div class="card-body objekt-card-body">
    <div class="objekt-card-badges">
      <span class="badge badge-sm objekt-card-season" style="background:#FF8389">${season || 'Cream02'}</span>
      <a href="/cosmo/objekt/detail/cream02-${english.toLowerCase()}-356a" class="objekt-detail-link" title="Cream02 ${english} 356A">${title || english + ' 356A'}</a>
    </div>
    <div class="objekt-card-foot">
      <a href="/cosmo/objekt/member?name=${encodeURIComponent(member)}" class="objekt-member-chip" style="--artist-color:${color}">
        ${mark}
        <span class="objekt-member-name">${member}</span>
      </a>
      <span class="badge badge-sm badge-neutral objekt-card-class">${klass || 'Double'}</span>
    </div>
    <p class="objekt-card-supply">발행량 1개</p>
  </div>
</article>`;
}

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
.objekt-card-grid{width:min(72rem,100%);margin:1.5rem auto;padding:0 1rem;display:grid;grid-template-columns:repeat(auto-fill,minmax(11.5rem,1fr));gap:12px}
${css}
</style></head><body>
<div class="objekt-card-grid">
${members.map(([member, english, color, logo]) => cardHtml({member, english, color, logo})).join('\n')}
${cardHtml({member: 'zenith', english: 'zenith', color: '#00f5d4', logo: '', title: 'zenith 001Z'})}
${cardHtml({member: 'S8 X S12', english: 'unit', color: '#00f5d4', logo: '', title: 'S8 X S12 602Z'})}
</div>
</body></html>`;

  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage({viewport: {width: 1100, height: 1600}});
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/' || url.pathname === '/preview/card') {
        return route.fulfill({contentType: 'text/html', body: html});
      }
      if (url.pathname === '/preview/sullin.webp' && fs.existsSync(thumbPath)) {
        return route.fulfill({path: thumbPath, contentType: 'image/webp'});
      }
      if (url.pathname.startsWith('/images/objekt-member-logos/')) {
        const file = path.join(logoRoot, path.basename(url.pathname));
        if (file.startsWith(logoRoot + path.sep) && fs.existsSync(file)) {
          return route.fulfill({path: file, contentType: 'image/png'});
        }
      }
      return route.fulfill({status: 404, body: ''});
    });
    await page.goto('https://preview.test/preview/card', {waitUntil: 'load'});
    await page.waitForFunction(() => [...document.querySelectorAll('img.objekt-member-logo')]
      .every(img => img.complete && img.naturalWidth > 0));
    assert.equal(await page.locator('img.objekt-member-logo').count(), 24);
    assert.equal(await page.locator('span.objekt-member-logo').count(), 2);
    for (const [member, , , logo] of members) {
      const src = await page.locator(`[data-member="${member}"] img.objekt-member-logo`).getAttribute('src');
      assert.equal(src, `/images/objekt-member-logos/${logo}.png`);
      const nameColor = await page.locator(`[data-member="${member}"] .objekt-member-name`).evaluate(el => getComputedStyle(el).color);
      assert.equal(nameColor, 'rgb(244, 246, 251)');
    }
    assert.equal(await page.locator('[data-member="설린"] img.objekt-member-logo').getAttribute('src'), '/images/objekt-member-logos/s22.png');
    for (const member of ['zenith', 'S8 X S12']) {
      const empty = page.locator(`[data-member="${member}"] span.objekt-member-logo`);
      assert.equal(await empty.count(), 1);
      const style = await empty.evaluate(el => ({mask: getComputedStyle(el).maskImage, border: getComputedStyle(el).borderTopWidth, text: el.textContent}));
      assert.equal(style.mask, 'none', `${member}: 음표 대신 빈 사진`);
      assert.equal(style.border, '1px');
      assert.equal(style.text, '');
    }
    fs.mkdirSync('/tmp/objekt-ui', {recursive: true});
    fs.mkdirSync('/opt/cursor/artifacts', {recursive: true});
    await page.screenshot({path: '/tmp/objekt-ui/objekt-card-chrome.png', fullPage: true});
    await page.screenshot({path: '/opt/cursor/artifacts/objekt-card-s1-s24-logos.png', fullPage: true});
    await page.locator('[data-member="코토네"]').screenshot({path: '/opt/cursor/artifacts/objekt-card-kotone-logo.png'});
    await page.locator('[data-member="설린"]').screenshot({path: '/opt/cursor/artifacts/objekt-card-sullin-logo.png'});
    await page.locator('[data-member="지연"]').screenshot({path: '/opt/cursor/artifacts/objekt-card-jiyeon-logo.png'});
    await page.setViewportSize({width: 390, height: 4200});
    await page.screenshot({path: '/opt/cursor/artifacts/objekt-card-s1-s24-logos-mobile.png', fullPage: true});
    console.log('PASS: 오브젝트 카드 S1~S24 멤버 로고 · 유닛/기타 빈 사진');
  } finally {
    await browser.close();
  }
})().catch(error => {
  console.error(error);
  process.exit(1);
});
