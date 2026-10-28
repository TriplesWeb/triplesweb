#!/usr/bin/env python3
"""PR 26의 분리된 미리보기에만 암호로 보호된 임시 HTTPS 연결을 추가한다."""
import argparse
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

def run(args,timeout=180):
    result=subprocess.run(args,stdin=subprocess.DEVNULL,capture_output=True,text=True,timeout=timeout)
    if result.returncode:
        raise RuntimeError("모바일 미리보기 명령 실패: "+args[0])
    return result.stdout

def save(path,value):
    path.write_text(json.dumps(value),encoding="utf-8")
    path.chmod(0o600)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--root",required=True)
    parser.add_argument("--gateway",required=True)
    args=parser.parse_args()
    root=Path(args.root).resolve()
    if not (root/"docker-compose.yml").is_file():
        raise RuntimeError("배포 스택 경로 확인 필요")
    directory=(root/"previews"/"pr-26"/"mobile").resolve()
    if root not in directory.parents:
        raise RuntimeError("미리보기 경로 오류")
    info=json.loads(run(["docker","inspect","triples-pr26-preview"]))[0]
    if info["Config"].get("Labels",{}).get("org.triples.preview.pr")!="26" or not info["State"]["Running"]:
        raise RuntimeError("PR 26 미리보기 컨테이너가 필요합니다")
    env=dict(value.split("=",1) for value in info["Config"].get("Env",[]) if "=" in value)
    password=env.get("CHEMISTRY_ADMIN_PASSWORD","")
    if not password:
        raise RuntimeError("관리자 비밀번호가 없어 공개 연결을 만들지 않습니다")
    network=next(iter(info["NetworkSettings"]["Networks"]))
    for name in ("triples-pr26-mobile-gateway","triples-pr26-mobile-tunnel"):
        old=subprocess.run(["docker","inspect",name],stdin=subprocess.DEVNULL,capture_output=True,text=True)
        if old.returncode==0 and json.loads(old.stdout)[0]["Config"].get("Labels",{}).get("org.triples.preview.mobile")!="26":
            raise RuntimeError("다른 컨테이너를 덮어쓰지 않습니다")
    directory.mkdir(parents=True,exist_ok=True,mode=0o700)
    directory.chmod(0o700)
    access_file=directory/"access.json"
    session=json.loads(access_file.read_text())["session"] if access_file.exists() else secrets.token_urlsafe(32)
    salt=secrets.token_bytes(16)
    save(access_file,{"salt":salt.hex(),"passwordHash":hashlib.pbkdf2_hmac("sha256",password.encode(),salt,260000).hex(),"session":session})
    gateway_file=directory/"gateway.py"
    gateway_file.write_text(Path(args.gateway).read_text(),encoding="utf-8")
    gateway_file.chmod(0o600)
    labels={"org.triples.preview.mobile":"26"}
    spec={"services":{
        "gateway":{"image":"python:3.12-alpine","container_name":"triples-pr26-mobile-gateway","restart":"unless-stopped","labels":labels,
                   "command":["python","-u","/app/gateway.py"],"user":str(os.getuid())+":"+str(os.getgid()),"mem_limit":"128m","cpus":"0.5",
                   "volumes":[str(gateway_file)+":/app/gateway.py:ro",str(access_file)+":/config/access.json:ro"],
                   "networks":["app","mobile"],"read_only":True,"tmpfs":["/tmp"],"cap_drop":["ALL"],"security_opt":["no-new-privileges:true"]},
        "tunnel":{"image":"cloudflare/cloudflared:latest","container_name":"triples-pr26-mobile-tunnel","restart":"unless-stopped","labels":labels,
                  "command":["tunnel","--no-autoupdate","--protocol","http2","--url","http://gateway:8080"],
                  "depends_on":["gateway"],"mem_limit":"128m","cpus":"0.5","networks":["mobile"],"cap_drop":["ALL"],"security_opt":["no-new-privileges:true"]}
    },"networks":{"app":{"external":True,"name":network},"mobile":{}}}
    compose=directory/"compose.json"
    save(compose,spec)
    command=["docker","compose","-p","triples-pr26-mobile","-f",str(compose)]
    run([*command,"pull"],timeout=300)
    run([*command,"up","-d"],timeout=120)
    run(["docker","restart","triples-pr26-mobile-gateway"],timeout=30)
    url=None
    for _ in range(60):
        logs=subprocess.run(["docker","logs","--tail","500","triples-pr26-mobile-tunnel"],stdin=subprocess.DEVNULL,capture_output=True,text=True)
        text=logs.stdout+logs.stderr
        found=re.findall(r"https://[a-z0-9-]+\.trycloudflare\.com",text)
        if found:
            url=found[-1]
        if url:
            try:
                jar=http.cookiejar.CookieJar()
                opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
                with opener.open(url+"/__preview/login",timeout=20) as login_page:
                    form=login_page.read().decode("utf-8")
                csrf=re.search(r'name="csrf" value="([^"]+)"',form).group(1)
                request=urllib.request.Request(url+"/__preview/login",data=urllib.parse.urlencode({"password":password,"csrf":csrf}).encode(),headers={"Origin":url})
                with opener.open(request,timeout=25) as response:
                    html=response.read().decode("utf-8")
                    if response.status!=200 or "/js/pr-preview.js" not in html:
                        raise RuntimeError("로그인 후 화면 확인 실패")
                with opener.open(url+"/api/preview/info",timeout=20) as response:
                    build=json.load(response)
                    if build.get("pr")!=26:
                        raise RuntimeError("잘못된 미리보기 응답")
                try:
                    urllib.request.urlopen(url+"/api/preview/info",timeout=15)
                    raise RuntimeError("로그인 보호가 적용되지 않았습니다")
                except urllib.error.HTTPError as error:
                    if error.code!=401:
                        raise
                print("HTTPS_PREVIEW_LOGIN_AND_PAGE_VERIFIED=26")
                print("PUBLIC_PREVIEW_URL="+url+"/cosmo/objekt")
                print("PUBLIC_PREVIEW_REVISION="+build["revision"])
                return
            except (OSError,ValueError,RuntimeError):
                pass
        time.sleep(3)
    raise RuntimeError("HTTPS 연결 또는 로그인 보호 검증이 완료되지 않았습니다")

if __name__=="__main__":
    main()
