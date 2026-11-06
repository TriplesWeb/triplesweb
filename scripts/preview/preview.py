#!/usr/bin/env python3
"""Temporary PR #58 preview supervisor. No push, merge or production deployment."""
import argparse
import contextlib
import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.request

from data import LocalDatabase, atomic_json, connect_source, copy_assets, dump_source, source_config

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
SHA = re.compile(r'^[a-f0-9]{40}$')


class Superseded(Exception):
    pass


class RecoveryFailed(Exception):
    pass


def control(state, action, **values):
    with socket.socket(socket.AF_UNIX) as client:
        client.settimeout(70)
        client.connect(str(Path(state) / 'control.sock'))
        client.sendall((json.dumps({'action': action, **values}) + '\n').encode())
        with client.makefile('rb') as reply:
            response = json.loads(reply.readline(65536))
    if not response.get('ok'):
        raise RuntimeError(response.get('error', 'Gateway control failed'))
    return response


def read_json(file, default=None):
    try:
        return json.loads(Path(file).read_text())
    except FileNotFoundError:
        return default


class Reconciler:
    """Injectable state machine: failed/stale builds never replace a healthy release."""
    def __init__(self, remote, build, deploy, status, current=None, clock=time.monotonic):
        self.remote, self.build, self.deploy, self.status = remote, build, deploy, status
        self.current, self.clock = current, clock
        self.failed, self.retry_at = None, 0

    def tick(self):
        latest = self.remote()
        self.status(detectedCommit=latest)
        if latest == self.current or (latest == self.failed and self.clock() < self.retry_at):
            return
        try:
            self.status(phase='building', error=None)
            release = self.build(latest)
            if self.remote() != latest:
                raise Superseded()
            self.status(phase='deploying')
            self.deploy(latest, release)
            self.current = latest
            self.failed = None
            self.status(phase='ready', runningCommit=latest, error=None)
        except Superseded:
            self.status(phase='superseded', error=None)
        except RecoveryFailed:
            self.status(phase='recovery-failed', error='복구에 실패해 미리보기를 점검 상태로 유지합니다. 수동 확인이 필요합니다.')
            raise
        except Exception:
            self.failed, self.retry_at = latest, self.clock() + 300
            self.status(phase='failed', error='빌드 또는 서버 검증 실패. 기존 버전을 유지합니다. 비공개 실행 로그를 확인해주세요.')


