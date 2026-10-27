const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const { chromium } = require('playwright');
const base = path.resolve(__dirname, '../..');
(async () => {
  const browser = await chromium.launch({headless:true, executablePath:process.env.CHROMIUM_PATH || undefined, args:['--no-sandbox']});
  try {
    const page = await browser.newPage();
    await page.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.pathname.startsWith('/uploads/') || url.pathname === '/portrait.svg') {
        return route.fulfill({contentType:'image/svg+xml', body:'<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect x="2" y="2" width="96" height="96" fill="none" stroke="#a1beb6" stroke-width="3"/></svg>'});
      }
      const file = path.join(base, 'src/main/resources/static', url.pathname);
      if (fs.existsSync(file) && fs.statSync(file).isFile())
        return route.fulfill({path:file,contentType:url.pathname.endsWith('.css')?'text/css':'application/javascript'});
      return route.fulfill({status:404,body:''});
    });
    for (const mode of ['standalone','bundle','artist']) {
      const artist = mode === 'artist';
      const css = mode === 'standalone' ? 'member-portrait-picker.css' : 'triples.css';
      const markup = Array.from({length:8}, (_, i) => '<button type="button" class="'+(artist?'nav-tab-btn':'mpp-item')+'" data-member-tab data-value="'+i+'" data-frame="'+(i+1)+'" style="--artist-color:#22aeff" aria-pressed="false"><span class="artist-tab-avatar"><img class="artist-tab-photo" src="https://motion.test/portrait.svg" alt="멤버 '+i+'"><img class="artist-tab-frame" src="https://motion.test/uploads/images/artist-tab-frames/'+(i+1)+'.png" alt=""><span class="artist-tab-shine"></span></span><span class="artist-tab-label">멤버 '+i+'</span></button>').join('');
      await page.setViewportSize({width:390,height:844});
      await page.setContent('<html><head><base href="https://motion.test/"><link rel="stylesheet" href="/css/'+css+'">'+(artist?'<link rel="stylesheet" href="/css/artist-performance.css">':'')+'</head><body style="margin:20px;background:#101216" class="'+(artist?'artist-page':'')+'"><div class="'+(artist?'artist-member-tabs':'member-portrait-picker')+'">'+markup+'</div><script src="/js/member-portrait-picker.js"></script></body></html>');
      await page.waitForFunction(()=>!!window.MemberPortraitPicker);
      if (artist) {
        await page.evaluate(() => document.querySelectorAll('.nav-tab-btn').forEach(button => button.onclick=()=>{
          document.querySelectorAll('.nav-tab-btn').forEach(other=>other.classList.toggle('is-active', other===button));
        }));
      } else {
        await page.evaluate(() => {
          window.changes=[];
          MemberPortraitPicker.bindMultiSelect(document.querySelector('.member-portrait-picker'), {onChange:values=>changes.push(values)});
        });
      }
      const buttons = page.locator(artist?'.nav-tab-btn':'.mpp-item');
      const first = buttons.first(), second = buttons.nth(1);
      const frame = first.locator('.artist-tab-frame');
      const src = await frame.getAttribute('src');
      await first.click();
      await page.waitForTimeout(350);
      const stable = await first.locator('.artist-tab-avatar').evaluate(el => ({
        shadow:getComputedStyle(el).boxShadow, bounds:el.getBoundingClientRect().width,
        animations:el.getAnimations({subtree:true}).filter(a=>a.playState==='running').map(a=>({
          properties:[...new Set(a.effect.getKeyframes().flatMap(frame=>Object.keys(frame)).filter(key=>!['offset','computedOffset','easing','composite'].includes(key)))],
          duration:a.effect.getTiming().duration
        }))
      }));
      assert.notEqual(stable.shadow, 'none');
      assert.equal(stable.animations.length, 1, mode+' 광택 레이어만 반복');
      assert.deepEqual(stable.animations[0].properties.sort(), ['opacity','transform'], mode+' 광택 레이어의 이동과 투명도만 애니메이션');
      assert.ok(stable.animations[0].duration >= 6000, mode+' 느린 대각선 광택');
      assert.equal(await frame.getAttribute('src'), src, mode+' 프레임 주소 유지');
      assert.equal(await frame.evaluate(el=>getComputedStyle(el).filter), 'none');
      assert.equal(await first.locator('.artist-tab-shine').isVisible(), true);
      const direction = await first.locator('.artist-tab-avatar').evaluate(el => {
        const animation = el.getAnimations({subtree:true}).find(a=>a.animationName==='member-selection-sheen');
        animation.pause();
        const layer = el.querySelector('.artist-tab-shine');
        animation.currentTime = 0;
        const start = new DOMMatrix(getComputedStyle(layer, '::before').transform);
        animation.currentTime = animation.effect.getTiming().duration * .70;
        const end = new DOMMatrix(getComputedStyle(layer, '::before').transform);
        animation.currentTime = 350; animation.play();
        return {dx:end.m41-start.m41,dy:end.m42-start.m42};
      });
      assert.ok(direction.dx > 0 && direction.dy > 0, mode+' 왼쪽 위에서 오른쪽 아래로 이동');
      assert.equal(await first.locator('.artist-tab-photo').evaluate(el=>getComputedStyle(el).filter), 'none');
      await page.waitForTimeout(850);
      assert.equal(await first.locator('.artist-tab-avatar').evaluate(el=>getComputedStyle(el).boxShadow), stable.shadow, mode+' 외곽선 고정');
      await second.click();
      assert.equal(await page.locator('.is-active').count(), artist?1:2);
      if (!artist) {
        assert.deepEqual(await page.evaluate(()=>changes.at(-1)), ['0','1']);
        await first.click();
        assert.deepEqual(await page.evaluate(()=>changes.at(-1)), ['1']);
        await first.focus(); await page.keyboard.press('Enter');
        assert.deepEqual(await page.evaluate(()=>changes.at(-1)), ['0','1']);
      }
      await page.emulateMedia({reducedMotion:'reduce'});
      await buttons.nth(2).click();
      assert.equal(await buttons.nth(2).locator('.artist-tab-photo').evaluate(el=>getComputedStyle(el).animationName), 'none');
      await page.waitForTimeout(200);
      assert.equal(await buttons.nth(2).locator('.artist-tab-avatar').evaluate(el=>el.getAnimations({subtree:true}).filter(a=>a.playState==='running').length), 0, mode+' 모션 감소 시 광택 정지');
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth), true);
      await page.emulateMedia({reducedMotion:'no-preference'});
    }
    console.log('통과: 단독/번들/그룹 소개 선택 효과 · 프레임 보존 · 외곽선 고정 · 좌상단→우하단 광택 · transform/opacity만 이동 · 다중선택·키보드 · 모션 감소 · 모바일');
  } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exit(1);});
