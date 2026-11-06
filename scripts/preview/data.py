"""Read-only source snapshots and a loopback-only, disposable MariaDB target."""
import contextlib
import hashlib
import html
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import urllib.parse
import urllib.request

import pymysql


def atomic_json(file, value):
    file = Path(file)
    temp = file.with_suffix('.tmp')
    temp.write_text(json.dumps(value, ensure_ascii=False))
    temp.chmod(0o600)
    temp.replace(file)


def docker(*args, **kwargs):
    env = dict(os.environ)
    for key in ('DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH'):
        env.pop(key, None)
    env.update(kwargs.pop('env', {}))
    return subprocess.run(['docker', '--host=unix:///var/run/docker.sock', *args], env=env, check=True, **kwargs)


def source_config(repo):
    properties = {}
    for line in (Path(repo) / 'src/main/resources/application.properties').read_text().splitlines():
        if line and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            match = re.fullmatch(r'\$\{([^:}]+):([^}]*)}', value)
            properties[key] = os.environ.get(match[1], match[2]) if match else value
    url = urllib.parse.urlsplit(properties['spring.datasource.url'].removeprefix('jdbc:'))
    return dict(host=os.environ.get('PREVIEW_SOURCE_DB_HOST', url.hostname),
                port=int(os.environ.get('PREVIEW_SOURCE_DB_PORT', url.port or 3306)),
                database=os.environ.get('PREVIEW_SOURCE_DB_NAME', url.path.lstrip('/')),
                user=os.environ.get('PREVIEW_SOURCE_DB_USER', properties['spring.datasource.username']),
                password=os.environ.get('PREVIEW_SOURCE_DB_PASSWORD', properties['spring.datasource.password']))


def connect_source(config):
    """Use the authorized managed CONNECT transport, including private VPN DNS."""
    proxy = urllib.parse.urlsplit(os.environ.get('PREVIEW_TCP_PROXY', 'http://proxy:8088'))
    sock = socket.create_connection((proxy.hostname, proxy.port or 8088), timeout=10)
    destination = f"{config['host']}:{config['port']}"
    if not re.fullmatch(r'[a-zA-Z0-9.-]+:[0-9]+', destination):
        sock.close()
        raise RuntimeError('Invalid source DB endpoint')
    sock.sendall(f'CONNECT {destination} HTTP/1.1\r\nHost: {destination}\r\n\r\n'.encode('ascii'))
    headers = b''
    while not headers.endswith(b'\r\n\r\n') and len(headers) < 8192:
        chunk = sock.recv(1)
        if not chunk:
            break
        headers += chunk
    status = headers.split(b'\r\n', 1)[0].split()
    if not headers.endswith(b'\r\n\r\n') or len(status) < 2 or status[1] != b'200':
        sock.close()
        raise RuntimeError('Source DB CONNECT failed: ' + (status[1].decode('ascii', errors='replace') if len(status) > 1 else 'no response'))
    try:
        connection = pymysql.Connection(**config, charset='utf8mb4', connect_timeout=10,
                                        read_timeout=120, write_timeout=120, defer_connect=True)
        connection.connect(sock=sock)
        with connection.cursor() as cursor:
            cursor.execute('SET SESSION TRANSACTION READ ONLY')
            cursor.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
        return connection
    except Exception:
        sock.close()
        raise


def quote(name):
    return '`' + name.replace('`', '``') + '`'


def upload_path(raw):
    raw = html.unescape(urllib.parse.unquote(raw)).replace('\\', '/')
    if '/uploads/' in raw:
        raw = raw.split('/uploads/', 1)[1]
    raw = raw.split('?', 1)[0].split('#', 1)[0]
    parts = raw.split('/')
    if raw.startswith('/') or any(p in ('', '.', '..') for p in parts) or '\x00' in raw:
        return None
    return raw


def references(value, column):
    if not isinstance(value, str):
        return set()
    result = set()
    for raw in re.findall(r'/uploads/[^\s"\'<>\\]+', value):
        candidate = upload_path(raw)
        if candidate:
            result.add(candidate)
    if column in ('relative_path', 'saved_name') or value.startswith(('images/', 'videos/', 'files/', 'chemistry/')):
        candidate = upload_path(value)
        if candidate:
            result.add(candidate)
    return result


def dump_source(connection, destination):
    """No writes, DDL, locks, triggers or events are sent to the source connection."""
    refs, blobs = set(), set()
    with connection.cursor() as cursor:
        cursor.execute('SHOW FULL TABLES')
        tables = list(cursor.fetchall())
    file = Path(destination)
    with file.open('w', encoding='utf8') as out:
        file.chmod(0o600)
        out.write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\nSET SQL_MODE='NO_AUTO_VALUE_ON_ZERO';\n")
        views = []
        for table, kind in tables:
            with connection.cursor() as cursor:
                cursor.execute('SHOW CREATE TABLE ' + quote(table))
                create = cursor.fetchone()[1]
                if kind == 'VIEW':
                    # A production DEFINER must not be needed in the preview.
                    create = re.sub(r'DEFINER=`[^`]*`@`[^`]*`', '', create)
                    views.append(create)
                    continue
                cursor.execute('SHOW FULL COLUMNS FROM ' + quote(table))
                columns = [row[0] for row in cursor.fetchall() if 'GENERATED' not in row[6]]
            out.write(create + ';\n')
            with connection.cursor(pymysql.cursors.SSCursor) as cursor:
                cursor.execute('SELECT ' + ','.join(map(quote, columns)) + ' FROM ' + quote(table))
                for row in cursor:
                    values = []
                    for column, value in zip(columns, row):
                        refs.update(references(value, column))
                        values.append("X'" + value.hex() + "'" if isinstance(value, bytes) else connection.escape(value))
                    if table == 'upload_blob' and 'relative_path' in columns:
                        candidate = upload_path(row[columns.index('relative_path')])
                        if candidate:
                            blobs.add(candidate)
                    out.write('INSERT INTO ' + quote(table) + ' (' + ','.join(map(quote, columns)) + ') VALUES (' + ','.join(values) + ');\n')
        for view in views:
            out.write(view + ';\n')
        out.write('SET FOREIGN_KEY_CHECKS=1;\n')
    connection.rollback()
    return {'references': sorted(refs), 'blobPaths': sorted(blobs), 'tables': len(tables)}


