import fs from 'node:fs';
import path from 'node:path';
import {startGateway} from './gateway.mjs';
import {startTunnel} from './tunnel.mjs';

const stateDir=process.env.PREVIEW_STATE_DIR;
if(!stateDir)throw new Error('PREVIEW_STATE_DIR is required');
const localOnly=process.argv.includes('--local-only');
const gateway=await startGateway({password:process.env.PREVIEW_ACCESS_PASSWORD,stateDir,port:Number(process.env.PREVIEW_GATEWAY_PORT||8787),requireExternalVerification:!localOnly});
function atomic(name,value){const file=path.join(stateDir,name);fs.writeFileSync(file+'.tmp',JSON.stringify(value),{mode:0o600});fs.renameSync(file+'.tmp',file);}
atomic('gateway.json',{port:gateway.port,healthId:gateway.healthId});
let tunnel;
if(!localOnly) {
  let denied=false;
  tunnel=startTunnel({port:gateway.port,onState:(url,state)=>{
    gateway.setTunnel(url,state);
    atomic('tunnel.json',{url,state});
    if(state.startsWith('blocked:')&&!denied){denied=true;setImmediate(()=>tunnel.close());}
  }});
}else atomic('tunnel.json',{url:null,state:'local-only'});
let closing=false;
async function stop(){if(closing)return;closing=true;tunnel?.close();await gateway.close();process.exit(0);}
process.on('SIGTERM',stop);process.on('SIGINT',stop);
