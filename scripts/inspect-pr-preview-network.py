#!/usr/bin/env python3
"""운영 설정을 변경하지 않고 현재 PR 미리보기 접속 경로만 점검한다."""
import http.cookiejar
import urllib.parse
import urllib.error
import argparse
import json
import re
import sys
import subprocess
import urllib.request

def command(args):
    return subprocess.run(args, stdin=subprocess.DEVNULL, capture_output=True, text=True, timeout=20)

parser=argparse.ArgumentParser()
parser.add_argument("--pr",type=int,required=True)
pr=parser.parse_args().pr
if not 1<=pr<=9999:
    raise ValueError("PR 번호 범위 오류")
port=18000+pr

status=command(["tailscale","status","--json"])
if status.returncode:
    raise SystemExit("Tailscale 상태 확인 실패")
tail=json.loads(status.stdout)
node=tail.get("Self",{})
ips=[ip for ip in node.get("TailscaleIPs",[]) if ":" not in ip]
dns=node.get("DNSName","").rstrip(".")
result={"backend":tail.get("BackendState"),"dns":dns,"ip":ips[0] if ips else "",
        "previousAddressMatches": "100.89.171.59" in ips}
container=command(["docker","inspect",f"triples-pr{pr}-preview"])
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
        with opener.open("http://"+ips[0]+f":{port}/api/preview/info",timeout=15) as response:
            result["localHttp"]=response.status
            result["build"]=json.load(response)
    except Exception as error:
        result["localHttpError"]=type(error).__name__
mobile_log=command(["docker","logs","--tail","500",f"triples-pr{pr}-mobile-tunnel"])
urls=re.findall(r"https://[a-z0-9-]+\.trycloudflare\.com",mobile_log.stdout+mobile_log.stderr)
if urls:
    result["publicPreviewURL"]=urls[-1]
    if container.returncode==0:
        # 비밀번호는 메모리에서만 사용하고 상태·경로만 출력한다.
        environment=dict(value.split("=",1) for value in data.get("Config",{}).get("Env",[]) if "=" in value)
        password=environment.get("CHEMISTRY_ADMIN_PASSWORD", "")
        stage="login_page"
        try:
            jar=http.cookiejar.CookieJar()
            public=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
            base=urls[-1]
            with public.open(base+"/__preview/login",timeout=15) as page:
                form=page.read().decode()
            csrf=re.search(r'name="csrf" value="([^"]+)"',form).group(1)
            stage="login_submit"
            request=urllib.request.Request(base+"/__preview/login",data=urllib.parse.urlencode({"password":password,"csrf":csrf}).encode())
            with public.open(request,timeout=20) as page:
                result["publicLogin"]={"status":page.status,"path":urllib.parse.urlsplit(page.url).path,"previewPage":"/js/pr-preview.js" in page.read().decode()}
            result["publicPages"]=[]
            for path in ("/api/preview/info", "/sitemap", "/cosmo/group-orders", "/article/group-orders"):
                stage=path
                try:
                    with public.open(base+path,timeout=15) as page:
                        body=page.read().decode()
                        result["publicPages"].append({"path":path,"status":page.status,"finalScheme":urllib.parse.urlsplit(page.url).scheme,"finalPath":urllib.parse.urlsplit(page.url).path,"board":"분철 게시판" in body,"previewAsset":"/js/pr-preview.js" in body})
                except Exception as error:
                    result["publicPages"].append({"path":path,"error":type(error).__name__,"status":getattr(error,"code",None)})
        except Exception as error:
            result["publicLoginError"]={"stage":stage,"error":type(error).__name__,"status":getattr(error,"code",None)}
print(json.dumps(result,ensure_ascii=False))
for name in (f"triples-pr{pr}-preview",f"triples-pr{pr}-mobile-gateway",f"triples-pr{pr}-mobile-tunnel"):
    check=command(["docker","inspect",name])
    if check.returncode:
        print(name+" MISSING",file=sys.stderr)
        continue
    details=json.loads(check.stdout)[0]
    print(json.dumps({"container":name,"state":details.get("State"),"user":details.get("Config",{}).get("User"),"restartCount":details.get("RestartCount")}),file=sys.stderr)
    if not name.endswith("tunnel"):
        log=command(["docker","logs","--tail","35",name])
        print(log.stdout+log.stderr,file=sys.stderr)

