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
    for (const mode of ['standalone','bundle','artist','about']) {
      const artist = mode === 'artist' || mode === 'about';
      // About/멤버 소개는 artist.css + member-portrait-picker.css 를 함께 쓴다.
      const cssLinks = mode === 'standalone'
        ? '<link rel="stylesheet" href="/css/member-portrait-picker.css">'
        : mode === 'about'
          ? '<link rel="stylesheet" href="/css/artist.css"><link rel="stylesheet" href="/css/member-portrait-picker.css"><link rel="stylesheet" href="/css/artist-performance.css">'
          : '<link rel="stylesheet" href="/css/triples.css">'+(artist?'<link rel="stylesheet" href="/css/artist-performance.css">':'');
      const markup = Array.from({length:8}, (_, i) => '<button type="button" class="'+(artist?'nav-tab-btn':'mpp-item')+'" data-member-tab data-value="'+i+'" data-frame="'+(i+1)+'" style="--artist-color:#22aeff" aria-pressed="false"><span class="artist-tab-avatar"><img class="artist-tab-photo" src="https://motion.test/portrait.svg" alt="멤버 '+i+'"><img class="artist-tab-frame" src="https://motion.test/uploads/images/artist-tab-frames/'+(i+1)+'.png" alt=""><span class="artist-tab-shine"></span></span><span class="artist-tab-label">멤버 '+i+'</span></button>').join('');
      await page.setViewportSize({width:390,height:844});
      await page.setContent('<html><head><base href="https://motion.test/">'+cssLinks+'</head><body style="margin:20px;background:#101216" class="'+(artist?'artist-page':'')+'"><div class="'+(artist?'artist-member-tabs':'member-portrait-picker')+'">'+markup+'</div><script src="/js/member-portrait-picker.js"></script></body></html>');
      await page.waitForFunction(()=>!!window.MemberPortraitPicker);
      if (artist) {
        await page.evaluate(() => document.querySelectorAll('.nav-tab-btn').forEach(button => {
          button.onclick=()=>{
            document.querySelectorAll('.nav-tab-btn').forEach(other=>other.classList.toggle('is-active', other===button));
          };
          const avatar=button.querySelector('.artist-tab-avatar');
          const frame=button.querySelector('.artist-tab-frame');
          if (avatar && frame && frame.src) {
            avatar.style.setProperty('--frame-mask','url("'+frame.src+'")');
            avatar.classList.add('has-frame-mask');
          }
        }));
      } else {
        await page.evaluate(() => {
          window.changes=[];
          MemberPortraitPicker.bindMultiSelect(document.querySelector('.member-portrait-picker'), {onChange:values=>changes.push(values)});
        });
      }
      if (artist) await page.locator('.nav-tab-btn').first().evaluate(el=>{
        el.removeAttribute('data-member-tab');
        el.querySelector('.artist-tab-avatar').classList.add('artist-tab-avatar--group');
      });
      const buttons = page.locator(artist?'.nav-tab-btn':'.mpp-item');
      const first = buttons.first(), second = buttons.nth(1), third = buttons.nth(2);
      // About/artist: 그룹 탭이 기본 활성이어도 멤버는 연한 테두리(idle). 선택 효과는 멤버 탭으로 검증한다.
      const selected = artist ? second : first;
      const sibling = artist ? third : second;
      const frame = selected.locator('.artist-tab-frame');
      const src = await frame.getAttribute('src');
      if (artist) {
        await first.click();
        await page.waitForTimeout(50);
      }
      await selected.locator('.artist-tab-avatar.has-frame-mask').waitFor();
      const idleOverlay = await selected.locator('.artist-tab-avatar').evaluate(el => {
        const overlay = getComputedStyle(el, '::after');
        return { opacity: Number(overlay.opacity), filter: overlay.filter };
      });
      assert.ok(idleOverlay.opacity > 0 && idleOverlay.opacity < 1, mode+' 미선택 ::after는 연한 상징색 윤곽');
      assert.doesNotMatch(String(idleOverlay.filter), /drop-shadow/, mode+' 미선택에는 발광 없음');
      await selected.click();
      await page.waitForTimeout(350);
      await selected.locator('.artist-tab-avatar.has-frame-mask').waitFor();
      const stable = await selected.locator('.artist-tab-avatar').evaluate(el => {
        const overlay = getComputedStyle(el, '::after');
        return {
        shadow:getComputedStyle(el).boxShadow, bounds:el.getBoundingClientRect().width,
        overlayBg: overlay.backgroundColor,
        overlayFilter: overlay.filter,
        overlayOpacity: Number(overlay.opacity),
        frameFilter:getComputedStyle(el.querySelector('.artist-tab-frame')).filter,
        animations:el.getAnimations({subtree:true}).filter(a=>a.playState==='running').map(a=>({
          properties:[...new Set(a.effect.getKeyframes().flatMap(frame=>Object.keys(frame)).filter(key=>!['offset','computedOffset','easing','composite'].includes(key)))],
          duration:a.effect.getTiming().duration
        }))
        };
      });
      if (artist) {
        const rgb = await selected.locator('.artist-tab-label').evaluate(el => {
          const c = getComputedStyle(el).color;
          if (c.startsWith('color(srgb')) return [...c.matchAll(/[0-9.]+/g)].slice(0, 3).map(n => Math.round(Number(n)*255));
          return (c.match(/\d+/g) || []).map(Number);
        });
        assert.ok(rgb[2] > rgb[0] + 80, mode+' 멤버 이름이 상징색으로 부각');
      }
      const glowCount = (String(stable.overlayFilter).match(/drop-shadow/g) || []).length;
      assert.ok(glowCount >= 1 && glowCount <= 3, mode+' 선택 테두리 빛은 3겹까지');
      assert.equal(stable.overlayOpacity, 1, mode+' 선택 ::after는 완전 불투명');
      assert.match(String(stable.overlayBg), /34,\s*174,\s*255|#22aeff/i, mode+' 윤곽을 상징색으로 칠함');
      assert.doesNotMatch(String(stable.overlayFilter), /255,\s*255,\s*255|#fff\b/i, mode+' 빛 색에 흰색을 섞지 않음');
      const siblingIdle = await sibling.locator('.artist-tab-avatar').evaluate(el => {
        const overlay = getComputedStyle(el, '::after');
        const frameEl = el.querySelector('.artist-tab-frame');
        return {
          overlayOpacity: Number(overlay.opacity),
          frameOpacity: frameEl ? Number(getComputedStyle(frameEl).opacity) : null
        };
      });
      assert.equal(siblingIdle.overlayOpacity, 0, mode+' 선택 후 미선택 ::after는 숨김');
      assert.equal(siblingIdle.frameOpacity, 0, mode+' 선택 후 미선택 프레임은 숨김');
      const overlayMask = await selected.locator('.artist-tab-avatar').evaluate(el => {
        const overlay = getComputedStyle(el, '::after');
        return {
          composite: overlay.maskComposite || overlay.webkitMaskComposite,
          size: overlay.maskSize || overlay.webkitMaskSize
        };
      });
      assert.match(String(overlayMask.composite), /subtract|source-out/i, mode+' S번호 뱃지는 마스크에서 뺌');
      assert.match(String(overlayMask.size), /30%/, mode+' 뱃지 구멍은 직선 외곽선만 남김');
      assert.equal(stable.animations.length, 1, mode+' 광택 레이어만 반복');
      assert.deepEqual(stable.animations[0].properties.sort(), ['opacity','transform'], mode+' 광택 레이어의 이동과 투명도만 애니메이션');
      assert.ok(stable.animations[0].duration >= 6800 && stable.animations[0].duration <= 6900, mode+' 6.85초 반복 주기');
      assert.equal(await frame.getAttribute('src'), src, mode+' 프레임 주소 유지');
      assert.equal(await selected.locator('.artist-tab-shine').isVisible(), true);
      const direction = await selected.locator('.artist-tab-avatar').evaluate(el => {
        const animation = el.getAnimations({subtree:true}).find(a=>a.animationName==='member-selection-sheen');
        animation.pause();
        const layer = el.querySelector('.artist-tab-shine');
        animation.currentTime = 0;
        const start = new DOMMatrix(getComputedStyle(layer, '::before').transform);
        animation.currentTime = 1750;
        const mid = new DOMMatrix(getComputedStyle(layer, '::before').transform);
        animation.currentTime = 5600;
        const end = new DOMMatrix(getComputedStyle(layer, '::before').transform);
        animation.currentTime = 6400;
        const rest = new DOMMatrix(getComputedStyle(layer, '::before').transform);
        animation.currentTime = 350; animation.play();
        return {dx:mid.m41-start.m41,dy:mid.m42-start.m42,restDx:rest.m41-end.m41,restDy:rest.m42-end.m42};
      });
      assert.ok(direction.dx > 0 && direction.dy > 0, mode+' 왼쪽 위에서 오른쪽 아래로 이동');
      assert.ok(Math.abs(direction.restDx)<.01 && Math.abs(direction.restDy)<.01, mode+' 이동 후 대기');
      assert.equal(await selected.locator('.artist-tab-photo').evaluate(el=>getComputedStyle(el).filter), 'none');
      await page.waitForTimeout(850);
      assert.equal(await selected.locator('.artist-tab-avatar').evaluate(el=>getComputedStyle(el,'::after').filter), stable.overlayFilter, mode+' 윤곽 발광 고정');
      const shine = selected.locator('.artist-tab-shine');
      const beforeTint = await shine.evaluate(el=>getComputedStyle(el,'::before').backgroundImage);
      await selected.evaluate(el=>el.style.setProperty('--artist-color','#db0c74'));
      const afterTint = await shine.evaluate(el=>getComputedStyle(el,'::before').backgroundImage);
      assert.notEqual(afterTint, beforeTint, mode+' 멤버 상징색에 따라 광택 색 변경');
      await selected.evaluate(el=>el.style.setProperty('--artist-color','#22aeff'));
      if (artist) {
        await first.click();
        const restored = await selected.locator('.artist-tab-avatar').evaluate(el => {
          const overlay = getComputedStyle(el, '::after');
          return { opacity: Number(overlay.opacity), filter: overlay.filter };
        });
        assert.ok(restored.opacity > 0 && restored.opacity < 1, mode+' 그룹 입문으로 돌아가면 연한 윤곽 복구');
        assert.doesNotMatch(String(restored.filter), /drop-shadow/, mode+' 그룹 idle에는 발광 없음');
        await selected.click();
      } else {
        await second.click();
      }
      assert.equal(await page.locator('.is-active').count(), artist?1:2);
      if (!artist) {
        assert.deepEqual(await page.evaluate(()=>changes.at(-1)), ['0','1']);
        await first.click();
        assert.deepEqual(await page.evaluate(()=>changes.at(-1)), ['1']);
        await first.focus(); await page.keyboard.press('Enter');
        assert.deepEqual(await page.evaluate(()=>changes.at(-1)), ['0','1']);
      }
      if (!artist) {
        const fallback=buttons.nth(7);
        await fallback.evaluate(el=>{el.dataset.keepOption='true';el.querySelector('.artist-tab-photo').dispatchEvent(new Event('error'));});
        assert.equal(await fallback.locator('.artist-tab-shine').count(),1, mode+' 대체 초상화에도 광택 레이어 유지');
        await fallback.click();
        await page.waitForTimeout(350);
        assert.equal(await fallback.locator('.artist-tab-avatar').evaluate(el=>el.getAnimations({subtree:true}).filter(a=>a.playState==='running').length),1);
      }
      await page.emulateMedia({reducedMotion:'reduce'});
      await buttons.nth(2).click();
      assert.equal(await buttons.nth(2).locator('.artist-tab-photo').evaluate(el=>getComputedStyle(el).animationName), 'none');
      await page.waitForTimeout(200);
      assert.equal(await buttons.nth(2).locator('.artist-tab-avatar').evaluate(el=>el.getAnimations({subtree:true}).filter(a=>a.playState==='running').length), 0, mode+' 모션 감소 시 광택 정지');
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth), true);
      await page.emulateMedia({reducedMotion:'no-preference'});
    }
    await page.setContent('<html><head><base href="https://motion.test/"><link rel="stylesheet" href="/css/archive-page.css"></head><body><main class="archive-page" data-archive-kind="image"><article class="ar-card"><a class="ar-photo-link"><img src="/portrait.svg" alt="큰 사진"></a><div class="ar-body"><span class="archive-member-chip"><img src="/portrait.svg" alt="멤버"><span>멤버 이름</span></span></div></article></main></body></html>');
    await page.locator('.archive-member-chip img').waitFor();
    const sizes=await page.evaluate(()=>({badge:document.querySelector('.archive-member-chip img').getBoundingClientRect().height,photo:document.querySelector('.ar-photo-link img').getBoundingClientRect().height}));
    assert.ok(sizes.badge<=40 && sizes.badge>20,'사진 아카이브 멤버 배지 높이 고정');
    assert.ok(sizes.photo>=250,'원본 사진 크기 유지');
    console.log('통과: 단독/번들/그룹 소개/About CSS 선택 효과 · 프레임 보존 · 윤곽 drop-shadow · 상징색 광택 · 6.85초 반복 · 좌상단→우하단 광택 · transform/opacity만 이동 · 다중선택·키보드 · 모션 감소 · 모바일');
  } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exit(1);});
