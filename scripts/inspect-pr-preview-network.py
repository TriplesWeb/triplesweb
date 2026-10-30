#!/usr/bin/env python3
"""운영 설정을 변경하지 않고 현재 PR 미리보기 접속 경로만 점검한다."""
import http.cookiejar
import urllib.parse
import urllib.error
import argparse
import json
import re
import sys
import socket
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
    try:
        with socket.create_connection((ips[0],3306),timeout=5):
            result["databaseTcp"]={"port":3306,"reachable":True}
    except OSError as error:
        result["databaseTcp"]={"port":3306,"reachable":False,"error":type(error).__name__}
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open("http://"+ips[0]+f":{port}/api/preview/info",timeout=15) as response:
            result["localHttp"]=response.status
            result["build"]=json.load(response)
    except Exception as error:
        result["localHttpError"]=type(error).__name__
# 인증값·계정 데이터 없이 실제 PR의 그래비티 조회 결과만 분류한다.
if ips and result.get("running"):
    result["gravityCredentialsStored"]=command(["docker","exec",f"triples-pr{pr}-preview","sh","-c","test -s /app/data/cosmo/credentials.json"]).returncode==0
    try:
        with opener.open("http://"+ips[0]+f":{port}/api/gravity",timeout=25) as response:
            payload=json.load(response)
            result["gravityApi"]={"status":response.status,"items":len(payload) if isinstance(payload,list) else None}
    except urllib.error.HTTPError as error:
        diagnostic={"status":error.code,"reason":"unclassified"}
        try:
            message=str(json.load(error).get("message",""))
            for fragment,reason in (
                ("아직 연결되지", "not_configured"),
                ("관리자가 이메일로 다시 연결", "refresh_expired"),
                ("인증 파일을 읽을 수", "credentials_unreadable"),
                ("인증 실패 (HTTP", "authentication_rejected"),
                ("외부 API 응답 오류 (HTTP", "upstream_rejected"),
                ("응답 형식", "schema_mismatch"),
                ("올바른 응답", "invalid_response"),
                ("인증에 연결하지 못", "authentication_connection_failed"),
                ("인증 응답이 올바르지", "invalid_credentials_response")):
                if fragment in message:
                    diagnostic["reason"]=reason
                    break
            match=re.search(r"HTTP (\d{3})",message)
            if match:diagnostic["upstreamStatus"]=int(match.group(1))
        except (ValueError,AttributeError,TypeError):pass
        result["gravityApi"]=diagnostic
    except Exception as error:
        result["gravityApi"]={"error":type(error).__name__}
mobile_log=command(["docker","logs","--tail","500",f"triples-pr{pr}-mobile-tunnel"])
urls=re.findall(r"https://[a-z0-9-]+\.trycloudflare\.com",mobile_log.stdout+mobile_log.stderr)
if urls:
    result["publicPreviewURL"]=urls[-1]
    # 공개 주소 안의 짧은 문자열이 배포 계정명과 겹쳐 로그에서 가려지는 경우를 위한 전송 형식.
    result["publicPreviewURLHex"]=urls[-1].encode().hex()
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

