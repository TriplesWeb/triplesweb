const {chromium} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const maskPath = '/images/objekt-member-logos/clef.png';
const css = ['objekt-page.css', 'objekt-grid-cols.css']
  .map(file => fs.readFileSync(path.join(staticRoot, 'css', file), 'utf8')).join('\n');
const thumbPath = '/images/image-upload-pending.png';

const members = [
  ['윤서연', 'SeoYeon', '#22aeff'],
  ['정혜린', 'HyeRin', '#9200ff'],
  ['이지우', 'JiWoo', '#fff800'],
  ['김채연', 'ChaeYeon', '#98f21d'],
  ['김유연', 'YooYeon', '#db0c74'],
  ['김수민', 'SooMin', '#fc83a4'],
  ['김나경', 'NaKyoung', '#6799a0'],
  ['공유빈', 'YuBin', '#ffe3e2'],
  ['카에데', 'Kaede', '#ffc935'],
  ['서다현', 'DaHyun', '#ff9ad6'],
  ['코토네', 'Kotone', '#ffde00'],
  ['곽연지', 'YeonJi', '#5974ff'],
  ['니엔', 'Nien', '#ff953f'],
  ['박소현', 'SoHyun', '#1222b5'],
  ['신위', 'XinYu', '#d51313'],
  ['마유', 'Mayu', '#fe8e76'],
  ['린', 'Lynn', '#ac62b7'],
  ['주빈', 'JooBin', '#b7f54c'],
  ['정하연', 'HaYeon', '#52d9bb'],
  ['박시온', 'ShiOn', '#ff428a'],
  ['김채원', 'ChaeWon', '#c7a3e0'],
  ['설린', 'Sullin', '#7bba8d'],
  ['서아', 'SeoAh', '#cff3ff'],
  ['지연', 'JiYeon', '#ffab62']
];

function cardHtml({member, memberDisplay = member, english, fill, klass, season, title}) {
  const mark = fill
    ? `<span class="objekt-member-logo objekt-member-logo--filled" aria-hidden="true" style="--member-logo-mask:url('${maskPath}');--member-mark-fill:${fill}"></span>`
    : '<span class="objekt-member-logo" aria-hidden="true"></span>';
  return `<article class="card objekt-card objekt-item group relative border rounded-2xl overflow-hidden" data-member="${member}">
  <button type="button" class="objekt-open-detail" aria-label="Cream02 ${english} 356A 상세 보기">
    <img src="${thumbPath}" alt="Cream02 ${english} 356A" class="objekt-image">
  </button>
  <div class="card-body objekt-card-body">
    <div class="objekt-card-badges">
      <span class="badge badge-sm objekt-card-season" style="background:#FF8389">${season || 'Cream02'}</span>
      <span class="objekt-card-title" title="Cream02 ${english} 356A">${title || english + ' 356A'}</span>
    </div>
    <div class="objekt-card-foot">
      <span class="objekt-member-chip">
        ${mark}
        <span class="objekt-member-name">${memberDisplay}</span>
      </span>
      <span class="badge badge-sm badge-neutral objekt-card-class">${klass || 'Double'}</span>
    </div>
    <p class="objekt-card-supply">발행량 1개</p>
  </div>
</article>`;
}

