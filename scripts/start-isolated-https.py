#!/usr/bin/env python3
"""별도 실행기의 비밀번호 게이트웨이와 임시 HTTPS 연결."""
import argparse, hashlib, hmac, http.cookiejar, json, os, pathlib, re, secrets, subprocess, time
import urllib.parse, urllib.request, urllib.error

p=argparse.ArgumentParser()
p.add_argument("--runtime",required=True)
p.add_argument("--upstream",required=True)
p.add_argument("--pr",type=int,required=True)
p.add_argument("--revision",required=True)
args=p.parse_args()
runtime=json.loads(pathlib.Path(args.runtime).read_text())
password=runtime.get("adminPassword","")
if not password: raise RuntimeError("관리자 비밀번호가 없어 HTTPS 연결을 시작하지 않습니다")
upstream=urllib.parse.urlsplit(args.upstream)
if upstream.scheme!="http" or not upstream.hostname or upstream.port!=18000+args.pr:
    raise ValueError("미리보기 주소 오류")
root=pathlib.Path(os.environ["RUNNER_TEMP"])/f"pr-{args.pr}"
root.mkdir(mode=0o700,exist_ok=True)
salt=secrets.token_bytes(16)
access={"salt":salt.hex(),"passwordHash":hashlib.pbkdf2_hmac("sha256",password.encode(),salt,260000).hex(),"session":secrets.token_urlsafe(32)}
config=root/"gateway-access.json"
config.write_text(json.dumps(access),encoding="utf-8")
config.chmod(0o600)
env=os.environ.copy()
env.update({"APP_PREVIEW_NUMBER":str(args.pr),"PREVIEW_ACCESS_CONFIG":str(config),
            "PREVIEW_UPSTREAM_HOST":upstream.hostname,"PREVIEW_UPSTREAM_PORT":str(upstream.port),
            "PREVIEW_GATEWAY_BIND":"127.0.0.1","PREVIEW_GATEWAY_PORT":"18080"})
gateway=subprocess.Popen(["python3","scripts/pr-preview-mobile-gateway.py"],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
(root/"gateway.pid").write_text(str(gateway.pid))
logfile=root/"cloudflared.log"
log=open(logfile,"w")
tunnel=subprocess.Popen([os.environ["CLOUDFLARED_BIN"],"tunnel","--no-autoupdate","--protocol","http2","--url","http://127.0.0.1:18080"],stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
(root/"tunnel.pid").write_text(str(tunnel.pid))
url=None
for _ in range(90):
    if gateway.poll() is not None or tunnel.poll() is not None: raise RuntimeError("HTTPS 연결 프로세스가 종료되었습니다")
    match=re.search(r"https://[a-z0-9-]+\.trycloudflare\.com",logfile.read_text(errors="replace"))
    if match: url=match.group(0);break
    time.sleep(2)
if not url: raise RuntimeError("HTTPS 주소 생성 시간 초과")
last=None
for _ in range(30):
    try:
        cookies=http.cookiejar.CookieJar()
        opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))
        try:
            opener.open(url+"/api/preview/info",timeout=10)
            raise RuntimeError("로그인 전 API 접근 차단 실패")
        except urllib.error.HTTPError as err:
            if err.code!=401: raise
        with opener.open(url+"/__preview/login",timeout=10) as response:
            html=response.read().decode("utf-8")
        csrf=re.search(r'name="csrf" value="([^"]+)"',html)
        if not csrf: raise RuntimeError("로그인 CSRF 누락")
        body=urllib.parse.urlencode({"password":password,"csrf":csrf.group(1)}).encode()
        opener.open(urllib.request.Request(url+"/__preview/login",data=body,method="POST"),timeout=15).read()
        with opener.open(url+"/api/preview/info",timeout=15) as response:
            info=json.load(response)
        if info.get("revision")!=args.revision or info.get("pr")!=args.pr:
            raise RuntimeError("HTTPS 미리보기 리비전 불일치")
        with pathlib.Path(os.environ["GITHUB_OUTPUT"]).open("a") as output:
            output.write("url="+url+"\n")
        print("암호 보호 HTTPS 로그인 및 미리보기 커밋 확인 완료")
        break
    except (OSError,ValueError,RuntimeError) as error:
        last=type(error).__name__
        time.sleep(3)
else:
    raise RuntimeError("HTTPS 로그인 검증 실패: "+str(last))
