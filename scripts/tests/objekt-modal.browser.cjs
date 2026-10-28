const {chromium} = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const base = path.resolve(__dirname, '../..');
(async () => {
  const browser = await chromium.launch({headless:true, executablePath:process.env.CHROMIUM_PATH || (fs.existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined), args:['--no-sandbox']});
  try {
    const page = await browser.newPage({viewport:{width:1280,height:1000}});
    let favorite=false, favoriteAttempts=0;
    let isAdmin = true, description = '기존 설명', writes = [], failMore = true;
    const data = () => ({isFavorite:favorite,objektKey:'uuid',collectionId:'Cream02 HaYeon 367Z',member:'HaYeon',season:'Cream02',class:'Special',collectionNo:'367Z',description,isAdmin,
      detailUrl:'/cosmo/objekt/detail/hayeoncc367z',memberUrl:'/cosmo/objekt?group=members&member=HaYeon',tradeUrl:'/article/trade/new?selectedObjektIds=uuid',sellUrl:'/article/sell/new?selectedObjektIds=uuid',descriptionUrl:'/cosmo/objekt/detail/hayeoncc367z/description'});
    await page.context().addCookies([{name:'XSRF-TOKEN',value:'test-csrf',domain:'preview.test',path:'/'}]);
    await page.route('**/*', async route => {
      const req=route.request(), url=new URL(req.url()), p=url.pathname;
      if(url.origin!=='https://preview.test') return route.abort();
      const json = body => route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
      if(p.endsWith('/favorite') && req.method()==='PUT') {
        assert.equal(req.headers()['x-xsrf-token'],'test-csrf');
        favoriteAttempts++;
        if(favoriteAttempts===1)return route.fulfill({status:403,body:'{}',contentType:'application/json'});
        if(favoriteAttempts===4)return route.fulfill({status:503,body:'{}',contentType:'application/json'});
        favorite=url.searchParams.get('favorite')==='true';
        return json({favorite});
      }
      if(p.endsWith('/description') && req.method()==='POST') {
        assert.equal(req.headers()['x-xsrf-token'],'test-csrf');
        description=new URLSearchParams(req.postData()).get('description'); writes.push(description);
        return json({description});
      }
      if(p.endsWith('/lookup')) {
        const next=url.searchParams.get('page')==='1';
        if(next&&failMore){failMore=false;return route.fulfill({status:503,body:'{}',contentType:'application/json'});}
        const transfers=Array.from({length:next?1:20},(_,i)=>({timestamp:`2026-10-${next?'01':'26'}T10:00:00Z`,nickname:i===0?'Current':'Previous'}));
        return json({status:'available',serial:1,page:next?1:0,total:21,hasNext:!next,owner:{status:'indexed',nickname:'Current',timestamp:'2026-10-26T10:00:00Z',basis:'latest_transfer'},transfers});
      }
      if(p.startsWith('/cosmo/objekt/detail/') && (req.headers().accept||'').includes('application/json')) return json(data());
      if(p==='/cosmo/objekt' || p==='/cosmo/objekt/detail/hayeoncc367z') {
        const file=p==='/cosmo/objekt'?'objekt-modal-preview.html':'objekt-detail-preview.html';
        return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(base,'build',file),'utf8')});
      }
      if(p.endsWith('/admin/status'))return json({isAdmin});
      const file=path.resolve(base,'src/main/resources/static','.'+p);
      if(file.startsWith(path.join(base,'src/main/resources/static')+path.sep) && fs.existsSync(file) && fs.statSync(file).isFile()) {
        return route.fulfill({path:file,contentType:p.endsWith('.js')?'application/javascript':p.endsWith('.css')?'text/css':undefined});
      }
      return route.fulfill({status:404,body:''});
    });
    await page.goto('https://preview.test/cosmo/objekt');
    await page.locator('.objekt-open-detail').first().click();
    await page.locator('#objektDetailTitle').filter({hasText:'Cream02 HaYeon 367Z'}).waitFor();
    await page.locator('#objektDetailFavorite').click();
    await page.waitForFunction(()=>document.querySelector('#objektDetailFavorite').dataset.favorite==='true');
    assert.equal(favoriteAttempts,2);
    await page.locator('#objektDetailFavorite').click();
    await page.waitForFunction(()=>document.querySelector('#objektDetailFavorite').dataset.favorite==='false');
    await page.locator('#objektDetailFavorite').click();
    await page.waitForFunction(()=>document.querySelector('#objektFavoriteStatus').textContent.includes('저장하지 못했습니다'));
    assert.equal(await page.locator('#objektDetailFavorite').getAttribute('data-favorite'),'false');
    assert.equal(await page.locator('#objektDetailFavorite').isEnabled(),true);
    assert.equal(await page.locator('#objektDetailFullLink').textContent(),'상세 페이지 보기');
    assert.match(await page.locator('#objektDetailMemberLink').getAttribute('href'),/\/cosmo\/objekt\?group=members&member=HaYeon/);
    assert.match(await page.locator('#objektDetailTradeLink').getAttribute('href'),/selectedObjektIds=uuid/);
    assert.match(await page.locator('#objektDetailSellLink').getAttribute('href'),/\/article\/sell\/new\?selectedObjektIds=uuid/);
    await page.locator('#objektDescriptionEditor summary').click();
    await page.locator('#objektModalDescription').fill('<b>새 설명</b>');
    await page.locator('#objektDescriptionForm button[type=submit]').click();
    await page.waitForFunction(()=>document.querySelector('#objektDescriptionStatus').textContent.includes('저장했습니다'));
    assert.equal(await page.locator('#objektDetailDesc').textContent(),'<b>새 설명</b>');
    assert.equal(await page.locator('#objektDetailDesc b').count(),0);
    page.once('dialog',dialog=>dialog.accept()); await page.locator('#objektDescriptionDelete').click();
    await page.waitForFunction(()=>document.querySelector('#objektDescriptionStatus').textContent.includes('삭제했습니다'));
    assert.deepEqual(writes,['<b>새 설명</b>','']);
    fs.mkdirSync('/tmp/objekt-ui',{recursive:true});
    await page.screenshot({path:'/tmp/objekt-ui/modal-desktop.png',fullPage:true});
    await page.setViewportSize({width:390,height:844});
    assert.equal(await page.locator('.objekt-detail-panel').evaluate(node=>node.getBoundingClientRect().right<=innerWidth),true);
    await page.screenshot({path:'/tmp/objekt-ui/modal-mobile.png',fullPage:true});
    await page.locator('#objektDetailClose').click(); isAdmin=false;
    await page.locator('.objekt-open-detail').first().click();
    await page.waitForFunction(()=>document.querySelector('#objektDetailTitle').textContent==='Cream02 HaYeon 367Z');
    assert.equal(await page.locator('#objektDescriptionEditor').isVisible(),false);
    await page.goto('https://preview.test/cosmo/objekt/detail/hayeoncc367z');
    const favoriteForm=page.locator('[data-objekt-favorite]');
    await favoriteForm.locator('button').click();
    await page.waitForFunction(()=>document.querySelector('[data-objekt-favorite] button').getAttribute('aria-pressed')==='true');
    await favoriteForm.locator('button').click();
    await page.waitForFunction(()=>document.querySelector('[data-objekt-favorite] button').getAttribute('aria-pressed')==='false');
    await page.evaluate(()=>document.fonts.ready);
    assert.equal(await page.evaluate(()=>document.fonts.check('16px "Material Symbols Outlined"')),true);
    assert.equal(await page.locator('[data-admin-only]').count(),0);
    assert.equal(await page.locator('[data-serial-lookup]').count(),1);
    await page.locator('[data-serial-lookup] button').click();
    await page.locator('.serial-owner-name').filter({hasText:'Current'}).waitFor();
    assert.equal(await page.locator('[data-serial-rows] li').count(),20);
    await page.locator('[data-serial-more]').click();
    await page.waitForFunction(()=>document.querySelector('[data-serial-status]').textContent.includes('불러오지 못했습니다'));
    assert.equal(await page.locator('[data-serial-rows] li').count(),20);
    await page.locator('[data-serial-more]').click();
    await page.waitForFunction(()=>document.querySelectorAll('[data-serial-rows] li').length===21);
    await page.screenshot({path:'/tmp/objekt-ui/serial-mobile.png',fullPage:true});
    console.log('PASS: 모달 설명 저장·삭제·CSRF, 비관리자 숨김, 선택 유지 링크, 통합 시리얼 조회와 실패 후 이어받기');
  } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});
