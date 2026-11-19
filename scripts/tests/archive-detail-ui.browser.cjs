const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const css = fs.readFileSync(path.join(staticRoot, 'css', 'archive-page.css'), 'utf8');
const site = fs.readFileSync(path.join(staticRoot, 'css', 'site.css'), 'utf8');
const siteUi = fs.readFileSync(path.join(staticRoot, 'css', 'site-ui.css'), 'utf8');
const pickerJs = fs.readFileSync(path.join(staticRoot, 'js', 'member-portrait-picker.js'), 'utf8');
const browseJs = fs.readFileSync(path.join(staticRoot, 'js', 'archive-browse.js'), 'utf8');
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
  <section class="ar-panel ar-toolbar">
    <form class="ar-filters" id="archiveFilterForm" onsubmit="event.preventDefault()">
      <div class="ar-filter-tools">
        <button type="button" class="ar-member-toggle" id="archiveMemberToggle" aria-expanded="false" aria-controls="archiveMemberPanel">
          <span class="material-symbols-outlined" aria-hidden="true">group</span>
          <span data-member-toggle-label>멤버 선택</span>
          <span class="ar-member-count" id="archiveMemberCount" hidden>0</span>
          <span class="material-symbols-outlined ar-member-chevron" aria-hidden="true">expand_more</span>
        </button>
      </div>
    </form>
    <div class="ar-member-panel" id="archiveMemberPanel" hidden>
      <div class="member-portrait-picker" id="archive-member-picker">
        <button type="button" class="mpp-item is-active" data-value="주빈">주빈</button>
        <button type="button" class="mpp-item is-active" data-value="마유">마유</button>
        <button type="button" class="mpp-item" data-value="지연">지연</button>
      </div>
    </div>
  </section>
  <section class="ar-hero">
    <div class="ar-panel ar-headline">
      <div class="ar-stats" id="archiveStats">
        <article class="ar-stat ar-stat-filter" style="--filter-count: 2">
          <div class="ar-stat-label"><span>검색</span><span class="material-symbols-outlined">search</span></div>
          <div class="ar-filter-summary">
            <strong>사진</strong><span class="ar-filter-sep">,</span>
            <strong>코스모룸</strong>
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
    <article class="ar-panel ar-card" id="pair-card" data-id="2" data-title="마유 주빈" data-artists="마유,주빈" data-registered="2026-11-05" data-url="/portrait.svg" data-source="COSMO_ROOM" data-source-label="코스모룸">
      <button type="button" class="ar-photo-link" data-archive-open><img src="/portrait.svg" alt=""></button>
      <div class="ar-body"><h3>마유 주빈</h3>
        <div class="ar-card-members" hidden>
          <a class="unit-member-chip" style="--member-color:#f0a"><img src="/portrait.svg" alt=""><span>마유</span></a>
          <a class="unit-member-chip" style="--member-color:#7bba8d"><img src="/portrait.svg" alt=""><span>주빈</span></a>
        </div>
      </div>
    </article>
  </section>
</main>
<script type="application/json" id="archiveChemistryNames">[{"members":["마유","주빈"],"label":"심장마빈즈"},{"members":["마유","주빈","지연"],"label":"셋"},{"members":["윤서연","정혜린"],"label":""}]</script>
<div class="ar-dialog" id="archiveDetail" hidden>
  <button type="button" class="ar-dialog-backdrop" data-ar-close aria-label="닫기"></button>
  <div class="ar-dialog-panel ar-detail-panel" role="dialog">
    <div class="ar-detail-photo" id="archiveDetailPhoto">
      <button type="button" class="ar-detail-nav ar-detail-prev" id="archiveDetailPrev" aria-label="이전 사진" hidden><span aria-hidden="true">‹</span></button>
      <img id="archiveDetailImage" alt="">
      <button type="button" class="ar-detail-nav ar-detail-next" id="archiveDetailNext" aria-label="다음 사진" hidden><span aria-hidden="true">›</span></button>
      <div class="ar-detail-dots" id="archiveDetailDots" hidden></div>
      <p class="ar-detail-count" id="archiveDetailCount" hidden></p>
    </div>
    <div class="ar-detail-side">
      <div class="ar-dialog-head"><h2 id="archiveDetailTitle">사진</h2><button type="button" class="ar-close" data-ar-close>&times;</button></div>
      <div class="ar-detail-body">
        <p class="ar-detail-date" id="archiveDetailDate" hidden></p>
        <div class="ar-detail-line"><div class="ar-source-badge" id="archiveDetailSource" hidden></div></div>
        <section class="ar-detail-cast"><div class="ar-detail-members" id="archiveDetailMembers"></div></section>
      </div>
    </div>
  </div>
