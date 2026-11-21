const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const script = fs.readFileSync(path.join(__dirname, '../../src/main/resources/static/js/archive-browse.js'), 'utf8');

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium', args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage();
    await page.route('**/archive**', route => {
      const url = new URL(route.request().url());
      const query = url.searchParams.get('q') || '';
      const offset = url.searchParams.has('page') ? 20 : 0;
      return route.fulfill({ contentType: 'text/html; charset=utf-8', body: `<!doctype html><html><body>
        <main class="archive-page"><form id="archiveFilterForm" action="/archive"><input class="ar-search" name="q" value="${query}"></form>
        <section id="archiveGrid" data-number-offset="${offset}"><article class="ar-card" data-id="1"><span data-archive-number></span><span>${query || '전체'}</span></article></section>
        <nav class="ar-pager"></nav></main><script>${script}</script></body></html>` });
    });
    await page.goto('https://archive-history.test/archive?page=3');
    assert.equal(await page.locator('[data-archive-number]').textContent(), '21');
    await page.locator('.ar-search').fill('첫검색');
    await page.waitForURL('**/archive?q=*');
    assert.equal(await page.locator('[data-archive-number]').textContent(), '1');
    await page.locator('.ar-search').fill('두번째');
    await page.waitForURL('**/archive?q=%EB%91%90%EB%B2%88%EC%A7%B8');
    await page.goBack();
    await page.waitForLoadState('load');
    await page.waitForFunction(() => document.querySelector('.ar-search').value === '첫검색');
    assert.equal(await page.locator('#archiveGrid').innerText(), '1첫검색');
    console.log('archive history and numbering passed');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
