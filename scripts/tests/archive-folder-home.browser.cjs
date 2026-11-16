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
</style></head>
<body class="site-page">
<main class="archive-page is-folder-home" data-archive-kind="image">
  <section class="ar-hero">
    <div class="ar-panel ar-headline">
      <div class="ar-headline-copy">
        <div class="ar-kicker">Archive</div>
        <h1>아카이브</h1>
        <p>폴더를 선택하세요</p>
      </div>
    </div>
  </section>
  <section class="ar-folders" aria-label="아카이브 폴더">
    ${folder({ extraClass: ' is-group', href: '/archive?folder=s', color: '#f5c518', label: '전체 멤버' })}
    ${folder({ href: '/archive?folder=unit-17', color: '#22aeff', label: 'Aura' })}
    ${folder({ extraClass: ' is-solo', href: '/archive?folder=s1', color: '#22aeff', label: 'S1 윤서연' })}
    ${folder({ art: true, href: '/archive?folder=combo-2', color: '#7dd3fc', label: '2명 케미', count: '1' })}
    ${folder({ art: true, href: '/archive?folder=combo-3', color: '#a5b4fc', label: '3명 케미', count: '1' })}
    ${folder({ art: true, href: '/archive?folder=combo-4', color: '#c4b5fd', label: '4명 케미', count: '1' })}
    ${folder({ art: true, href: '/archive?folder=combo-5', color: '#86efac', label: '5명 이상', count: '1' })}
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
<body class="site-page">
<main class="archive-page is-folder-view" data-archive-kind="image">
  <section class="ar-hero">
    <div class="ar-panel ar-headline is-member-space" style="--folder-color:#7dd3fc">
      <a class="site-btn site-btn--reset ar-back-folders" href="/archive">←목록으로</a>
      <div class="ar-headline-copy">
        <div class="ar-kicker">Archive</div>
        <h1>아카이브</h1>
        <p class="ar-folder-title">시선강탈</p>
      </div>
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
        <label class="ar-chem">
          <span class="ar-sort-label">케미</span>
          <span class="ar-sort-control ar-chem-control">
            <select id="archiveChem" name="chem" aria-label="케미씃트리 케미">
              <option value="">케미 전체</option>
              <option value="pair-3" selected>시선강탈 · 윤서연 × 정혜린</option>
              <option value="pair-4">하트시그널 · 김채연 × 김유연</option>
            </select>
            <span class="material-symbols-outlined" aria-hidden="true">arrow_drop_down</span>
          </span>
        </label>
        <div class="ar-search-row">
          <label class="ar-search-wrap">
            <span class="material-symbols-outlined" aria-hidden="true">search</span>
            <input class="ar-search" type="search" name="q" placeholder="사진 제목 검색">
          </label>
          <button type="submit" class="ar-submit">검색</button>
        </div>
      </div>
    </form>
  </section>
  <section class="ar-grid" id="archiveGrid"></section>
</main>
<script>${browseJs}</script>
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
      if (url.pathname.endsWith('combo.html')) {
        return route.fulfill({ contentType: 'text/html', body: comboHtml });
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
      const aura = [...document.querySelectorAll('.ar-folder')].find((el) => el.textContent.includes('Aura'));
      const group = document.querySelector('.ar-folder.is-group');
      const member = document.querySelector('.ar-folder.is-solo');
      const artIcon = document.querySelector('.ar-folder.has-art .ar-folder-icon');
      const artImg = document.querySelector('.ar-folder-art');
      const memberIcon = member.querySelector('.ar-folder-icon');
      const groupIcon = group.querySelector('.ar-folder-icon');
      const shine = getComputedStyle(groupIcon, '::after');
      return {
        labels,
        auraPreview: Boolean(aura?.querySelector('.ar-folder-preview, .ar-folder-art')),
        placeholder: [...document.querySelectorAll('img')].some((img) => (img.getAttribute('src') || '').includes('image-upload-pending')),
        member: memberIcon.getBoundingClientRect(),
        art: artIcon.getBoundingClientRect(),
        artImg: artImg.getBoundingClientRect(),
        overflow: getComputedStyle(groupIcon).overflow,
        shineMask: shine.webkitMaskImage || shine.maskImage || '',
        shineBlend: shine.mixBlendMode
      };
    });
    assert.deepEqual(home.labels, ['전체 멤버', 'Aura', 'S1 윤서연', '2명 케미', '3명 케미', '4명 케미', '5명 이상']);
    assert.equal(home.auraPreview, false, '유닛 이미지가 없는데 미리보기가 있다');
    assert.equal(home.placeholder, false, '업로드 예정 플레이스홀더가 남아 있다');
    assert.ok(Math.abs(home.member.width - home.art.width) <= 1, '케미 폴더 그림 너비가 멤버 폴더와 다르다: ' + JSON.stringify(home));
    assert.ok(Math.abs(home.member.height - home.art.height) <= 1, '케미 폴더 그림 높이가 멤버 폴더와 다르다: ' + JSON.stringify(home));
    assert.ok(Math.abs(home.member.width - home.artImg.width) <= 1, '케미 사진이 폴더 아이콘 너비를 못 채운다: ' + JSON.stringify(home));
    assert.ok(Math.abs(home.member.height - home.artImg.height) <= 1, '케미 사진이 폴더 아이콘 높이를 못 채운다: ' + JSON.stringify(home));
    assert.equal(home.overflow, 'hidden', '호버 효과가 폴더 밖으로 새지 않게 잘리지 않는다');
    assert.match(home.shineMask, /archive-folder\.svg/, '호버 샤인이 폴더 실루엣으로 잘리지 않는다: ' + home.shineMask);

    await page.hover('.ar-folder.is-group');
    const artifactDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(artifactDir, { recursive: true });
    await page.screenshot({ path: path.join(artifactDir, 'archive-folder-home.png'), fullPage: true });

    await page.goto('https://archive-folders.test/combo.html', { waitUntil: 'load' });
    const combo = await page.evaluate(() => {
      const select = document.getElementById('archiveChem');
      const trigger = document.querySelector('.ar-chem .ar-sort-trigger');
      return {
        title: document.querySelector('.ar-folder-title')?.textContent.trim() || '',
        values: [...(select?.options || [])].map((option) => option.value),
        labels: [...(select?.options || [])].map((option) => option.textContent.trim()),
        selected: select?.value || '',
        enhanced: Boolean(trigger)
      };
    });
    assert.equal(combo.title, '시선강탈');
    assert.deepEqual(combo.values, ['', 'pair-3', 'pair-4']);
    assert.ok(combo.labels[1].includes('시선강탈'));
    assert.equal(combo.selected, 'pair-3');
    assert.equal(combo.enhanced, true, '케미 드롭다운이 정렬과 같은 스타일로 열리지 않는다');
    await page.click('.ar-chem .ar-sort-trigger');
    await page.screenshot({ path: path.join(artifactDir, 'archive-chem-filter.png'), fullPage: true });
    console.log('PASS: 아카이브 폴더 홈 실루엣·케미 필터');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
