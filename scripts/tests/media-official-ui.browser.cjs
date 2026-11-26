const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const staticRoot = path.resolve(__dirname, '../../src/main/resources/static');
const read = (rel) => fs.readFileSync(path.join(staticRoot, rel), 'utf8');
const kotoneLogo = '/images/archive/member-logos/11.png';
const kotoneLogoFile = path.join(staticRoot, 'images/archive/member-logos/11.png');
assert.ok(fs.existsSync(kotoneLogoFile), '코토네 주사위 로고 파일이 없다');

const videos = Array.from({ length: 13 }, (_, index) => ({
  id: 20 - index,
  youtubeId: 'vid' + String(index).padStart(3, '0'),
  tab: index % 2 ? 'mv' : 'signal',
  tabLabel: index % 2 ? 'MV' : 'SIGNAL',
  title: '영상 ' + index,
  duration: '3:1' + String(index % 10),
  views: index * 10,
  viewCountFmt: String(index * 10),
  publishedOn: '2024-01-' + String((index % 27) + 1).padStart(2, '0'),
  publishedLabel: '2024.01',
  artistNames: '',
  memberBadges: []
}));
videos[0].artistNames = '김유연,김나경';
videos[0].memberBadges = [
  { name: '김유연', artistId: 5, color: '#ffde00', image: '/portrait-a.png', portrait: '/portrait-a.png' },
  { name: '김나경', artistId: 7, color: '#7bba8d', image: '/portrait-b.png', portrait: '/portrait-b.png' }
];

