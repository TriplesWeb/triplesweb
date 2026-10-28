#!/usr/bin/env python3
"""PR 26 모바일 미리보기 전용 로그인 게이트웨이."""
from collections import defaultdict, deque
import hashlib
import hmac
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import http.client
import json
import os
import secrets
from pathlib import Path
import time
from urllib.parse import parse_qs, urlsplit

CONFIG=json.loads(Path(os.environ.get("PREVIEW_ACCESS_CONFIG","/config/access.json")).read_text())
COOKIE="PR26MOBILEACCESS"
LOGIN_COOKIE="__Host-PR26LOGINCSRF"
ATTEMPTS=defaultdict(deque)
HOP={"connection","keep-alive","proxy-authenticate","proxy-authorization","te","trailer","transfer-encoding","upgrade"}
PAGE="""<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>PR #26 미리보기</title><style>*{box-sizing:border-box}body{margin:0;min-height:100dvh;display:grid;place-items:center;background:#10151b;color:#f0f6f6;font:16px/1.6 system-ui,sans-serif;padding:24px}main{width:min(100%,390px);border:1px solid #35474c;border-radius:24px;padding:28px;background:#172028}small{color:#69d7c8}h1{font-size:25px;margin:8px 0 12px}p{color:#b8c9ce}input,button{font:inherit;width:100%;border-radius:12px;padding:12px;margin-top:12px}input{background:#10151b;border:1px solid #52676f;color:white}button{border:0;background:#69d7c8;color:#0c2724;font-weight:700}label{display:block;margin-top:20px}.error{color:#ffb7b7}</style></head><body><main><small>tripleS · PR #26</small><h1>모바일 미리보기</h1><p>사이트 관리자 비밀번호로 접속하세요.</p>MESSAGE<form method="post" action="/__preview/login"><input type="hidden" name="csrf" value="CSRFVALUE"><label for="password">관리자 비밀번호</label><input id="password" name="password" type="password" autocomplete="current-password" required autofocus><button type="submit">미리보기 열기</button></form></main></body></html>"""

def password_matches(password):
    digest=hashlib.pbkdf2_hmac("sha256",password.encode(),bytes.fromhex(CONFIG["salt"]),260000).hex()
    return hmac.compare_digest(digest,CONFIG["passwordHash"])

WARMING_PAGE="""<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="refresh" content="5"><title>미리보기 준비 중</title><style>body{margin:0;min-height:100dvh;display:grid;place-items:center;background:#10151b;color:#edf7f6;font:16px/1.7 system-ui;padding:24px;box-sizing:border-box}main{max-width:390px}h1{font-size:24px}p{color:#b8c9ce}a{color:#69d7c8}</style></head><body><main><h1>미리보기를 준비하고 있어요</h1><p>서버를 시작하고 있습니다. 잠시 기다리면 자동으로 다시 연결합니다.</p><a href="">지금 다시 확인</a></main></body></html>"""

