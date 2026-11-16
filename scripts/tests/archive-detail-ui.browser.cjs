const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const css = fs.readFileSync(path.join(staticRoot, 'css', 'archive-page.css'), 'utf8');
const site = fs.readFileSync(path.join(staticRoot, 'css', 'site.css'), 'utf8');
const siteUi = fs.readFileSync(path.join(staticRoot, 'css', 'site-ui.css'), 'utf8');
const dateJs = fs.readFileSync(path.join(staticRoot, 'js', 'date-range-filter.js'), 'utf8');
const detailJs = fs.readFileSync(path.join(staticRoot, 'js', 'archive-detail.js'), 'utf8');

const html = `<!doctype html><html lang="ko"><head><meta charset="utf-8">
<style>
*,*::before,*::after{box-sizing:border-box}
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
${site}${css}${siteUi}
</style></head>
<body class="site-page">
<main class="archive-page is-folder-view" data-archive-kind="image" data-date-label="사진 날짜">
  <section class="ar-hero">
    <div class="ar-panel ar-headline">
      <div class="ar-stats" id="archiveStats">
        <article class="ar-stat ar-stat-filter" style="--filter-count: 3">
          <div class="ar-stat-label"><span>검색</span><span class="material-symbols-outlined">search</span></div>
          <div class="ar-filter-summary">
            <strong>사진</strong><span class="ar-filter-sep">,</span>
            <strong>코스모룸</strong><span class="ar-filter-sep">,</span>
            <strong>2026/11/16</strong>
          </div>
        </article>
      </div>
    </div>
  </section>
  <section class="ar-grid" id="archiveGrid">
    <article class="ar-panel ar-card" data-id="7" data-title="지연" data-artists="지연" data-registered="2026-11-16" data-url="/portrait.svg" data-source="COSMO_ROOM" data-source-label="코스모룸">
      <button type="button" class="ar-photo-link" data-archive-open><img src="/portrait.svg" alt=""></button>
      <div class="ar-body">
        <h3>지연</h3>
        <div class="ar-meta" style="--member-count: 1" title="지연">
          <span class="archive-member-chip archive-symbol-chip" style="--member-color:#7bba8d"><img src="/portrait.svg" alt=""><span class="archive-chip-name">지연</span></span>
          <time class="ar-card-date" datetime="2026-11-16">2026/11/16</time>
        </div>
        <div class="ar-card-members" hidden>
          <a class="unit-member-chip" style="--member-color:#7bba8d"><img src="/portrait.svg" alt=""><span>지연</span></a>
        </div>
      </div>
    </article>
  </section>
</main>
<div class="ar-dialog" id="archiveDetail" hidden>
  <button type="button" class="ar-dialog-backdrop" data-ar-close aria-label="닫기"></button>
  <div class="ar-dialog-panel ar-detail-panel" role="dialog">
    <div class="ar-detail-photo"><img id="archiveDetailImage" alt=""></div>
    <div class="ar-detail-side">
      <div class="ar-dialog-head"><h2 id="archiveDetailTitle">사진</h2><button type="button" class="ar-close" data-ar-close>&times;</button></div>
      <div class="ar-detail-body">
        <p class="ar-detail-date" id="archiveDetailDate" hidden></p>
        <div class="ar-source-badge" id="archiveDetailSource" hidden></div>
        <section class="ar-detail-cast"><div class="ar-detail-members" id="archiveDetailMembers"></div></section>
      </div>
    </div>
  </div>
</div>
<div class="ar-dialog" id="editModal" hidden>
  <div class="ar-dialog-panel" style="width:min(100%,46rem)">
    <form id="archiveEditForm">
      <div class="ar-dialog-head"><h2>사진 수정</h2></div>
      <div class="ar-dialog-body">
        <label class="ar-field ar-date-field">사진 날짜
          <input type="date" name="registeredOn" required aria-label="사진 날짜" lang="ko" value="2026-11-16">
        </label>
        <label class="ar-field ar-source-field">출처
          <select name="source" aria-label="출처">
            <option value="">출처를 선택하세요</option>
            <option value="COSMO_ROOM" selected>코스모룸</option>
            <option value="COSMO_TALK">코스모톡/프롬</option>
          </select>
        </label>
      </div>
    </form>
  </div>
</div>
<script>${dateJs}</script>
<script>${detailJs}</script>
</body></html>`;

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage({ viewport: { width: 1100, height: 900 } });
    await page.route('**/*', (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === '/' || url.pathname.endsWith('.html')) {
        return route.fulfill({ contentType: 'text/html', body: html });
      }
      if (url.pathname.endsWith('.svg')) {
        return route.fulfill({
          contentType: 'image/svg+xml',
          body: '<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><circle cx="40" cy="40" r="40" fill="#7bba8d"/></svg>'
        });
      }
      const file = path.resolve(staticRoot, '.' + url.pathname);
      if (file.startsWith(staticRoot + path.sep) && fs.existsSync(file)) {
        return route.fulfill({ path: file });
      }
      return route.fulfill({ status: 404, body: '' });
    });
    await page.goto('https://archive-detail.test/', { waitUntil: 'load' });

    const layout = await page.evaluate(() => {
      const meta = document.querySelector('.ar-meta');
      const date = document.querySelector('.ar-card-date');
      const chip = document.querySelector('.ar-meta .archive-member-chip');
      const summary = document.querySelector('.ar-filter-summary').textContent.replace(/\s+/g, ' ').trim();
      const metaBox = meta.getBoundingClientRect();
      const dateBox = date.getBoundingClientRect();
      const chipBox = chip.getBoundingClientRect();
      const native = document.querySelector('input[name="registeredOn"]');
      return {
        summary,
        dateText: date.textContent.trim(),
        dateRight: metaBox.right - dateBox.right < 8,
        dateAfterChip: dateBox.left >= chipBox.right - 2,
        dateSize: parseFloat(getComputedStyle(date).fontSize),
        dateWeight: getComputedStyle(date).fontWeight,
        nativeType: native?.type || '',
        calReady: native?.dataset.calReady === 'true'
      };
    });
    assert.equal(layout.summary, '사진, 코스모룸, 2026/11/16');
    assert.equal(layout.dateText, '2026/11/16');
    assert.equal(layout.dateAfterChip, true, '날짜가 멤버 오른쪽에 없다');
    assert.ok(layout.dateSize <= 13, '카드 날짜가 작지 않다: ' + layout.dateSize);
    assert.ok(Number(layout.dateWeight) >= 700, '카드 날짜가 흐리다: ' + layout.dateWeight);
    assert.equal(layout.nativeType, 'date');
    assert.equal(layout.calReady, true);

    await page.click('[data-archive-open]');
    const detail = await page.evaluate(() => {
      const badge = document.getElementById('archiveDetailSource');
      const members = document.getElementById('archiveDetailMembers');
      const badgeBox = badge.getBoundingClientRect();
      const membersBox = members.getBoundingClientRect();
      return {
        hidden: badge.hidden,
        text: badge.textContent.trim(),
        source: badge.dataset.source,
        aboveMembers: badgeBox.bottom <= membersBox.top + 1,
        radius: getComputedStyle(badge).borderRadius
      };
    });
    assert.equal(detail.hidden, false);
    assert.equal(detail.text, '코스모룸');
    assert.equal(detail.source, 'cosmo_room');
    assert.equal(detail.aboveMembers, true, '출처 배지가 멤버 위에 없다');
    assert.ok(detail.radius.includes('999') || parseFloat(detail.radius) >= 12, '출처가 버튼처럼 보이지 않는다: ' + detail.radius);

    const artifactDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(artifactDir, { recursive: true });
    const detailBox = await page.locator('#archiveDetail .ar-detail-side').boundingBox();
    await page.screenshot({
      path: path.join(artifactDir, 'archive-detail-source-badge.png'),
      clip: { x: Math.max(0, detailBox.x - 8), y: Math.max(0, detailBox.y - 8), width: detailBox.width + 16, height: Math.min(360, detailBox.height + 16) }
    });
    await page.click('#archiveDetail .ar-close');
    await page.evaluate(() => { document.getElementById('editModal').hidden = false; });
    const form = await page.evaluate(() => {
      const select = document.querySelector('.ar-source-field select');
      const panel = document.querySelector('#editModal .ar-dialog-body');
      const cal = document.querySelector('.ar-cal-text');
      const selectBox = select.getBoundingClientRect();
      const panelBox = panel.getBoundingClientRect();
      return {
        calText: cal?.textContent.trim() || '',
        selectGap: panelBox.right - selectBox.right,
        selectNarrower: selectBox.width < panelBox.width - 24
      };
    });
    assert.equal(form.calText, '2026/11/16');
    assert.ok(form.selectNarrower, '출처 셀렉트가 여전히 가득 차 있다');
    assert.ok(form.selectGap >= 24, '출처 셀렉트 오른쪽 여백이 없다: ' + form.selectGap);
    await page.locator('#editModal .ar-dialog-panel').scrollIntoViewIfNeeded();
    const editBox = await page.locator('#editModal .ar-dialog-panel').boundingBox();
    await page.screenshot({
      path: path.join(artifactDir, 'archive-edit-date-source.png'),
      clip: { x: Math.max(0, editBox.x - 12), y: Math.max(0, editBox.y - 12), width: Math.min(1100, editBox.width + 24), height: editBox.height + 24 }
    });
    await page.evaluate(() => { document.getElementById('editModal').hidden = true; });
    await page.evaluate(() => {
      const photo = document.querySelector('.ar-photo-link');
      if (photo) photo.style.display = 'none';
    });
    await page.locator('.ar-card').screenshot({ path: path.join(artifactDir, 'archive-card-date.png') });
    await page.evaluate(() => {
      const card = document.querySelector('.ar-card');
      if (card) card.style.display = 'none';
    });
    await page.locator('.ar-stat-filter').screenshot({ path: path.join(artifactDir, 'archive-search-stat.png') });
    console.log('PASS: 아카이브 출처 배지·달력 표시·카드 날짜');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