const html = `<!doctype html><html lang="ko"><head><meta charset="utf-8">
<style>
${read('css/site-ui.css')}
${read('css/archive-page.css')}
${read('css/date-calendar.css')}
${read('css/member-portrait-picker.css')}
${read('css/media-page.css')}
</style></head>
<body class="site-page media-page site-admin-enabled">
<main class="media-shell">
  <header class="media-intro">
    <div class="media-intro__actions">
      <a class="site-btn site-btn--secondary media-channel" href="https://www.youtube.com/@triplescosmos" aria-label="YouTube 채널"><svg viewBox="0 0 24 24" width="22" height="22"><rect x="1.6" y="5.2" width="20.8" height="13.6" rx="4.2" fill="#ff0033"></rect><path d="M10 9.15v5.7l5.4-2.85L10 9.15z" fill="#fff"></path></svg></a>
    </div>
  </header>
  <section class="media-filters">
    <form class="ar-filters" id="mediaFilterForm" method="get" action="/media">
      <div class="ar-sort">
        <span class="ar-sort-control">
          <select id="mediaSort" name="sort" aria-label="정렬">
            <option value="latest">최신순</option>
            <option value="registered">등록순</option>
          </select>
        </span>
      </div>
      <div class="ar-date-filter" data-date-filter>
        <button type="button" class="ar-date-toggle" data-date-toggle aria-expanded="false">전체 기간</button>
        <div class="ar-date-popover" data-date-popover hidden>
          <div class="ar-date-fields">
            <label class="ar-date-row">
              <span class="ar-date-kicker">시작</span>
              <span class="ar-date-control"><input type="date" name="from" value="" data-date-from data-ar-cal lang="ko"></span>
            </label>
          </div>
        </div>
      </div>
    </form>
  </section>
  <section class="media-feature" aria-label="재생목록">
    <div class="media-feature__head">
      <h2>재생목록</h2>
      <span>FEATURED</span>
    </div>
    <div class="media-feature__layout">
      <a class="media-feature__hero is-empty" href="/media">
        <img class="media-feature__still" alt="" data-yt-still="" data-no-placeholder="1">
        <span class="media-feature__shade"></span>
        <img class="media-feature__play" src="/images/media/play-featured.svg" width="60" height="60" alt="">
        <span class="media-feature__caption"><strong id="mediaFeatureTitle"></strong></span>
      </a>
      <aside class="media-playlist" id="mediaPlaylist">
        <div class="media-playlist__head">
          <h3 id="mediaPlaylistTitle">재생목록</h3>
          <button type="button" class="media-playlist-add media-playlist-add--head" data-playlist-add>재생목록 추가</button>
          <span id="mediaPlaylistCount">00</span>
        </div>
        <div class="media-playlist__list" id="mediaPlaylistList"></div>
      </aside>
    </div>
  </section>
  <section class="media-archive">
    <div class="media-archive__head">
      <h2>유튜브 아카이브</h2>
      <div class="media-cols" data-media-cols-filter>
        <button type="button" class="media-cols-toggle" data-media-cols-toggle aria-label="한 줄에 볼 영상 수" aria-expanded="false" aria-haspopup="listbox" aria-controls="mediaArchiveColsMenu">
          <span class="media-cols-icon" aria-hidden="true"><span></span><span></span><span></span></span>
          <span class="media-sr" data-media-cols-label>3줄</span>
        </button>
        <div id="mediaArchiveColsMenu" class="media-cols-menu" data-media-cols-menu hidden role="listbox" aria-label="한 줄에 볼 영상 수">
          <button type="button" class="media-cols-option" data-media-cols-option="3" role="option">3줄</button>
          <button type="button" class="media-cols-option" data-media-cols-option="4" role="option">4줄</button>
          <button type="button" class="media-cols-option" data-media-cols-option="5" role="option">5줄</button>
        </div>
      </div>
    </div>
    <div class="media-grid" data-cols="3">
      <article class="media-card" data-symbol-fixture="1">
        <a class="media-card__link" href="/media/mv/vid000">
          <span class="media-card__thumb">
            <img src="https://img.youtube.com/vi/vid000/hqdefault.jpg" alt="">
            <span class="media-card__time">3:11</span>
          </span>
          <span class="media-card__body">
            <small class="media-card__kind">MV</small>
            <span class="media-card__title">코토네 영상</span>
          </span>
        </a>
        <div class="media-card__members">
          <span class="archive-member-chip archive-symbol-chip" style="--member-color:#ffde00;" title="코토네" aria-label="코토네">
            <img src="${kotoneLogo}" alt="" referrerpolicy="no-referrer">
            <span class="archive-chip-name">코토네</span>
          </span>
        </div>
        <div class="media-card__foot">
          <span class="media-card__meta"><span class="media-card__views">조회수 10</span></span>
        </div>
      </article>
    </div>
  </section>
  <section class="media-shorts is-preview">
    <article class="media-short">
      <a class="media-short__open" href="/media/shorts/qdQ6RN_aOO4" data-tab="shorts" data-kind="" data-title="'ANDLESS' in Chicago" data-duration="0:57">
        <span class="media-short__caption">
          <strong>'ANDLESS' in Chicago</strong>
          <span class="media-short__meta">
            <span aria-label="재생시간 0:57">0:57</span>
            <time datetime="2026-11-22" aria-label="유튜브 등록일 2026.11.22">2026.11.22</time>
          </span>
        </span>
      </a>
    </article>
  </section>
</main>
<div class="site-modal" id="mediaPickModal" tabindex="-1" aria-hidden="true">
  <div class="site-modal-dialog media-pick-dialog">
    <div class="site-modal-panel">
      <div class="site-modal-body">
        <div class="ar-toolbar media-toolbar media-pick-toolbar">
          <form class="ar-filter-tools media-filter-tools media-pick-mini" id="mediaPickMini">
            <div class="objekt-filter ar-sort">
              <span class="objekt-filter-label">정렬</span>
              <span class="ar-sort-control">
                <select id="mediaPickSort" aria-label="정렬">
                  <option value="latest">최신순</option>
                  <option value="registered">등록순</option>
                </select>
              </span>
            </div>
            <div class="objekt-filter ar-date-filter">
              <span class="objekt-filter-label">날짜</span>
              <button type="button" class="ar-date-toggle objekt-filter-control">전체</button>
            </div>
            <div class="objekt-filter ar-member-filter">
              <span class="objekt-filter-label">멤버</span>
              <button type="button" class="ar-member-toggle objekt-filter-control" id="mediaPickMemberToggle" aria-controls="mediaPickMemberModal">멤버 선택</button>
            </div>
            <label class="ar-search-wrap">
              <input class="ar-search" id="mediaPickSearch" type="search" aria-label="재생목록 영상 검색">
            </label>
            <div class="objekt-filter ar-sort">
              <span class="objekt-filter-label">분류</span>
              <span class="ar-sort-control">
                <select id="mediaPickTab" aria-label="분류"><option value="">전체</option><option value="mv">MV</option></select>
              </span>
            </div>
          </form>
        </div>
        <div class="media-pick-grid" id="mediaPickGrid"></div>
        <div id="mediaPickPager"></div>
      </div>
      <div class="site-modal-footer">
        <button type="button" class="site-btn site-btn--primary" id="mediaPickConfirm">선택 완료</button>
      </div>
    </div>
  </div>
</div>
<div class="site-modal" id="mediaPickMemberModal" tabindex="-1" aria-hidden="true">
  <div class="site-modal-dialog media-pick-member-dialog">
    <div class="site-modal-panel">
      <div class="site-modal-header"><h5 class="site-modal-title">멤버 선택</h5>
        <button type="button" data-site-dismiss="modal">닫기</button></div>
      <div class="site-modal-body"><div class="member-portrait-picker" id="media-pick-picker">
        <button type="button" class="mpp-item" data-value="김유연" data-name="김유연">김유연</button>
        <button type="button" class="mpp-item" data-value="김나경" data-name="김나경">김나경</button>
      </div></div>
    </div>
  </div>
</div>
<div class="site-modal" id="ytModal" tabindex="-1" aria-hidden="true">
  <div class="site-modal-dialog media-player-dialog">
    <div class="site-modal-panel">
      <div class="site-modal-body media-player-body">
        <div class="media-player-stage"><iframe id="ytPlayer" src="" title="player"></iframe></div>
        <aside class="media-player-detail">
          <p id="ytModalKind"></p>
          <h6 id="ytModalHeading"></h6>
          <p id="ytModalMeta"></p>
          <section class="ar-detail-cast" id="ytModalCast" hidden>
            <p class="ar-detail-kicker">출연 멤버</p>
            <div class="ar-unit-badge" id="ytModalUnit" hidden></div>
            <div class="ar-detail-members" id="ytModalMembers"></div>
          </section>
          <div id="ytModalDesc"></div>
        </aside>
      </div>
      <div class="site-modal-footer ar-detail-actions media-player-actions">
        <form id="ytModalDelete" method="post" action="/media_delete" data-admin-fetch data-confirm="삭제 하시겠습니까?" data-confirm-accept="삭제" data-confirm-compact>
          <input type="hidden" name="youtubeId" id="ytDeleteId" value="">
          <button type="submit">삭제</button>
        </form>
      </div>
    </div>
  </div>
</div>
<p id="ytModalTitle" hidden></p>
<script type="application/json" id="mediaCatalog">${JSON.stringify(videos)}</script>
<script type="application/json" id="mediaPickCatalog">${JSON.stringify(videos)}</script>
<script type="application/json" id="memberUnitRoster">${JSON.stringify([{ name: 'LOVElution', unitId: 7, members: ['김유연', '김나경'] }])}</script>
<script>${read('js/about-admin-auth.js')}</script>
<script>${read('js/date-range-filter.js')}</script>
<script>${read('js/site-ui.js')}</script>
<script>${read('js/image-fallback.js')}</script>
<script>${read('js/site-feedback.js')}</script>
<script>${read('js/site-admin-controls.js')}</script>
<script>${read('js/member-selection-summary.js')}</script>
<script>${read('js/site-video-volume.js')}</script>
<script>${read('js/media-page.js')}</script>
</body></html>`;

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
    await page.route('https://media.test/**', (route) => {
      const url = route.request().url();
      if (url.endsWith('/media_delete')) {
        return route.fulfill({ status: 200, body: 'ok' });
      }
      if (url.split('?')[0].endsWith('/media/pick-catalog.json')) {
        return route.fulfill({ contentType: 'application/json', body: JSON.stringify(videos) });
      }
      if (url.includes('/api/media/youtube/search-status')) {
        return route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({ ready: true, running: false })
        });
      }
      if (url.includes('/api/artist/admin/status')) {
        return route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({ configured: true, admin: true })
        });
      }
      if (url.includes('/images/archive/member-logos/')) {
        const file = path.join(staticRoot, 'images/archive/member-logos', path.basename(new URL(url).pathname));
        if (file.startsWith(path.join(staticRoot, 'images/archive/member-logos')) && fs.existsSync(file)) {
          return route.fulfill({ path: file, contentType: 'image/png' });
        }
      }
      return route.fulfill({ contentType: 'text/html', body: html });
    });
    await page.route(/i\.ytimg\.com|img\.youtube\.com|portrait-|\.svg$/, (route) => {
      route.fulfill({
        contentType: 'image/png',
        body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64')
      });
    });
    await page.addInitScript(() => {
      localStorage.removeItem('triples-media-playlist');
      localStorage.removeItem('triples-media-archive-cols');
    });
    await page.goto('https://media.test/media');

    assert.equal(await page.locator('text=채널 동기화').count(), 0);
    assert.equal(await page.locator('#mediaSort option[value=registered]').textContent(), '등록순');

    assert.equal(await page.locator('.media-feature__head h2').textContent(), '재생목록');
    await page.locator('.media-feature__head').screenshot({ path: '/opt/cursor/artifacts/media-feature-playlist-heading.png' });
    const hero = page.locator('.media-feature__hero');
    assert.equal(await hero.getAttribute('class'), 'media-feature__hero is-empty');
    const still = page.locator('.media-feature__still');
    assert.equal(await still.getAttribute('data-no-placeholder'), '1');
    assert.equal(await still.getAttribute('src'), null);
    assert.equal(await still.evaluate((img) => img.src.includes('image-upload-pending')), false);
    assert.equal(await page.locator('.media-channel').getAttribute('aria-label'), 'YouTube 채널');
    assert.equal(await page.locator('.media-cols-icon span').count(), 3);
    assert.equal(await page.locator('[data-media-cols-option]').count(), 3);
    assert.deepEqual(await page.locator('[data-media-cols-option]').allTextContents(), ['3줄', '4줄', '5줄']);
    assert.equal(await page.locator('.media-grid').getAttribute('data-cols'), '3');
    const members = page.locator('.media-card__members');
    const chip = members.locator('.archive-member-chip').first();
    await chip.locator('img').waitFor();
    await page.waitForFunction(() => {
      const img = document.querySelector('.media-card__members .archive-symbol-chip img');
      return img && img.complete && img.naturalWidth > 1;
    });
    assert.equal(await chip.getAttribute('aria-label'), '코토네');
    assert.equal(await chip.evaluate((node) => node.classList.contains('archive-symbol-chip')), true);
    assert.equal(await chip.locator('img').getAttribute('src'), kotoneLogo);
    const chipLook = await chip.evaluate((node) => {
      const img = node.querySelector('img');
      const style = getComputedStyle(img);
      return {
        radius: style.borderRadius,
        objectFit: style.objectFit,
        blend: style.mixBlendMode
      };
    });
    assert.equal(chipLook.objectFit, 'contain');
    assert.equal(chipLook.blend, 'screen');
    assert.ok(
      chipLook.radius !== '50%' && !chipLook.radius.includes('999'),
      'symbol chip should not be a circular portrait'
    );
    fs.mkdirSync('/opt/cursor/artifacts', { recursive: true });
    await members.screenshot({ path: '/opt/cursor/artifacts/media-card-members-symbol-chip.png' });
    await page.locator('.media-card[data-symbol-fixture]').screenshot({ path: '/opt/cursor/artifacts/media-card-kotone-symbol.png' });
    await page.locator('.media-channel').screenshot({ path: '/opt/cursor/artifacts/media-youtube-icon-button.png' });
    await page.locator('.media-archive__head').screenshot({ path: '/opt/cursor/artifacts/media-archive-density-closed.png' });
    await page.locator('[data-media-cols-toggle]').click();
    await page.locator('[data-media-cols-menu]:not([hidden])').waitFor();
    const density = await page.locator('[data-media-cols-filter]').boundingBox();
    assert.ok(density, 'density control should be visible');
    await page.screenshot({
      path: '/opt/cursor/artifacts/media-archive-cols-open-clip.png',
      clip: {
        x: Math.max(0, density.x - 90),
        y: Math.max(0, density.y - 12),
        width: 200,
        height: 180
      }
    });
    await page.locator('[data-media-cols-menu]').screenshot({ path: '/opt/cursor/artifacts/media-archive-cols-menu.png' });
    await page.locator('[data-media-cols-option="4"]').click();
    assert.equal(await page.locator('.media-grid').getAttribute('data-cols'), '4');
    assert.equal(await page.locator('[data-media-cols-label]').textContent(), '4줄');
    assert.equal(await page.locator('[data-media-cols-menu]').evaluate((node) => node.hidden), true);
    await page.locator('#mediaFilterForm .ar-date-toggle').click();
    await page.locator('[data-date-popover]:not([hidden]) .ar-cal').waitFor();
    const dateGaps = await page.evaluate(() => {
      const wrap = document.querySelector('.ar-cal.is-empty');
      const year = wrap.querySelector('[data-part="year"]').getBoundingClientRect();
      const month = wrap.querySelector('[data-part="month"]').getBoundingClientRect();
      const day = wrap.querySelector('[data-part="day"]').getBoundingClientRect();
      const seps = [...wrap.querySelectorAll('.ar-cal-sep')].map((node) => node.getBoundingClientRect());
      return {
        yearToDot: seps[0].left - year.right,
        dotToMonth: month.left - seps[0].right,
        yearWidth: year.width,
        monthWidth: month.width
      };
    });
    assert.ok(Math.abs(dateGaps.yearWidth - dateGaps.monthWidth) < 8, 'empty year should match month width');
    assert.ok(Math.abs(dateGaps.yearToDot - dateGaps.dotToMonth) < 4, '년 and 월 should sit evenly around the first dot');
    await page.locator('[data-date-popover]').screenshot({ path: '/opt/cursor/artifacts/media-date-placeholder-dots.png' });
    await page.keyboard.press('Escape');

    await page.getByRole('button', { name: '재생목록 추가' }).click();
    const mini = page.locator('#mediaPickMini');
    const pager = page.locator('#mediaPickPager .ar-pager');
    await pager.waitFor();
    const boxes = await page.evaluate(() => {
      const filter = document.getElementById('mediaPickMini').getBoundingClientRect();
      const nav = document.querySelector('#mediaPickPager .ar-pager').getBoundingClientRect();
      return {
        miniBottom: filter.bottom,
        pagerTop: nav.top,
        miniCenter: filter.left + filter.width / 2,
        pagerCenter: nav.left + nav.width / 2
      };
    });
    assert.ok(boxes.miniBottom <= boxes.pagerTop + 1, 'pager should sit below the mini row');
    assert.ok(Math.abs(boxes.miniCenter - boxes.pagerCenter) < 48, 'mini row and pager should stay centered');
    assert.equal(await mini.locator('#mediaPickSearch').count(), 1);
    assert.equal(await page.locator('#mediaPickSort option[value=registered]').count(), 1);
    assert.equal(await page.locator('#mediaPickMemberToggle').count(), 1);
    const sizes = await page.evaluate(() => {
      const miniBox = document.getElementById('mediaPickMini').getBoundingClientRect();
      const tab = document.querySelector('#mediaPickMini .ar-sort-control').getBoundingClientRect();
      const pager = document.querySelector('#mediaPickPager .ar-pager');
      const style = getComputedStyle(pager);
      return {
        miniHeight: miniBox.height,
        miniWidth: miniBox.width,
        tabWidth: tab.width,
        pagerTop: pager.getBoundingClientRect().top,
        pagerPadLeft: parseFloat(style.paddingLeft)
      };
    });
    assert.ok(sizes.miniHeight < 80, 'pick mini should stay on one row');
    assert.ok(sizes.tabWidth <= sizes.miniWidth + 1, 'pick tab dropdown should fit inside mini');
    assert.ok(sizes.pagerPadLeft <= 8, 'pick pager left padding should be tight');
    await page.locator('.media-pick-toolbar').screenshot({ path: '/opt/cursor/artifacts/media-pick-mini-one-row.png' });
    await page.locator('#mediaPickMemberToggle').click();
    await page.locator('#mediaPickMemberModal.is-open').waitFor();
    const pagerTopOpen = await page.locator('#mediaPickPager .ar-pager').evaluate((node) => node.getBoundingClientRect().top);
    assert.ok(Math.abs(pagerTopOpen - sizes.pagerTop) < 2, 'pager should not move when member modal opens');
    await page.locator('#mediaPickMemberModal .site-modal-dialog').screenshot({ path: '/opt/cursor/artifacts/media-member-select-modal.png' });
    await page.locator('#mediaPickMemberModal [data-site-dismiss="modal"]').click().catch(() => {});
    assert.equal(await page.locator('.media-short__meta [aria-label="재생시간 0:57"]').textContent(), '0:57');
    assert.equal(await page.locator('.media-short__meta time').getAttribute('datetime'), '2026-11-22');
    assert.equal(await page.locator('.media-short__caption strong').first().textContent(), "'ANDLESS' in Chicago");

    await page.locator('#mediaPickGrid input.media-pick-check').first().check();
    await page.locator('#mediaPickConfirm').click();
    await page.waitForFunction(() => document.querySelectorAll('#mediaPlaylistList .media-playlist__item').length === 1);
    assert.equal(await hero.evaluate((node) => node.classList.contains('is-empty')), false);

    await page.locator('[data-playlist-remove]').click();
    await page.waitForFunction(() => document.querySelectorAll('#mediaPlaylistList .media-playlist__item').length === 0);
    assert.equal(await hero.evaluate((node) => node.classList.contains('is-empty')), true);
    assert.equal(await still.getAttribute('src'), null);

    await page.evaluate(() => {
      const trigger = document.createElement('a');
      trigger.setAttribute('data-ytid', 'vid000');
      trigger.setAttribute('data-site-target', '#ytModal');
      document.body.append(trigger);
      trigger.click();
    });
    await page.locator('#ytModalMembers .unit-member-chip img').first().waitFor();
    assert.equal(await page.locator('#ytModalMembers .unit-member-chip img').count(), 2);
    assert.equal(await page.locator('#ytModalMembers img').first().getAttribute('src'), '/portrait-a.png');
    assert.equal(await page.locator('#ytModalUnit').textContent(), 'LOVElution');
    assert.equal(await page.locator('#ytModalUnit').evaluate((node) => node.hidden), false);

    await page.locator('#ytModalDelete button').click();
    const dialog = page.getByRole('alertdialog');
    await dialog.waitFor();
    assert.equal(await dialog.evaluate((node) => node.classList.contains('is-compact')), true);
    assert.equal(await dialog.locator('p').textContent(), '삭제 하시겠습니까?');
    assert.equal(await dialog.getByRole('button', { name: '삭제', exact: true }).count(), 1);
    await dialog.getByRole('button', { name: '취소', exact: true }).click();
    await dialog.waitFor({ state: 'hidden' });
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
