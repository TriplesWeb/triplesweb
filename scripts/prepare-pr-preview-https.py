#!/usr/bin/env python3
"""기존 Serve 설정을 보존하면서 PR 전용 비공개 HTTPS 연결을 준비한다."""
import json
import socket
import subprocess

PORT=18443

def run(args):
    return subprocess.run(args,stdin=subprocess.DEVNULL,capture_output=True,text=True,timeout=25)

status=run(["tailscale","status","--json"])
if status.returncode:
    raise SystemExit("Tailscale 상태 확인 실패")
tail=json.loads(status.stdout)
node=tail.get("Self",{})
dns=node.get("DNSName","").rstrip(".")
ip=next((value for value in node.get("TailscaleIPs",[]) if ":" not in value),"")
result={"dns":dns,"ip":ip,"port":PORT,"ready":False}
if not dns or not ip:
    raise SystemExit("서버 Tailscale 주소가 없습니다")
if dns not in tail.get("CertDomains",[]):
    result.update(reason="HTTPS_NOT_ENABLED",setupUrl="https://login.tailscale.com/admin/dns")
else:
    current=run(["tailscale","serve","status","--json"])
    if current.returncode:
        raise SystemExit("기존 Serve 설정을 확인할 수 없어 변경하지 않습니다")
    config=json.loads(current.stdout or "{}") or {}
    target="http://"+ip+":18026"
    web=config.get("Web",{}).get(dns+":"+str(PORT),{})
    existing=web.get("Handlers",{}).get("/",{}).get("Proxy")
    if str(PORT) in config.get("TCP",{}) and existing!=target:
        result["reason"]="EXISTING_SERVE_PORT_PRESERVED"
    else:
        occupied=False
        if not existing:
            with socket.socket() as sock:
                sock.settimeout(2)
                occupied=sock.connect_ex((ip,PORT))==0
        if occupied:
            result["reason"]="EXISTING_LISTENER_PRESERVED"
        else:
            args=["tailscale","serve","--bg","--https="+str(PORT),target]
            attempt=run(args)
            if attempt.returncode and ("Access denied" in attempt.stderr or "Permission denied" in attempt.stderr):
                attempt=run(["sudo","-n",*args])
            result.update(ready=attempt.returncode==0,detail=(attempt.stdout+attempt.stderr)[-1500:])
            if not result["ready"]:
                result["reason"]="SERVE_CONFIGURATION_FAILED"
if result["ready"]:
    result["url"]="https://"+dns+":"+str(PORT)
print(json.dumps(result,ensure_ascii=False))
