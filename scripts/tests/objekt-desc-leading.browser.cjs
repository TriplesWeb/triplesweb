const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '../../src/main/resources/static/css');
const html = `<!doctype html><html><head><meta charset="utf-8">
<style>
${fs.readFileSync(path.join(root, 'objekt-page.css'), 'utf8')}
${fs.readFileSync(path.join(root, 'objekt-modal.css'), 'utf8')}
</style></head>
<body>
<p class="objekt-detail-desc" id="objektDetailDesc">tripleS 공식 캐릭터 피규어 byteS 구매 특전 오브젝트
tripleS Official Character Figure byteS Purchase Bonus Object</p>
<form class="objekt-description-editor">
<textarea id="objektModalDescription">tripleS 공식 캐릭터 피규어 byteS 구매 특전 오브젝트
tripleS Official Character Figure byteS Purchase Bonus Object</textarea>
</form>
</body></html>`;

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage({ viewport: { width: 480, height: 800 } });
    await page.setContent(html);
    const metrics = await page.evaluate(() => {
      const ratio = (el) => {
        const style = getComputedStyle(el);
        return {
          lineHeight: parseFloat(style.lineHeight),
          fontSize: parseFloat(style.fontSize),
          ratio: parseFloat(style.lineHeight) / parseFloat(style.fontSize)
        };
      };
      return {
        desc: ratio(document.querySelector('#objektDetailDesc')),
        editor: ratio(document.querySelector('#objektModalDescription'))
      };
    });
    assert.ok(Math.abs(metrics.desc.ratio - 1.05) < 0.08, '상세 설명 행 간격: ' + metrics.desc.ratio);
    assert.ok(Math.abs(metrics.editor.ratio - 1.05) < 0.08, '수정폼 행 간격: ' + metrics.editor.ratio);
    console.log('PASS', metrics);
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
