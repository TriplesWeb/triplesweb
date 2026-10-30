#!/usr/bin/env python3
"""PR 전용 미리보기 배포. 운영 스키마는 읽기만 하고 별도 스키마에 공개 데이터만 복사한다."""
import argparse
import datetime as dt
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile
import time
import urllib.request
import urllib.parse

PUBLIC_TABLES = {
    "artist", "unit", "unit_member", "album", "album_section", "track",
    "schedule", "schedule_artist", "site_content", "archive", "media", "media_artist",
    "chemistry", "chemistry_group", "chemistry_group_artist", "shop", "shop_image",
    "objekt_catalog", "objekt_description", "flyway_schema_history",
}
IDENTIFIER = re.compile(r"[A-Za-z0-9_-]+")
IMAGE = re.compile(r"ghcr\.io/[a-z0-9_.-]+/[a-z0-9_.-]+:pr-(\d+)-([0-9a-f]{40})")

def checked_identifier(value):
    if not IDENTIFIER.fullmatch(value) or len(value) > 64:
        raise ValueError("허용하지 않는 DB 식별자")
    return value

def names(source_database, pr):
    checked_identifier(source_database)
    if not 1 <= pr <= 9999:
        raise ValueError("PR 번호 범위 오류")
    database = checked_identifier(f"{source_database}_pr{pr}_preview")
    if database == source_database:
        raise ValueError("운영 DB와 미리보기 DB는 달라야 합니다")
    return database, f"pr_preview_{pr}"

def public_tables(tables):
    return [name for name in tables if name.lower() in PUBLIC_TABLES and IDENTIFIER.fullmatch(name)]

def compose_spec(directory, pr, image, revision, bind_ip, network, database_host, database, username, password, admin_password, snapshot_at):
    match = IMAGE.fullmatch(image)
    if not match or int(match[1]) != pr or match[2] != revision:
        raise ValueError("미리보기 이미지와 PR/커밋이 일치하지 않습니다")
    if ipaddress.ip_address(bind_ip) not in ipaddress.ip_network("100.64.0.0/10"):
        raise ValueError("미리보기 포트는 Tailscale 주소에만 공개합니다")
    if not database.endswith(f"_pr{pr}_preview") or username != f"pr_preview_{pr}":
        raise ValueError("분리된 미리보기 DB 계정만 사용할 수 있습니다")
    checked_identifier(database)
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", database_host):
        raise ValueError("DB 호스트 형식 오류")
    url = f"jdbc:mariadb://{database_host}:3306/{database}?useSSL=false&connectTimeout=5000"
    environment = {
        "SPRING_PROFILES_ACTIVE": "preview",
        "SPRING_DATASOURCE_URL": url, "SPRING_DATASOURCE_USERNAME": username, "SPRING_DATASOURCE_PASSWORD": password,
        "PREVIEW_DATASOURCE_URL": url, "PREVIEW_DATASOURCE_USERNAME": username, "PREVIEW_DATASOURCE_PASSWORD": password,
        "APP_PREVIEW_NUMBER": str(pr), "APP_PREVIEW_REVISION": revision, "APP_PREVIEW_SNAPSHOT_AT": snapshot_at,
        "SERVER_PORT": "8080", "SERVER_SERVLET_SESSION_COOKIE_NAME": f"PR{pr}SESSION",
        "SERVER_SERVLET_SESSION_COOKIE_SECURE": "false", "CHEMISTRY_ADMIN_PASSWORD": admin_password,
        "FILE_UPLOAD_DIR": "/app/uploads", "MEDIA_CATALOG_PATH": "",
        "UPLOAD_FALLBACK_BASE_URL": "https://triples.eht3r.cloud",
        "UPLOAD_SYNC_ENABLED": "false", "UPLOAD_SYNC_PULL_ON_STARTUP": "false",
        "OBJEKT_INGEST_ENABLED": "false", "ABSTRACT_INDEXER_ENABLED": "false",
        "JAVA_TOOL_OPTIONS": "-XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=15.0 -XX:+ExitOnOutOfMemoryError",
    }
    # Compose의 달러 치환으로 비밀번호가 달라지지 않도록 한다.
    environment = {key: value.replace("$", "$$") for key, value in environment.items()}
    return {
        "services": {f"preview_{pr}": {
            "image": image, "container_name": f"triples-pr{pr}-preview", "restart": "unless-stopped",
            "labels": {"org.triples.preview.pr": str(pr), "org.triples.preview.revision": revision},
            "mem_limit": "512m", "cpus": "0.75",
            "ports": [f"{bind_ip}:{18000+pr}:8080"],
            "environment": environment,
            "volumes": [f"{directory}/uploads:/app/uploads", f"{directory}/data:/app/data"],
            "networks": ["database"],
        }},
        "networks": {"database": {"external": True, "name": network}},
    }

