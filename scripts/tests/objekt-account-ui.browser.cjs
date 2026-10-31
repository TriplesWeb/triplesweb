// Local browser verification: node scripts/tests/objekt-account-ui.browser.cjs
const {chromium} = require('playwright');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const path = require('node:path');
const base = path.resolve(__dirname, '../..');
const read = p => fs.readFileSync(path.join(base, p), 'utf8');
const fixtures = [
  {timestamp:'2026-10-26T12:30:00Z',direction:'받음',member:'SeoYeon',season:'Cream02',collectionNo:'101A',class:'First Class',reportedSerial:42,fromNickname:'<script>alert(1)</script>',toNickname:'lunlun',collectionId:'Cream02 SeoYeon 101A'},
  {timestamp:'2026-10-26T11:20:00Z',direction:'보냄',member:'HaYeon',season:'Cream02',collectionNo:'367Z',class:'Special Class',reportedSerial:128,fromNickname:'lunlun',toNickname:'cosmo',collectionId:'Cream02 HaYeon 367Z'},
  {timestamp:'2026-10-25T10:00:00Z',direction:'받음',member:'SooMin',season:'Divine01',collectionNo:'203A',fromNickname:'',toNickname:'lunlun'},
];
(async () => {
  const browser = await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined),args:['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1100}});
    const errors=[]; page.on('pageerror',e=>errors.push(e.message));
    let calls=0;
    await page.route('https://preview.test/**', async route => {
      if (route.request().url().includes('/api/objekt/transfers')) {
        calls++;
        if(calls===2) return route.fulfill({status:503,contentType:'application/json',body:'{}'});
        return route.fulfill({contentType:'application/json',body:JSON.stringify({status:'available',transfers:calls===1?fixtures:[],hasNext:calls===1,cursorTimestamp:'2026-10-25T10:00:00Z',cursorId:'opaque+id'})});
      }
      let html=read('src/main/resources/templates/cosmo/transfers.mustache').replace(/{{>[^}]+}}/g,'').replaceAll('{{nickname}}','lunlun').replace(/<link[^>]+>/g,'').replace(/<script[^>]+><\/script>/g,'');
      html=html.replace('</head>',`<style>body{margin:0;font-family:Arial,sans-serif}${['gravity-page','archive-page','cosmo-profile','cosmo-collection','objekt-transfers'].map(name=>read('src/main/resources/static/css/'+name+'.css')).join('\n')}</style></head>`);
      return route.fulfill({contentType:'text/html',body:html});
    });
    await page.goto('https://preview.test/cosmo/transfers?nickname=lunlun');
    await page.addScriptTag({content:read('src/main/resources/static/js/season-filters.js')});
    await page.addScriptTag({content:read('src/main/resources/static/js/objekt-dropdown.js')});
    await page.addScriptTag({content:read('src/main/resources/static/js/objekt-account-transfers.js')});
    await page.waitForFunction(()=>document.querySelectorAll('.tr-row').length===3);
    assert.equal(await page.locator('.tr-day').count(),2);
    assert.equal(await page.locator('[data-account-title]').textContent(),'lunlun');
    assert.equal(await page.locator('.tr-account-form, [data-profile-link], [data-count], [data-filter="자기 전송"]').count(),0);
    assert.deepEqual(await page.locator('.tr-profile-nav > *').allTextContents(),['전체 현황','전송 내역','멤버별 상세']);
    assert.equal(await page.locator('[data-profile-members]').getAttribute('href'),'/cosmo/profile/lunlun?tab=members');
    assert.equal(await page.locator('.tr-tabs .is-active').evaluate(node=>getComputedStyle(node).backgroundColor),'rgb(141, 236, 217)');
    assert.equal(await page.locator('.tr-account-panel').evaluate(node=>getComputedStyle(node).backgroundColor),'rgb(20, 22, 28)');
    assert.equal(await page.locator('.tr-peer script').count(),0);
    assert.match(await page.locator('.tr-peer').first().textContent(),/<script>/);
    await page.getByRole('button',{name:'↗ 보낸 내역'}).click();
    assert.equal(await page.locator('.tr-row').count(),1);
    await page.getByRole('button',{name:'전체',exact:true}).click();
    await page.locator('[data-search]').fill('SooMin');
    assert.equal(await page.locator('.tr-row').count(),1);
    await page.locator('[data-search]').fill('nothingmatches');
    assert.equal(await page.locator('[data-empty]').isVisible(),true);
    await page.locator('[data-search]').fill('');
    assert.equal(await page.getByRole('button',{name:'시즌 필터',exact:true}).count(),1);
    await page.getByRole('button',{name:'시즌 필터',exact:true}).click();
    await page.getByRole('button',{name:'시즌 Divine01',exact:true}).click();
    assert.equal(await page.locator('.tr-row').count(),1);
    await page.getByRole('button',{name:'시즌 필터',exact:true}).click();
    await page.getByRole('button',{name:'시즌 전체',exact:true}).click();
    await page.getByRole('button',{name:'시즌 필터',exact:true}).click();
    await page.getByRole('button',{name:'시즌 전체',exact:true}).focus();
    await page.keyboard.press('Escape');
    assert.equal(await page.getByRole('button',{name:'시즌 필터',exact:true}).getAttribute('aria-expanded'),'false');
    await page.locator('[data-more]').click();
    await page.waitForFunction(()=>!document.querySelector('[data-retry]').hidden);
    assert.equal(await page.locator('.tr-row').count(),3);
    await page.locator('[data-retry]').click();
    await page.waitForFunction(()=>document.querySelector('[data-more]').hidden);
    assert.equal(await page.locator('.tr-row').count(),3);
    fs.mkdirSync('/tmp/objekt-ui',{recursive:true});
    await page.screenshot({path:'/tmp/objekt-ui/desktop.png',fullPage:true});
    await page.setViewportSize({width:390,height:844});
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true,
      JSON.stringify(await page.evaluate(()=>[...document.querySelectorAll('main,main *')].filter(n=>n.getBoundingClientRect().right>innerWidth).slice(0,12).map(n=>({class:n.className,width:n.getBoundingClientRect().width,right:n.getBoundingClientRect().right})))));
    await page.screenshot({path:'/tmp/objekt-ui/mobile.png',fullPage:true});
    await page.setViewportSize({width:320,height:740});
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true);
    assert.deepEqual(errors,[]);
    const missingHtml = read('build/profile-missing-preview.html').replace(/<link[^>]+>/g,'').replace(/<script\b[^>]*>[\s\S]*?<\/script>/g,'');
    await page.setContent(missingHtml);
    await page.addStyleTag({content:['gravity-page','archive-page','cosmo-profile'].map(name=>read('src/main/resources/static/css/'+name+'.css')).join('\n')});
    assert.equal(await page.locator('.profile-owned-class').count(),1);
    assert.equal(await page.locator('.profile-season-fold, [role=progressbar]').count(),0);
    assert.equal(await page.locator('.profile-checklist-card.is-missing').count(),1);
    await page.mouse.move(0,0);
    assert.match(await page.locator('.profile-checklist-card.is-missing').evaluate(card=>{const image=document.createElement('img');card.querySelector('.profile-checklist-image').append(image);return getComputedStyle(image).filter;}),/brightness\(0\.72\)/);
    await page.screenshot({path:'/tmp/objekt-ui/profile-missing.png',fullPage:true});
    console.log('PASS: 날짜 묶음, 방향 필터, 검색, 빈 결과, 오류 재시도, 안전한 텍스트 렌더링, 모바일 가로 넘침 검사');
  } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exit(1)});
