const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const css = fs.readFileSync(path.join(staticRoot, 'css', 'archive-page.css'), 'utf8');
const site = fs.readFileSync(path.join(staticRoot, 'css', 'site.css'), 'utf8');
const siteChrome = fs.readFileSync(path.join(staticRoot, 'css', 'site-chrome.css'), 'utf8');
const siteUi = fs.readFileSync(path.join(staticRoot, 'css', 'site-ui.css'), 'utf8');
const browseJs = fs.readFileSync(path.join(staticRoot, 'js', 'archive-browse.js'), 'utf8');
const pickerJs = fs.readFileSync(path.join(staticRoot, 'js', 'member-portrait-picker.js'), 'utf8');

const folderSvg = fs.readFileSync(path.join(staticRoot, 'images', 'archive-folder.svg'), 'utf8');
const artSvg = `<svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180">
  <rect width="240" height="180" fill="#38bdf8"/>
  <rect x="28" y="36" width="184" height="108" fill="#0f172a"/>
  <circle cx="96" cy="90" r="28" fill="#f472b6"/>
  <circle cx="148" cy="102" r="22" fill="#facc15"/>
</svg>`;

function folder(attrs) {
  const {
    extraClass = '',
    href = '#',
    color = '#7dd3fc',
    label,
    count = '',
    art = false,
    preview = ''
  } = attrs;
  return `<a class="ar-folder${extraClass}${art ? ' has-art' : ''}" href="${href}" style="--folder-color: ${color};">
    <span class="ar-folder-icon" aria-hidden="true">
      ${art ? '<img class="ar-folder-art" src="/folder-art.svg" alt="">' : '<span class="ar-folder-shape"></span>'}
      ${preview ? `<img class="ar-folder-preview" src="${preview}" alt="">` : ''}
    </span>
    <span class="ar-folder-label">${label}</span>
    ${count ? `<span class="ar-folder-count">${count}</span>` : ''}
  </a>`;
}

const homeHtml = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
*,*::before,*::after{box-sizing:border-box}
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
${site}
${siteChrome}
${css}
${siteUi}
body.site-admin-enabled button[data-admin-only] { display: inline-flex !important; }
</style></head>
<body class="site-page site-admin-enabled">
<main class="archive-page is-folder-home" data-archive-kind="image">
  <section class="ar-hero">
    <div class="ar-panel ar-headline">
      <div class="ar-headline-copy">
        <div class="ar-kicker">Archive</div>
        <h1>아카이브</h1>
        <p>폴더를 선택하세요</p>
      </div>
      <button type="button" class="ar-submit ar-home-upload" data-admin-only>사진 추가</button>
      <div class="ar-stats" id="archiveStats">
        <article class="ar-stat"><div class="ar-stat-label"><span>등록된 사진</span></div><strong>2<em>장</em></strong></article>
        <article class="ar-stat"><div class="ar-stat-label"><span>등록된 영상</span></div><strong>1<em>개</em></strong></article>
        <article class="ar-stat"><div class="ar-stat-label"><span>폴더</span></div><strong>42<em>개</em></strong></article>
      </div>
    </div>
  </section>
  <section class="ar-folders" aria-label="아카이브 폴더">
    ${folder({ extraClass: ' is-group', href: '/archive?folder=s', color: '#f5c518', label: '전체 멤버' })}
    ${folder({ extraClass: ' is-parent', href: '/archive?folder=units', color: '#a78bfa', label: '유닛별' })}
    ${folder({ extraClass: ' is-parent', href: '/archive?folder=combos', color: '#7dd3fc', label: '케미', count: '4' })}
    ${folder({ extraClass: ' is-solo', href: '/archive?folder=s1', color: '#22aeff', label: 'S1 윤서연' })}
  </section>
