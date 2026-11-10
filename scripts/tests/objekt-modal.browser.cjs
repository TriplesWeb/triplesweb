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
    let isAdmin = true, description = '기존 설명', writes = [], failMore = true, apolloStory = null;
    const data = () => ({isFavorite:favorite,objektKey:'hayeoncc367z',collectionId:'Cream02 HaYeon 367Z',member:'HaYeon',season:'Cream02',class:'Special',collectionNo:'367Z',description,isAdmin,
      detailUrl:'/cosmo/objekt/detail/hayeoncc367z',memberUrl:'/cosmo/objekt?group=members&member=HaYeon',tradeUrl:'/article/trade/new?selectedObjektIds=uuid',sellUrl:'/article/sell/new?selectedObjektIds=uuid',descriptionUrl:'/cosmo/objekt/detail/hayeoncc367z/description',
      descriptionScope:'group',descriptionGroupLabel:'Cream02 367Z',descriptionGroupCount:24,
      ...(apolloStory ? {apollo: apolloStory} : {})});
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
        favorite=(url.searchParams.get('favorite') || new URLSearchParams(req.postData()).get('favorite'))==='true';
        return json({favorite});
      }
      if(p.endsWith('/description') && req.method()==='POST') {
        assert.equal(req.headers()['x-xsrf-token'],'test-csrf');
        const params=new URLSearchParams(req.postData());
        description=params.get('description'); writes.push({description,scope:params.get('scope')});
        const scope=params.get('scope')||'group';
        return json({description,descriptionScope:scope,descriptionGroupLabel:'Cream02 367Z',descriptionGroupCount:24});
      }
      if(p.endsWith('/lookup')) {
        const next=url.searchParams.get('page')==='1';
        if(next&&failMore){failMore=false;return route.fulfill({status:503,body:'{}',contentType:'application/json'});}
        const transfers=Array.from({length:next?1:20},(_,i)=>({timestamp:`2026-10-${next?'01':'26'}T10:00:00Z`,nickname:i===0?'Current':'Previous'}));
        return json({status:'available',serial:1,page:next?1:0,total:21,hasNext:!next,owner:{status:'indexed',nickname:'Current',timestamp:'2026-10-26T10:00:00Z',basis:'latest_transfer'},transfers});
      }
      if(p.endsWith('/issued'))return json({status:'available',count:4,ranges:[{first:1,last:2},{first:4,last:5}],hasMore:false,nextOffset:2,observedAt:'2026-10-29T00:00:00Z'});
      if(p==='/cosmo/objekt/supplies')return json({hayeoncc367z:{status:'available',count:4,observedAt:'2026-10-29T00:00:00Z'}});
      if(p==='/cosmo/objekt/pulse')return json({catalogSig:'1:test',catalogCount:1,supplies:{hayeoncc367z:{status:'available',count:4}},dropWindow:false,pollMs:60000,forceRefreshMs:600000,historyTtlMs:30000});
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
    const memberStyle = await page.locator('#objektDetailMember').evaluate((el) => {
      const style = getComputedStyle(el);
      return {color: style.color, textShadow: style.textShadow};
    });
    assert.equal(memberStyle.color, 'rgb(255, 255, 255)');
    assert.equal(memberStyle.textShadow, 'none');
    await page.locator('#objektDetailFavorite').click();
    await page.waitForFunction(()=>document.querySelector('#objektDetailFavorite').dataset.favorite==='true');
    assert.equal(favoriteAttempts,2);
    await page.locator('#objektDetailFavorite').click();
    await page.waitForFunction(()=>document.querySelector('#objektDetailFavorite').dataset.favorite==='false');
    await page.locator('#objektDetailFavorite').click();
    await page.waitForFunction(()=>document.querySelector('#objektFavoriteStatus').textContent.includes('저장하지 못했습니다'));
    assert.equal(await page.locator('#objektDetailFavorite').getAttribute('data-favorite'),'false');
    assert.equal(await page.locator('#objektDetailFavorite').isEnabled(),true);
    assert.equal(await page.locator('#objektDetailFullLink').count(),0);
    await page.waitForFunction(()=>document.querySelector('#objektIssuedCount').textContent==='4개');
    assert.equal(await page.locator('#objektIssuedRanges, #objektIssuedTime').count(),0);
    assert.match(await page.locator('#objektDetailMemberLink').getAttribute('href'),/\/cosmo\/objekt\?group=members&member=HaYeon/);
    assert.match(await page.locator('#objektDetailTradeLink').getAttribute('href'),/selectedObjektIds=uuid/);
    assert.match(await page.locator('#objektDetailSellLink').getAttribute('href'),/\/article\/sell\/new\?selectedObjektIds=uuid/);
    assert.equal(await page.locator('#objektDescriptionForm').isVisible(),false);
    await page.locator('#objektDescriptionEdit').click();
    assert.equal(await page.locator('#objektDetailDesc').isVisible(),false);
    assert.equal(await page.locator('#objektModalDescription').inputValue(),'기존 설명');
    assert.equal(await page.locator('#objektDescriptionScope').isVisible(),true);
    assert.equal(await page.locator('input[name=descriptionScope][value=group]').isChecked(),true);
    assert.match(await page.locator('#objektDescriptionScopeHint').textContent(), /Cream02 367Z 전체 24장/);
    await page.locator('input[name=descriptionScope][value=member]').check();
    assert.match(await page.locator('#objektDescriptionScopeHint').textContent(), /이 카드만/);
    await page.locator('input[name=descriptionScope][value=group]').check();
    await page.locator('#objektModalDescription').fill('<b>새 설명</b>');
    await page.locator('#objektDescriptionForm button[type=submit]').click();
    await page.waitForFunction(()=>document.querySelector('#objektDescriptionStatus').textContent.includes('저장했습니다'));
    assert.equal(await page.locator('#objektDetailDesc').isVisible(),true);
    assert.equal(await page.locator('#objektDescriptionForm').isVisible(),false);
    assert.equal(await page.locator('#objektDetailDesc').textContent(),'<b>새 설명</b>');
    assert.equal(await page.locator('#objektDetailDesc b').count(),0);
    await page.locator('#objektDescriptionEdit').click();
    page.once('dialog',dialog=>dialog.accept()); await page.locator('#objektDescriptionDelete').click();
    await page.waitForFunction(()=>document.querySelector('#objektDescriptionStatus').textContent.includes('삭제했습니다'));
    assert.equal(await page.locator('#objektDescriptionForm').isVisible(),false);
    assert.deepEqual(writes,[{description:'<b>새 설명</b>',scope:'group'},{description:'',scope:'group'}]);
    fs.mkdirSync('/tmp/objekt-ui',{recursive:true});
    await page.screenshot({path:'/tmp/objekt-ui/modal-desktop.png',fullPage:true});
    await page.setViewportSize({width:390,height:844});
    assert.equal(await page.locator('.objekt-detail-panel').evaluate(node=>node.getBoundingClientRect().right<=innerWidth),true);
    await page.screenshot({path:'/tmp/objekt-ui/modal-mobile.png',fullPage:true});
    await page.locator('#objektDetailClose').click(); isAdmin=false;
    await page.locator('.objekt-open-detail').first().click();
    await page.waitForFunction(()=>document.querySelector('#objektDetailTitle').textContent==='Cream02 HaYeon 367Z');
    assert.equal(await page.locator('#objektDescriptionEdit').isVisible(),false);
    assert.equal(await page.locator('#objektDescriptionForm').isVisible(),false);
    // 소유자 조회·전송 내역은 별도 페이지가 아니라 같은 모달 안에 존재한다.
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
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('#objektDetailModal').isVisible(),false);
    assert.equal(new URL(page.url()).searchParams.has('objekt'),false);
    await page.goto('https://preview.test/cosmo/objekt?objekt=hayeoncc367z');
    await page.locator('#objektDetailTitle').filter({hasText:'Cream02 HaYeon 367Z'}).waitFor();
    assert.equal(await page.locator('#objektDetailModal').isVisible(),true);
    await page.locator('#objektDetailClose').click();
    description = 'tripleS 공식 캐릭터 byteS 인형 키링의 일부';
    apolloStory = null;
    isAdmin = true;
    await page.locator('.objekt-open-detail').first().click();
    await page.waitForFunction(()=>document.querySelector('#objektDetailDesc')?.textContent.includes('byteS'));
    assert.match(await page.locator('#objektDetailDesc').textContent(), /byteS/);
    await page.locator('#objektDetailClose').click();
    description = '카코마린 커버송 이벤트 오브젝트\nKaKoMaLynn Cover Song Objekt';
    await page.locator('.objekt-open-detail').first().click();
    await page.waitForFunction(()=>document.querySelector('#objektDetailDesc')?.textContent.includes('KaKoMaLynn'));
    const storyGap = await page.locator('#objektDetailDesc').evaluate((el) => ({
      blocks: [...el.querySelectorAll('.objekt-detail-desc-block')].map((node) => node.textContent),
      breaks: el.querySelectorAll('.objekt-detail-desc-break').length,
      breakHeight: el.querySelector('.objekt-detail-desc-break')?.getBoundingClientRect().height || 0,
      lineHeight: parseFloat(getComputedStyle(el).lineHeight),
      leading: parseFloat(getComputedStyle(el).lineHeight) / parseFloat(getComputedStyle(el).fontSize)
    }));
    assert.deepEqual(storyGap.blocks, ['카코마린 커버송 이벤트 오브젝트', 'KaKoMaLynn Cover Song Objekt']);
    assert.equal(storyGap.breaks, 1);
    assert.ok(Math.abs(storyGap.leading - 1.05) < 0.08, '상세 설명 행 간격: ' + storyGap.leading);
    assert.ok(storyGap.breakHeight > 4 && storyGap.breakHeight < storyGap.lineHeight * 0.9, '엔터 간격: ' + storyGap.breakHeight);
    await page.locator('#objektDetailClose').click();
    description = '첫 줄\n\n둘째 줄';
    await page.locator('.objekt-open-detail').first().click();
    await page.waitForFunction(()=>document.querySelector('#objektDetailDesc')?.textContent.includes('둘째 줄'));
    const enterGap = await page.locator('#objektDetailDesc').evaluate((el) => ({
      blocks: [...el.querySelectorAll('.objekt-detail-desc-block')].map((node) => node.textContent),
      breaks: el.querySelectorAll('.objekt-detail-desc-break').length
    }));
    assert.deepEqual(enterGap.blocks, ['첫 줄', '둘째 줄']);
    assert.equal(enterGap.breaks, 1);
    const leadingOf = (el) => {
      const style = getComputedStyle(el);
      return parseFloat(style.lineHeight) / parseFloat(style.fontSize);
    };
    const descLeading = await page.locator('#objektDetailDesc').evaluate(leadingOf);
    assert.ok(Math.abs(descLeading - 1.05) < 0.08, '상세 설명 행 간격: ' + descLeading);
    await page.locator('#objektDescriptionEdit').click();
    const editorLeading = await page.locator('#objektModalDescription').evaluate(leadingOf);
    assert.ok(Math.abs(editorLeading - 1.05) < 0.08, '수정폼 행 간격: ' + editorLeading);
    await page.locator('#objektDetailClose').click();
    description = 'tripleS 공식 캐릭터 byteS 인형 키링의 일부';
    await page.locator('.objekt-open-detail').first().click();
    await page.waitForFunction(()=>document.querySelector('#objektDetailDesc')?.textContent.includes('byteS'));
    assert.match(await page.locator('#objektDetailDesc').textContent(), /byteS/);
    assert.doesNotMatch(await page.locator('#objektDetailDesc').textContent(), /Plush Keyring/);
    assert.equal(await page.locator('#objektDetailEvent').isHidden(), true);
    await page.locator('#objektDescriptionEdit').click();
    assert.equal(await page.locator('#objektModalDescription').inputValue(), description);
    await page.locator('#objektDetailClose').click();
    isAdmin = false;
    await page.locator('.objekt-open-detail').first().click();
    await page.waitForFunction(()=>document.querySelector('#objektDetailDesc')?.textContent.includes('byteS'));
    assert.equal(await page.locator('#objektDescriptionEdit').isVisible(),false);
    await page.locator('#objektDetailClose').click();
    await page.evaluate(()=>{
      const original=document.querySelector('.objekt-card[data-objekt-key]');
      for(let i=0;i<49;i++){const card=original.cloneNode(true);delete card.dataset.supplyDone;delete card.dataset.supplyTries;original.parentElement.append(card);}
      window.ObjektIssued.hydrate();
    });
    await page.waitForFunction(()=>document.querySelectorAll('.objekt-card[data-supply-done="true"]').length===50);
    console.log('PASS: 모달 설명 저장·삭제·CSRF, 비관리자 숨김, 선택 유지 링크, 통합 시리얼 조회와 실패 후 이어받기');
  } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});
