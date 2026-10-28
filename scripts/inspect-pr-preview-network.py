#!/usr/bin/env python3
"""운영 설정을 변경하지 않고 현재 PR 미리보기 접속 경로만 점검한다."""
import json
import subprocess
import urllib.request

def command(args):
    return subprocess.run(args, stdin=subprocess.DEVNULL, capture_output=True, text=True, timeout=20)

status=command(["tailscale","status","--json"])
if status.returncode:
    raise SystemExit("Tailscale 상태 확인 실패")
tail=json.loads(status.stdout)
node=tail.get("Self",{})
ips=[ip for ip in node.get("TailscaleIPs",[]) if ":" not in ip]
dns=node.get("DNSName","").rstrip(".")
result={"backend":tail.get("BackendState"),"dns":dns,"ip":ips[0] if ips else "",
        "previousAddressMatches": "100.89.171.59" in ips}
container=command(["docker","inspect","triples-pr26-preview"])
if container.returncode:
    result["containerExists"]=False
else:
    data=json.loads(container.stdout)[0]
    state=data.get("State",{})
    result.update(containerExists=True,running=state.get("Running"),oomKilled=state.get("OOMKilled"),
                  restartCount=data.get("RestartCount"),ports=data.get("NetworkSettings",{}).get("Ports",{}))
if ips:
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open("http://"+ips[0]+":18026/api/preview/info",timeout=15) as response:
            result["localHttp"]=response.status
            result["build"]=json.load(response)
    except Exception as error:
        result["localHttpError"]=type(error).__name__
print(json.dumps(result,ensure_ascii=False))
