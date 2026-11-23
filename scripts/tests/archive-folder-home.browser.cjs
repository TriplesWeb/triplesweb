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

function headline(attrs = {}) {
  const {
    back = false,
    folderTitle = '',
    solo = false,
    stats = '',
    upload = false,
    color = '#a78bfa',
    memberSpace = true,
    home = false
  } = attrs;
  const title = folderTitle
    ? (solo
      ? `<h1 class="ar-folder-title is-solo" data-base-title="${folderTitle}"><span class="ar-folder-num">S1</span> <span class="ar-folder-name">윤서연</span></h1>`
      : `<h1 class="ar-folder-title">${folderTitle}</h1>`)
    : '<h1>아카이브</h1>';
  const hasStats = Boolean(stats);
  const toolbar = home ? '' : `<div class="ar-headline-toolbar">
        ${back ? '<a class="site-back-link site-back-link--chip ar-back-folders" href="/archive">← 목록으로</a>' : ''}
        <div class="ar-headline-actions">
          ${upload ? '<button type="button" class="site-btn site-btn--primary site-btn--sm ar-submit ar-home-upload" data-admin-only>사진 추가</button>' : ''}
        </div>
      </div>`;
  return `<div class="ar-panel ar-headline${memberSpace ? ' is-member-space' : ''}" style="--folder-color:${color}">
      ${toolbar}
      <div class="ar-headline-body${hasStats ? '' : ' is-copy-only'}">
        <div class="ar-headline-copy">
          <div class="ar-kicker">${home ? 'folder ' : ''}Archive</div>
          ${title}
        </div>
        ${stats}
      </div>
    </div>`;
}

