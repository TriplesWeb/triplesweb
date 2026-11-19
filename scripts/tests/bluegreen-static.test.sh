#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

bash -n scripts/deploy-pi-bluegreen.sh
# Verify that the deployment never stops a live slot before readiness checks.
grep -Fq 'wait_candidate "$slot"' scripts/deploy-pi-bluegreen.sh
grep -Fq 'switch_proxy "$active" "$next"' scripts/deploy-pi-bluegreen.sh
grep -Fq 'wait_proxy_slot "$candidate"' scripts/deploy-pi-bluegreen.sh
grep -Fq 'wait_proxy_slot "$old"' scripts/deploy-pi-bluegreen.sh
grep -Fq 'BG_MIN_AVAILABLE_MB' scripts/deploy-pi-bluegreen.sh
grep -Fq 'BG_ALLOW_SCHEMA_CHANGE' scripts/deploy-pi-bluegreen.sh
grep -Fq 'flock -w 900' scripts/deploy-pi-bluegreen.sh
grep -Fq 'docker exec "$proxy_cid" nginx -t' scripts/deploy-pi-bluegreen.sh
grep -Fq 'docker exec "$proxy_cid" nginx -s reload' scripts/deploy-pi-bluegreen.sh
! grep -Fq -- '--remove-orphans' scripts/deploy-pi-bluegreen.sh
! grep -Fq 'docker compose down' scripts/deploy-pi-bluegreen.sh
grep -Fq 'profiles: [legacy]' docker-compose.yml
grep -Fq 'bash ./deploy-pi-bluegreen.sh' .github/workflows/deploy-main.yml
grep -Fq 'docker-compose.bluegreen.yml' .github/workflows/deploy-main.yml

# Validate actual Docker Compose interpolation and make sure neither candidate
# publishes a host port or mounts MariaDB's data volume.
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-ci}" MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-ci}" APP_PORT=8090
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
docker compose --profile bluegreen -f docker-compose.yml -f docker-compose.bluegreen.yml config --format json >"$tmp"
python3 - "$tmp" <<'PY'
import json,sys
cfg=json.load(open(sys.argv[1],encoding="utf8"))
services=cfg["services"]
for slot in ("app_blue","app_green"):
    svc=services[slot]
    assert not svc.get("ports"), f"{slot} must not expose an external port"
    assert svc.get("mem_limit"), f"{slot} must have a memory limit"
    assert svc["environment"]["SPRING_JPA_HIBERNATE_DDL_AUTO"]=="none"
    assert any(str(m.get("target"))=="/app/uploads" for m in svc.get("volumes",[]))
    assert any(str(m.get("target"))=="/app/data" for m in svc.get("volumes",[]))
    assert all(str(m.get("target"))!="/var/lib/mysql" for m in svc.get("volumes",[]))
proxy=services["proxy"]
ports=proxy.get("ports",[])
assert len(ports)==1 and str(ports[0]["published"])=="8090"
assert str(ports[0]["target"])=="80"
assert "mariadb" in services
if "app" in services:
    assert "legacy" in services["app"].get("profiles",[]), "legacy app must be opt-in"
print("PASS: Blue-Green Compose safety invariants")
PY