</main>
</body></html>`;

const unitsHtml = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
*,*::before,*::after{box-sizing:border-box}
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
${site}
${siteChrome}
${css}
${siteUi}
</style></head>
<body class="site-page">
<main class="archive-page is-folder-home is-folder-index" data-archive-kind="image">
  <section class="ar-hero">
    <div class="ar-panel ar-headline is-member-space" style="--folder-color:#a78bfa">
      <a class="site-back-link ar-back-folders" href="/archive">← 목록으로</a>
      <div class="ar-headline-copy">
        <div class="ar-kicker">Archive</div>
        <h1>아카이브</h1>
        <p class="ar-folder-title">유닛별</p>
      </div>
    </div>
  </section>
  <section class="ar-folders" aria-label="아카이브 폴더">
    ${folder({ href: '/archive?folder=unit-17', color: '#22aeff', label: 'Aura' })}
  </section>
</main>
</body></html>`;

const combosHtml = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
*,*::before,*::after{box-sizing:border-box}
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
${site}
${siteChrome}
${css}
${siteUi}
</style></head>
<body class="site-page">
<main class="archive-page is-folder-home is-folder-index" data-archive-kind="image">
  <section class="ar-hero">
    <div class="ar-panel ar-headline is-member-space" style="--folder-color:#7dd3fc">
      <a class="site-back-link ar-back-folders" href="/archive">← 목록으로</a>
      <div class="ar-headline-copy">
        <div class="ar-kicker">Archive</div>
        <h1>아카이브</h1>
        <p class="ar-folder-title">케미</p>
      </div>
    </div>
  </section>
  <section class="ar-folders" aria-label="아카이브 폴더">
    ${folder({ extraClass: ' is-combo', href: '/archive?folder=combo-2', color: '#7dd3fc', label: '2명 케미', count: '1' })}
    ${folder({ extraClass: ' is-combo', href: '/archive?folder=combo-3', color: '#a5b4fc', label: '3명 케미', count: '1' })}
    ${folder({ extraClass: ' is-combo', href: '/archive?folder=combo-4', color: '#c4b5fd', label: '4명 케미', count: '1' })}
    ${folder({ extraClass: ' is-combo', href: '/archive?folder=combo-5', color: '#86efac', label: '5명 이상', count: '1' })}
  </section>
</main>
</body></html>`;

const comboHtml = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
*,*::before,*::after{box-sizing:border-box}
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
${site}
${siteChrome}
${css}
${siteUi}
</style></head>
<body class="site-page site-admin-enabled">
<main class="archive-page is-folder-view" data-archive-kind="image">
  <section class="ar-hero">
    <div class="ar-panel ar-headline is-member-space" style="--folder-color:#7dd3fc">
      <a class="site-back-link ar-back-folders" href="/archive">← 목록으로</a>
      <div class="ar-headline-copy">
        <div class="ar-kicker">Archive</div>
        <h1>아카이브</h1>
        <p class="ar-folder-title" data-base-title="2명 케미">시선강탈</p>
      </div>
      <button type="button" class="ar-submit ar-home-upload" data-admin-only>사진 추가</button>
    </div>
  </section>
  <section class="ar-panel ar-toolbar">
    <form class="ar-filters" id="archiveFilterForm" method="get" action="/archive">
      <input type="hidden" name="folder" value="combo-2">
      <div class="ar-filter-tools">
        <label class="ar-sort">
          <span class="ar-sort-label">정렬</span>
          <span class="ar-sort-control">
            <select id="archiveSort" aria-label="정렬">
              <option value="newest">최신순</option>
              <option value="oldest">오래된 순</option>
            </select>
            <span class="material-symbols-outlined" aria-hidden="true">arrow_drop_down</span>
          </span>
        </label>
        <select class="ar-chem-select" id="archiveChem" name="chem" aria-label="케미">
          <option value="">케미 전체</option>
          <option value="pair-3" selected data-members="윤서연,정혜린" data-chem-name="시선강탈">시선강탈 · 윤서연 × 정혜린</option>
          <option value="pair-4" data-members="김채연,김유연" data-chem-name="하트시그널">하트시그널 · 김채연 × 김유연</option>
        </select>
        <button type="button" class="ar-member-toggle" id="archiveMemberToggle" aria-expanded="false" aria-controls="archiveMemberPanel">
          <span data-member-toggle-label>멤버 · 케미</span>
        </button>
        <div class="ar-search-row">
          <label class="ar-search-wrap">
            <span class="material-symbols-outlined" aria-hidden="true">search</span>
            <input class="ar-search" type="search" name="q" placeholder="사진 제목 검색">
          </label>
          <button type="submit" class="ar-submit">검색</button>
        </div>
        <a class="site-btn site-btn--reset ar-reset" href="/archive">초기화</a>
      </div>
    </form>
    <div class="ar-member-panel" id="archiveMemberPanel" hidden>
      <div class="ar-chem-matches" data-chem-matches>
        <p class="ar-chem-matches-hint" data-chem-hint>멤버를 고르면 케미가 나타납니다</p>
      </div>
      <div class="member-portrait-picker" id="archive-member-picker">
        <button type="button" class="mpp-item is-active" data-value="윤서연">윤서연</button>
        <button type="button" class="mpp-item is-active" data-value="정혜린">정혜린</button>
        <button type="button" class="mpp-item" data-value="이지우">이지우</button>
        <button type="button" class="mpp-item" data-value="김채연">김채연</button>
        <button type="button" class="mpp-item" data-value="김유연">김유연</button>
      </div>
    </div>
  </section>
  <section class="ar-grid" id="archiveGrid"></section>
</main>
<script>${pickerJs}</script>
<script>${browseJs}</script>
</body></html>`;

