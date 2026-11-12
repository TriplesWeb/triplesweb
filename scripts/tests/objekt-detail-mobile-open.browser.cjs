const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {chromium, devices} = require('playwright');

const root = path.resolve(__dirname, '../..');
const staticRoot = path.join(root, 'src/main/resources/static');
const tinyMp4 = fs.readFileSync(path.join(__dirname, 'fixtures/tiny-motion.mp4'));

const html = `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="/css/site.css">
<link rel="stylesheet" href="/css/site-mobile.css">
<link rel="stylesheet" href="/css/objekt-page.css">
<link rel="stylesheet" href="/css/objekt-grid-cols.css">
<link rel="stylesheet" href="/css/objekt-modal.css">
<style>
body{margin:0;background:#0d0e12;color:#e3e2e8}
.hidden{display:none}
</style>
</head>
<body class="site-page min-h-screen flex flex-col">
<header class="site-header sticky top-0 z-50" id="top-header" style="height:64px;background:#16181f">헤더</header>
<main class="main-content">
<section class="card-box relative z-30" style="backdrop-filter:blur(12px);padding:1rem">
<form id="objekt-search-form" action="/cosmo/objekt" method="get">
<input type="hidden" name="page" value="0">
<input type="hidden" name="group" id="objekt-member-group" value="all">
<div id="member-dropdown">
  <button type="button" id="objekt-member-toggle">멤버 <span id="member-label">전체</span></button>
  <div id="objekt-member-picker"></div>
  <p id="objekt-other-notice" hidden>기타</p>
</div>
<div class="objekt-filter-bar">
  <div class="objekt-filter" id="season-dropdown">
    <span id="season-label">전체</span>
    <div id="season-menu" class="objekt-dropdown-menu hidden" hidden></div>
  </div>
  <div class="objekt-filter" id="class-dropdown">
    <span id="class-label">전체</span>
    <div id="class-menu" class="objekt-dropdown-menu hidden" hidden></div>
  </div>
  <div class="objekt-filter" id="kind-dropdown">
    <input class="objekt-kind-select" name="kind" value="전체">
    <button type="button" id="objekt-kind-toggle"></button>
    <div id="kind-menu" class="objekt-dropdown-menu hidden" hidden>
      <button type="button" class="objekt-kind-option" data-value="all">전체</button>
    </div>
  </div>
  <div class="objekt-filter relative" id="sort-dropdown">
    <button type="button" id="objekt-sort-toggle" class="objekt-filter-control"><span id="sort-label">최신순</span></button>
    <label id="sort-native-control"><select name="sort" class="objekt-sort-select"><option value="latest">최신순</option></select></label>
    <div id="sort-menu" class="objekt-dropdown-menu hidden"></div>
  </div>
  <div class="objekt-search-row">
    <div class="objekt-cols-control" id="objekt-cols-dropdown" data-objekt-cols-filter>
      <button type="button" class="objekt-cols-toggle" data-objekt-cols-toggle aria-label="열 수">열</button>
    </div>
    <div class="objekt-search site-search">
      <span class="objekt-search-glyph" aria-hidden="true">🔍</span>
      <label><input type="search" name="search" class="objekt-search-input site-search__input"></label>
    </div>
    <button type="submit" id="objekt-search-submit" class="objekt-search-button">검색</button>
    <button type="button" id="objekt-reset" class="objekt-reset-button">초기화</button>
  </div>
</div>
</form>
</section>
<p id="objekt-result-summary" data-result-count="2" data-all-count="2" data-loaded="2">검색 결과 <strong>2</strong>개 <span id="objekt-all-count">2</span> <span id="objekt-loaded-count">2</span></p>
<div id="objekt-empty" class="hidden"></div>
<div id="objekt-card-grid" class="objekt-card-grid" data-objekt-grid data-cols="2">
  <article class="objekt-card objekt-item" data-objekt-key="special-card">
    <button type="button" class="objekt-open-detail" aria-label="일반 상세 보기">
      <img class="objekt-image" alt="" src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==">
    </button>
    <a class="objekt-detail-link" href="/cosmo/objekt/detail/special-card">일반 카드</a>
  </article>
  <article class="objekt-card objekt-item" data-objekt-key="cream02-kaede-504z">
    <button type="button" class="objekt-open-detail" aria-label="Motion 상세 보기">
      <img class="objekt-image" alt="" src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==">
    </button>
    <a class="objekt-detail-link" href="/cosmo/objekt/detail/cream02-kaede-504z">Kaede 504Z</a>
  </article>
</div>
<div id="objekt-scroll-sentinel" data-has-more="false" data-next-page="1">
  <p id="objekt-scroll-status"></p>
  <div id="objekt-scroll-spinner" class="hidden"></div>
</div>
</main>
<div id="objektDetailModal" class="objekt-detail-modal" hidden aria-hidden="true">
  <div class="objekt-detail-panel" role="dialog" aria-modal="true" aria-labelledby="objektDetailTitle" tabindex="-1">
    <button type="button" class="objekt-detail-close" id="objektDetailClose" aria-label="닫기">&times;</button>
    <figure class="objekt-detail-media">
      <div class="objekt-detail-media-stage">
        <img id="objektDetailImage" src="" alt="" data-front="" data-back="" data-motion="" data-showing-back="false">
        <video id="objektDetailVideo" hidden muted loop playsinline webkit-playsinline preload="none"></video>
        <button type="button" class="objekt-detail-media-btn" id="objektDetailFlip" aria-label="오브젝트 이미지">
          <span class="objekt-detail-face" id="objektDetailFace" hidden>앞면</span>
        </button>
      </div>
    </figure>
    <div class="objekt-detail-body">
      <h2 class="objekt-detail-title" id="objektDetailTitle">—</h2>
      <dl class="objekt-detail-meta">
        <div><dt>멤버</dt><dd><a id="objektDetailMember">—</a></dd></div>
        <div><dt>시즌</dt><dd id="objektDetailSeason">—</dd></div>
        <div><dt>클래스</dt><dd id="objektDetailClass">—</dd></div>
        <div><dt>번호</dt><dd id="objektDetailNo">—</dd></div>
        <div><dt>발행량</dt><dd id="objektIssuedCount">확인 중</dd></div>
      </dl>
      <p class="objekt-detail-desc is-empty" id="objektDetailDesc">불러오는 중…</p>
      <p id="objektFavoriteStatus" hidden></p>
      <div class="objekt-detail-actions">
        <button type="button" id="objektDetailFavorite">♡ 찜하기</button>
        <a id="objektDetailMemberLink" href="/cosmo/objekt">멤버</a>
        <a id="objektDetailTradeLink" href="/article/trade/new">교환</a>
        <a id="objektDetailSellLink" href="/article/sell/new">거래</a>
        <a id="objektDetailFullLink" href="/cosmo/objekt/detail/x">전체</a>
      </div>
    </div>
  </div>
</div>
<script src="/js/about-modal.js"></script>
<script src="/js/objekt-grid-cols.js"></script>
<script src="/js/objekt-page.js"></script>
</body>
</html>`;