(async () => {
  const html = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
.objekt-card-grid{box-sizing:border-box;width:min(72rem,100%);margin:1.5rem auto;padding:0 1rem;display:grid;grid-template-columns:repeat(auto-fill,minmax(11.5rem,1fr));gap:12px}
${css}
</style></head><body>
<div class="objekt-card-grid" data-objekt-grid data-cols="3">
${cardHtml({member: 'S8 X S12', memberDisplay: '공유빈 × 곽연지', english: 'unit', fill: 'linear-gradient(135deg, #ffe3e2, #5974ff)', title: 'S8 X S12 602Z'})}
${cardHtml({member: 'zenith', english: 'zenith', title: 'zenith 001Z'})}
${members.map(([member, english, fill]) => cardHtml({member, english, fill})).join('\n')}
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
      if (url.pathname === thumbPath || url.pathname === maskPath) {
        return route.fulfill({path: path.join(staticRoot, url.pathname), contentType: 'image/png'});
      }
      return route.fulfill({status: 404, body: ''});
    });
    await page.goto('https://preview.test/preview/card', {waitUntil: 'load'});
    const grid = page.locator('[data-objekt-grid]');
    const unit = page.locator('[data-member="S8 X S12"]');
    const unitMark = unit.locator('.objekt-member-logo');
    const soloMark = page.locator('[data-member="윤서연"] .objekt-member-logo');
    const emptyMark = page.locator('[data-member="zenith"] .objekt-member-logo');
    const maskUrl = await page.evaluate(async path => {
      const image = new Image();
      image.src = path;
      await image.decode();
      if (image.naturalWidth !== 204 || image.naturalHeight !== 320) throw new Error('마스크 파일 규격 불일치');
      return image.src;
    }, maskPath);

    assert.equal(await page.locator('.objekt-member-logo--filled').count(), 25);
    assert.equal(await page.locator('img.objekt-member-logo').count(), 0);
    for (const [member, , fill] of members) {
      const card = page.locator(`[data-member="${member}"]`);
      const style = await card.locator('.objekt-member-logo').evaluate(el => {
        const css = getComputedStyle(el);
        return {mask: css.maskImage, fill: css.backgroundColor, gradient: css.backgroundImage};
      });
      const rgb = fill.slice(1).match(/../g).map(hex => Number.parseInt(hex, 16)).join(', ');
      assert.equal(style.mask, `url("${maskUrl}")`);
      assert.equal(style.fill, `rgb(${rgb})`);
      assert.equal(style.gradient, 'none');
    }
    assert.equal(await unit.locator('.objekt-member-name').textContent(), '공유빈 × 곽연지');
    assert.equal(await unit.locator('a').count(), 0);

    const screenshots = '/tmp/objekt-ui/member-masks';
    fs.mkdirSync(screenshots, {recursive: true});
    for (const colorScheme of ['dark', 'light']) {
      await page.emulateMedia({colorScheme});
      await page.locator('body').evaluate((el, theme) => {
        el.style.backgroundColor = theme === 'light' ? '#f4f6fb' : '#0d0e12';
      }, colorScheme);
      const styles = await unitMark.evaluate(el => {
        const css = getComputedStyle(el);
        return {mask: css.maskImage, size: css.maskSize, repeat: css.maskRepeat,
          position: css.maskPosition, border: css.borderWidth, gradient: css.backgroundImage};
      });
      assert.deepEqual(styles, {
        mask: `url("${maskUrl}")`, size: 'contain', repeat: 'no-repeat', position: '50% 50%', border: '0px',
        gradient: 'linear-gradient(135deg, rgb(255, 227, 226), rgb(89, 116, 255))'
      }, colorScheme);
      assert.equal(await emptyMark.evaluate(el => getComputedStyle(el).maskImage), 'none');
      assert.equal(await emptyMark.evaluate(el => getComputedStyle(el).backgroundImage), 'none');
      for (const [width, columnCounts] of [[1100, [3, 4, 5, 6, 7, 8]], [390, [3, 4]]]) {
        await page.setViewportSize({width, height: 900});
        for (const columns of columnCounts) {
          await grid.evaluate((el, value) => { el.dataset.cols = value; }, columns);
          const expectedVisible = columns < 7 && !(width === 390 && columns === 4);
          assert.equal(await unitMark.isVisible(), expectedVisible);
          assert.equal(await soloMark.isVisible(), expectedVisible);
          if (expectedVisible) {
            const measure = el => {
              const box = el.getBoundingClientRect();
              const chip = el.parentElement.getBoundingClientRect();
              return [box.width, box.height, box.y - chip.y, box.x - chip.x];
            };
            assert.deepEqual(await unitMark.evaluate(measure), await soloMark.evaluate(measure),
              `${colorScheme}, ${width}px, ${columns}열`);
          }
          await page.screenshot({path: path.join(screenshots, `${colorScheme}-${width}px-${columns}cols.png`)});
        }
      }
    }
    await page.setViewportSize({width: 1100, height: 1600});
    await grid.evaluate(el => { el.dataset.cols = '3'; });
    await page.screenshot({path: '/tmp/objekt-ui/objekt-card-chrome.png', fullPage: true});
    const artifacts = '/opt/cursor/artifacts';
    if (fs.existsSync('/opt/cursor')) {
      fs.mkdirSync(artifacts, {recursive: true});
      await page.screenshot({path: path.join(artifacts, 'objekt-card-s1-s24-logos.png'), fullPage: true});
      await page.locator('[data-member="코토네"]').screenshot({path: path.join(artifacts, 'objekt-card-kotone-logo.png')});
      await page.locator('[data-member="설린"]').screenshot({path: path.join(artifacts, 'objekt-card-sullin-logo.png')});
      await page.locator('[data-member="지연"]').screenshot({path: path.join(artifacts, 'objekt-card-jiyeon-logo.png')});
      await page.setViewportSize({width: 390, height: 4200});
      await page.screenshot({path: path.join(artifacts, 'objekt-card-s1-s24-logos-mobile.png'), fullPage: true});
    }
    console.log('PASS: 공통 높은음자리표 마스크, 솔로 단색·유닛 그라데이션, 다크/라이트·열별 크기 및 정렬');
  } finally {
    await browser.close();
  }
})().catch(error => {
  console.error(error);
  process.exit(1);
});
