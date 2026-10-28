const assert=require('node:assert/strict');
const {chromium}=require('playwright');
(async()=>{
  const url=new URL(process.env.PUBLIC_PREVIEW_URL);
  assert.equal(url.protocol,'https:');
  const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||undefined,args:['--no-sandbox']});
  try{
    const context=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
    const page=await context.newPage();
    await page.goto(url.origin+'/__preview/login',{waitUntil:'domcontentloaded'});
    await page.locator('input[name="csrf"]').waitFor({state:'attached'});
    assert.ok((await context.cookies()).some(cookie=>cookie.name==='__Host-PR26LOGINCSRF'&&cookie.secure&&cookie.httpOnly));
    const icon=await context.request.get(url.origin+'/favicon.ico');
    assert.equal(icon.status(),204);
    const secondTab=await context.newPage();
    await secondTab.goto(url.origin+'/__preview/login',{waitUntil:'domcontentloaded'});
    await secondTab.close();
    await page.waitForTimeout(1000);
    await page.locator('input[name="password"]').fill('invalid-preview-password-for-browser-test');
    const responsePromise=page.waitForResponse(response=>response.request().method()==='POST'&&new URL(response.url()).pathname==='/__preview/login');
    await page.getByRole('button',{name:'미리보기 열기'}).click();
    const response=await responsePromise;
    assert.equal(response.status(),401,'브라우저 요청은 주소 비교 차단 없이 비밀번호 검증까지 도달해야 합니다');
    await page.getByText('비밀번호를 확인해 주세요.').waitFor();
    assert.equal(await page.locator('input[name="csrf"]').count(),1);
    console.log('통과: 실제 HTTPS 모바일 Chrome 폼·아이콘 요청·다중 탭·입력 대기 후 CSRF 제출·비밀번호 오류 후 재시도 화면');
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
