const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const controller = path.join(root, 'src/main/resources/static/js/modal-navigation.js');
const html = `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
.hidden,[hidden]{display:none!important}
.site-modal{display:none}
.site-modal.is-open{display:block}
.about-modal.flex,.ar-dialog:not([hidden]){display:flex}
.site-modal,.about-modal,.ar-dialog{position:fixed;inset:0;background:#0008}
.site-modal-dialog,.about-modal-panel,.ar-dialog-panel{margin:15vh auto;background:white;padding:24px;height:120px}
</style>
<script defer src="/js/modal-navigation.js"></script>
</head>
<body>
<button id="openSite">사이트 모달</button>
<button id="openAbout">편집 모달</button>
<button id="openArchive">아카이브 모달</button>
<button id="openNative">네이티브 다이얼로그</button>
<div class="site-modal" id="site" aria-hidden="true">
  <div class="site-modal-dialog"><button data-site-dismiss="modal">닫기</button></div>
</div>
<div class="about-modal hidden" id="about">
  <div class="about-modal-panel"><button class="about-close">닫기</button></div>
</div>
<div class="ar-dialog" id="archive" hidden>
  <div class="ar-dialog-backdrop" aria-hidden="true"></div>
  <div class="ar-dialog-panel"><button data-ar-close class="ar-close">닫기</button></div>
</div>
<dialog id="native"><button id="nativeClose">닫기</button></dialog>
<script>
const site = document.getElementById('site');
const about = document.getElementById('about');
const archive = document.getElementById('archive');
const nativeDialog = document.getElementById('native');
document.getElementById('openSite').onclick = () => {
  site.classList.add('is-open','show');
  site.setAttribute('aria-hidden','false');
};
site.querySelector('[data-site-dismiss]').onclick = () => {
  site.classList.remove('is-open','show');
  site.setAttribute('aria-hidden','true');
};
document.getElementById('openAbout').onclick = () => {
  about.classList.remove('hidden');
  about.classList.add('flex');
};
about.querySelector('.about-close').onclick = () => {
  about.classList.add('hidden');
  about.classList.remove('flex');
};
document.getElementById('openArchive').onclick = () => {
  archive.hidden = false;
};
archive.querySelector('.ar-close').onclick = () => { archive.hidden = true; };
document.getElementById('openNative').onclick = () => { nativeDialog.showModal(); };
document.getElementById('nativeClose').onclick = () => nativeDialog.close();
</script>
</body></html>`;

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    for (const fallback of [false, true]) {
      const page = await browser.newPage({ viewport: { width: 390, height: 800 } });
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      if (fallback) await page.addInitScript(() => { window.CloseWatcher = undefined; });
      await page.route('**/*', route => {
        const url = new URL(route.request().url());
        if (url.pathname === '/') return route.fulfill({ contentType: 'text/html', body: html });
        if (url.pathname === '/js/modal-navigation.js') return route.fulfill({ contentType: 'application/javascript', path: controller });
        return route.fulfill({ status: 404 });
      });
      await page.goto('https://modal.test/');

      await page.click('#openSite');
      await page.waitForFunction(() => SiteModalNavigation.openCount === 1);
      await page.locator('#site').evaluate(element => element.click());
      assert.equal(await page.locator('#site').getAttribute('aria-hidden'), 'false', '배경 클릭은 모달을 닫지 않는다');

      await page.click('#openAbout', { force: true });
      await page.waitForFunction(() => SiteModalNavigation.openCount === 2);
      await page.keyboard.press('Escape');
      await page.waitForFunction(() => SiteModalNavigation.openCount === 1);
      assert.equal(await page.locator('#site').getAttribute('aria-hidden'), 'false', '중첩 모달은 최상단만 닫는다');

      await page.keyboard.press('Escape');
      await page.waitForFunction(() => SiteModalNavigation.openCount === 0);
      assert.equal(await page.locator('#site').getAttribute('aria-hidden'), 'true');

      await page.click('#openArchive');
      await page.waitForFunction(() => SiteModalNavigation.openCount === 1);
      await page.locator('.ar-dialog-backdrop').click({ force: true });
      assert.equal(await page.locator('#archive').evaluate(element => element.hidden), false, '아카이브 배경 클릭은 무시한다');
      await page.locator('#archive .ar-close').click();
      await page.waitForFunction(() => SiteModalNavigation.openCount === 0);

      await page.click('#openSite');
      await page.waitForFunction(() => SiteModalNavigation.openCount === 1);
      if (fallback) {
        await page.evaluate(() => history.back()); // Android Back fallback
        await page.waitForFunction(() => SiteModalNavigation.openCount === 0);
      } else {
        await page.keyboard.press('Escape'); // Chrome close request
        await page.waitForFunction(() => SiteModalNavigation.openCount === 0);
      }

      await page.click('#openNative');
      assert.equal(await page.locator('#native').evaluate(el => el.open), true);
      await page.keyboard.press('Escape');
      await page.waitForFunction(() => !document.getElementById('native').open);
      assert.deepEqual(errors, [], '페이지 스크립트 오류 없음');
      await page.close();
    }
    console.log('모달 배경 클릭 차단, X/Escape/뒤로가기 회귀 테스트 통과');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
