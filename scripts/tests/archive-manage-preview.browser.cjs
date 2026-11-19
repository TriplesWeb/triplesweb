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
const manageJs = fs.readFileSync(path.join(staticRoot, 'js', 'archive-manage.js'), 'utf8');

const jiyeonLogo = '/uploads/images/' + encodeURIComponent('상징로고') + '/S24.png';

function pageHtml(kind) {
  const isVideo = kind === 'video';
  const noun = isVideo ? '영상' : '사진';
  const previewLabel = isVideo ? '목록에서 보이는 모습' : '목록에서 보이는 모습 · 카드/슬라이드';
  const titleField = isVideo
    ? '<label class="ar-field">제목<input type="text" name="title"></label>'
    : '<label class="ar-field">설명<textarea name="description" rows="3"></textarea></label>';
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
*,*::before,*::after{box-sizing:border-box}
body{margin:0;background:#0d0e12;color:#e3e2e8;font-family:"Pretendard Variable",Pretendard,sans-serif}
${site}${css}${siteUi}
</style></head>
<body class="site-page">
<main class="archive-page" data-archive-kind="${kind}" data-date-label="${noun} 날짜" data-default-artists="지연">
  <section class="ar-grid" id="archiveGrid">
    <article class="ar-panel ar-card" data-id="1" data-title="${isVideo ? '지서연' : ''}" data-description="${isVideo ? '' : '지서연'}" data-headline="지서연" data-artists="지연" data-url="${isVideo ? '/clip.mp4' : '/portrait.svg'}" data-images='${isVideo ? '' : '["/portrait.svg","/poster.svg"]'}' data-registered="2026-10-25">
      ${isVideo
        ? '<a class="ar-video-thumb" href="/clip.mp4"><img src="/poster.svg" alt=""><span class="ar-video-play" aria-hidden="true">▶</span></a>'
        : '<button type="button" class="ar-photo-link"><img src="/portrait.svg" alt=""></button>'}
      <div class="ar-body">
        <h3>지서연</h3>
        <div class="ar-meta" style="--member-count: 1">
          <span class="archive-member-chip archive-symbol-chip" style="--member-color:#7bba8d" title="지연" aria-label="지연"><img src="${jiyeonLogo}" alt="지연"><span class="archive-chip-name">지연</span></span>
          <time class="ar-card-date" datetime="2026-10-25">2026/10/25</time>
        </div>
      </div>
      <button type="button" class="site-btn" data-archive-edit data-id="1" data-title="${isVideo ? '지서연' : ''}" data-description="${isVideo ? '' : '지서연'}" data-artists="지연" data-url="${isVideo ? '/clip.mp4' : '/portrait.svg'}" data-images='${isVideo ? '' : '["/portrait.svg","/poster.svg"]'}' data-thumbnail-url="${isVideo ? '/poster.svg' : ''}" data-registered="2026-10-25" data-source="COSMO_ROOM">수정하기</button>
    </article>
  </section>
</main>
<div class="ar-dialog" id="uploadModal" hidden>
  <div class="ar-dialog-panel">
    <form id="archiveUploadForm" enctype="multipart/form-data" data-upload-url="/archive/upload/${kind}">
      ${titleField}
      <label class="ar-field">${noun} 날짜<input type="date" name="registeredOn"></label>
      <div class="member-portrait-picker" id="archive-upload-picker">
        <button type="button" class="mpp-item" data-value="지연" data-name="지연" data-frame="24" style="--artist-color:#7bba8d" aria-pressed="false">
          <span class="artist-tab-avatar"><img class="artist-tab-photo" src="/portrait.svg" alt=""></span>
          <span class="artist-tab-label">지연</span>
        </button>
      </div>
      <div data-artist-chips></div>
      <span data-picker-count>0</span>
      <span class="ar-file">
        <input type="file" id="archiveUploadFile" name="file" accept="${isVideo ? 'video/*' : 'image/*'}" ${isVideo ? '' : 'multiple'}>
        <label for="archiveUploadFile" class="ar-file-pick">파일 선택</label>
        <span class="ar-file-name" data-file-name>선택된 파일 없음</span>
      </span>
      ${isVideo ? '<input type="file" name="thumbnail" accept="image/*"><div class="ar-thumbnail-preview" data-thumbnail-preview hidden><img alt=""><p data-thumbnail-info></p></div>' : ''}
      <div class="ar-preview" id="uploadPreview" hidden>
        <p class="ar-preview-info" data-preview-file></p>
        <div><p>원본</p><div class="ar-preview-full"></div></div>
        <div><p>${previewLabel}</p><div class="ar-preview-card"></div></div>
        <p class="ar-preview-error" data-preview-error hidden></p>
      </div>
    </form>
  </div>
</div>
<div class="ar-dialog" id="editModal" hidden>
  <div class="ar-dialog-panel">
    <form id="archiveEditForm" enctype="multipart/form-data">
      ${titleField}
      <label class="ar-field">${noun} 날짜<input type="date" name="registeredOn"></label>
      <label class="ar-field">출처<select name="source"><option value="COSMO_ROOM">코스모룸</option></select></label>
      <div class="member-portrait-picker" id="archive-edit-picker">
        <button type="button" class="mpp-item" data-value="지연" data-name="지연" data-frame="24" style="--artist-color:#7bba8d" aria-pressed="false">
          <span class="artist-tab-avatar"><img class="artist-tab-photo" src="/portrait.svg" alt=""></span>
          <span class="artist-tab-label">지연</span>
        </button>
      </div>
      <div data-artist-chips></div>
      <span data-picker-count>0</span>
      <input type="file" name="file">
      ${isVideo ? '<input type="file" name="thumbnail" accept="image/*"><input type="hidden" name="clearThumbnail" value="false"><div class="ar-thumbnail-preview" data-thumbnail-preview hidden><img alt=""><p data-thumbnail-info></p></div>' : ''}
      <div class="ar-preview" id="editPreview" hidden>
        <p class="ar-preview-info" data-preview-file></p>
        <div><p>현재 ${noun}</p><div class="ar-preview-full"></div></div>
        <div><p>${previewLabel}</p><div class="ar-preview-card"></div></div>
        <p class="ar-preview-error" data-preview-error hidden></p>
      </div>
    </form>
  </div>
</div>
<script>${pickerJs}</script>
<script>${manageJs}</script>
</body></html>`;
}

const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><circle cx="40" cy="40" r="40" fill="#7bba8d"/></svg>';

async function withPage(html, run) {
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
      if (url.pathname.startsWith('/uploads/')) {
        const file = path.resolve(root, '.' + decodeURIComponent(url.pathname));
        if (file.startsWith(root + path.sep) && fs.existsSync(file)) {
          return route.fulfill({ path: file });
        }
      }
      if (url.pathname.endsWith('.svg') || url.pathname.endsWith('.png')) {
        return route.fulfill({ contentType: 'image/svg+xml', body: svg });
      }
      if (url.pathname.endsWith('.mp4')) {
        return route.fulfill({ contentType: 'video/mp4', body: Buffer.from('0000') });
      }
      const file = path.resolve(staticRoot, '.' + url.pathname);
      if (file.startsWith(staticRoot + path.sep) && fs.existsSync(file)) {
        return route.fulfill({ path: file });
      }
      return route.fulfill({ status: 404, body: '' });
    });
    await page.goto('https://archive-manage-preview.test/', { waitUntil: 'load' });
    await run(page);
  } finally {
    await browser.close();
  }
}

(async () => {
  await withPage(pageHtml('image'), async (page) => {
    const artifactDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(artifactDir, { recursive: true });
    await page.locator('#archiveGrid .ar-card').screenshot({
      path: path.join(artifactDir, 'archive-live-photo-card.png')
    });
    await page.evaluate(() => window.showAddModal());
    const preselected = await page.evaluate(() => {
      const active = document.querySelectorAll('#uploadModal .mpp-item.is-active');
      return {
        count: active.length,
        names: Array.from(active).map((btn) => btn.dataset.value || ''),
        chip: document.querySelector('#uploadModal [data-artist-chips]')?.textContent.trim() || ''
      };
    });
    assert.equal(preselected.count, 1, '멤버 폴더 기본 선택이 없다');
    assert.deepEqual(preselected.names, ['지연']);
    assert.match(preselected.chip, /지연/);
    await page.evaluate(() => {
      document.getElementById('uploadModal').hidden = true;
      document.body.style.overflow = '';
    });

    await page.click('[data-archive-edit]');
    await page.locator('#editPreview .ar-preview-photo-card').waitFor();

    const preview = await page.evaluate(() => {
      const card = document.querySelector('#editPreview .ar-preview-card');
      const article = card.querySelector('.ar-preview-photo-card');
      const photo = article.querySelector('.ar-photo-link > img');
      const title = article.querySelector('[data-preview-title]');
      const chip = article.querySelector('.archive-member-chip');
      const name = chip?.querySelector('.archive-chip-name');
      const date = article.querySelector('.ar-card-date');
      const badge = article.querySelector('.ar-badge');
      const photoStyle = photo ? getComputedStyle(photo) : {};
      const chipImg = chip?.querySelector('img');
      const chipBox = chipImg?.getBoundingClientRect();
      const keptTiles = Array.from(document.querySelectorAll('#archiveEditForm [data-file-name] .ar-file-tile img'))
        .map((img) => img.getAttribute('src') || '');
      return {
        hidden: document.getElementById('editPreview').hidden,
        title: title?.textContent || '',
        chipName: name?.textContent || '',
        chipLabel: chip?.getAttribute('aria-label') || '',
        symbol: chip?.classList.contains('archive-symbol-chip') || false,
        logo: chipImg?.getAttribute('src') || '',
        date: date?.textContent || '',
        datetime: date?.getAttribute('datetime') || '',
        badges: article.querySelectorAll('.ar-badge').length,
        photoFit: photoStyle.objectFit,
        photoHeight: photoStyle.height,
        chipHeight: chipBox ? chipBox.height : 0,
        stacked: card.classList.contains('is-photo-preview'),
        bodyText: article.querySelector('.ar-body')?.innerText.replace(/\s+/g, ' ').trim() || '',
        keptTiles,
        previewSrc: photo?.getAttribute('src') || ''
      };
    });

    assert.equal(preview.hidden, false, '수정 미리보기가 열리지 않는다');
    assert.equal(preview.title, '지서연');
    assert.equal(preview.chipName, '지연');
    assert.equal(preview.chipLabel, '지연');
    assert.equal(preview.symbol, true, '목록 미리보기가 상징로고 칩이 아니다');
    assert.match(preview.logo, /S24\.png/);
    assert.equal(preview.date, '2026/10/25');
    assert.equal(preview.datetime, '2026-10-25');
    assert.equal(preview.badges, 0, '예전 ar-badge가 남아 있다');
    assert.equal(preview.photoFit, 'contain');
    assert.notEqual(preview.photoHeight, '9.5rem');
    assert.ok(preview.chipHeight > 0 && preview.chipHeight <= 40, '미리보기 칩이 큰 사진 규칙을 따른다: ' + preview.chipHeight);
    assert.equal(preview.stacked, true);
    assert.deepEqual(preview.keptTiles, ['/portrait.svg', '/poster.svg'], '수정 모달에 기존 사진 타일이 없다');
    assert.equal(preview.previewSrc, '/portrait.svg');

    await page.locator('#editPreview .ar-preview-photo-card').screenshot({
      path: path.join(artifactDir, 'archive-edit-preview-photo-card.png')
    });

    await page.fill('#archiveEditForm [name="description"]', '지서연 수정');
    await page.fill('#archiveEditForm [name="registeredOn"]', '2026-11-16');
    const updated = await page.evaluate(() => ({
      title: document.querySelector('#editPreview [data-preview-title]').textContent,
      date: document.querySelector('#editPreview .ar-card-date').textContent
    }));
    assert.equal(updated.title, '지서연 수정');
    assert.equal(updated.date, '2026/11/16');

    await page.evaluate(() => {
      document.getElementById('editModal').hidden = true;
      document.getElementById('uploadModal').hidden = false;
    });
    const pasted = await page.evaluate(() => {
      const png = Uint8Array.from(atob(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='
      ), (c) => c.charCodeAt(0));
      const blob = new File([png], 'image.png', { type: 'image/png' });
      const clipboardData = {
        items: [{
          kind: 'file',
          type: 'image/png',
          getAsFile: () => blob
        }],
        files: [blob]
      };
      const event = new Event('paste', { bubbles: true, cancelable: true });
      Object.defineProperty(event, 'clipboardData', { get: () => clipboardData });
      document.dispatchEvent(event);
      const input = document.querySelector('#archiveUploadForm input[name="file"]');
      const preview = document.getElementById('uploadPreview');
      return {
        name: input?.files?.[0]?.name || '',
        type: input?.files?.[0]?.type || '',
        previewHidden: preview?.hidden ?? true,
        fullImage: Boolean(preview?.querySelector('.ar-preview-full img'))
      };
    });
    assert.equal(pasted.type, 'image/png');
    assert.match(pasted.name, /\.(png|jpe?g|webp|gif|bmp)$/i);
    assert.equal(pasted.previewHidden, false, '붙여넣은 사진 미리보기가 열리지 않는다');
    assert.equal(pasted.fullImage, true, '붙여넣은 사진 원본 미리보기가 없다');

    const multi = await page.evaluate(() => {
      const png = Uint8Array.from(atob(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='
      ), (c) => c.charCodeAt(0));
      const input = document.querySelector('#archiveUploadForm input[name="file"]');
      const transfer = new DataTransfer();
      transfer.items.add(new File([png], 'one.png', { type: 'image/png' }));
      transfer.items.add(new File([png], 'two.png', { type: 'image/png' }));
      transfer.items.add(new File([png], 'three.png', { type: 'image/png' }));
      input.files = transfer.files;
      input.dispatchEvent(new Event('change', { bubbles: true }));
      const tiles = Array.from(document.querySelectorAll('#archiveUploadForm .ar-file-tile'));
      const thumbs = tiles.map((tile) => ({
        src: tile.querySelector('.ar-file-thumb img')?.getAttribute('src') || '',
        label: tile.querySelector('.ar-file-remove')?.getAttribute('aria-label') || ''
      }));
      const summary = document.querySelector('#uploadPreview [data-preview-file]')?.textContent.trim() || '';
      document.querySelectorAll('#archiveUploadForm .ar-file-remove')[1]?.click();
      const afterLabels = Array.from(document.querySelectorAll('#archiveUploadForm .ar-file-remove')).map((btn) => btn.getAttribute('aria-label') || '');
      const afterNames = Array.from(input.files || []).map((file) => file.name);
      const afterThumbs = Array.from(document.querySelectorAll('#archiveUploadForm .ar-file-thumb img')).map((img) => img.getAttribute('src') || '');
      return {
        tileCount: tiles.length,
        thumbs,
        summary,
        afterLabels,
        afterNames,
        afterThumbs,
        count: input.files.length
      };
    });
    assert.equal(multi.tileCount, 3);
    assert.deepEqual(multi.thumbs.map((item) => item.label), ['one.png 제거', 'two.png 제거', 'three.png 제거']);
    assert.ok(multi.thumbs.every((item) => item.src.startsWith('blob:')), '선택 파일 썸네일이 없다');
    assert.equal(multi.summary, '3장 선택');
    assert.deepEqual(multi.afterLabels, ['one.png 제거', 'three.png 제거']);
    assert.deepEqual(multi.afterNames, ['one.png', 'three.png']);
    assert.ok(multi.afterThumbs.every((src) => src.startsWith('blob:')), '제거 후 썸네일이 깨졌다');
    assert.equal(multi.count, 2);
  });

  await withPage(pageHtml('video'), async (page) => {
    await page.click('[data-archive-edit]');
    await page.locator('#editPreview .ar-preview-video-card').waitFor();

    const preview = await page.evaluate(() => {
      const card = document.querySelector('#editPreview .ar-preview-card');
      const article = card.querySelector('.ar-preview-video-card');
      const poster = article.querySelector('[data-preview-poster]');
      const play = article.querySelector('.ar-video-play');
      const title = article.querySelector('[data-preview-title]');
      const chip = article.querySelector('.archive-member-chip');
      const date = article.querySelector('.ar-card-date');
      const cropped = card.querySelector(':scope > video');
      const full = document.querySelector('#editPreview .ar-preview-full video');
      return {
        title: title?.textContent || '',
        chip: chip?.getAttribute('aria-label') || '',
        symbol: chip?.classList.contains('archive-symbol-chip') || false,
        date: date?.textContent || '',
        poster: poster?.getAttribute('src') || '',
        posterHidden: poster?.hidden || false,
        play: play?.textContent.trim() || '',
        cropped: Boolean(cropped),
        fullVideo: Boolean(full),
        badges: article.querySelectorAll('.ar-badge').length,
        videoPreview: card.classList.contains('is-video-preview')
      };
    });

    assert.equal(preview.title, '지서연');
    assert.equal(preview.chip, '지연');
    assert.equal(preview.symbol, true);
    assert.equal(preview.date, '2026/10/25');
    assert.equal(preview.poster, '/poster.svg');
    assert.equal(preview.posterHidden, false);
    assert.equal(preview.play, '▶');
    assert.equal(preview.cropped, false, '영상 카드 미리보기가 잘린 video다');
    assert.equal(preview.fullVideo, true, '원본 영상 미리보기가 없다');
    assert.equal(preview.badges, 0);
    assert.equal(preview.videoPreview, true);

    const artifactDir = fs.existsSync('/opt/cursor/artifacts') ? '/opt/cursor/artifacts' : '/tmp/objekt-ui';
    fs.mkdirSync(artifactDir, { recursive: true });
    await page.locator('#editPreview .ar-preview-video-card').screenshot({
      path: path.join(artifactDir, 'archive-edit-preview-video-card.png')
    });
  });
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
