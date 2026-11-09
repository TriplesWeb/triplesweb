const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const css = fs.readFileSync(path.join(staticRoot, 'css', 'scroll-top.css'), 'utf8');
const js = fs.readFileSync(path.join(staticRoot, 'js', 'scroll-top.js'), 'utf8');

function pageHtml(title) {
  return `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title}</title>
<style>
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:sans-serif}
main{min-height:2400px;padding:1rem}
${css}
</style></head>
<body>
<main><h1>${title}</h1><p>긴 목록</p></main>
<button type="button" class="scroll-top" data-scroll-top hidden aria-label="맨 위로">
  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5.5 5.8 11.7l1.4 1.4L11 9.3V19h2V9.3l3.8 3.8 1.4-1.4Z"/></svg>
</button>
<script>${js}</script>
</body></html>`;
}

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage();
    await page.setViewportSize({ width: 390, height: 844 });
    await page.route('**/*', (route) => {
      const url = new URL(route.request().url());
      const titles = {
        '/cosmo/objekt': '오브젝트',
        '/cosmo/collection': '코스모 아이디 검색결과',
        '/cosmo/profile': '내 진행도'
      };
      const title = titles[url.pathname];
      if (title) return route.fulfill({ contentType: 'text/html', body: pageHtml(title) });
      return route.fulfill({ status: 404, body: '' });
    });

    for (const pathname of ['/cosmo/objekt', '/cosmo/collection', '/cosmo/profile']) {
      await page.goto('https://scroll-top.test' + pathname, { waitUntil: 'load' });
      const button = page.locator('[data-scroll-top]');
      assert.equal(await button.count(), 1, pathname + ' 맨 위 버튼 없음');
      assert.equal(await button.isVisible(), false, pathname + ' 맨 위에서는 버튼이 숨겨져야 한다');
      await page.evaluate(() => window.scrollTo(0, 900));
      await page.waitForFunction(() => {
        const node = document.querySelector('[data-scroll-top]');
        return node && !node.hidden;
      });
      const box = await button.boundingBox();
      assert.ok(box, pathname + ' 스크롤 후 버튼 위치 없음');
      assert.ok(box.x + box.width > 390 - 80, pathname + ' 오른쪽이 아님: ' + JSON.stringify(box));
      assert.ok(box.y + box.height > 844 - 90, pathname + ' 하단이 아님: ' + JSON.stringify(box));
      await button.click();
      await page.waitForFunction(() => (window.scrollY || document.documentElement.scrollTop) < 8);
      assert.equal(await button.isVisible(), false, pathname + ' 맨 위로 올린 뒤 버튼이 남아 있다');
    }
    console.log('PASS: 오브젝트·컬렉션·진행도에서 맨 위 버튼이 오른쪽 아래에 나타난다');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