def needs_app_restart(current, previous_spec, desired_spec, revision):
    if not current:
        return True
    labels=current.get("Config",{}).get("Labels",{})
    return not (current.get("State",{}).get("Running")
                and labels.get("org.triples.preview.revision")==revision
                and previous_spec==desired_spec)

def run(args, *, data=None, env=None, cwd=None, sensitive=False, timeout=300, check=True):
    result = subprocess.run(args, input=data, stdin=subprocess.DEVNULL if data is None else None, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            env=env, cwd=cwd, timeout=timeout)
    if check and result.returncode:
        # DB 명령의 stderr에는 질의/값이 들어갈 수 있으므로 출력하지 않는다.
        detail = "" if sensitive else result.stderr[-1500:]
        raise RuntimeError(f"명령 실패: {args[0]} (exit {result.returncode}) {detail}")
    return result

def atomic_json(file, data):
    temporary = file.with_suffix(file.suffix + ".tmp")
    temporary.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    temporary.chmod(0o600)
    temporary.replace(file)

def mysql_command(container, database=None, dump=False, args=()):
    program = "mariadb-dump" if dump else "mariadb"
    shell = 'export MYSQL_PWD="$MARIADB_ROOT_PASSWORD"; exec ' + program + ' -uroot --default-character-set=utf8mb4 "$@"'
    return ["docker", "exec", "-i", container, "sh", "-c", shell, "preview-db", *args, *([database] if database else [])]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--root",required=True);parser.add_argument("--pr",type=int,required=True)
    parser.add_argument("--image");parser.add_argument("--revision");parser.add_argument("--registry-user")
    parser.add_argument("--remove",action="store_true")
    parser.add_argument("--database-only",action="store_true")
    args=parser.parse_args()
    token=None if args.remove or args.database_only else sys.stdin.readline().strip()
    if not args.remove and not args.database_only and not token:
        raise RuntimeError("컨테이너 레지스트리 인증 정보가 없습니다")
    root=Path(args.root).resolve()
    if not (root/"docker-compose.yml").is_file() or not 1<=args.pr<=9999:
        raise ValueError("운영 스택 경로/PR 번호를 확인하세요")
    directory=(root/"previews"/f"pr-{args.pr}").resolve()
    if root not in directory.parents:
        raise ValueError("미리보기 디렉터리가 운영 스택 밖입니다")
    directory.mkdir(parents=True,exist_ok=True,mode=0o700)
    directory.chmod(0o700)
    main_compose=["docker","compose","-f",str(root/"docker-compose.yml")]
    db=run([*main_compose,"ps","-q","mariadb"],cwd=root).stdout.strip()
    app=run([*main_compose,"ps","-q","app"],cwd=root).stdout.strip()
    if not db or not app:
        raise RuntimeError("운영 app/mariadb 컨테이너가 필요합니다")
    def sql(query,database=None):
        return run(mysql_command(db,database,args=("--batch","--skip-column-names")),data=query,sensitive=True).stdout.strip()
    source_url=run(["docker","exec",app,"printenv","SPRING_DATASOURCE_URL"],sensitive=True).stdout.strip()
    source_database=checked_identifier(urllib.parse.urlparse(source_url.removeprefix("jdbc:")).path.lstrip("/"))
    database,username=names(source_database,args.pr)
    state_file=directory/"state.json";compose_file=directory/"compose.json"
    state=json.loads(state_file.read_text()) if state_file.exists() else None
    if state and (state.get("database")!=database or state.get("username")!=username):
        raise RuntimeError("미리보기 상태의 DB 소유 정보가 일치하지 않습니다")
    container=f"triples-pr{args.pr}-preview"
    inspect=run(["docker","inspect",container],check=False)
    old_image=None
    old=None
    previous_spec=json.loads(compose_file.read_text()) if compose_file.exists() else None
    if inspect.returncode==0:
        old=json.loads(inspect.stdout)[0]
        if old.get("Config",{}).get("Labels",{}).get("org.triples.preview.pr")!=str(args.pr):
            raise RuntimeError("같은 이름의 다른 컨테이너는 변경하지 않습니다")
        old_image=old["Config"]["Image"]
    if args.remove:
        mobile_compose=directory/"mobile"/"compose.json"
        if mobile_compose.exists():
            run(["docker","compose","-p",f"triples-pr{args.pr}-mobile","-f",str(mobile_compose),"down"],cwd=directory)
        if compose_file.exists():
            run(["docker","compose","-p",f"triples-pr{args.pr}","-f",str(compose_file),"down"],cwd=directory)
        if state:
            sql(f"DROP DATABASE IF EXISTS `{database}`; DROP USER IF EXISTS '{username}'@'%';")
            state_file.unlink(missing_ok=True);compose_file.unlink(missing_ok=True)
        print(f"PR {args.pr} 미리보기 정리 완료 (운영 스택 유지)")
        return
    if not args.database_only and (not args.image or not args.revision or not args.registry_user):
        raise ValueError("이미지/커밋/레지스트리 사용자 값이 필요합니다")
    match=IMAGE.fullmatch(args.image or "")
    if not args.database_only and (not match or int(match[1])!=args.pr or match[2]!=args.revision):
        raise ValueError("미리보기 이미지 검증 실패")
    bind_ip=run(["tailscale","ip","-4"]).stdout.strip().splitlines()[0]
    if ipaddress.ip_address(bind_ip) not in ipaddress.ip_network("100.64.0.0/10"):
        raise RuntimeError("서버 Tailscale 주소를 찾을 수 없습니다")
    db_info=json.loads(run(["docker","inspect",db]).stdout)[0]
    network=next(iter(db_info["NetworkSettings"]["Networks"]))
    database_host=db_info["Name"].lstrip("/")
    if not state:
        if sql(f"SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{database}'") or sql(f"SELECT User FROM mysql.user WHERE User='{username}'"):
            raise RuntimeError("동일 이름의 기존 DB/계정이 있어 덮어쓰지 않습니다")
        state={"database":database,"username":username,"password":secrets.token_hex(24),
               "snapshot_at":dt.datetime.now(dt.timezone.utc).isoformat(),"initialized":False}
        atomic_json(state_file,state)
    if not state["initialized"]:
        print("운영 공개 데이터의 분리된 복사본 준비 중")
        charset,collation=sql(f"SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{source_database}'").split("\t")
        checked_identifier(charset);checked_identifier(collation)
        sql(f"DROP DATABASE IF EXISTS `{database}`; CREATE DATABASE `{database}` CHARACTER SET {charset} COLLATE {collation};")
        tables=sql(f"SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='{source_database}' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME").splitlines()
        if not tables or any(not IDENTIFIER.fullmatch(name) for name in tables):
            raise RuntimeError("운영 테이블 목록을 확인할 수 없습니다")
        schema=run(mysql_command(db,dump=True,args=("--no-data","--skip-comments","--skip-triggers","--skip-lock-tables",source_database,*tables)),sensitive=True).stdout
        if re.search(r"^\s*(USE |CREATE DATABASE |DROP DATABASE )",schema,re.I|re.M):
            raise RuntimeError("DB 선택 명령을 포함한 덤프는 가져오지 않습니다")
        schema=schema.replace(f"`{source_database}`.",f"`{database}`.")
        sql(schema,database)
        selected=public_tables(tables)
        dump_args=mysql_command(db,dump=True,args=("--no-create-info","--skip-comments","--skip-triggers","--skip-add-locks","--skip-lock-tables","--single-transaction","--quick","--complete-insert",source_database,*selected))
        with tempfile.TemporaryFile(mode="w+t") as dump_errors:
            producer=subprocess.Popen(dump_args,stdout=subprocess.PIPE,stderr=dump_errors,text=True)
            consumer=subprocess.run(mysql_command(db,database),stdin=producer.stdout,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,text=True,timeout=300)
            producer.stdout.close()
            code=producer.wait(timeout=300)
            if code or consumer.returncode:
                raise RuntimeError("공개 데이터 복사 실패 (운영 데이터는 변경하지 않았습니다)")
        sql(f"DROP USER IF EXISTS '{username}'@'%'; CREATE USER '{username}'@'%' IDENTIFIED BY '{state['password']}'; GRANT ALL PRIVILEGES ON `{database}`.* TO '{username}'@'%';")
        state["initialized"]=True;atomic_json(state_file,state)
        print(f"공개 데이터 {len(selected)}개 테이블 복사 완료")
    admin_password=run(["docker","exec",app,"printenv","CHEMISTRY_ADMIN_PASSWORD"],sensitive=True,check=False).stdout.rstrip("\n")
    if args.database_only:
        print(json.dumps({"host":bind_ip,"database":database,"username":username,
                          "password":state["password"],"adminPassword":admin_password,
                          "snapshotAt":state["snapshot_at"]}))
        return
    for name in ("uploads","data"):(directory/name).mkdir(exist_ok=True)
    spec=compose_spec(directory,args.pr,args.image,args.revision,bind_ip,network,database_host,database,username,state["password"],admin_password,state["snapshot_at"])
    atomic_json(compose_file,spec)
    if needs_app_restart(old,previous_spec,spec,args.revision):
        with tempfile.TemporaryDirectory(prefix="preview-docker-auth-",dir=directory) as auth_dir:
            env={**os.environ,"DOCKER_CONFIG":auth_dir}
            run(["docker","login","ghcr.io","-u",args.registry_user,"--password-stdin"],data=token+"\n",env=env,sensitive=True)
            token=None
            run(["docker","pull",args.image],env=env)
            run(["docker","compose","-p",f"triples-pr{args.pr}","-f",str(compose_file),"up","-d","--remove-orphans"],env=env,cwd=directory)
    else:
        print("같은 버전과 설정이 실행 중이므로 앱 재시작을 생략합니다",flush=True)
    url=f"http://{bind_ip}:{18000+args.pr}"
    deadline=time.monotonic()+600
    while time.monotonic()<deadline:
        try:
            with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(url+"/api/preview/info",timeout=3) as response:
                info=json.load(response)
            if info.get("revision")==args.revision and info.get("pr")==args.pr:
                opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
                for path in ("/cosmo/objekt", "/schedule", "/triples/artist"):
                    with opener.open(url+path,timeout=15) as page:
                        html=page.read().decode("utf-8")
                        if page.status!=200 or "/js/pr-preview.js" not in html:
                            raise RuntimeError("미리보기 화면 확인 실패: "+path)
                print("미리보기 오브젝트/일정/아티스트 화면 HTTP 200 확인 완료")
                print("PREVIEW_URL="+url)
                print("PR_PREVIEW_REVISION="+args.revision)
                break
        except Exception:pass
        time.sleep(3)
    else:
        raise RuntimeError("미리보기 앱의 기동/커밋 확인이 완료되지 않았습니다")
    if old_image and old_image!=args.image and IMAGE.fullmatch(old_image) and int(IMAGE.fullmatch(old_image)[1])==args.pr:
        run(["docker","image","rm",old_image],check=False)

if __name__=="__main__":
    try:main()
    except Exception as error:
        print("미리보기 배포 실패: "+str(error),file=sys.stderr);sys.exit(1)
