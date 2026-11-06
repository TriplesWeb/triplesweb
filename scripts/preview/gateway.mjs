import http from 'node:http';
import net from 'node:net';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const cookieName = '__Host-preview-session';
const nonceName = '__Host-preview-login';
const escapeHtml = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const same = (a, b) => {
  const x = crypto.createHash('sha256').update(String(a)).digest();
  const y = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(x, y);
};
const cookies = value => Object.fromEntries((value || '').split(';').map(x => x.trim().split(/=(.*)/s)).filter(x => x.length >= 2).map(x => [x[0], x[1]]));
const hopHeaders = ['connection','keep-alive','proxy-authenticate','proxy-authorization','te','trailer','transfer-encoding','upgrade'];
export function cleanHeaders(input) {
  const headers = Object.fromEntries(Object.entries(input).map(([k,v]) => [k.toLowerCase(),v]));
  const connection = String(headers.connection || '').toLowerCase().split(',').map(x => x.trim());
  for (const key of [...hopHeaders, ...connection]) delete headers[key];
  return headers;
}
function page(title, body) {
  return `<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title><style>body{font:16px system-ui;background:#111827;color:#f9fafb;max-width:48rem;margin:3rem auto;padding:1rem;line-height:1.7}input,button,a{font:inherit}input{box-sizing:border-box;width:100%;padding:1rem;margin:.5rem 0}button{padding:.7rem 1rem}a{color:#93c5fd}pre{white-space:pre-wrap;overflow-wrap:anywhere}label{display:block}</style><h1>${title}</h1>${body}</html>`;
}