const memberHtml = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
*,*::before,*::after{box-sizing:border-box}
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
${site}
${siteChrome}
${css}
${siteUi}
</style></head>
<body class="site-page">
<main class="archive-page is-folder-view" data-archive-kind="image">
  <section class="ar-hero">
    <div class="ar-panel ar-headline is-member-space" style="--folder-color:#22aeff">
      <a class="site-back-link ar-back-folders" href="/archive">← 목록으로</a>
      <div class="ar-headline-copy">
        <div class="ar-kicker">Archive</div>
        <h1>아카이브</h1>
        <p class="ar-folder-title is-solo" data-base-title="S1 윤서연"><span class="ar-folder-num">S1</span> <span class="ar-folder-name">윤서연</span></p>
      </div>
      <div class="ar-stats" id="archiveStats">
        <article class="ar-stat"><div class="ar-stat-label"><span>등록된 사진</span><span class="material-symbols-outlined">image</span></div><strong>0<em>장</em></strong></article>
        <article class="ar-stat ar-stat-date"><div class="ar-stat-label"><span>날짜</span><span class="material-symbols-outlined">calendar_month</span></div><strong>2026년 11월 16일</strong></article>
      </div>
    </div>
  </section>
</main>
</body></html>`;

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    await page.route('**/*', (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === '/' || url.pathname.endsWith('home.html')) {
        return route.fulfill({ contentType: 'text/html', body: homeHtml });
      }
      if (url.pathname.endsWith('units.html')) {
        return route.fulfill({ contentType: 'text/html', body: unitsHtml });
      }
      if (url.pathname.endsWith('combos.html')) {
        return route.fulfill({ contentType: 'text/html', body: combosHtml });
      }
      if (url.pathname.endsWith('combo.html')) {
        return route.fulfill({ contentType: 'text/html', body: comboHtml });
      }
      if (url.pathname.endsWith('member.html')) {
        return route.fulfill({ contentType: 'text/html', body: memberHtml });
      }
      if (url.pathname === '/images/archive-folder.svg') {
        return route.fulfill({ contentType: 'image/svg+xml', body: folderSvg });
      }
      if (url.pathname === '/folder-art.svg') {
        return route.fulfill({ contentType: 'image/svg+xml', body: artSvg });
      }
      const file = path.resolve(staticRoot, '.' + url.pathname);
      if (file.startsWith(staticRoot + path.sep) && fs.existsSync(file)) {
        return route.fulfill({ path: file });
      }
      return route.fulfill({ status: 404, body: '' });
    });

    await page.goto('https://archive-folders.test/home.html', { waitUntil: 'load' });
    const home = await page.evaluate(() => {
      const labels = [...document.querySelectorAll('.ar-folder-label')].map((el) => el.textContent.trim());
      const group = document.querySelector('.ar-folder.is-group');
      const member = document.querySelector('.ar-folder.is-solo');
      const parent = document.querySelector('.ar-folder.is-parent');
      const memberIcon = member.querySelector('.ar-folder-icon');
      const parentIcon = parent.querySelector('.ar-folder-icon');
      const groupIcon = group.querySelector('.ar-folder-icon');
      const shine = getComputedStyle(groupIcon, '::after');
      const upload = document.querySelector('.ar-home-upload');
      const headline = document.querySelector('.ar-headline').getBoundingClientRect();
      const uploadBox = upload.getBoundingClientRect();
      return {
        labels,
        comboArt: Boolean(document.querySelector('.ar-folder-art, .ar-folder-preview')),
        placeholder: [...document.querySelectorAll('img')].some((img) => (img.getAttribute('src') || '').includes('image-upload-pending')),
        member: memberIcon.getBoundingClientRect(),
        parent: parentIcon.getBoundingClientRect(),
        overflow: getComputedStyle(groupIcon).overflow,
        shineMask: shine.webkitMaskImage || shine.maskImage || '',
        shineBlend: shine.mixBlendMode,
        uploadText: upload?.textContent.trim() || '',
        uploadVisible: uploadBox.width > 0 && uploadBox.height > 0,
        uploadRight: Math.round(headline.right - uploadBox.right),
        uploadBottom: Math.round(headline.bottom - uploadBox.bottom)
      };
    });
    assert.deepEqual(home.labels, ['전체 멤버', '유닛별', '케미', 'S1 윤서연']);
    assert.equal(home.comboArt, false, '초기 화면에 케미·유닛 폴더 이미지가 보인다');
    assert.equal(home.placeholder, false, '업로드 예정 플레이스홀더가 남아 있다');
    assert.ok(Math.abs(home.member.width - home.parent.width) <= 1, '상위 폴더 너비가 멤버 폴더와 다르다: ' + JSON.stringify(home));
    assert.ok(Math.abs(home.member.height - home.parent.height) <= 1, '상위 폴더 높이가 멤버 폴더와 다르다: ' + JSON.stringify(home));
    assert.equal(home.overflow, 'hidden', '호버 효과가 폴더 밖으로 새지 않게 잘리지 않는다');
    assert.match(home.shineMask, /archive-folder\.svg/, '호버 샤인이 폴더 실루엣으로 잘리지 않는다: ' + home.shineMask);
    assert.equal(home.uploadText, '사진 추가');
    assert.equal(home.uploadVisible, true, '폴더 홈 사진 추가 버튼이 안 보인다');
    assert.ok(home.uploadRight >= 8 && home.uploadRight <= 40, '사진 추가가 우측이 아니다: ' + JSON.stringify(home));
    assert.ok(home.uploadBottom >= 8 && home.uploadBottom <= 40, '사진 추가가 하단이 아니다: ' + JSON.stringify(home));

    await page.hover('.ar-folder.is-group');
    const artifactDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(artifactDir, { recursive: true });
    await page.screenshot({ path: path.join(artifactDir, 'archive-folder-home.png'), fullPage: true });
    await page.locator('.ar-headline').screenshot({ path: path.join(artifactDir, 'archive-folder-home-upload.png') });
    await page.locator('.ar-folder.is-parent').nth(1).screenshot({ path: path.join(artifactDir, 'archive-combo-parent.png') });

    await page.goto('https://archive-folders.test/units.html', { waitUntil: 'load' });
    const units = await page.evaluate(() => {
      const aura = [...document.querySelectorAll('.ar-folder')].find((el) => el.textContent.includes('Aura'));
      return {
        title: document.querySelector('.ar-folder-title')?.textContent.trim() || '',
        labels: [...document.querySelectorAll('.ar-folder-label')].map((el) => el.textContent.trim()),
        back: document.querySelector('.ar-back-folders')?.getAttribute('href') || '',
        auraPreview: Boolean(aura?.querySelector('.ar-folder-preview, .ar-folder-art'))
      };
    });
    assert.equal(units.title, '유닛별');
    assert.deepEqual(units.labels, ['Aura']);
    assert.equal(units.back, '/archive');
    assert.equal(units.auraPreview, false, '유닛 이미지가 없는데 미리보기가 있다');
    await page.screenshot({ path: path.join(artifactDir, 'archive-units-index.png'), fullPage: true });

    await page.goto('https://archive-folders.test/combos.html', { waitUntil: 'load' });
    const combos = await page.evaluate(() => ({
      title: document.querySelector('.ar-folder-title')?.textContent.trim() || '',
      labels: [...document.querySelectorAll('.ar-folder-label')].map((el) => el.textContent.trim()),
      back: document.querySelector('.ar-back-folders')?.getAttribute('href') || '',
      art: Boolean(document.querySelector('.ar-folder-art, .ar-folder-preview'))
    }));
    assert.equal(combos.title, '케미');
    assert.deepEqual(combos.labels, ['2명 케미', '3명 케미', '4명 케미', '5명 이상']);
    assert.equal(combos.back, '/archive');
    assert.equal(combos.art, false, '케미 폴더에 이미지가 남아 있다');
    await page.locator('.ar-folder.is-combo').first().screenshot({ path: path.join(artifactDir, 'archive-combo-folder.png') });
    await page.screenshot({ path: path.join(artifactDir, 'archive-combos-index.png'), fullPage: true });

    await page.goto('https://archive-folders.test/combo.html', { waitUntil: 'load' });
    const combo = await page.evaluate(() => {
      const select = document.getElementById('archiveChem');
      const upload = document.querySelector('.ar-home-upload');
      const toolbarUpload = document.querySelector('.ar-filter-tools [data-admin-only], .ar-filter-tools .ar-home-upload');
      const headline = document.querySelector('.ar-headline').getBoundingClientRect();
      const uploadBox = upload.getBoundingClientRect();
      return {
        title: document.querySelector('.ar-folder-title')?.textContent.trim() || '',
        kickerLeft: Math.round(document.querySelector('.ar-kicker').getBoundingClientRect().left),
        copyLeft: Math.round(document.querySelector('.ar-headline-copy').getBoundingClientRect().left),
        h1Size: parseFloat(getComputedStyle(document.querySelector('.ar-headline h1')).fontSize),
        titleSize: parseFloat(getComputedStyle(document.querySelector('.ar-folder-title')).fontSize),
        values: [...(select?.options || [])].map((option) => option.value),
        labels: [...(select?.options || [])].map((option) => option.textContent.trim()),
        selected: select?.value || '',
        chemDropdown: Boolean(document.querySelector('.ar-chem .ar-sort-trigger')),
        toggle: document.querySelector('[data-member-toggle-label]')?.textContent.trim() || '',
        uploadInHeadline: Boolean(upload) && document.querySelector('.ar-headline').contains(upload),
        toolbarUpload: Boolean(toolbarUpload),
        uploadRight: Math.round(headline.right - uploadBox.right),
        uploadBottom: Math.round(headline.bottom - uploadBox.bottom)
      };
    });
    assert.equal(combo.title, '시선강탈');
    assert.ok(Math.abs(combo.kickerLeft - combo.copyLeft) <= 4, 'ARCHIVE 제목이 왼쪽에 있지 않다: ' + JSON.stringify(combo));
    assert.ok(combo.h1Size >= 24, '아카이브 제목이 예전 크기가 아니다: ' + combo.h1Size);
    assert.ok(combo.titleSize < combo.h1Size, '폴더 이름이 아카이브 제목보다 크다: ' + JSON.stringify(combo));
    await page.locator('.ar-headline').screenshot({ path: path.join(artifactDir, 'archive-folder-title-left.png') });
    assert.deepEqual(combo.values, ['', 'pair-3', 'pair-4']);
    assert.ok(combo.labels[1].includes('시선강탈'));
    assert.equal(combo.selected, 'pair-3');
    assert.equal(combo.chemDropdown, false, '케미 드롭다운이 툴바에 남아 있다');
    assert.equal(combo.toggle, '시선강탈');
    assert.equal(combo.uploadInHeadline, true, '폴더 안 사진 추가가 제목 패널에 없다');
    assert.equal(combo.toolbarUpload, false, '폴더 안 툴바에 사진 추가가 남아 있다');
    assert.ok(combo.uploadRight >= 8 && combo.uploadRight <= 40, '폴더 안 사진 추가가 우측이 아니다: ' + JSON.stringify(combo));
    assert.ok(combo.uploadBottom >= 8 && combo.uploadBottom <= 40, '폴더 안 사진 추가가 하단이 아니다: ' + JSON.stringify(combo));
    await page.click('#archiveMemberToggle');
    const matched = await page.evaluate(() => [...document.querySelectorAll('.ar-chem-match')].map((el) => el.textContent.trim()));
    assert.deepEqual(matched, ['시선강탈']);
    await page.click('.mpp-item[data-value="정혜린"]');
    const afterOne = await page.evaluate(() => ({
      chips: [...document.querySelectorAll('.ar-chem-match')].map((el) => el.textContent.trim()),
      title: document.querySelector('.ar-folder-title')?.textContent.trim() || '',
      toggle: document.querySelector('[data-member-toggle-label]')?.textContent.trim() || ''
    }));
    assert.ok(afterOne.chips.includes('시선강탈'));
    assert.ok(afterOne.chips.length >= 1);
    assert.equal(afterOne.title, '2명 케미');
    assert.equal(afterOne.toggle, '1명');
    await page.screenshot({ path: path.join(artifactDir, 'archive-chem-filter.png'), fullPage: true });
    await page.locator('.ar-toolbar').screenshot({ path: path.join(artifactDir, 'archive-toolbar-compact.png') });

    await page.goto('https://archive-folders.test/member.html', { waitUntil: 'load' });
    const memberTitle = await page.evaluate(() => {
      const headline = document.querySelector('.ar-headline');
      const title = document.querySelector('.ar-folder-title');
      const name = document.querySelector('.ar-folder-name');
      const num = document.querySelector('.ar-folder-num');
      const kicker = document.querySelector('.ar-kicker');
      const headlineBox = headline.getBoundingClientRect();
      const titleBox = title.getBoundingClientRect();
      const nameBox = name.getBoundingClientRect();
      const numBox = num.getBoundingClientRect();
      const kickerBox = kicker.getBoundingClientRect();
      const style = getComputedStyle(headline);
      const innerLeft = headlineBox.left + parseFloat(style.paddingLeft);
      const innerRight = headlineBox.right - parseFloat(style.paddingRight);
      const nameStyle = getComputedStyle(name);
      const clusterCenter = (numBox.left + nameBox.right) / 2;
      const panelCenter = (innerLeft + innerRight) / 2;
      return {
        centered: Math.abs(clusterCenter - panelCenter) <= 12,
        kickerLeft: kickerBox.left - innerLeft <= 4,
        nameText: name.textContent.trim(),
        numText: num.textContent.trim(),
        underline: nameStyle.borderBottomColor,
        underlineWidth: parseFloat(nameStyle.borderBottomWidth),
        numUnderline: parseFloat(getComputedStyle(num).borderBottomWidth),
        dateStat: document.querySelector('.ar-stat-date strong')?.textContent.trim() || '',
        dateIcon: document.querySelector('.ar-stat-date .material-symbols-outlined')?.textContent.trim() || ''
      };
    });
    assert.equal(memberTitle.centered, true, '멤버 이름이 가운데 있지 않다: ' + JSON.stringify(memberTitle));
    assert.equal(memberTitle.kickerLeft, true, 'ARCHIVE 라벨이 왼쪽에 있지 않다');
    assert.equal(memberTitle.nameText, '윤서연');
    assert.equal(memberTitle.numText, 'S1');
    assert.ok(memberTitle.underlineWidth >= 2, '이름 밑줄이 없다: ' + memberTitle.underlineWidth);
    assert.ok(memberTitle.numUnderline < 1, 'S1에도 밑줄이 있다');
    assert.ok(memberTitle.underline.includes('34, 174, 255') || memberTitle.underline.includes('22aeff') || memberTitle.underline.includes('34,174,255'), '이름 밑줄이 멤버색이 아니다: ' + memberTitle.underline);
    assert.equal(memberTitle.dateStat, '2026년 11월 16일');
    assert.equal(memberTitle.dateIcon, 'calendar_month');
    await page.locator('.ar-headline').screenshot({ path: path.join(artifactDir, 'archive-member-title-center.png') });
    console.log('PASS: 아카이브 폴더 홈 실루엣·케미 필터');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
