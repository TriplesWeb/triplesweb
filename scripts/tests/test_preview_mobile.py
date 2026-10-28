import hashlib
import http.client
import importlib.util
import io
import json
import os
import re
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib.parse import urlencode

class PreviewMobileSecurityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory()
        config=Path(cls.temp.name)/"access.json"
        salt=bytes.fromhex("11"*16)
        config.write_text(json.dumps({"salt":salt.hex(),"passwordHash":hashlib.pbkdf2_hmac("sha256",b"preview-test-password",salt,260000).hex(),"session":"test-session"}))
        with patch.dict(os.environ,{"PREVIEW_ACCESS_CONFIG":str(config),"APP_PREVIEW_NUMBER":"29"}):
            spec=importlib.util.spec_from_file_location("mobile_gateway",Path(__file__).resolve().parents[1]/"pr-preview-mobile-gateway.py")
            cls.module=importlib.util.module_from_spec(spec)
            spec.loader.exec_module(cls.module)
        cls.server=cls.module.ThreadingHTTPServer(("127.0.0.1",0),cls.module.Handler)
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True)
        cls.thread.start()
        cls.connection=http.client.HTTPConnection

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.temp.cleanup()

    def request(self,method,path,body=None,headers=None):
        conn=self.connection("127.0.0.1",self.server.server_port,timeout=5)
        conn.request(method,path,body,headers or {})
        response=conn.getresponse()
        result=(response.status,dict(response.getheaders()),response.read())
        conn.close()
        return result

    def login_request(self,password,extra_headers=None):
        _,headers,body=self.request("GET","/__preview/login")
        csrf=re.search(rb'name="csrf" value="([^"]+)"',body).group(1).decode()
        cookie=headers["Set-Cookie"].split(";")[0]
        request_headers={"Content-Type":"application/x-www-form-urlencoded","Cookie":cookie}
        request_headers.update(extra_headers or {})
        return self.request("POST","/__preview/login",urlencode({"password":password,"csrf":csrf}),request_headers)

    def test_unavailable_app_shows_retry_page_instead_of_bad_gateway(self):
        with patch.object(self.module.http.client,"HTTPConnection") as unavailable:
            unavailable.return_value.request.side_effect=ConnectionRefusedError
            status,headers,body=self.request("GET","/cosmo/objekt",headers={"Cookie":"PR29MOBILEACCESS=test-session"})
            self.assertEqual(status,503)
            self.assertEqual(headers["Retry-After"],"5")
            self.assertIn('미리보기를 준비하고 있어요',body.decode())
            self.assertIn('http-equiv="refresh"',body.decode())

    def test_private_api_requires_login(self):
        status,headers,body=self.request("GET","/api/preview/info")
        self.assertEqual(status,401)
        self.assertIn(b"preview_login_required",body)
        self.assertEqual(self.request("GET","/cosmo/objekt")[0],303)

    def test_wrong_password_is_rejected(self):
        status,_,_=self.login_request("wrong")
        self.assertEqual(status,401)

    def test_login_cookie_is_secure_and_http_only(self):
        status,headers,_=self.login_request("preview-test-password")
        self.assertEqual(status,303)
        self.assertIn("PR29MOBILEACCESS",headers["Set-Cookie"])
        self.assertIn("HttpOnly",headers["Set-Cookie"])
        self.assertIn("Secure",headers["Set-Cookie"])
        self.assertIn("SameSite=Lax",headers["Set-Cookie"])

    def test_cross_origin_login_is_rejected(self):
        self.assertEqual(self.request("POST","/__preview/login","password=anything",{"Origin":"https://other.example"})[0],403)

    def test_browser_origin_survives_proxy_host_rewrite(self):
        status,headers,_=self.login_request("preview-test-password",{"Origin":"https://preview.example","Host":"gateway:8080"})
        self.assertEqual(status,303)
        self.assertIn("PR29MOBILEACCESS",headers["Set-Cookie"])

    def test_opening_another_login_tab_keeps_original_form_valid(self):
        _,headers,body=self.request("GET","/__preview/login")
        csrf=re.search(rb'name="csrf" value="([^"]+)"',body).group(1).decode()
        first_cookie=headers["Set-Cookie"].split(";")[0]
        _,second_headers,_=self.request("GET","/__preview/login",headers={"Cookie":first_cookie})
        cookie=second_headers["Set-Cookie"].split(";")[0]
        status,_,_=self.request("POST","/__preview/login",urlencode({"password":"preview-test-password","csrf":csrf}),{"Cookie":cookie,"Content-Type":"application/x-www-form-urlencoded"})
        self.assertEqual(status,303)

    def test_browser_icon_does_not_redirect_to_login_or_change_cookie(self):
        status,headers,_=self.request("GET","/favicon.ico")
        self.assertEqual(status,204)
        self.assertNotIn("Set-Cookie",headers)
        self.assertNotIn("Location",headers)

    def test_signed_token_cannot_be_forged(self):
        status,_,_=self.request("POST","/__preview/login",urlencode({"password":"preview-test-password","csrf":"forged.token"}),{"Cookie":"__Host-PR29LOGINCSRF=forged.token"})
        self.assertEqual(status,403)

    def test_access_cookie_is_not_sent_to_application(self):
        response=type("Response",(),{"status":200,"getheaders":lambda self:[("Content-Type","application/json"),("Content-Length","2")],"read":io.BytesIO(b"{}").read})()
        with patch.object(self.module.http.client,"HTTPConnection") as upstream:
            upstream.return_value.getresponse.return_value=response
            status,_,body=self.request("GET","/api/preview/info",headers={"Cookie":"PR29MOBILEACCESS=test-session; JSESSIONID=app-session"})
            self.assertEqual(status,200)
            forwarded=upstream.return_value.request.call_args.kwargs["headers"]
            self.assertNotIn("PR29MOBILEACCESS",forwarded.get("Cookie",""))
            self.assertIn("JSESSIONID=app-session",forwarded["Cookie"])
            self.assertEqual(forwarded["X-Forwarded-Proto"],"https")

if __name__=="__main__":
    unittest.main()
