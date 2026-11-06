"""Opt-in actual PR WAR + isolated MariaDB + authenticated gateway smoke test."""
import base64
from contextlib import closing
import http.client
from http.cookies import SimpleCookie
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import unittest
import urllib.parse
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from data import LocalDatabase, docker
from preview import Supervisor


@unittest.skipUnless(os.environ.get('PREVIEW_APP_TESTS') == '1', 'set PREVIEW_APP_TESTS=1 and PREVIEW_TEST_RELEASE for WAR smoke test')
class AppTests(unittest.TestCase):
    def test_actual_war_crud_upload_range_and_restart_persistence(self):
        release = Path(os.environ['PREVIEW_TEST_RELEASE']).resolve()
        if not (release / 'built.json').exists():
            self.fail('Provide a successfully built PR release')
        password = secrets.token_urlsafe(32)  # Test-only value, never exposed publicly or logged.
        old_password = os.environ.get('PREVIEW_ACCESS_PASSWORD')
        os.environ['PREVIEW_ACCESS_PASSWORD'] = password
        with tempfile.TemporaryDirectory(prefix='pr58-app-', dir=os.environ.get('PREVIEW_TEST_TMPDIR', tempfile.gettempdir())) as directory:
            supervisor = Supervisor(directory, local_only=True)
            supervisor.db = LocalDatabase(directory, port=23308)
            supervisor.backend_port, supervisor.gateway_port = 8890, 8790
            uploads = Path(directory) / 'uploads'; uploads.mkdir()
            jar = {}
            def request(route, method='GET', body=None, headers=None):
                if isinstance(body, dict):
                    body = json.dumps(body).encode()
                    headers = {'Content-Type': 'application/json', **(headers or {})}
                headers = dict(headers or {}, Cookie='; '.join(k+'='+v for k,v in jar.items()))
                if method not in ('GET', 'HEAD') and 'XSRF-TOKEN' in jar:
                    headers['X-XSRF-TOKEN'] = urllib.parse.unquote(jar['XSRF-TOKEN'])
                with closing(http.client.HTTPConnection('127.0.0.1', supervisor.gateway_port, timeout=30)) as connection:
                    connection.request(method, route, body=body, headers=headers)
                    reply = connection.getresponse()
                    data = reply.read()
                    for key, value in reply.getheaders():
                        if key.lower() == 'set-cookie':
                            parsed = SimpleCookie(); parsed.load(value)
                            for name, item in parsed.items():
                                if item['max-age'] == '0':
                                    jar.pop(name, None)
                                else:
                                    jar[name] = item.value
                    return reply.status, dict(reply.getheaders()), data
            try:
                supervisor.db.start()
                supervisor.start_gateway()
                # The repository's migrations upgrade an existing schema; V1 does
                # not bootstrap an empty database. Create a current-schema fixture,
                # then test the unmodified production-style preview configuration.
                fixture_env = supervisor.app_environment(release)
                fixture_env.update(SPRING_FLYWAY_ENABLED='false', SPRING_JPA_HIBERNATE_DDL_AUTO='create')
                with patch.object(supervisor, 'app_environment', return_value=fixture_env):
                    supervisor.start_app(release)
                supervisor.stop_app()
                versions = [int(re.match(r'V(\d+)__', p.name)[1]) for p in (release / 'src/main/resources/db/migration').glob('V*__*.sql')]
                with supervisor.db.connect() as connection, connection.cursor() as cursor:
                    cursor.execute('CREATE TABLE flyway_schema_history (installed_rank INT PRIMARY KEY, version VARCHAR(50), description VARCHAR(200) NOT NULL, type VARCHAR(20) NOT NULL, script VARCHAR(1000) NOT NULL, checksum INT, installed_by VARCHAR(100) NOT NULL, installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, execution_time INT NOT NULL, success BOOL NOT NULL)')
                    cursor.execute('INSERT INTO flyway_schema_history (installed_rank,version,description,type,script,installed_by,execution_time,success) VALUES (1,%s,%s,%s,%s,%s,0,1)', (str(max(versions)), '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', 'root'))
                supervisor.start_app(release)
                from preview import control
                control(supervisor.state, 'activate', port=supervisor.backend_port)
                code, _, body = request('/_preview/login')
                self.assertEqual(code, 200)
                nonce = re.search(b'name="nonce" value="([^"]+)"', body)[1].decode()
                code, _, _ = request('/_preview/login', 'POST', urllib.parse.urlencode({'password': password, 'nonce': nonce}), {'Content-Type': 'application/x-www-form-urlencoded'})
                self.assertEqual(code, 303)
                code, _, _ = request('/api/album/admin/status')
                self.assertEqual(code, 200)
                code, _, _ = request('/api/album/admin/login', 'POST', {'password': password})
                self.assertEqual(code, 200)
                code, _, body = request('/api/album/sections', 'POST', {'name': '미리보기 테스트'})
                self.assertEqual(code, 200, body[:300])
                section = json.loads(body)['sectionId']
                code, _, _ = request(f'/api/album/sections/{section}', 'PUT', {'name': '수정 완료'})
                self.assertEqual(code, 200)
                png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=')
                boundary = 'preview' + secrets.token_hex(8)
                body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="preview.png"\r\nContent-Type: image/png\r\n\r\n').encode() + png + f'\r\n--{boundary}--\r\n'.encode()
                code, _, body = request('/api/article/trade/uploadFile', 'POST', body, {'Content-Type': 'multipart/form-data; boundary=' + boundary})
                self.assertEqual(code, 200, body[:300])
                upload = json.loads(body)['url']
                code, _, _ = request(upload)
                self.assertEqual(code, 200)
                code, headers, part = request(upload, headers={'Range': 'bytes=0-3'})
                self.assertEqual(code, 206)
                self.assertEqual(part, png[:4])
                # Exercise the actual deployment transaction, including a DB/files backup.
                supervisor.current = {'commit': release.name, 'release': str(release)}
                supervisor.remote = lambda: release.name
                supervisor.deploy(release.name, release)
                code, _, body = request('/api/album/sections')
                self.assertEqual(code, 200)
                self.assertTrue(any(s['sectionId'] == section and s['name'] == '수정 완료' for s in json.loads(body)))
                self.assertEqual(request(upload)[0], 200)
                # The outer preview session survives an app restart; the app admin session is renewed.
                self.assertEqual(request('/api/album/admin/login', 'POST', {'password': password})[0], 200)
                self.assertEqual(request(f'/api/album/sections/{section}', 'DELETE', {})[0], 200)
            except Exception:
                # Keep private diagnostic logs out of the test output.
                for log in ('gateway.log',):
                    source = Path(directory) / log
                    if source.exists():
                        destination = Path('/workspace/.onboarding') / ('pr58-failed-' + log)
                        shutil.copy2(source, destination)
                raise
            finally:
                supervisor.stop_app()
                if supervisor.gateway and supervisor.gateway.poll() is None:
                    supervisor.gateway.terminate(); supervisor.gateway.wait(10)
                docker('rm', '-f', supervisor.db.container, stdout=subprocess.DEVNULL)
                docker('volume', 'rm', supervisor.db.volume, stdout=subprocess.DEVNULL)
                if old_password is None:
                    os.environ.pop('PREVIEW_ACCESS_PASSWORD', None)
                else:
                    os.environ['PREVIEW_ACCESS_PASSWORD'] = old_password


if __name__ == '__main__':
    unittest.main()
