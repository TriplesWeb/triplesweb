import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import net from 'node:net';
import {startGateway} from '../gateway.mjs';
import {relayRequest} from '../tunnel.mjs';
import {startTunnel} from '../tunnel.mjs';
import {WebSocketServer} from 'ws';

const password='a-local-test-password-only';
function call(port,url,method='GET',body=Buffer.alloc(0),headers={}) {
  return new Promise((resolve,reject)=>{
    const req=http.request({hostname:'127.0.0.1',port,path:url,method,headers:{...headers,'Content-Length':body.length}},res=>{
      const chunks=[];res.on('data',c=>chunks.push(c));res.on('end',()=>resolve({status:res.statusCode,headers:res.headers,body:Buffer.concat(chunks)}));
    });req.on('error',reject);req.end(body);
  });
}
function command(dir,action,values={}) {
  return new Promise((resolve,reject)=>{
    const s=net.createConnection(path.join(dir,'control.sock'));let data='';
    s.on('connect',()=>s.write(JSON.stringify({action,...values})+'\n'));
    s.on('data',chunk=>data+=chunk);s.on('end',()=>resolve(JSON.parse(data)));s.on('error',reject);
  });
}
async function login(port) {
  const form=await call(port,'/_preview/login');
  const nonce=form.body.toString().match(/name="nonce" value="([^"]+)"/)[1];
  const body=Buffer.from(new URLSearchParams({password,nonce}).toString());
  const reply=await call(port,'/_preview/login','POST',body,{Cookie:form.headers['set-cookie'][0].split(';')[0],'Content-Type':'application/x-www-form-urlencoded'});
  assert.equal(reply.status,303);
  assert.match(reply.headers['set-cookie'][0],/Secure; HttpOnly; SameSite=Lax/);
  return reply.headers['set-cookie'][0].split(';')[0];
}
async function fixture(t,options={}) {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pr58-gateway-'));
  const gateway=await startGateway({password,stateDir:dir,port:0,drainMs:2000,...options});
  t.after(async()=>{await gateway.close();fs.rmSync(dir,{recursive:true,force:true});});
  return {dir,gateway,cookie:await login(gateway.port)};
}
async function backend(t,handler) {
  const server=http.createServer(handler);
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  t.after(()=>new Promise(r=>{server.closeAllConnections();server.close(r);}));
  return server.address().port;
}

test('authentication protects API/static/status, rejects forged login, expires sessions',async t=>{
  const {gateway,cookie}=await fixture(t,{sessionMs:50});
  for(const url of ['/api/chemistry','/css/site.css'])assert.equal((await call(gateway.port,url)).status,303);
  assert.equal((await call(gateway.port,'/_preview/status')).status,401);
  assert.equal((await call(gateway.port,'/api/chemistry','PUT',Buffer.from('{}'))).status,401);
  assert.equal((await call(gateway.port,'/_preview/login','POST',Buffer.from('password='+password))).status,401);
  await new Promise(r=>setTimeout(r,60));
  assert.equal((await call(gateway.port,'/_preview/status','GET',undefined,{Cookie:cookie})).status,401);
  const health=await call(gateway.port,'/_preview/health');
  assert.deepEqual(Object.keys(JSON.parse(health.body)).sort(),['healthId','service']);
});

test('proxy preserves methods, binary bodies, cookies, query and CSRF without leaking preview cookie',async t=>{
  let saved;
  const port=await backend(t,async(req,res)=>{
    const chunks=[];for await(const chunk of req)chunks.push(chunk);
    if(req.method==='PUT')saved=Buffer.concat(chunks);
    res.setHeader('Set-Cookie',['JSESSIONID=app; HttpOnly','XSRF-TOKEN=csrf']);
    res.setHeader('Content-Type','application/json');
    res.end(JSON.stringify({method:req.method,url:req.url,body:Buffer.concat(chunks).toString('base64'),headers:req.headers,saved:saved?.toString('base64')}));
  });
  const {dir,gateway,cookie}=await fixture(t);
  await command(dir,'activate',{port});
  const bytes=Buffer.from([0,255,128,10,42]);
  for(const method of ['GET','POST','PUT','PATCH','DELETE','OPTIONS']) {
    const reply=await call(gateway.port,'/api/example?q=a%20b',method,bytes,{Cookie:cookie+'; JSESSIONID=app; XSRF-TOKEN=csrf','X-XSRF-TOKEN':'csrf','Content-Type':'application/octet-stream','X-Forwarded-For':'spoofed'});
    assert.equal(reply.status,200);
    const result=JSON.parse(reply.body);
    assert.equal(result.method,method);assert.equal(result.body,bytes.toString('base64'));assert.equal(result.url,'/api/example?q=a%20b');
    assert.equal(result.headers['x-xsrf-token'],'csrf');assert.doesNotMatch(result.headers.cookie,/preview-session/);assert.match(result.headers.cookie,/JSESSIONID=app/);
    assert.notEqual(result.headers['x-forwarded-for'],'spoofed');assert.equal(reply.headers['set-cookie'].length,2);
  }
});

