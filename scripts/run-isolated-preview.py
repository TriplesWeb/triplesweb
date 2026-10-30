#!/usr/bin/env python3
"""PR 앱을 별도 GitHub 실행기에 띄운다. 비밀값은 파일에서만 읽는다."""
import argparse, ipaddress, json, os, pathlib, subprocess, sys, time, urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("--runtime", required=True)
parser.add_argument("--pr", type=int, required=True)
parser.add_argument("--revision", required=True)
args = parser.parse_args()
if not 1 <= args.pr <= 9999 or len(args.revision) != 40:
    raise ValueError("PR 번호 또는 리비전 오류")
runtime = json.loads(pathlib.Path(args.runtime).read_text())
host = str(ipaddress.ip_address(runtime["host"]))
if ipaddress.ip_address(host) not in ipaddress.ip_network("100.64.0.0/10"):
    raise ValueError("DB가 Tailscale 주소가 아닙니다")
if not runtime["database"].endswith(f"_pr{args.pr}_preview") or runtime["username"] != f"pr_preview_{args.pr}":
    raise ValueError("PR 전용 DB가 아닙니다")
ip = subprocess.check_output(["tailscale", "ip", "-4"], text=True).strip().splitlines()[0]
if ipaddress.ip_address(ip) not in ipaddress.ip_network("100.64.0.0/10"):
    raise ValueError("미리보기 주소가 Tailscale 주소가 아닙니다")
root = pathlib.Path(os.environ["RUNNER_TEMP"]) / f"pr-{args.pr}"
root.mkdir(mode=0o700, exist_ok=True)
(root / "uploads").mkdir(exist_ok=True)
(root / "data").mkdir(exist_ok=True)
port = 18000 + args.pr
url = f"jdbc:mariadb://{host}:3306/{runtime['database']}?useSSL=false&connectTimeout=5000"
env = os.environ.copy()
env.update({
    "SPRING_PROFILES_ACTIVE": "preview",
    "PREVIEW_DATASOURCE_URL": url,
    "PREVIEW_DATASOURCE_USERNAME": runtime["username"],
    "PREVIEW_DATASOURCE_PASSWORD": runtime["password"],
    "APP_PREVIEW_NUMBER": str(args.pr),
    "APP_PREVIEW_REVISION": args.revision,
    "APP_PREVIEW_SNAPSHOT_AT": runtime["snapshotAt"],
    "SERVER_ADDRESS": ip, "SERVER_PORT": str(port),
    "CHEMISTRY_ADMIN_PASSWORD": runtime["adminPassword"],
    "FILE_UPLOAD_DIR": str(root / "uploads"),
    "COSMO_CREDENTIALS_FILE": str(root / "data" / "credentials.json"),
    "UPLOAD_FALLBACK_BASE_URL": "https://triples.eht3r.cloud",
    "UPLOAD_SYNC_ENABLED": "false", "OBJEKT_INGEST_ENABLED": "false",
    "ABSTRACT_INDEXER_ENABLED": "false", "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE": "4",
    "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE": "0"
})
log = open(root / "application.log", "w")
process = subprocess.Popen(["java", "-Xmx512m", "-jar", "build/libs/triples.war"],
                           stdout=log, stderr=subprocess.STDOUT, env=env, start_new_session=True)
(root / "pid").write_text(str(process.pid))
base = f"http://{ip}:{port}"
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
for _ in range(120):
    if process.poll() is not None:
        raise RuntimeError("미리보기 앱이 시작 중 종료되었습니다. 실행기 로그를 확인하세요.")
    try:
        with opener.open(base + "/api/preview/info", timeout=3) as response:
            info = json.load(response)
        if info.get("revision") == args.revision and info.get("pr") == args.pr:
            for page in ("/cosmo/objekt", "/schedule", "/triples/artist"):
                with opener.open(base + page, timeout=15) as response:
                    if response.status != 200: raise RuntimeError("화면 확인 실패")
            pathlib.Path(os.environ["GITHUB_OUTPUT"]).open("a").write(f"url={base}\n")
            print("격리 미리보기 실행 완료: " + base)
            sys.exit(0)
    except (OSError, ValueError):
        time.sleep(2)
raise RuntimeError("미리보기 앱 기동 시간 초과")
