// Tunnelmole's public initialise/forwardedRequest/forwardedResponse protocol.
// Keep the managed HTTPS proxy and CA trust; never dial around an egress denial.
import http from 'node:http';
import crypto from 'node:crypto';
import WebSocket from 'ws';
import { HttpsProxyAgent } from 'https-proxy-agent';
import { cleanHeaders } from './gateway.mjs';

export function relayRequest(message, port, hostname, limit=64*1024*1024) {
  return new Promise(resolve => {
    const finish = (statusCode,body,headers={}) => resolve({type:'forwardedResponse',requestId:message.requestId,url:message.url,statusCode,headers,body:Buffer.from(body).toString('base64')});
    if (!['GET','HEAD','POST','PUT','PATCH','DELETE','OPTIONS'].includes(message.method) || typeof message.url!=='string' || !message.url.startsWith('/') || message.url.startsWith('//')) {
      finish(400,'Invalid request');return;
    }
    if(typeof message.body!=='string' || message.body.length>Math.ceil(limit/3)*4 || !/^[A-Za-z0-9+/]*={0,2}$/.test(message.body)) {
      finish(413,'Temporary tunnel body limit exceeded');return;
    }
    const body=Buffer.from(message.body,'base64');
    if(body.length>limit){finish(413,'Temporary tunnel body limit exceeded');return;}
    const headers=cleanHeaders(message.headers||{});
    headers.host=hostname;headers['content-length']=String(body.length);
    const request=http.request({hostname:'127.0.0.1',port,path:message.url,method:message.method,headers},response=>{
      const chunks=[];let size=0;
      response.on('data',chunk=>{
        size+=chunk.length;
        if(size>limit){response.destroy();finish(502,'Temporary tunnel response limit exceeded');}
        else chunks.push(chunk);
      });
      response.on('end',()=>finish(response.statusCode,Buffer.concat(chunks),cleanHeaders(response.headers)));
      response.on('error',()=>finish(502,'Local response interrupted'));
    });
    request.setTimeout(120_000,()=>request.destroy());
    request.on('error',()=>finish(502,'Local gateway unavailable'));
    request.end(body);
  });
}

export function startTunnel({port,onState,limit=64*1024*1024,endpoint='wss://service.tunnelmole.com:8083',clientId=crypto.randomUUID(),proxy=process.env.HTTPS_PROXY||process.env.https_proxy}) {
  const agent=proxy ? new HttpsProxyAgent(proxy) : undefined;
  let socket,heartbeat,closed=false,assigned=false;
  const connect=()=>{
    if(closed)return;
    assigned=false;
    onState(null,'connecting');
    socket=new WebSocket(endpoint,{agent,handshakeTimeout:15_000,maxPayload:Math.ceil(limit*1.4)+65536});
    let hostname=null,pending=0;
    socket.once('open',()=>{
      socket.send(JSON.stringify({type:'initialise',clientId,connectionInfo:{isCli:true,isNpm:false,nodeVersion:process.version,tunnelmoleVersion:'2.4.0-compatible'}}));
      heartbeat=setInterval(()=>{if(socket.readyState===WebSocket.OPEN)socket.ping();},25_000);
    });
    socket.on('message',async raw=>{
      let message;
      try{message=JSON.parse(raw.toString());}catch{socket.close(1002);return;}
      if(!message || typeof message!=='object' || Array.isArray(message)){socket.close(1002);return;}
      if(message.type==='hostnameAssigned') {
        if(assigned || typeof message.hostname!=='string' || !/^[a-z0-9][a-z0-9.-]*\.tunnelmole\.net$/.test(message.hostname)) {socket.close(1002);return;}
        assigned=true;hostname=message.hostname;
        onState(`https://${hostname}`,'connected');
      } else if(message.type==='forwardedRequest' && hostname) {
        if(pending>=4){socket.send(JSON.stringify({type:'forwardedResponse',requestId:message.requestId,url:message.url,statusCode:429,headers:{'retry-after':'5'},body:Buffer.from('Preview is busy').toString('base64')}));return;}
        pending++;
        try{
          const result=await relayRequest(message,port,hostname,limit);
          if(socket.readyState===WebSocket.OPEN)socket.send(JSON.stringify(result));
        }catch{
          if(socket.readyState===WebSocket.OPEN)socket.send(JSON.stringify({type:'forwardedResponse',requestId:message.requestId,url:message.url,statusCode:400,headers:{},body:''}));
        }finally{pending--;}
      } else if(message.type==='clientDisconnect') socket.close();
    });
    socket.on('error',error=>{
      // Avoid logging bodies, headers or proxy credentials.
      const blocked=/403/.test(error.message);
      if(blocked){closed=true;clearInterval(heartbeat);}
      onState(null,blocked?'blocked: HTTP proxy 403':'connection failed');
    });
    socket.once('close',()=>{
      clearInterval(heartbeat);
      if(!closed){onState(null,'disconnected');retry=setTimeout(connect,30_000);}
    });
  };
  let retry;
  connect();
  return {close(){closed=true;clearTimeout(retry);clearInterval(heartbeat);socket?.terminate();}};
}
