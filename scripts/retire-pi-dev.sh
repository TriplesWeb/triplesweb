#!/usr/bin/env bash
# 기존 개발 앱 컨테이너만 종료해 Pi의 CPU/RAM을 회수한다.
# 운영 MariaDB, 데이터, 이미지/업로드 디렉터리는 삭제하지 않는다.
set -euo pipefail
PROD_DIR="${1:?production directory required}"
DEV_DIR="${2:?development directory required}"
PROD_REAL="$(realpath -m -- "$PROD_DIR")"
DEV_REAL="$(realpath -m -- "$DEV_DIR")"
if [[ "$PROD_REAL" == "$DEV_REAL" || "$DEV_REAL" == "/" || "$DEV_REAL" == "$PROD_REAL/"* || "$PROD_REAL" == "$DEV_REAL/"* ]]; then
  echo "ERROR: unsafe development path; refusing cleanup" >&2
  exit 1
fi
if [[ ! -f "$DEV_DIR/docker-compose.yml" ]]; then
  echo "No development Compose stack found; nothing to stop"
  exit 0
fi
# 실수로 다른 MariaDB나 운영 스택을 중지하지 않도록 구성 자체를 검사한다.
SERVICES="$(docker compose -f "$DEV_DIR/docker-compose.yml" --project-directory "$DEV_DIR" config --services)"
if [[ "$SERVICES" != "app" ]]; then
  echo "ERROR: dev Compose services must contain only app; found: $SERVICES" >&2
  exit 1
fi
echo "Stopping obsolete Pi dev app (data remains intact)"
docker compose -f "$DEV_DIR/docker-compose.yml" --project-directory "$DEV_DIR" down --remove-orphans
echo "Dev app container retired; no DB/volume deletion performed"