class Handler(BaseHTTPRequestHandler):
    timeout=30
    protocol_version="HTTP/1.1"
    def log_message(self,*args):
        pass
    def reply(self,status,body=b"",content_type="text/html; charset=utf-8",headers=()):
        self.send_response(status)
        self.send_header("Content-Type",content_type)
        self.send_header("Content-Length",str(len(body)))
        self.send_header("Cache-Control","no-store")
        self.send_header("Referrer-Policy","no-referrer")
        self.send_header("X-Content-Type-Options","nosniff")
        for key,value in headers:
            self.send_header(key,value)
        self.end_headers()
        if self.command!="HEAD":
            self.wfile.write(body)
    def login_page(self,status=200,message=""):
        token=""
        try:
            cookie=SimpleCookie(self.headers.get("Cookie","")).get(LOGIN_COOKIE)
            if cookie and self.valid_login_csrf(cookie.value):
                token=cookie.value
        except Exception:
            pass
        if not token:
            nonce=secrets.token_urlsafe(24)
            signature=hmac.new(CONFIG["session"].encode(),nonce.encode(),hashlib.sha256).hexdigest()
            token=nonce+"."+signature
        page=PAGE.replace("MESSAGE",message).replace("CSRFVALUE",token).encode()
        self.reply(status,page,headers=(("Set-Cookie",LOGIN_COOKIE+"="+token+"; Path=/; HttpOnly; Secure; SameSite=Lax"),))
    def valid_login_csrf(self,token):
        try:
            cookies=SimpleCookie(self.headers.get("Cookie",""))
            cookie=cookies.get(LOGIN_COOKIE)
            if not cookie or not hmac.compare_digest(cookie.value,token):
                return False
            nonce,signature=token.split(".")
            expected=hmac.new(CONFIG["session"].encode(),nonce.encode(),hashlib.sha256).hexdigest()
            return hmac.compare_digest(signature,expected)
        except (ValueError,TypeError):
            return False
    def authenticated(self):
        try:
            cookies=SimpleCookie(self.headers.get("Cookie",""))
            value=cookies.get(COOKIE)
            return bool(value) and hmac.compare_digest(value.value,CONFIG["session"])
        except Exception:
            return False
    def handle_request(self):
        parsed=urlsplit(self.path)
        path=parsed.path
        if path in {"/favicon.ico","/favicon.png","/apple-touch-icon.png","/apple-touch-icon-precomposed.png"} and not self.authenticated():
            self.reply(204)
            return
        if path=="/__preview/login":
            if self.command=="GET":
                self.login_page()
                return
            if self.command!="POST":
                self.reply(405)
                return
            size=int(self.headers.get("Content-Length","0"))
            if not 0<size<=2048:
                self.reply(413)
                return
            fields=parse_qs(self.rfile.read(size).decode("utf-8",errors="replace"))
            if not self.valid_login_csrf(fields.get("csrf",[""])[0]):
                self.login_page(403,'<p class="error">로그인 화면을 새로고침한 뒤 다시 시도해 주세요.</p>')
                return
            password=fields.get("password",[""])[0]
            key=self.headers.get("CF-Connecting-IP","unknown")[:64]
            now=time.monotonic()
            attempts=ATTEMPTS[key]
            while attempts and now-attempts[0]>60:
                attempts.popleft()
            if len(attempts)>=8:
                self.login_page(429,'<p class="error">잠시 후 다시 시도해 주세요.</p>')
                return
            attempts.append(now)
            if not password_matches(password):
                self.login_page(401,'<p class="error">비밀번호를 확인해 주세요.</p>')
                return
            attempts.clear()
            self.reply(303,headers=(("Location","/cosmo/objekt"),("Set-Cookie",COOKIE+"="+CONFIG["session"]+"; Path=/; HttpOnly; Secure; SameSite=Lax")))
            return
        if not self.authenticated():
            if path.startswith("/api/"):
                self.reply(401,b'{"error":"preview_login_required"}',"application/json")
            else:
                self.reply(303,headers=(("Location","/__preview/login"),))
            return
        if path=="/__preview/logout":
            self.reply(303,headers=(("Location","/__preview/login"),("Set-Cookie",COOKIE+"=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0")))
            return
        size=int(self.headers.get("Content-Length","0"))
        if size>100*1024*1024 or size<0:
            self.reply(413)
            return
        body=self.rfile.read(size) if size else None
        headers={key:value for key,value in self.headers.items() if key.lower() not in HOP and key.lower() not in {"authorization","cookie","x-forwarded-proto","x-forwarded-host"}}
        cookies=SimpleCookie(self.headers.get("Cookie",""))
        for name in (COOKIE,LOGIN_COOKIE):
            if name in cookies:
                del cookies[name]
        if cookies:
            headers["Cookie"]="; ".join(m.OutputString() for m in cookies.values())
        headers["X-Forwarded-Proto"]="https"
        headers["X-Forwarded-Host"]=self.headers.get("Host","")
        upstream=http.client.HTTPConnection("triples-pr26-preview",8080,timeout=40)
        headers_sent=False
        try:
            target=path+("?"+parsed.query if parsed.query else "")
            upstream.request(self.command,target,body=body,headers=headers)
            response=upstream.getresponse()
            self.send_response(response.status)
            for key,value in response.getheaders():
                if key.lower() not in HOP:
                    self.send_header(key,value)
            self.send_header("Connection","close")
            self.send_header("Referrer-Policy","no-referrer")
            self.end_headers()
            headers_sent=True
            self.close_connection=True
            if self.command!="HEAD":
                while chunk:=response.read(65536):
                    self.wfile.write(chunk)
        except (OSError,http.client.HTTPException):
            if not headers_sent:
                try:
                    if path.startswith("/api/"):
                        self.reply(503,b'{"error":"preview_starting"}',"application/json",headers=(("Retry-After","5"),))
                    else:
                        self.reply(503,WARMING_PAGE.encode(),headers=(("Retry-After","5"),))
                except OSError:
                    pass
            self.close_connection=True
        finally:
            upstream.close()
    do_GET=handle_request
    do_POST=handle_request
    do_PUT=handle_request
    do_PATCH=handle_request
    do_DELETE=handle_request
    do_OPTIONS=handle_request
    do_HEAD=handle_request

if __name__=="__main__":
    ThreadingHTTPServer(("0.0.0.0",8080),Handler).serve_forever()
