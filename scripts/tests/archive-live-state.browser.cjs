const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const script = fs.readFileSync(path.join(__dirname, '../../src/main/resources/static/js/archive-browse.js'), 'utf8');
const dateScript = fs.readFileSync(path.join(__dirname, '../../src/main/resources/static/js/date-range-filter.js'), 'utf8');
const html = (query = '') => `<!doctype html><meta charset="utf-8"><body style="min-height:5000px">
<main class="archive-page"><form id="archiveFilterForm" action="/archive">
<input class="ar-search" name="q" value="${query}">
<div data-date-filter><button type="button" data-date-toggle>날짜</button><div data-date-popover hidden>
<input type="date" name="from" data-date-from><button type="submit">적용</button></div></div></form>
<div class="ar-folder-tabs"><nav class="ar-type-tabs"><a href="/archive?type=video&q=${query}">영상</a></nav>
<nav class="ar-source-tabs"><a href="/archive?source=official&q=${query}">공식</a></nav></div>
<section id="archiveGrid"><article class="ar-card" data-id="1">${query || '전체'}</article></section>
<nav class="ar-pager"></nav></main><script>${dateScript}</script><script>${script}</script>`;
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium', args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage();
    let pending = [];
    let requests = 0;
    await page.route('**/archive**', route => {
      if (!route.request().headers()['x-archive-filter']) return route.fulfill({ contentType: 'text/html', body: html() });
      requests++;
      pending.push(route);
    });
    const submit = async query => {
      await page.evaluate(value => {
        document.querySelector('.ar-search').value = value;
        document.querySelector('form').requestSubmit();
      }, query);
    };
    const take = async () => {
      for (let i = 0; i < 100 && !pending.length; i++) await page.waitForTimeout(20);
      assert.ok(pending.length, '필터 요청이 없다');
      return pending.shift();
    };
    const respond = (route, query) => route.fulfill({ contentType: 'text/html', body: html(query) });
    await page.goto('https://archive-live.test/archive');
    await submit('new');
    await respond(await take(), 'new');
    await page.waitForURL('**q=new');
    for (const selector of ['.ar-type-tabs a', '.ar-source-tabs a']) {
      assert.equal(new URL(await page.locator(selector).getAttribute('href'), page.url()).searchParams.get('q'), 'new', '탭 이동 시 최신 검색이 사라진다');
    }
    await page.evaluate(() => window.scrollTo(0, 500));
    await submit('scroll');
    const scrollRequest = await take();
    await page.evaluate(() => window.scrollTo(0, 1000));
    await respond(scrollRequest, 'scroll');
    await page.waitForURL('**q=scroll');
    await page.waitForTimeout(50);
    assert.equal(await page.evaluate(() => window.scrollY), 1000, '응답 대기 중 이동한 스크롤이 되돌아간다');
    await submit('old');
    const oldRequest = await take();
    await submit('latest');
    await respond(await take(), 'latest');
    await page.waitForURL('**q=latest');
    await oldRequest.abort('failed');
    await page.waitForTimeout(200);
    assert.equal(new URL(page.url()).searchParams.get('q'), 'latest', '이전 실패가 최신 화면을 덮어쓴다');
    await submit('stale');
    const staleRequest = await take();
    await page.locator('.ar-search').fill('typed');
    await respond(staleRequest, 'stale');
    await page.waitForTimeout(50);
    assert.equal(await page.locator('#archiveGrid').innerText(), 'latest', '입력 중인 새 검색보다 이전 응답이 먼저 표시된다');
    await respond(await take(), 'typed');
    await page.waitForURL('**q=typed');
    await page.locator('[data-date-toggle]').click();
    await page.evaluate(() => {
      document.querySelector('[data-date-from]').value = '2026-11-17';
      document.querySelector('.ar-search').dispatchEvent(new Event('input', { bubbles: true }));
    });
    const before = requests;
    await page.locator('[data-date-popover] button[type="submit"]').click();
    await respond(await take(), 'typed');
    await page.waitForTimeout(400);
    assert.equal(await page.locator('[data-date-popover]').isHidden(), true, '적용 후 날짜 팝업이 닫히지 않는다');
    assert.equal(requests, before + 1, '날짜 적용이 중복 요청된다');
    console.log('PASS: 아카이브 탭·스크롤·이전 요청 실패·날짜 적용');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
