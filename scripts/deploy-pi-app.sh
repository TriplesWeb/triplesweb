#!/usr/bin/env bash
# Pi에서 개발/운영이 공유하는 Docker daemon을 직렬화하고 readiness 확인.
set -euo pipefail
: "${IMAGE:?IMAGE is required}"
: "${APP_PORT:?APP_PORT is required}"
MODE="${1:?dev or prod}"
[[ "$MODE" == "dev" || "$MODE" == "prod" ]] || exit 2
LOCK=/tmp/triplesweb-docker-deploy.lock
HEALTH_URL="http://127.0.0.1:${APP_PORT}/actuator/health/readiness"

run_deploy() {
  local start="$SECONDS" cid running previous_image="" ok=false
  cid="$(docker compose ps -q app 2>/dev/null || true)"
  if [[ -n "$cid" ]]; then
    previous_image="$(docker inspect -f '{{.Config.Image}}' "$cid" 2>/dev/null || true)"
    if [[ "$previous_image" == "$IMAGE" ]] && curl --silent --show-error --fail --max-time 8 "$HEALTH_URL" >/dev/null; then
      echo "Already healthy on $IMAGE; no restart needed"
      return 0
    fi
  fi

  for attempt in 1 2 3 4 5; do
    if docker compose pull app; then break; fi
    if [[ "$attempt" == 5 ]]; then
      echo "Pull failed after five attempts" >&2
      return 1
    fi
    sleep "$((attempt * 8))"
  done
  echo "Image pulled in $((SECONDS-start))s"
  local started="$SECONDS"
  docker compose up -d
  cid="$(docker compose ps -q app)"
  [[ -n "$cid" ]] || { echo "app container missing after up" >&2; return 1; }
  running="$(docker inspect -f '{{.Config.Image}}' "$cid")"
  [[ "$running" == "$IMAGE" ]] || { echo "Image mismatch: $running != $IMAGE" >&2; return 1; }

  for attempt in $(seq 1 60); do
    if curl --silent --show-error --fail --max-time 8 "$HEALTH_URL" >/dev/null 2>&1; then
      echo "Readiness OK, startup=$((SECONDS-started))s, total=$((SECONDS-start))s, mode=$MODE"
      return 0
    fi
    sleep 5
  done
  echo "Readiness failed on $HEALTH_URL" >&2
  docker compose ps >&2 || true
  docker compose logs --tail=120 app >&2 || true

  # 이전 이미지로만 되돌린다. DB 스키마 롤백은 절대 자동 실행하지 않는다.
  if [[ -n "$previous_image" && "$previous_image" != "$IMAGE" ]]; then
    echo "Attempting container rollback to $previous_image" >&2
    sed -i "s|^IMAGE=.*|IMAGE=$previous_image|" .env
    docker compose up -d app || true
  fi
  return 1
}
(
  flock -w 900 9
  run_deploy
) 9>"$LOCK"