class Supervisor:
    def __init__(self, state, local_only=False, repo=REPO):
        self.state = Path(state).resolve()
        self.repo, self.local_only = Path(repo).resolve(), local_only
        if self.state.is_relative_to(self.repo):
            raise RuntimeError('Preview state must be outside the repository checkout')
        self.state.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.state.chmod(0o700)
        self.db = LocalDatabase(self.state)
        self.gateway = self.app = None
        self.backend_port = 8888
        self.gateway_port = 8787
        self.current = read_json(self.state / 'active.json', {})
        self.status_values = read_json(self.state / 'status.json', {})

    def status(self, **values):
        self.status_values.update(values, updatedAt=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()))
        atomic_json(self.state / 'status.json', self.status_values)

    def remote(self):
        result = subprocess.run(['git', 'ls-remote', 'origin', 'refs/pull/58/head'], cwd=self.repo,
                                capture_output=True, text=True, check=True, timeout=30)
        parts = result.stdout.split()
        if len(parts) != 2 or not SHA.fullmatch(parts[0]):
            raise RuntimeError('PR #58 head was not found')
        return parts[0]

    def init_data(self):
        marker = self.state / 'data-ready.json'
        self.db.start()
        if marker.exists():
            return
        dump = self.state / 'initial.sql'
        manifest_file = self.state / 'source-manifest.json'
        if not dump.exists() or not manifest_file.exists():
            self.status(phase='copying-data')
            with contextlib.closing(connect_source(source_config(self.repo))) as source:
                with source.cursor() as cursor:
                    cursor.execute('SELECT COALESCE(SUM(DATA_LENGTH+INDEX_LENGTH),0) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()')
                    estimate = int(cursor.fetchone()[0])
                if shutil.disk_usage(self.state).free < max(2_000_000_000, estimate * 5):
                    raise RuntimeError('Insufficient free space for source copy and rollback snapshots')
                partial = self.state / 'initial.sql.partial'
                manifest = dump_source(source, partial)
                partial.replace(dump)
                atomic_json(manifest_file, manifest)
        manifest = read_json(manifest_file)
        if not (self.state / 'data-imported.json').exists():
            self.db.restore(dump)
            atomic_json(self.state / 'data-imported.json', {'tables': manifest['tables']})
        self.status(phase='copying-files')
        missing = copy_assets(self.repo, self.state, manifest)
        self.status(missingFiles=len(missing))
        if missing:
            raise RuntimeError(f'{len(missing)} referenced upload files could not be copied; see missing-files.json')
        atomic_json(marker, {'copiedAt': time.time(), 'tables': manifest['tables']})

    def build(self, sha):
        if not SHA.fullmatch(sha):
            raise ValueError('Invalid commit')
        release = self.state / 'releases' / sha
        if (release / 'built.json').exists():
            return release
        release.mkdir(parents=True, exist_ok=True)
        env = dict(os.environ, npm_config_cache=str(self.state / 'npm-cache'))
        with (release / 'build.log').open('ab') as log:
            subprocess.run(['git', 'fetch', '--no-tags', '--force', 'origin', 'refs/pull/58/head:refs/preview/mobile-pr58'],
                           cwd=self.repo, stdout=log, stderr=log, check=True, timeout=120)
            archive = release / 'source.tar'
            with archive.open('wb') as out:
                subprocess.run(['git', 'archive', sha], cwd=self.repo, stdout=out, stderr=log, check=True)
            with tarfile.open(archive) as source:
                source.extractall(release, filter='data')
            archive.unlink()
            for command in (['npm', 'ci', '--no-audit', '--no-fund'], ['npm', 'run', 'build:css'],
                            ['bash', './gradlew', 'test', 'bootWar', '--no-daemon', '--max-workers=2']):
                subprocess.run(command, cwd=release, env=env, stdout=log, stderr=log, check=True, timeout=1800)
        atomic_json(release / 'built.json', {'commit': sha})
        return release

    def app_environment(self, release):
        env = dict(os.environ)
        env.update({
            'SPRING_PROFILES_ACTIVE': 'preview,local',
            'SPRING_DATASOURCE_URL': f'jdbc:mariadb://127.0.0.1:{self.db.port}/{self.db.database}',
            'SPRING_DATASOURCE_USERNAME': 'root', 'SPRING_DATASOURCE_PASSWORD': self.db.secret.read_text(),
            'SPRING_DATASOURCE_DRIVER_CLASS_NAME': 'org.mariadb.jdbc.Driver',
            'SPRING_JPA_HIBERNATE_DDL_AUTO': 'none', 'SPRING_FLYWAY_ENABLED': 'true',
            'SPRING_DEVTOOLS_RESTART_ENABLED': 'false',
            'SERVER_ADDRESS': '127.0.0.1', 'SERVER_PORT': str(self.backend_port),
            'SERVER_FORWARD_HEADERS_STRATEGY': 'framework', 'SERVER_SERVLET_SESSION_COOKIE_SECURE': 'true',
            'MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE': 'health,info',
            'MANAGEMENT_INFO_ENV_ENABLED': 'true', 'INFO_PREVIEW_COMMIT': Path(release).name,
            'FILE_UPLOAD_DIR': str(self.state / 'uploads'), 'MEDIA_CATALOG_PATH': '',
            'UPLOAD_SYNC_ENABLED': 'false', 'UPLOAD_SYNC_PULL_ON_STARTUP': 'false', 'UPLOAD_FALLBACK_BASE_URL': '',
            'CHEMISTRY_SEED_ENABLED': 'false', 'ABSTRACT_INDEXER_ENABLED': 'false',
            'CHEMISTRY_ADMIN_PASSWORD': os.environ['PREVIEW_ACCESS_PASSWORD'],
            'COSMO_ACCESS_TOKEN': os.environ.get('PREVIEW_COSMO_ACCESS_TOKEN', ''),
            'COSMO_REFRESH_TOKEN': os.environ.get('PREVIEW_COSMO_REFRESH_TOKEN', ''),
            'COSMO_KEY': os.environ.get('PREVIEW_COSMO_KEY', ''),
            'COSMO_CREDENTIALS_FILE': str(self.state / 'preview-cosmo-credentials.json'),
            'YOUTUBE_API_KEY': os.environ.get('PREVIEW_YOUTUBE_API_KEY', ''),
        })
        return env

    def start_app(self, release):
        with socket.socket() as available:
            available.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            available.bind(('127.0.0.1', self.backend_port))
        env = self.app_environment(release)
        # CLI guards outrank inherited JVM system properties and any archived config.
        guards = ['--spring.datasource.url=' + env['SPRING_DATASOURCE_URL'],
                  '--spring.datasource.username=root', '--spring.datasource.driver-class-name=org.mariadb.jdbc.Driver',
                  '--spring.profiles.active=preview,local', '--upload.sync.enabled=false',
                  '--upload.sync.pull-on-startup=false', '--upload.fallback-base-url=',
                  '--file.upload-dir=' + env['FILE_UPLOAD_DIR'], '--media.catalog.path=',
                  '--server.address=127.0.0.1', '--server.port=' + str(self.backend_port)]
        log = (Path(release) / 'app.log').open('ab')
        try:
            self.app = subprocess.Popen(['java', '-Dspring.devtools.restart.enabled=false', '-jar',
                                         str(Path(release) / 'build/libs/triples.war'), *guards], cwd=release,
                                        env=env, stdout=log, stderr=log, start_new_session=True)
        finally:
            log.close()
        deadline = time.monotonic() + 120
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        while time.monotonic() < deadline:
            if self.app.poll() is not None:
                raise RuntimeError('Spring process exited before readiness')
            try:
                for route in ('/api/chemistry', '/main'):
                    with opener.open(f'http://127.0.0.1:{self.backend_port}' + route, timeout=3) as response:
                        if response.status != 200 or not response.read(1024):
                            raise RuntimeError('Unexpected readiness response')
                with opener.open(f'http://127.0.0.1:{self.backend_port}/actuator/info', timeout=3) as response:
                    if json.load(response).get('preview', {}).get('commit') != Path(release).name:
                        raise RuntimeError('Backend commit did not match the requested release')
                return
            except (OSError, urllib.error.URLError):
                time.sleep(1)
        raise RuntimeError('Spring readiness timed out')

    def stop_app(self):
        if self.app and self.app.poll() is None:
            os.killpg(self.app.pid, signal.SIGTERM)
            try:
                self.app.wait(30)
            except subprocess.TimeoutExpired:
                os.killpg(self.app.pid, signal.SIGKILL)
                self.app.wait()
        self.app = None

    def restore_backup(self, backup):
        self.db.restore(Path(backup) / 'db.sql')
        uploads = self.state / 'uploads'
        shutil.rmtree(uploads, ignore_errors=True)
        shutil.copytree(Path(backup) / 'uploads', uploads)

    def recover(self):
        pending = read_json(self.state / 'deployment.json')
        if pending:
            self.restore_backup(self.state / 'rollback')
            self.current = pending['previous']
            atomic_json(self.state / 'active.json', self.current)
            (self.state / 'deployment.json').unlink()

    def deploy(self, sha, release):
        previous = dict(self.current)
        backup = self.state / 'rollback'
        marker = self.state / 'deployment.json'
        try:
            control(self.state, 'quiesce')
        except Exception:
            if previous:
                control(self.state, 'activate', port=self.backend_port)
            raise
        self.stop_app()
        backup_ready = False
        try:
            shutil.rmtree(backup, ignore_errors=True)
            backup.mkdir()
            self.db.backup(backup / 'db.sql')
            shutil.copytree(self.state / 'uploads', backup / 'uploads')
            backup_ready = True
            atomic_json(marker, {'previous': previous, 'target': sha})
            self.start_app(release)
            if self.remote() != sha:
                raise Superseded()
            atomic_json(self.state / 'active.json', {'commit': sha, 'release': str(release)})
            self.current = {'commit': sha, 'release': str(release)}
            marker.unlink()
            control(self.state, 'activate', port=self.backend_port)
        except Exception:
            self.stop_app()
            try:
                if backup_ready:
                    self.restore_backup(backup)
                self.current = previous
                atomic_json(self.state / 'active.json', previous)
                if previous:
                    self.start_app(Path(previous['release']))
                    control(self.state, 'activate', port=self.backend_port)
                marker.unlink(missing_ok=True)
            except Exception as error:
                atomic_json(marker, {'previous': previous, 'target': sha})
                raise RecoveryFailed() from error
            raise

    def verify_tunnel(self):
        gateway = read_json(self.state / 'gateway.json')
        tunnel = read_json(self.state / 'tunnel.json', {})
        if not gateway or not tunnel.get('url'):
            return False
        with urllib.request.urlopen(tunnel['url'] + '/_preview/health', timeout=15) as response:
            result = json.load(response)
        if result.get('healthId') != gateway['healthId']:
            raise RuntimeError('Public URL did not reach this gateway')
        control(self.state, 'verify-tunnel', url=tunnel['url'])
        if self.status_values.get('publicUrl') != tunnel['url']:
            self.status(publicUrl=tunnel['url'])
            print('Verified temporary URL: ' + tunnel['url'], flush=True)
        return True

    def start_gateway(self):
        sock = self.state / 'control.sock'
        if sock.exists():
            try:
                control(self.state, 'status')
            except (OSError, ValueError):
                sock.unlink()
            else:
                raise RuntimeError('A preview gateway is already running in this state directory')
        for name in ('gateway.json', 'tunnel.json'):
            (self.state / name).unlink(missing_ok=True)
        env = dict(os.environ, PREVIEW_STATE_DIR=str(self.state), PREVIEW_GATEWAY_PORT=str(self.gateway_port))
        log = (self.state / 'gateway.log').open('ab')
        try:
            self.gateway = subprocess.Popen(['node', str(HERE / 'server.mjs'), *(['--local-only'] if self.local_only else [])],
                                            env=env, stdout=log, stderr=log, start_new_session=True)
        finally:
            log.close()
        deadline = time.monotonic() + 25
        while time.monotonic() < deadline:
            if self.gateway.poll() is not None:
                raise RuntimeError('Gateway exited; see private gateway.log')
            gateway = read_json(self.state / 'gateway.json')
            tunnel = read_json(self.state / 'tunnel.json', {})
            if gateway and self.local_only:
                return
            if tunnel.get('state', '').startswith('blocked:'):
                raise RuntimeError('Tunnelmole blocked by the HTTP proxy (403); apply the saved network policy first')
            if gateway and tunnel.get('url'):
                self.verify_tunnel()
                return
            time.sleep(.2)
        raise RuntimeError('No verified public tunnel within 25 seconds')

    def run(self, once=False):
        lock = (self.state / 'supervisor.lock').open('a')
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('A preview supervisor already owns this state directory')
        try:
            if len(os.environ.get('PREVIEW_ACCESS_PASSWORD', '')) < 12:
                raise RuntimeError('Set PREVIEW_ACCESS_PASSWORD (12+ characters) securely in environment settings')
            self.status(phase='starting', runningCommit=self.current.get('commit'), error=None)
            self.start_gateway()  # Keep the backend sealed until external transport is proven.
            self.init_data()
            self.recover()
            if self.current:
                self.start_app(Path(self.current['release']))
                control(self.state, 'activate', port=self.backend_port)
                self.status(phase='ready', runningCommit=self.current['commit'])
            reconciler = Reconciler(self.remote, self.build, self.deploy, self.status, self.current.get('commit'))
            while True:
                try:
                    if not self.local_only:
                        self.verify_tunnel()
                    if self.app and self.app.poll() is not None:
                        control(self.state, 'quiesce')
                        self.app = None
                        self.status(phase='server-stopped', error='실행 서버가 종료됐습니다. 비공개 앱 로그를 확인해주세요.')
                    reconciler.tick()
                except RecoveryFailed:
                    # Keep the authenticated maintenance/status page available;
                    # never attempt another deployment on ambiguous DB state.
                    self.stop_app()
                    while True:
                        time.sleep(30)
                except Exception:
                    self.status(phase='poll-failed', error='PR 조회 실패. 기존 버전을 유지하며 다시 시도합니다.')
                if once:
                    break
                time.sleep(30)
        finally:
            self.stop_app()
            if self.gateway and self.gateway.poll() is None:
                os.killpg(self.gateway.pid, signal.SIGTERM)
                self.gateway.wait(10)
            lock.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['run', 'init-data', 'status', 'preflight'])
    parser.add_argument('--state-dir', default=os.environ.get('PREVIEW_STATE_DIR', '/workspace/.onboarding/pr58-preview'))
    parser.add_argument('--local-only', action='store_true', help='Internal verification only; never a phone URL')
    args = parser.parse_args()
    supervisor = Supervisor(args.state_dir, args.local_only)
    if args.command == 'status':
        print(json.dumps(read_json(supervisor.state / 'status.json', {}), ensure_ascii=False, indent=2))
    elif args.command == 'preflight':
        policy = read_json('/etc/codex/network-policy.json', {})
        hosts = [r.get('host') for r in policy.get('http_network_policy', {}).get('egress_rules', [])]
        print(json.dumps({'previewPasswordConfigured': len(os.environ.get('PREVIEW_ACCESS_PASSWORD', '')) >= 12,
                          'customTunnelRulePresent': 'service.tunnelmole.com' in hosts,
                          'customPhoneUrlRulePresent': '*.tunnelmole.net' in hosts,
                          'publicUrlVerified': False}, indent=2))
    else:
        try:
            if args.command == 'init-data':
                with (supervisor.state / 'supervisor.lock').open('a') as lock:
                    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    supervisor.init_data()
            else:
                supervisor.run()
        except KeyboardInterrupt:
            pass
        except Exception as error:
            # Detailed DB/HTTP errors can contain queries, user data or credentials.
            message = str(error) if isinstance(error, RuntimeError) else type(error).__name__ + '; inspect private logs'
            supervisor.status(phase='blocked', error=message)
            print(message, file=sys.stderr)
            return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
