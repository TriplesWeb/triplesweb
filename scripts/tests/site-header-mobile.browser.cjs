const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require('playwright');

async function testMobileMenu() {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH });
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 700 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.setContent(`
      <style>.hidden { display: none; }</style>
      <header id="top-header">
        <button id="mobile-menu-btn" aria-expanded="false" aria-controls="mobile-menu">
          <span id="mobile-menu-icon">menu</span>
        </button>
        <nav id="mobile-menu" class="hidden"><a href="/main">홈</a></nav>
      </header>
    `);
    await page.addScriptTag({
      path: path.resolve(__dirname, '../../src/main/resources/static/js/site-header.js')
    });
    const button = page.locator('#mobile-menu-btn');
    const menu = page.locator('#mobile-menu');
    assert.equal(await menu.isVisible(), false);
    await button.click();
    assert.equal(await menu.isVisible(), true);
    assert.equal(await button.getAttribute('aria-expanded'), 'true');

    await menu.getByRole('link', { name: '홈' }).focus();
    await page.keyboard.press('Escape');
    assert.equal(await menu.isVisible(), false);
    assert.equal(await button.getAttribute('aria-expanded'), 'false');
    assert.equal(await button.evaluate(node => node === document.activeElement), true);
    assert.deepEqual(errors, []);
    console.log('PASS: mobile header opens and Escape closes it and restores focus');
  } finally {
    await browser.close();
  }
}

testMobileMenu().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