function folder(attrs) {
  const {
    extraClass = '',
    href = '#',
    color = '#7dd3fc',
    label,
    count = '',
    art = false,
    bundledArt = '',
    preview = ''
  } = attrs;
  return `<a class="ar-folder${extraClass}${art || bundledArt ? ' has-art' : ''}" href="${href}" style="--folder-color: ${color};">
    <span class="ar-folder-icon" aria-hidden="true">
      ${bundledArt ? `<img class="ar-folder-art is-bundled-art" src="${bundledArt}" alt="">` : art ? '<img class="ar-folder-art" src="/folder-art.svg" alt="">' : '<span class="ar-folder-shape"></span>'}
      ${preview ? `<img class="ar-folder-preview" src="${preview}" alt="">` : ''}
    </span>
    ${label ? `<span class="ar-folder-label">${label}</span>` : ''}
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
    ${headline({
      home: true,
      memberSpace: false,
      stats: `<div class="ar-stats" id="archiveStats">
        <article class="ar-stat"><div class="ar-stat-label"><span>등록된 사진</span></div><strong>2<em>장</em></strong></article>
        <article class="ar-stat"><div class="ar-stat-label"><span>등록된 영상</span></div><strong>1<em>개</em></strong></article>
        <article class="ar-stat"><div class="ar-stat-label"><span>폴더</span></div><strong>42<em>개</em></strong></article>
      </div>`
    })}
  </section>
  <div class="ar-folders-head">
    <button type="button" class="site-btn site-btn--primary site-btn--sm ar-submit ar-home-upload" data-admin-only>사진 추가</button>
  </div>
  <section class="ar-folders" aria-label="아카이브 폴더">
    ${folder({ extraClass: ' is-group', href: '/archive?folder=s', color: '#f5c518', label: 'tripleS', count: '3' })}
    ${folder({ extraClass: ' is-parent', href: '/archive?folder=units', color: '#a78bfa', label: '디멘션' })}
    ${folder({ extraClass: ' is-solo', href: '/archive?folder=s&members=윤서연', color: '#22aeff', label: 'S1 윤서연', bundledArt: '/images/archive/member-folders/1.png' })}
    ${folder({ extraClass: ' is-solo', href: '/archive?folder=s&members=정혜린', color: '#ff8ad4', label: 'S2 정혜린', bundledArt: '/images/archive/member-folders/2.png' })}
  </section>
</main>
<script>${browseJs}</script>
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
    ${headline({ back: true, folderTitle: '디멘션', color: '#a78bfa' })}
  </section>
  <section class="ar-folders" aria-label="아카이브 폴더">
    ${folder({ href: '/archive?folder=s&members=윤서연&members=정혜린', color: '#22aeff', label: 'Aura', bundledArt: '/images/archive/unit-folders/42.png' })}
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
    ${headline({ back: true, folderTitle: '케미', color: '#7dd3fc' })}
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
    ${headline({ back: true, folderTitle: '시선강탈', color: '#7dd3fc', upload: true })}
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
    ${headline({
      back: true,
      solo: true,
      folderTitle: 'S1 윤서연',
      color: '#22aeff'
    })}
  </section>
  <section class="ar-panel ar-toolbar">
    <form class="ar-filters" id="archiveFilterForm">
      <div class="ar-filter-tools">
        <label class="ar-sort"><span class="ar-sort-label">정렬</span></label>
        <button type="button" class="ar-member-toggle">멤버 선택</button>
        <div class="ar-search-row">
          <label class="ar-search-wrap"><input class="ar-search" type="search" placeholder="사진 제목 검색"></label>
          <button type="submit" class="ar-submit">검색</button>
        </div>
      </div>
    </form>
  </section>
</main>
</body></html>`;

const groupHtml = memberHtml.replace(/<h1 class="ar-folder-title[\s\S]*?<\/h1>/, '<h1>아카이브</h1>');

const viewHtml = `<!doctype html><html><head><meta charset="utf-8">
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
    ${headline({ back: true, color: '#f5c518' })}
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
      if (url.pathname.endsWith('group.html')) {
        return route.fulfill({ contentType: 'text/html', body: groupHtml });
      }
      if (url.pathname.endsWith('view.html')) {
        return route.fulfill({ contentType: 'text/html', body: viewHtml });
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
      const pageRoot = document.querySelector('.archive-page');
      const headline = document.querySelector('.ar-headline').getBoundingClientRect();
      const folders = document.querySelector('.ar-folders').getBoundingClientRect();
      const stat = document.querySelector('.ar-stat').getBoundingClientRect();
      const stats = document.getElementById('archiveStats').getBoundingClientRect();
      const style = getComputedStyle(document.querySelector('.ar-headline'));
      const innerLeft = headline.left + parseFloat(style.paddingLeft);
      const innerRight = headline.right - parseFloat(style.paddingRight);
      const count = document.querySelector('.ar-folder-count');
      const uploadBox = upload.getBoundingClientRect();
      const pageStyle = getComputedStyle(pageRoot);
      return {
        labels,
        comboArt: Boolean(document.querySelector('.ar-folder:not(.is-solo) .ar-folder-art, .ar-folder-preview')),
        memberArt: [...document.querySelectorAll('.ar-folder.is-solo .ar-folder-art')].map((image) => ({
          loaded: image.naturalWidth > 0,
          mask: getComputedStyle(image).maskImage
        })),
        placeholder: [...document.querySelectorAll('img')].some((img) => (img.getAttribute('src') || '').includes('image-upload-pending')),
        member: memberIcon.getBoundingClientRect(),
        parent: parentIcon.getBoundingClientRect(),
        overflow: getComputedStyle(groupIcon).overflow,
        shine: shine.content,
        uploadText: upload?.textContent.trim() || '',
        uploadVisible: uploadBox.width > 0 && uploadBox.height > 0,
        uploadHeight: Math.round(uploadBox.height),
        hint: document.querySelector('.ar-headline-copy p')?.textContent.trim() || '',
        uploadRight: Math.round(folders.right - uploadBox.right),
        aboveFolders: uploadBox.bottom <= folders.top + 4,
        inFoldersHead: Boolean(upload?.closest('.ar-folders-head')),
        inToolbar: Boolean(upload?.closest('.ar-headline-actions')),
        pageMax: pageStyle.maxWidth,
        padL: parseFloat(pageStyle.paddingLeft),
        padR: parseFloat(pageStyle.paddingRight),
        headlineWidth: Math.round(headline.width),
        foldersWidth: Math.round(folders.width),
        statWidth: Math.round(stat.width),
        statHeight: Math.round(stat.height),
        statsOnRight: stats.left > ((innerLeft + innerRight) / 2),
        bodyColumns: getComputedStyle(document.querySelector('.ar-headline-body')).gridTemplateColumns.split(' ').filter(Boolean).length,
        countHidden: count ? getComputedStyle(count).display === 'none' : false,
        memberHref: member?.getAttribute('href') || '',
        headlineGlow: getComputedStyle(document.querySelector('.ar-headline'), '::after').content
      };
    });
    assert.deepEqual(home.labels, ['tripleS', '디멘션', 'S1 윤서연', 'S2 정혜린']);
    assert.equal(home.memberHref, '/archive?folder=s&members=윤서연');
    assert.deepEqual(home.memberArt, [{ loaded: true, mask: 'none' }, { loaded: true, mask: 'none' }]);
    assert.equal(home.headlineGlow, 'none');
    assert.equal(home.comboArt, false, '초기 화면에 케미·유닛 폴더 이미지가 보인다');
    assert.equal(home.placeholder, false, '업로드 예정 플레이스홀더가 남아 있다');
    assert.ok(Math.abs(home.member.width - home.parent.width) <= 1, '상위 폴더 너비가 멤버 폴더와 다르다: ' + JSON.stringify(home));
    assert.ok(Math.abs(home.member.height - home.parent.height) <= 1, '상위 폴더 높이가 멤버 폴더와 다르다: ' + JSON.stringify(home));
    assert.equal(home.overflow, 'hidden');
    assert.equal(home.shine, 'none', '폴더의 반짝임 효과가 남아 있다');
    assert.equal(home.uploadText, '사진 추가');
    assert.equal(home.uploadVisible, true, '폴더 홈 사진 추가 버튼이 안 보인다');
    assert.equal(home.inToolbar, false, '사진 추가가 히어로 툴바에 남아 있다');
    assert.equal(home.inFoldersHead, true, '사진 추가가 폴더 목록 앞에 없다');
    assert.equal(home.aboveFolders, true, '사진 추가가 폴더 그리드 위에 없다');
    assert.ok(home.uploadRight >= 0 && home.uploadRight <= 40, '사진 추가가 우측이 아니다: ' + JSON.stringify(home));
    assert.equal(home.pageMax, '1440px', '폴더 홈이 오브젝트처럼 1440px로 안 묶인다: ' + home.pageMax);
    assert.ok(home.padL >= 40 && home.padR >= 40, '폴더 홈 좌우 여백이 좁다: ' + JSON.stringify(home));
    assert.ok(Math.abs(home.headlineWidth - home.foldersWidth) <= 1, '제목 패널과 폴더 폭이 다르다: ' + JSON.stringify(home));
    assert.ok(home.statWidth <= 320, '통계 카드가 여전히 크다: ' + home.statWidth);
    assert.ok(home.statHeight <= 96, '통계 카드 높이가 그대로다: ' + home.statHeight);
    assert.equal(home.bodyColumns, 2, '헤드라인이 제목|통계 2열이 아니다: ' + home.bodyColumns);
    assert.equal(home.statsOnRight, true, '홈 통계가 오른쪽 칸에 없다');
    assert.equal(home.hint, '', '폴더를 선택하세요 안내가 남아 있다');
    assert.ok(home.uploadHeight >= 32 && home.uploadHeight <= 40, '사진 추가 버튼이 여전히 두껍다: ' + home.uploadHeight);
    assert.equal(home.countHidden, true, '폴더 사진 숫자가 보인다');

    const fonts = await page.evaluate(() => [...document.querySelectorAll('.ar-stat-label span:first-child')].map((el) => getComputedStyle(el).fontFamily));
    for (const family of fonts) {
      assert.match(family, /Pretendard/i, '통계 라벨 글꼴이 사이트 본문과 다르다: ' + family);
      assert.doesNotMatch(family, /JetBrains/i, '통계 라벨이 모노스페이스다: ' + family);
    }

    assert.equal(await page.locator('[data-stats-toggle]').count(), 0, '통계 접기 버튼이 남아 있다');
    assert.equal(await page.locator('#archiveStats').count(), 1, '홈 통계가 없다');

    await page.hover('.ar-folder.is-group');
    const artifactDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(artifactDir, { recursive: true });
    await page.screenshot({ path: path.join(artifactDir, 'archive-folder-home.png'), fullPage: true });
    await page.locator('.ar-headline').screenshot({ path: path.join(artifactDir, 'archive-folder-home-upload.png') });
    await page.locator('.ar-folder.is-solo').first().screenshot({ path: path.join(artifactDir, 'archive-member-folder.png') });

    await page.goto('https://archive-folders.test/units.html', { waitUntil: 'load' });
    const units = await page.evaluate(() => {
      const aura = [...document.querySelectorAll('.ar-folder')].find((el) => el.textContent.includes('Aura'));
      const art = aura?.querySelector('.ar-folder-art');
      return {
        title: document.querySelector('.ar-folder-title')?.textContent.trim() || '',
        labels: [...document.querySelectorAll('.ar-folder-label')].map((el) => el.textContent.trim()),
        back: document.querySelector('.ar-back-folders')?.getAttribute('href') || '',
        artLoaded: (art?.naturalWidth ?? 0) > 0,
        artMask: art && getComputedStyle(art).maskImage,
        href: aura?.getAttribute('href') || ''
      };
    });
    assert.equal(units.title, '디멘션');
    assert.deepEqual(units.labels, ['Aura']);
    assert.equal(units.back, '/archive');
    assert.equal(units.href, '/archive?folder=s&members=윤서연&members=정혜린');
    assert.equal(units.artLoaded, true, '유닛 폴더 이미지를 불러오지 못했다');
    assert.equal(units.artMask, 'none', '유닛 폴더 이미지에 별도 폴더 모양이 씌워져 있다');
    await page.screenshot({ path: path.join(artifactDir, 'archive-units-index.png'), fullPage: true });

    await page.goto('https://archive-folders.test/member.html', { waitUntil: 'load' });
    const memberTitle = await page.evaluate(() => {
      const headline = document.querySelector('.ar-headline');
      const title = document.querySelector('.ar-folder-title');
      const name = document.querySelector('.ar-folder-name');
      const num = document.querySelector('.ar-folder-num');
      const kicker = document.querySelector('.ar-kicker');
      const headlineBox = headline.getBoundingClientRect();
      const titleBox = title.getBoundingClientRect();
      const kickerBox = kicker.getBoundingClientRect();
      const style = getComputedStyle(headline);
      const innerLeft = headlineBox.left + parseFloat(style.paddingLeft);
      const nameStyle = getComputedStyle(name);
      return {
        titleIsH1: title?.tagName === 'H1',
        noGenericH1: ![...document.querySelectorAll('.ar-headline h1')].some((el) => el.textContent.trim() === '아카이브'),
        titleLeft: Math.abs(titleBox.left - innerLeft) <= 4,
        kickerLeft: kickerBox.left - innerLeft <= 4,
        aligned: Math.abs(titleBox.left - kickerBox.left) <= 2,
        stacked: titleBox.top >= kickerBox.bottom - 1,
        nameText: name.textContent.trim(),
        numText: num.textContent.trim(),
        titleBar: (() => {
          const after = getComputedStyle(title, '::after');
          return {
            display: after.display,
            width: after.width,
            height: after.height,
            color: after.backgroundColor
          };
        })(),
        nameBar: nameStyle.backgroundImage,
        noStats: !document.getElementById('archiveStats'),
        filterFill: (() => {
          const tools = document.querySelector('.ar-filter-tools');
          const toolbar = document.querySelector('.ar-toolbar');
          if (!tools || !toolbar) return null;
          const toolsBox = tools.getBoundingClientRect();
          const bar = toolbar.getBoundingClientRect();
          const padL = parseFloat(getComputedStyle(toolbar).paddingLeft);
          const padR = parseFloat(getComputedStyle(toolbar).paddingRight);
          return Math.abs(toolsBox.width - (bar.width - padL - padR));
        })()
      };
    });
    assert.ok(memberTitle.filterFill != null && memberTitle.filterFill <= 8, '검색 필터가 툴바 폭을 안 채운다: ' + memberTitle.filterFill);
    assert.equal(memberTitle.titleIsH1, true, '멤버 이름이 주 제목이 아니다');
    assert.equal(memberTitle.noGenericH1, true, '폴더 보기에 일반 아카이브 제목이 남아 있다');
    assert.equal(memberTitle.titleLeft, true, '멤버 이름이 왼쪽 정렬이 아니다: ' + JSON.stringify(memberTitle));
    assert.equal(memberTitle.kickerLeft, true, 'ARCHIVE 라벨이 왼쪽에 있지 않다');
    assert.equal(memberTitle.aligned, true, '제목과 ARCHIVE 라벨이 어긋난다');
    assert.equal(memberTitle.stacked, true, 'ARCHIVE와 멤버 제목이 한 줄에 있다');
    assert.equal(memberTitle.nameText, '윤서연');
    assert.equal(memberTitle.numText, 'S1');
    assert.equal(memberTitle.titleBar.display, 'block', '제목 아래 막대가 없다');
    assert.ok(memberTitle.titleBar.width !== '0px' && memberTitle.titleBar.height !== '0px', '제목 막대 크기가 깨졌다: ' + JSON.stringify(memberTitle.titleBar));
    assert.ok(
      memberTitle.titleBar.color.includes('34, 174, 255')
        || memberTitle.titleBar.color.includes('22aeff')
        || memberTitle.titleBar.color.includes('34,174,255'),
      '제목 막대 색이 멤버색이 아니다: ' + memberTitle.titleBar.color
    );
    assert.equal(memberTitle.nameBar, 'none', '이름에만 따로 밑줄이 남아 있다');
    assert.equal(memberTitle.noStats, true, '폴더 보기에 통계가 남아 있다');
    await page.locator('.ar-headline').screenshot({ path: path.join(artifactDir, 'archive-member-title-center.png') });

    await page.goto('https://archive-folders.test/group.html', { waitUntil: 'load' });
    assert.equal(await page.locator('.ar-folder-title').count(), 0);
    assert.equal(await page.locator('.ar-headline h1').textContent(), '아카이브');
    await page.locator('.ar-headline').screenshot({ path: path.join(artifactDir, 'archive-group-title.png') });

    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('https://archive-folders.test/view.html', { waitUntil: 'load' });
    const view = await page.evaluate(() => {
      const headline = document.querySelector('.ar-headline');
      const back = document.querySelector('.ar-back-folders');
      const toggle = document.querySelector('.ar-stats-toggle');
      const headlineBox = headline.getBoundingClientRect();
      const backBox = back.getBoundingClientRect();
      const backStyle = getComputedStyle(back);
      return {
        noStats: !document.getElementById('archiveStats'),
        noToggle: !toggle,
        bodyCopyOnly: document.querySelector('.ar-headline-body')?.classList.contains('is-copy-only') || false,
        pageMax: getComputedStyle(document.querySelector('.archive-page')).maxWidth,
        backText: back.textContent.trim(),
        backHref: back.getAttribute('href'),
        backRadius: parseFloat(backStyle.borderRadius),
        backBg: backStyle.backgroundColor,
        backColor: backStyle.color,
        backBorder: backStyle.borderTopWidth,
        inside: backBox.left >= headlineBox.left - 1 && backBox.right <= headlineBox.right + 1,
      };
    });
    assert.equal(view.noStats, true, '폴더 보기에 통계가 남아 있다');
    assert.equal(view.noToggle, true, '폴더 보기에 통계 접기가 남아 있다');
    assert.equal(view.bodyCopyOnly, true, '폴더 보기 헤드라인이 제목 전용이 아니다');
    assert.equal(view.pageMax, '1440px', '폴더 보기 폭이 홈과 다르다: ' + view.pageMax);
    assert.equal(view.backText, '← 목록으로');
    assert.equal(view.backHref, '/archive');
    assert.ok(view.backRadius >= 16, '목록으로가 알약 버튼이 아니다: ' + view.backRadius);
    assert.ok(view.backBg.includes('13, 14, 18') || view.backBg.includes('rgba(13, 14, 18'), '목록으로 배경이 site-btn 계열이 아니다: ' + view.backBg);
    assert.ok(view.backColor.includes('185, 202, 196') || view.backColor.includes('b9cac4'), '목록으로 기본색이 muted가 아니다: ' + view.backColor);
    assert.equal(view.backBorder, '1px');
    assert.equal(view.inside, true);
    await page.locator('.ar-headline').screenshot({ path: path.join(artifactDir, 'archive-folder-view-stats.png') });
    await page.locator('.ar-back-folders').screenshot({ path: path.join(artifactDir, 'archive-folder-view-back.png') });
    await page.hover('.ar-back-folders');
    await page.locator('.ar-headline').screenshot({ path: path.join(artifactDir, 'archive-folder-view-back-hover.png') });

    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('https://archive-folders.test/view.html', { waitUntil: 'load' });
    const narrow = await page.evaluate(() => {
      const headline = document.querySelector('.ar-headline');
      const back = document.querySelector('.ar-back-folders');
      const copy = document.querySelector('.ar-headline-copy');
      const backBox = back.getBoundingClientRect();
      const copyBox = copy.getBoundingClientRect();
      const headlineBox = headline.getBoundingClientRect();
      return {
        backText: back.textContent.trim(),
        noToggle: !document.querySelector('.ar-stats-toggle'),
        inside: backBox.left >= headlineBox.left - 1 && backBox.right <= headlineBox.right + 1,
        aboveCopy: backBox.bottom <= copyBox.top + 4
      };
    });
    assert.equal(narrow.backText, '← 목록으로');
    assert.equal(narrow.noToggle, true, '좁은 화면에서 통계 접기가 남아 있다');
    assert.equal(narrow.inside, true, '좁은 화면에서 목록으로가 헤드라인 밖으로 나간다');
    assert.equal(narrow.aboveCopy, true, '좁은 화면에서 목록으로가 제목을 가린다');
    await page.locator('.ar-headline').screenshot({ path: path.join(artifactDir, 'archive-folder-view-narrow.png') });
    console.log('PASS: 아카이브 폴더 홈 실루엣·케미 필터');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