const details = {
  'special-card': {
    objektKey: 'special-card',
    collectionId: 'Cream02 HaYeon 367Z',
    member: 'HaYeon',
    season: 'Cream02',
    class: 'Special',
    collectionNo: '367Z',
    frontImage: 'https://example.com/front.webp',
    thumbnailImage: 'https://example.com/front.webp',
    backImage: '',
    hasBack: false,
    description: '일반',
    isFavorite: false,
    isAdmin: false
  },
  'cream02-kaede-504z': {
    objektKey: 'cream02-kaede-504z',
    collectionId: 'Cream02 Kaede 504Z',
    member: 'Kaede',
    season: 'Cream02',
    class: 'Motion',
    collectionNo: '504Z',
    slug: 'cream02-kaede-504z',
    artist: 'triples',
    frontImage: 'https://example.com/kaede.webp',
    thumbnailImage: 'https://example.com/kaede.webp',
    backImage: '',
    hasBack: false,
    frontMedia: 'https://cdn.apollo.cafe/mco/triples/cream02-kaede-504z.mp4',
    description: '모션',
    isFavorite: false,
    isAdmin: false
  }
};

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),
    args: ['--no-sandbox']
  });
  try {
    const page = await browser.newPage({
      ...devices['Pixel 7'],
      viewport: {width: 390, height: 844},
      deviceScaleFactor: 2,
      hasTouch: true,
      isMobile: true
    });
    await page.route('**/*', async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname === '/' || url.pathname === '/cosmo/objekt') {
        return route.fulfill({contentType: 'text/html', body: html});
      }
      if (url.pathname.startsWith('/cosmo/objekt/detail/') && (route.request().headers().accept || '').includes('application/json')) {
        const key = decodeURIComponent(url.pathname.split('/').pop());
        const body = details[key];
        if (!body) return route.fulfill({status: 404, body: '{}'});
        return route.fulfill({contentType: 'application/json', body: JSON.stringify(body)});
      }
      const file = path.resolve(staticRoot, '.' + url.pathname);
      if (file.startsWith(staticRoot + path.sep) && fs.existsSync(file) && fs.statSync(file).isFile()) {
        return route.fulfill({
          path: file,
          contentType: url.pathname.endsWith('.js') ? 'application/javascript' : url.pathname.endsWith('.css') ? 'text/css' : undefined
        });
      }
      if (url.pathname.endsWith('.webp')) {
        return route.fulfill({status: 200, body: Buffer.from(''), contentType: 'image/webp'});
      }
      if (url.pathname.endsWith('.mp4')) {
        return route.fulfill({status: 200, body: tinyMp4, contentType: 'video/mp4'});
      }
      return route.fulfill({status: 404, body: ''});
    });

    const errors = [];
    page.on('pageerror', (error) => errors.push(String(error)));
    await page.emulateMedia({reducedMotion: 'no-preference'});
    await page.goto('https://mobile.test/cosmo/objekt', {waitUntil: 'load'});
    assert.deepEqual(errors, [], '페이지 스크립트 오류: ' + errors.join('; '));

    const searchBtn = page.locator('#objekt-search-submit');
    const searchWidth = await searchBtn.evaluate((el) => el.getBoundingClientRect().width);
    assert.ok(searchWidth >= 80, '모바일 검색 버튼 너비: ' + searchWidth);

    await page.locator('[data-objekt-key="special-card"] .objekt-open-detail').tap();
    await page.locator('#objektDetailTitle').filter({hasText: 'Cream02 HaYeon 367Z'}).waitFor();
    assert.equal(await page.locator('#objektDetailModal').isVisible(), true);

    const layer = await page.evaluate(() => {
      const modal = document.getElementById('objektDetailModal');
      const filter = document.querySelector('.card-box');
      const header = document.getElementById('top-header');
      const z = (el) => Number.parseInt(getComputedStyle(el).zIndex, 10) || 0;
      const flip = document.getElementById('objektDetailFlip');
      const video = document.getElementById('objektDetailVideo');
      return {
        modalZ: z(modal),
        filterZ: z(filter),
        headerZ: z(header),
        open: modal.classList.contains('is-open'),
        hidden: modal.hidden,
        flipDisabled: flip.disabled,
        flippable: flip.classList.contains('is-flippable'),
        videoInsideButton: Boolean(flip.contains(video)),
        faceHidden: document.getElementById('objektDetailFace').hidden
      };
    });
    assert.equal(layer.open, true);
    assert.equal(layer.hidden, false);
    assert.ok(layer.modalZ > layer.filterZ, `모달 z ${layer.modalZ} > 필터 ${layer.filterZ}`);
    assert.ok(layer.modalZ > layer.headerZ, `모달 z ${layer.modalZ} > 헤더 ${layer.headerZ}`);
    assert.equal(layer.flipDisabled, true);
    assert.equal(layer.flippable, false);
    assert.equal(layer.videoInsideButton, false);
    assert.equal(layer.faceHidden, true);

    await page.evaluate(() => {
      const modal = document.getElementById('objektDetailModal');
      const card = document.querySelector('[data-objekt-key="special-card"] .objekt-open-detail');
      const box = card.getBoundingClientRect();
      modal.dispatchEvent(new MouseEvent('click', {
        bubbles: true,
        clientX: box.left + box.width / 2,
        clientY: box.top + box.height / 2
      }));
    });
    assert.equal(await page.locator('#objektDetailModal').isVisible(), true);

    await page.waitForTimeout(550);
    await page.locator('#objektDetailClose').click();
    await page.waitForFunction(() => {
      const modal = document.getElementById('objektDetailModal');
      return modal.hidden || !modal.classList.contains('is-open');
    });

    await page.locator('[data-objekt-key="cream02-kaede-504z"] .objekt-open-detail').tap();
    await page.locator('#objektDetailTitle').filter({hasText: 'Cream02 Kaede 504Z'}).waitFor();
    await page.waitForFunction(() => {
      const video = document.getElementById('objektDetailVideo');
      return video && video.dataset.active === 'true' && (video.getAttribute('src') || video.currentSrc);
    });
    const motion = await page.evaluate(() => ({
      open: document.getElementById('objektDetailModal').classList.contains('is-open'),
      videoSrc: document.getElementById('objektDetailVideo').getAttribute('src') || document.getElementById('objektDetailVideo').currentSrc,
      active: document.getElementById('objektDetailVideo').dataset.active,
      flipDisabled: document.getElementById('objektDetailFlip').disabled
    }));
    assert.equal(motion.open, true);
    assert.match(motion.videoSrc, /cream02-kaede-504z\.mp4/);
    assert.equal(motion.active, 'true');
    assert.equal(motion.flipDisabled, true);

    fs.mkdirSync('/tmp/objekt-ui', {recursive: true});
    fs.mkdirSync('/opt/cursor/artifacts', {recursive: true});
    await page.screenshot({path: '/tmp/objekt-ui/objekt-detail-mobile-open.png'});
    await page.screenshot({path: '/opt/cursor/artifacts/objekt-detail-mobile-motion.png'});
    console.log('PASS: 모바일 탭으로 상세 모달이 열리고 바로 닫히지 않으며 Motion 영상을 건다');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