</div>
<div class="ar-dialog" id="uploadModal" hidden>
  <div class="ar-dialog-panel">
    <form id="archiveUploadForm">
      <div class="ar-dialog-body">
        <label class="ar-field ar-date-field">사진 날짜
          <input type="date" name="registeredOn" required aria-label="사진 날짜" lang="ko" value="2026-11-05">
        </label>
      </div>
    </form>
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
<script>${pickerJs}</script>
<script>${browseJs}</script>
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
    const artifactDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(artifactDir, { recursive: true });

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
    assert.equal(layout.summary, '사진, 코스모룸');
    assert.equal(layout.dateText, '2026/11/16');
    assert.equal(layout.dateAfterChip, true, '날짜가 멤버 오른쪽에 없다');
    assert.ok(layout.dateSize <= 13, '카드 날짜가 작지 않다: ' + layout.dateSize);
    assert.ok(Number(layout.dateWeight) >= 700, '카드 날짜가 흐리다: ' + layout.dateWeight);
    assert.equal(layout.nativeType, 'date');
    assert.equal(layout.calReady, true);

    const toggle = await page.evaluate(() => {
      const label = document.querySelector('[data-member-toggle-label]');
      const count = document.getElementById('archiveMemberCount');
      return {
        text: label?.textContent.trim() || '',
        count: count?.textContent.trim() || '',
        countHidden: count?.hidden === true,
        selected: window.MemberPortraitPicker.selectedValues(document.getElementById('archiveMemberPanel')).join(',')
      };
    });
    assert.equal(toggle.selected, '주빈,마유');
    assert.equal(toggle.text, '심장마빈즈', '케미명이 있는 멤버 조합이 인원 수로 보인다: ' + toggle.text);
    assert.equal(toggle.count, '2');
    assert.equal(toggle.countHidden, false);
    const pick = (name) => page.evaluate((value) => {
      document.querySelector(`#archive-member-picker .mpp-item[data-value="${value}"]`).click();
    }, name);
    await pick('지연');
    assert.equal(await page.locator('[data-member-toggle-label]').innerText(), '셋');
    await pick('주빈');
    assert.equal(await page.locator('[data-member-toggle-label]').innerText(), '2명', '케미명이 없는 조합이 인원 수를 유지하지 않는다');
    await pick('지연');
    assert.equal(await page.locator('[data-member-toggle-label]').innerText(), '마유', '멤버 한 명일 때 이름이 보이지 않는다');
    await pick('주빈');
    assert.equal(await page.locator('[data-member-toggle-label]').innerText(), '심장마빈즈');
    const toggleBox = await page.locator('#archiveMemberToggle').boundingBox();
    await page.screenshot({
      path: path.join(artifactDir, 'archive-member-toggle-chemistry.png'),
      clip: {
        x: Math.max(0, toggleBox.x - 12),
        y: Math.max(0, toggleBox.y - 12),
        width: toggleBox.width + 24,
        height: toggleBox.height + 24
      }
    });

    await page.click('[data-archive-open]');
    const detail = await page.evaluate(() => {
      const badge = document.getElementById('archiveDetailSource');
      const members = document.getElementById('archiveDetailMembers');
      const date = document.getElementById('archiveDetailDate');
      const cast = document.querySelector('#archiveDetail .ar-detail-cast');
      const badgeBox = badge.getBoundingClientRect();
      const membersBox = members.getBoundingClientRect();
      const dateStyle = getComputedStyle(date);
      return {
        hidden: badge.hidden,
        text: badge.textContent.trim(),
        source: badge.dataset.source,
        aboveMembers: badgeBox.bottom <= membersBox.top + 1,
        radius: getComputedStyle(badge).borderRadius,
        dateSize: parseFloat(dateStyle.fontSize),
        dateWeight: Number(dateStyle.fontWeight),
        dateBorder: getComputedStyle(date).borderTopWidth,
        sourceBorder: getComputedStyle(badge.parentElement).borderTopWidth,
        castBorder: getComputedStyle(cast).borderTopWidth,
        title: document.getElementById('archiveDetailTitle').textContent.trim(),
        count: document.getElementById('archiveDetailCount').textContent.trim(),
        countHidden: document.getElementById('archiveDetailCount').hidden,
        prevHidden: document.getElementById('archiveDetailPrev').hidden,
        nextHidden: document.getElementById('archiveDetailNext').hidden,
        dots: document.querySelectorAll('#archiveDetailDots .ar-detail-dot').length
      };
    });
    assert.equal(detail.hidden, false);
    assert.equal(detail.text, '코스모룸');
    assert.equal(detail.source, 'cosmo_room');
    assert.equal(detail.aboveMembers, true, '출처 배지가 멤버 위에 없다');
    assert.ok(detail.dateSize >= 15 && detail.dateSize <= 18, '사진 날짜 글자가 커지지 않았다: ' + detail.dateSize);
    assert.ok(detail.dateWeight >= 600, '사진 날짜가 굵지 않다: ' + detail.dateWeight);
    assert.equal(detail.dateBorder, '0px', '첫 항목 위에 구분선이 있다');
    assert.equal(detail.sourceBorder, '1px', '출처 위에 구분선이 없다');
    assert.equal(detail.castBorder, '1px', '멤버 위에 구분선이 없다');
    assert.ok(detail.radius.includes('999') || parseFloat(detail.radius) >= 12, '출처가 버튼처럼 보이지 않는다: ' + detail.radius);
    assert.equal(await page.locator('#archiveDetail .ar-detail-chem').count(), 0, '케미명이 없는 멤버에도 케미 라벨이 있다');
    assert.equal(detail.title, '지연');
    assert.equal(detail.count, '1 / 2');
    assert.equal(detail.countHidden, false);
    assert.equal(detail.prevHidden, false);
    assert.equal(detail.nextHidden, false);
    assert.equal(detail.dots, 2);
    await page.click('#archiveDetailNext');
    const slid = await page.evaluate(() => ({
      title: document.getElementById('archiveDetailTitle').textContent.trim(),
      count: document.getElementById('archiveDetailCount').textContent.trim(),
      chem: document.querySelector('#archiveDetail .ar-detail-chem')?.textContent.trim() || ''
    }));
    assert.equal(slid.title, '마유 주빈');
    assert.equal(slid.count, '2 / 2');
    assert.equal(slid.chem, '심장마빈즈');
    await page.keyboard.press('ArrowLeft');
    assert.equal(await page.locator('#archiveDetailTitle').innerText(), '지연');
    const detailBox = await page.locator('#archiveDetail .ar-detail-side').boundingBox();
    await page.screenshot({
      path: path.join(artifactDir, 'archive-detail-source-badge.png'),
      clip: { x: Math.max(0, detailBox.x - 8), y: Math.max(0, detailBox.y - 8), width: detailBox.width + 16, height: Math.min(360, detailBox.height + 16) }
    });

    await page.evaluate(() => { document.getElementById('archiveDetail').hidden = true; });
    await page.click('#pair-card [data-archive-open]');
    const pair = await page.evaluate(() => {
      const chem = document.querySelector('#archiveDetail .ar-detail-chem');
      const members = document.getElementById('archiveDetailMembers');
      const chips = [...members.querySelectorAll('.unit-member-chip span')].map((node) => node.textContent.trim());
      const chemBox = chem?.getBoundingClientRect();
      const membersBox = members.getBoundingClientRect();
      const style = chem ? getComputedStyle(chem) : null;
      return {
        text: chem?.textContent.trim() || '',
        chips,
        above: Boolean(chemBox && chemBox.bottom <= membersBox.top + 2),
        color: style?.color || '',
        fontSize: style ? parseFloat(style.fontSize) : 0
      };
    });
    assert.equal(pair.text, '심장마빈즈');
    assert.deepEqual(pair.chips, ['마유', '주빈']);
    assert.equal(pair.above, true, '케미명이 초상화 위에 없다');
    assert.ok(pair.fontSize > 0 && pair.fontSize <= 14, '케미명 글자가 크다: ' + pair.fontSize);
    assert.ok(pair.color.includes('141') || pair.color.includes('8decd9'), '케미명 색이 민트가 아니다: ' + pair.color);
    const castBox = await page.locator('#archiveDetail .ar-detail-cast').boundingBox();
    await page.screenshot({
      path: path.join(artifactDir, 'archive-detail-chemistry-name.png'),
      clip: {
        x: Math.max(0, castBox.x - 16),
        y: Math.max(0, castBox.y - 16),
        width: Math.max(280, castBox.width + 32),
        height: castBox.height + 32
      }
    });

    await page.evaluate(() => {
      document.getElementById('archiveDetail').hidden = true;
      document.getElementById('editModal').hidden = false;
    });
    const form = await page.evaluate(() => {
      const measure = (root) => {
        const year = root.querySelector('.ar-cal-part[data-part="year"]');
        const month = root.querySelector('.ar-cal-part[data-part="month"]');
        const day = root.querySelector('.ar-cal-part[data-part="day"]');
        const cal = root.querySelector('.ar-cal');
        const open = root.querySelector('.ar-cal-open');
        const panel = root.querySelector('.ar-dialog-body');
        const yearBox = year.getBoundingClientRect();
        const monthBox = month.getBoundingClientRect();
        const dayBox = day.getBoundingClientRect();
        const calBox = cal.getBoundingClientRect();
        const openBox = open.getBoundingClientRect();
        const panelBox = panel.getBoundingClientRect();
        return {
          text: [year.value, month.value, day.value].join('/'),
          yearMonth: monthBox.left - yearBox.right,
          monthDay: dayBox.left - monthBox.right,
          calWidth: calBox.width,
          panelWidth: panelBox.width,
          openAfterDay: openBox.left >= dayBox.right - 1,
          openInside: openBox.right <= calBox.right + 2
        };
      };
      const select = document.querySelector('.ar-source-field select');
      const panel = document.querySelector('#editModal .ar-dialog-body');
      const selectBox = select.getBoundingClientRect();
      const panelBox = panel.getBoundingClientRect();
      return {
        edit: measure(document.getElementById('editModal')),
        selectGap: panelBox.right - selectBox.right,
        selectNarrower: selectBox.width < panelBox.width - 24,
        selectHeight: selectBox.height
      };
    });
    assert.equal(form.edit.text, '2026/11/16');
    assert.ok(form.edit.yearMonth < 36 && form.edit.monthDay < 36, '날짜 칸 간격이 넓다: ' + JSON.stringify(form.edit));
    assert.ok(form.edit.calWidth < 220, '날짜 칸이 한 줄을 차지한다: ' + form.edit.calWidth);
    assert.ok(form.edit.calWidth < form.edit.panelWidth * 0.55, '날짜 칸이 폼 너비에 붙어 있다: ' + JSON.stringify(form.edit));
    assert.equal(form.edit.openAfterDay, true, '달력 버튼이 날짜 뒤에 없다');
    assert.equal(form.edit.openInside, true, '달력 버튼이 날짜 칸 밖에 있다');
    assert.ok(form.selectNarrower, '출처 셀렉트가 여전히 가득 차 있다');
    assert.ok(form.selectGap >= 24, '출처 셀렉트 오른쪽 여백이 없다: ' + form.selectGap);
    assert.ok(form.selectHeight >= 34 && form.selectHeight <= 40, '출처 칸 높이가 맞지 않다: ' + form.selectHeight);
    await page.evaluate(() => { document.getElementById('uploadModal').hidden = false; document.getElementById('editModal').hidden = true; });
    const uploadDate = await page.evaluate(() => {
      const root = document.getElementById('uploadModal');
      const cal = root.querySelector('.ar-cal').getBoundingClientRect();
      const day = root.querySelector('.ar-cal-part[data-part="day"]').getBoundingClientRect();
      const open = root.querySelector('.ar-cal-open').getBoundingClientRect();
      return {
        text: [...root.querySelectorAll('.ar-cal-part')].map((el) => el.value).join('/'),
        calWidth: cal.width,
        openAfterDay: open.left >= day.right - 1
      };
    });
    assert.equal(uploadDate.text, '2026/11/05');
    assert.ok(Math.abs(uploadDate.calWidth - form.edit.calWidth) < 2, '추가 창 날짜 칸 너비가 수정 창과 다르다: ' + JSON.stringify(uploadDate));
    assert.equal(uploadDate.openAfterDay, true);
    await page.evaluate(() => { document.getElementById('uploadModal').hidden = true; document.getElementById('editModal').hidden = false; });
    const dateField = await page.locator('#archiveEditForm .ar-date-field').boundingBox();
    await page.screenshot({
      path: path.join(artifactDir, 'archive-edit-date-compact.png'),
      clip: {
        x: Math.max(0, dateField.x - 12),
        y: Math.max(0, dateField.y - 12),
        width: Math.min(520, dateField.width + 24),
        height: dateField.height + 24
      }
    });
    const editBox = await page.locator('#editModal .ar-dialog-panel').boundingBox();
    assert.ok(editBox && editBox.width > 0 && editBox.height > 0, '수정 창이 보이지 않는다');
    await page.screenshot({
      path: path.join(artifactDir, 'archive-edit-date-source.png'),
      clip: { x: Math.max(0, editBox.x - 12), y: Math.max(0, editBox.y - 12), width: Math.min(1100, editBox.width + 24), height: editBox.height + 24 }
    });
    await page.evaluate(() => { document.getElementById('editModal').hidden = true; });
    await page.evaluate(() => {
      const photo = document.querySelector('.ar-photo-link');
      if (photo) photo.style.display = 'none';
    });
    await page.locator('.ar-card[data-id="7"]').screenshot({ path: path.join(artifactDir, 'archive-card-date.png') });
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
