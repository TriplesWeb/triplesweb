const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const staticRoot = path.resolve(__dirname, '../../src/main/resources/static');
const script = fs.readFileSync(path.join(staticRoot, 'js/site-feedback.js'), 'utf8');
const css = fs.readFileSync(path.join(staticRoot, 'css/site-ui.css'), 'utf8');

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 750 } });
    page.on('dialog', unexpected => {
      throw new Error('Unexpected browser dialog: ' + unexpected.message());
    });
    await page.route('https://feedback.test/**', route => route.fulfill({ body: '<p>Saved</p>', contentType: 'text/html' }));
    await page.route('https://feedback.test/', route => route.fulfill({
      contentType: 'text/html',
      body: `<!doctype html><html lang="ko"><meta charset="utf-8"><style>${css}</style>
        <button id="discard" data-confirm-action="편집을 취소하시겠습니까?">편집 취소</button>
        <form action="/save" method="post" data-confirm="저장하시겠습니까?">
          <input name="title" required><button name="action" value="save">저장</button>
        </form>
        <script>
          let discarded = 0, escaped = 0;
          document.querySelector('#discard').addEventListener('click', () => discarded++);
          document.addEventListener('keydown', event => { if (event.key === 'Escape') escaped++; });
          window.fetch = url => {
            if (url === '/failure') return Promise.reject(new Error('offline'));
            return new Promise(resolve => {
              window[url] = resolve;
            });
          };
          ${script}
        </script>`
    }));
    await page.goto('https://feedback.test/');
    const dialog = page.getByRole('alertdialog');
    const progress = page.locator('.site-feedback-progress');
    await progress.waitFor({ state: 'hidden' });

    await page.locator('#discard').click();
    await dialog.waitFor();
    assert.equal(await dialog.getByRole('button', { name: '취소', exact: true }).evaluate(el => el === document.activeElement), true);
    await page.keyboard.press('Escape');
    await dialog.waitFor({ state: 'hidden' });
    assert.deepEqual(await page.evaluate(() => [discarded, escaped]), [0, 0]);
    await page.locator('#discard').click();
    await dialog.getByRole('button', { name: '확인', exact: true }).click();
    await page.waitForFunction(() => discarded === 1);

    await page.evaluate(() => {
      window.first = fetch('/first').then(response => response.text());
      window.second = fetch('/second');
    });
    await progress.waitFor();
    await page.evaluate(() => {
      window['/first'](new Response(new ReadableStream({
        start(controller) { window.finishBody = () => controller.close(); }
      })));
      window['/second'](new Response(null));
      return window.second;
    });
    assert.equal(await progress.isVisible(), true);
    await page.evaluate(() => { window.finishBody(); return window.first; });
    await progress.waitFor({ state: 'hidden' });
    await page.evaluate(() => fetch('/failure').catch(() => {}));
    assert.equal(await progress.isVisible(), false);

    const save = page.getByRole('button', { name: '저장', exact: true });
    await save.click();
    assert.equal(await dialog.isVisible(), false);
    await page.locator('[name=title]').fill('새 글');
    await save.click();
    await dialog.getByRole('button', { name: '취소', exact: true }).click();
    assert.equal(new URL(page.url()).pathname, '/');
    await save.click();
    const request = page.waitForRequest('https://feedback.test/save');
    await dialog.getByRole('button', { name: '확인', exact: true }).click();
    assert.deepEqual(Object.fromEntries(new URLSearchParams((await request).postData())), { title: '새 글', action: 'save' });
  } finally {
    await browser.close();
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
