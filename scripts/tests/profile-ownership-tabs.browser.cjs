const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');

function readCss(name) {
  return fs.readFileSync(path.join(staticRoot, 'css', name), 'utf8');
}

const html = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
${readCss('site-mobile.css')}
${readCss('cosmo-profile.css')}
</style></head>
<body class="site-page">
<main class="cosmo-profile" style="padding:.875rem">
  <nav class="profile-ownership-tabs" aria-label="보유 여부 모아보기">
    <a class="profile-ownership-tab is-active" href="#">진행도</a>
    <a class="profile-ownership-tab" href="#">전체</a>
    <a class="profile-ownership-tab" href="#">보유 12/24장</a>
    <a class="profile-ownership-tab" href="#">미보유 12/24장</a>
  </nav>
</main>
</body></html>`;

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage();
    await page.setViewportSize({ width: 390, height: 844 });
    await page.setContent(html);
    const metrics = await page.evaluate(() => {
      const tabs = [...document.querySelectorAll('.profile-ownership-tab')];
      return tabs.map((tab) => {
        const style = getComputedStyle(tab);
        return {
          text: tab.textContent,
          whiteSpace: style.whiteSpace,
          fontSize: parseFloat(style.fontSize),
          lines: tab.getClientRects().length,
          height: tab.getBoundingClientRect().height
        };
      });
    });
    for (const tab of metrics) {
      assert.equal(tab.whiteSpace, 'nowrap', tab.text + ' should stay on one line');
      assert.equal(tab.lines, 1, tab.text + ' wrapped inside the chip');
      assert.ok(tab.fontSize <= 13, tab.text + ' font too large: ' + tab.fontSize);
    }
    console.log('PASS: 프로필 보유·미보유 탭이 모바일에서 글자 중간에서 개행되지 않음');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
