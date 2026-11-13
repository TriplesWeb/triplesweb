const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const chemistryPage = fs.readFileSync(path.join(root, 'build/mobile-pages/triples_chemistry.mustache.html'), 'utf8');

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    await page.route('**/*', async (route) => {
      const url = new URL(route.request().url());
      if (url.origin !== 'https://chemistry.test') return route.abort();
      if (url.pathname === '/triples/chemistry') return route.fulfill({ contentType: 'text/html', body: chemistryPage });
      if (url.pathname === '/api/chemistry') return route.fulfill({ contentType: 'application/json', body: JSON.stringify([
        { id: 42, memberA: '윤서연', memberB: '김유연', label: '뽀토즈', description: '케미 설명', imageUrls: [], links: {} },
        { id: 43, memberA: '윤서연', memberB: '정혜린', label: '다른 케미', description: '', imageUrls: [], links: {} }
      ]) });
      if (url.pathname.includes('/admin/status')) return route.fulfill({ contentType: 'application/json', body: '{"configured":false,"admin":false}' });
      const file = path.resolve(staticRoot, '.' + url.pathname);
      if (file.startsWith(staticRoot + path.sep) && fs.existsSync(file) && fs.statSync(file).isFile()) {
        return route.fulfill({ path: file, contentType: file.endsWith('.js') ? 'application/javascript' : file.endsWith('.css') ? 'text/css' : undefined });
      }
      return route.fulfill({ status: 404, body: '' });
    });
    await page.goto('https://chemistry.test/triples/chemistry?pair=42');
    await page.locator('#chemistryModal').waitFor({ state: 'visible' });
    assert.equal((await page.locator('#chemistryTitle').innerText()).trim(), '뽀토즈');
    assert.match(await page.locator('#chemistryPair').innerText(), /윤서연.*김유연/);
    assert.match(await page.locator('#chemistryDescription').innerText(), /케미 설명/);
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('#chemistryModal').isVisible(), false);
    const loaded = page.waitForResponse((response) => response.url().endsWith('/api/chemistry'));
    await page.goto('https://chemistry.test/triples/chemistry?pair=999');
    await loaded;
    assert.equal(await page.locator('#chemistryModal').isVisible(), false);
    console.log('PASS: 검색 케미 링크로 정확한 상세 모달 열기 및 없는 ID 처리');
  } finally {
    await browser.close();
  }
})().catch((error) => { console.error(error); process.exit(1); });