export async function startGateway({password, stateDir, port=8787, sessionMs=8*3600_000, drainMs=60_000, requireExternalVerification=false}) {
  if (!password || password.length < 12) throw new Error('PREVIEW_ACCESS_PASSWORD must contain at least 12 characters');
  fs.mkdirSync(stateDir, {recursive:true, mode:0o700});
  const sessions = new Map();
  const loginKey = crypto.randomBytes(32);
  const healthId = crypto.randomUUID();
  let maintenance = true, backendPort = null, inFlight = 0;
  let publicUrl = null, tunnelState = 'disconnected', externallyVerified = false;
  let loginAttempts = 0, loginWindow = Date.now();
  const status = () => {
    let saved = {};
    try { saved = JSON.parse(fs.readFileSync(path.join(stateDir,'status.json'),'utf8')); } catch {}
    return {...saved, maintenance, inFlight, publicUrl, tunnelState, externallyVerified};
  };
  const cookie = (name, value, age) => `${name}=${value}; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=${age}`;
  const signedNonce = () => {
    const value = `${Date.now()}:${crypto.randomBytes(16).toString('hex')}`;
    return `${value}:${crypto.createHmac('sha256', loginKey).update(value).digest('hex')}`;
  };
  const validNonce = nonce => {
    const parts = String(nonce || '').split(':');
    if (parts.length !== 3 || Date.now()-Number(parts[0]) > 600_000 || Number(parts[0]) > Date.now()) return false;
    return same(parts[2],crypto.createHmac('sha256',loginKey).update(parts.slice(0,2).join(':')).digest('hex'));
  };
  const server = http.createServer(async (req,res) => {
    res.setHeader('Cache-Control','private, no-store');
    res.setHeader('X-Content-Type-Options','nosniff');
    res.setHeader('Referrer-Policy','no-referrer');
    res.setHeader('X-Frame-Options','DENY');
    const send = (code,body,type='text/html; charset=utf-8') => {res.writeHead(code,{'Content-Type':type});res.end(body);};
    let url;
    try {
      if (!req.url.startsWith('/') || req.url.startsWith('//')) throw new Error();
      url = new URL(req.url,'http://preview.local');
    } catch { send(400,'Invalid path'); return; }
    if (url.pathname === '/_preview/health' && req.method === 'GET') {
      send(200,JSON.stringify({service:'pr-preview',healthId}),'application/json'); return;
    }
    const jar = cookies(req.headers.cookie);
    const session = sessions.get(jar[cookieName]);
    const authenticated = session && session > Date.now();
    if (session && !authenticated) sessions.delete(jar[cookieName]);
    if (url.pathname === '/_preview/login' && req.method === 'POST') {
      // Check both an unforgeable form nonce and the browser Origin.
      const origin = req.headers.origin;
      if (origin && origin !== (publicUrl || `http://${req.headers.host}`)) {send(403,'Forbidden');return;}
      if (Date.now()-loginWindow > 600_000) {loginAttempts=0;loginWindow=Date.now();}
      if (++loginAttempts > 10) {res.setHeader('Retry-After','600');send(429,'잠시 후 다시 시도해주세요.');return;}
      let body = '';
      try {
        for await (const chunk of req) {body += chunk.toString();if(Buffer.byteLength(body)>4096) throw new Error();}
      } catch {send(413,'Request too large');return;}
      const form = new URLSearchParams(body);
      if (!same(form.get('nonce'),jar[nonceName]) || !validNonce(form.get('nonce')) || !same(form.get('password'),password)) {
        send(401,page('로그인 실패','<p>비밀번호를 확인해주세요.</p><a href="/_preview/login">다시 로그인</a>')); return;
      }
      // Bound memory even when one legitimate user opens many sessions.
      for (const [id, expiry] of sessions) if (expiry <= Date.now()) sessions.delete(id);
      if (sessions.size >= 100) sessions.delete(sessions.keys().next().value);
      const id = crypto.randomBytes(32).toString('hex');
      sessions.set(id,Date.now()+sessionMs);
      res.setHeader('Set-Cookie',[cookie(cookieName,id,sessionMs/1000),cookie(nonceName,'',0)]);
      res.writeHead(303,{Location:'/_preview'});res.end();return;
    }
    if (!authenticated) {
      if(url.pathname==='/_preview/status'){send(401,JSON.stringify({error:'Preview login required'}),'application/json');return;}
      if (req.method !== 'GET' && req.method !== 'HEAD') {send(401,'Preview login required');return;}
      const nonce = signedNonce();
      res.setHeader('Set-Cookie',cookie(nonceName,nonce,600));
      if (url.pathname !== '/_preview/login') {res.writeHead(303,{Location:'/_preview/login'});res.end();return;}
      send(200,page('PR #58 미리보기',`<form method="post" action="/_preview/login"><input type="hidden" name="nonce" value="${nonce}"><label>미리보기 비밀번호<input type="password" name="password" autocomplete="current-password" required></label><button>접속</button></form>`));return;
    }
    if (url.pathname === '/_preview/login') {res.writeHead(303,{Location:'/_preview'});res.end();return;}
    if (url.pathname === '/_preview/logout' && req.method === 'POST') {
      if (req.headers.origin !== (publicUrl || `http://${req.headers.host}`)) {send(403,'Forbidden');return;}
      sessions.delete(jar[cookieName]);res.setHeader('Set-Cookie',cookie(cookieName,'',0));res.writeHead(303,{Location:'/_preview/login'});res.end();return;
    }
    if (url.pathname === '/_preview/status' && req.method === 'GET') {send(200,JSON.stringify(status()),'application/json');return;}
    if ((url.pathname === '/_preview' || url.pathname === '/_preview/') && req.method === 'GET') {
      const initial=status();
      send(200,page('PR #58 미리보기',`<p><a href="/main">사이트 열기 →</a></p><p id="phase">상태를 확인하고 있습니다.</p><dl><dt>실행 중인 버전</dt><dd id="running">${escapeHtml(initial.runningCommit?.slice(0,7)||'준비 중')}</dd><dt>최신 감지 버전</dt><dd id="detected">${escapeHtml(initial.detectedCommit?.slice(0,7)||'확인 중')}</dd><dt>마지막 확인</dt><dd id="updated"></dd></dl><p id="error" role="status"></p><p>미리보기에서 저장한 내용은 별도 복사본에만 반영됩니다.</p><form method="post" action="/_preview/logout"><button>로그아웃</button></form><script>const labels={starting:'시작 중',building:'새 버전을 빌드하고 있습니다.',deploying:'새 버전으로 전환 중입니다.',ready:'최신 적용 버전을 확인할 수 있습니다.',failed:'새 버전 적용에 실패했습니다. 기존 버전을 유지합니다.',superseded:'더 최신 버전을 감지했습니다.',blocked:'연결 또는 설정을 확인해야 합니다.','copying-data':'데이터를 복사하고 있습니다.','copying-files':'파일을 복사하고 있습니다.','recovery-failed':'복구 확인이 필요합니다. 점검 상태입니다.','server-stopped':'실행 서버가 중지됐습니다.','poll-failed':'PR 업데이트를 다시 확인하고 있습니다.'};async function refresh(){try{const r=await fetch('/_preview/status');if(!r.ok){location.href='/_preview/login';return}const s=await r.json();document.querySelector('#phase').textContent=labels[s.phase]||'준비 중';document.querySelector('#running').textContent=s.runningCommit?.slice(0,7)||'준비 중';document.querySelector('#detected').textContent=s.detectedCommit?.slice(0,7)||'확인 중';document.querySelector('#updated').textContent=s.updatedAt?new Date(s.updatedAt).toLocaleString():'확인 중';document.querySelector('#error').textContent=s.error||''}catch{document.querySelector('#phase').textContent='연결을 다시 확인하고 있습니다.'}}refresh();setInterval(refresh,5000)</script>`));return;
    }
    if (url.pathname.startsWith('/_preview/')) {send(404,'Not found');return;}
    if (maintenance || !backendPort || (requireExternalVerification && !externallyVerified)) {res.setHeader('Retry-After','10');send(503,page('미리보기 준비 중','<p>업데이트 또는 접속 확인 중입니다. 잠시 후 새로고침해주세요.</p>'));return;}
    const headers = cleanHeaders(req.headers);
    headers.cookie = (req.headers.cookie || '').split(';').filter(x => ![cookieName,nonceName].includes(x.trim().split('=')[0])).join(';');
    delete headers.forwarded;
    for (const key of Object.keys(headers)) if(key.startsWith('x-forwarded-')) delete headers[key];
    const publicOrigin = publicUrl ? new URL(publicUrl) : new URL(`http://${req.headers.host}`);
    headers.host = publicOrigin.host;
    headers['x-forwarded-host'] = publicOrigin.host;
    headers['x-forwarded-proto'] = publicOrigin.protocol.slice(0,-1);
    headers['x-forwarded-for'] = req.socket.remoteAddress;
    inFlight++;
    let finished = false;
    const finish = () => {if(!finished){finished=true;inFlight--;}};
    res.once('finish',finish);res.once('close',finish);
    const upstream = http.request({hostname:'127.0.0.1',port:backendPort,path:req.url,method:req.method,headers}, reply => {
      const result = cleanHeaders(reply.headers);
      result['cache-control'] = 'private, no-store';
      if(result.location) {
        try {const target=new URL(result.location);if(['127.0.0.1','localhost'].includes(target.hostname)) result.location=publicOrigin.origin+target.pathname+target.search+target.hash;}catch{}
      }
      res.writeHead(reply.statusCode,result);reply.pipe(res);
      reply.on('error',()=>res.destroy());
    });
    upstream.setTimeout(120_000,()=>upstream.destroy(new Error('Backend timeout')));
    upstream.on('error',()=>{if(!res.headersSent)send(502,'미리보기 서버 응답이 없습니다.');else res.destroy();});
    req.on('aborted',()=>upstream.destroy());
    res.on('close',()=>upstream.destroy());
    req.pipe(upstream);
  });
  server.requestTimeout=120_000;
  server.headersTimeout=30_000;
  const socketPath = path.join(stateDir,'control.sock');
  if(fs.existsSync(socketPath)) throw new Error('control.sock already exists; check for a running preview before removing a stale socket');
  const control = net.createServer(socket => {
    let data='';
    socket.setTimeout(drainMs+5000,()=>socket.destroy());
    socket.on('data',async chunk=>{
      data+=chunk.toString();if(data.length>4096){socket.destroy();return;}
      if(!data.includes('\n'))return;
      socket.removeAllListeners('data');
      try {
        const command=JSON.parse(data.split('\n')[0]);
        if(command.action==='quiesce') {
          maintenance=true;
          const start=Date.now();
          while(inFlight && Date.now()-start<drainMs) await new Promise(r=>setTimeout(r,25));
          if(inFlight) throw new Error('Requests did not drain; deployment cancelled');
        } else if(command.action==='activate') {
          if(!Number.isInteger(command.port)||command.port<1024||command.port>65535)throw new Error('Invalid backend port');
          backendPort=command.port;maintenance=false;
        } else if(command.action==='verify-tunnel') {
          if(!publicUrl || command.url!==publicUrl)throw new Error('Tunnel URL changed before verification');
          externallyVerified=true;
        }
        else if(command.action!=='status')throw new Error('Unknown command');
        socket.end(JSON.stringify({ok:true,...status()})+'\n');
      } catch(e) {socket.end(JSON.stringify({ok:false,error:e.message})+'\n');}
    });
  });
  await new Promise((resolve,reject)=>{control.once('error',reject);control.listen(socketPath,resolve);});
  fs.chmodSync(socketPath,0o600);
  try {await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(port,'127.0.0.1',resolve);});}
  catch(e){control.close();throw e;}
  return {
    port:server.address().port,healthId,status,
    setTunnel(url,state){publicUrl=url;tunnelState=state;externallyVerified=false;},
    close:async()=>{server.closeAllConnections();await Promise.all([new Promise(r=>server.close(r)),new Promise(r=>control.close(r))]);}
  };
}