test('multipart and Range responses survive the Tunnelmole protocol adapter',async t=>{
  let multipart;
  const file=Buffer.from([0,1,255,127,88,0]);
  const port=await backend(t,async(req,res)=>{
    if(req.method==='POST'){const chunks=[];for await(const chunk of req)chunks.push(chunk);multipart=Buffer.concat(chunks);res.end('uploaded');return;}
    assert.equal(req.headers.range,'bytes=2-4');res.writeHead(206,{'Content-Range':'bytes 2-4/6','Content-Type':'application/octet-stream'});res.end(file.subarray(2,5));
  });
  const {dir,gateway,cookie}=await fixture(t);await command(dir,'activate',{port});
  const data=Buffer.concat([Buffer.from('--boundary\r\nContent-Disposition: form-data; name="file"; filename="x.bin"\r\n\r\n'),file,Buffer.from('\r\n--boundary--\r\n')]);
  const upload=await relayRequest({requestId:'1',url:'/upload',method:'POST',headers:{Cookie:cookie,'Content-Type':'multipart/form-data; boundary=boundary'},body:data.toString('base64')},gateway.port,'example.tunnelmole.net');
  assert.equal(upload.statusCode,200);assert.deepEqual(multipart,data);
  const download=await relayRequest({requestId:'2',url:'/file',method:'GET',headers:{Cookie:cookie,Range:'bytes=2-4'},body:''},gateway.port,'example.tunnelmole.net');
  assert.equal(download.statusCode,206);assert.equal(download.headers['content-range'],'bytes 2-4/6');assert.deepEqual(Buffer.from(download.body,'base64'),file.subarray(2,5));
  const tooBig=await relayRequest({requestId:'3',url:'/upload',method:'POST',headers:{},body:data.toString('base64')},gateway.port,'example.tunnelmole.net',3);
  assert.equal(tooBig.statusCode,413);
});

test('quiesce drains existing requests, rejects new work, and activation restores service',async t=>{
  let release,entered;
  const reached=new Promise(r=>entered=r);
  const port=await backend(t,async(_req,res)=>{entered();await new Promise(r=>release=r);res.end('done');});
  const {dir,gateway,cookie}=await fixture(t);await command(dir,'activate',{port});
  const old=call(gateway.port,'/slow','GET',undefined,{Cookie:cookie});await reached;
  let drained=false;const quiesce=command(dir,'quiesce').then(r=>{drained=true;return r;});
  await new Promise(r=>setTimeout(r,50));assert.equal(drained,false);
  assert.equal((await call(gateway.port,'/new','GET',undefined,{Cookie:cookie})).status,503);
  release();assert.equal((await old).status,200);assert.equal((await quiesce).ok,true);
  assert.equal((await command(dir,'status')).inFlight,0);
});

test('new public URLs remain sealed until that exact URL is externally verified',async t=>{
  const port=await backend(t,(_req,res)=>res.end('app'));
  const {dir,gateway,cookie}=await fixture(t,{requireExternalVerification:true});await command(dir,'activate',{port});
  gateway.setTunnel('https://first.tunnelmole.net','connected');
  assert.equal((await call(gateway.port,'/main','GET',undefined,{Cookie:cookie})).status,503);
  assert.equal((await command(dir,'verify-tunnel',{url:'https://other.tunnelmole.net'})).ok,false);
  await command(dir,'verify-tunnel',{url:'https://first.tunnelmole.net'});
  assert.equal((await call(gateway.port,'/main','GET',undefined,{Cookie:cookie})).status,200);
  gateway.setTunnel('https://second.tunnelmole.net','connected');
  assert.equal((await call(gateway.port,'/main','GET',undefined,{Cookie:cookie})).status,503);
});

test('Tunnelmole handshake and a 21 MiB binary response work through a simulated relay',async t=>{
  const bytes=Buffer.alloc(21*1024*1024,0x93);
  const app=await backend(t,(_req,res)=>{res.setHeader('Content-Type','application/octet-stream');res.end(bytes);});
  const service=new WebSocketServer({host:'127.0.0.1',port:0});
  await new Promise(r=>service.once('listening',r));
  t.after(()=>new Promise(r=>service.close(r)));
  let resolveResponse,rejectResponse;
  const response=new Promise((resolve,reject)=>{resolveResponse=resolve;rejectResponse=reject;});
  service.on('connection',socket=>socket.on('message',raw=>{
    try{
      const message=JSON.parse(raw);
      if(message.type==='initialise') {
        assert.ok(message.clientId);
        socket.send(JSON.stringify({type:'hostnameAssigned',hostname:'test.tunnelmole.net'}));
        socket.send(JSON.stringify({type:'forwardedRequest',requestId:'large',url:'/file',method:'GET',headers:{},body:''}));
      }else if(message.type==='forwardedResponse')resolveResponse(message);
    }catch(error){rejectResponse(error);}
  }));
  const states=[];
  const tunnel=startTunnel({port:app,endpoint:`ws://127.0.0.1:${service.address().port}`,proxy:null,onState:(url,state)=>states.push({url,state})});
  try{
    const message=await response;
    assert.equal(message.statusCode,200);assert.deepEqual(Buffer.from(message.body,'base64'),bytes);
    assert.ok(states.some(s=>s.url==='https://test.tunnelmole.net'&&s.state==='connected'));
  }finally{tunnel.close();}
});