class LocalDatabase:
    def __init__(self, state, port=23306):
        self.state = Path(state).resolve()
        self.port = port
        # Names cannot collide with the repository's production compose project.
        suffix = hashlib.sha256(str(self.state).encode()).hexdigest()[:12]
        self.container = 'triples-preview-' + suffix
        self.volume = self.container + '-data'
        self.database = 'triples_preview'
        self.secret = self.state / 'db-password'

    def start(self):
        import time
        if not self.secret.exists():
            fd = os.open(self.secret, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'w') as out:
                out.write(secrets.token_urlsafe(36))
        exists = docker('ps', '-a', '--filter', 'name=^/' + self.container + '$', '--format', '{{.Names}}', capture_output=True, text=True).stdout.strip()
        if exists:
            docker('start', self.container, stdout=subprocess.DEVNULL)
        else:
            docker('run', '-d', '--name', self.container,
                   '-p', f'127.0.0.1:{self.port}:3306',
                   '--mount', f'type=volume,src={self.volume},dst=/var/lib/mysql',
                   '--mount', f'type=bind,src={self.secret},dst=/run/preview-db-password,readonly',
                   '-e', 'MARIADB_ROOT_PASSWORD_FILE=/run/preview-db-password',
                   '-e', 'MARIADB_DATABASE=' + self.database, 'mariadb:11.4', stdout=subprocess.DEVNULL)
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            try:
                with self.connect() as connection, connection.cursor() as cursor:
                    cursor.execute('SELECT 1')
                    return
            except pymysql.Error:
                time.sleep(1)
        raise RuntimeError('Local MariaDB did not become ready')

    def connect(self):
        return pymysql.connect(host='127.0.0.1', port=self.port, user='root',
                               password=self.secret.read_text(), database=self.database,
                               charset='utf8mb4', autocommit=True, read_timeout=120)

    def client(self, command, *, stdin=None, stdout=None):
        return docker('exec', '-i', '-e', 'MYSQL_PWD', self.container, *command,
                      env={'MYSQL_PWD': self.secret.read_text()}, stdin=stdin, stdout=stdout,
                      stderr=subprocess.PIPE)

    def restore(self, dump):
        # The destination can only be this managed local container, never source config.
        with self.connect() as connection, connection.cursor() as cursor:
            cursor.execute('DROP DATABASE ' + quote(self.database))
            cursor.execute('CREATE DATABASE ' + quote(self.database) + ' CHARACTER SET utf8mb4')
        with Path(dump).open('rb') as source:
            self.client(['mariadb', '--user=root', self.database], stdin=source, stdout=subprocess.DEVNULL)

    def backup(self, destination):
        with Path(destination).open('wb') as out:
            os.chmod(destination, 0o600)
            self.client(['mariadb-dump', '--user=root', '--single-transaction', '--skip-lock-tables', '--hex-blob', self.database], stdout=out)

    def stop(self):
        docker('stop', self.container, stdout=subprocess.DEVNULL)


def copy_assets(repo, state, manifest, origin='https://triples.eht3r.cloud'):
    root = Path(state) / 'uploads'
    root.mkdir(exist_ok=True)
    for source in (Path(repo) / 'uploads').rglob('*'):
        if source.is_symlink():
            raise RuntimeError('Refusing symlink in initial upload snapshot')
        if source.is_file():
            destination = root / source.relative_to(Path(repo) / 'uploads')
            destination.parent.mkdir(parents=True, exist_ok=True)
            if not destination.exists():
                shutil.copy2(source, destination)
    missing = []
    blob_paths = set(manifest['blobPaths'])
    for relative in manifest['references']:
        destination = root / relative
        if destination.exists() or relative in blob_paths:
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        partial = destination.with_name(destination.name + '.partial')
        try:
            url = origin.rstrip('/') + '/uploads/' + urllib.parse.quote(relative, safe='/')
            with urllib.request.urlopen(url, timeout=30) as response, partial.open('wb') as out:
                if response.status != 200:
                    raise RuntimeError('Unexpected asset response')
                content_type = response.headers.get_content_type()
                if destination.suffix.lower() in ('.png', '.jpg', '.jpeg', '.webp', '.gif', '.svg') and not content_type.startswith('image/') and content_type != 'application/octet-stream':
                    raise RuntimeError('Image origin returned a non-image response')
                shutil.copyfileobj(response, out)
            partial.replace(destination)
        except Exception:
            partial.unlink(missing_ok=True)
            missing.append(relative)
    atomic_json(Path(state) / 'missing-files.json', missing)
    return missing
