#!/usr/bin/env bash
# 운영 Pi에서 Docker pull/up을 직렬화하고 readiness 확인.
set -euo pipefail
: "${IMAGE:?IMAGE is required}"
: "${APP_PORT:?APP_PORT is required}"
MODE="${1:-prod}"
[[ "$MODE" == "prod" ]] || exit 2
LOCK=/tmp/triplesweb-docker-deploy.lock
HEALTH_URL="http://127.0.0.1:${APP_PORT}/actuator/health/readiness"

# Compose uses the exported IMAGE during rollout. Save it only after readiness.
record_ready_image() {
  if grep -q '^IMAGE=' .env; then
    sed -i "s|^IMAGE=.*|IMAGE=$IMAGE|" .env
  else
    printf '\nIMAGE=%s\n' "$IMAGE" >> .env
  fi
}

run_deploy() {
  local start="$SECONDS" cid running previous_image=""
  rollback() {
    if [[ -n "$previous_image" && "$previous_image" != "$IMAGE" ]]; then
      echo "Attempting container rollback to $previous_image" >&2
      IMAGE="$previous_image" docker compose up -d --no-deps app || true
    fi
  }
  cid="$(docker compose ps -q app 2>/dev/null || true)"
  if [[ -n "$cid" ]]; then
    previous_image="$(docker inspect -f '{{.Config.Image}}' "$cid" 2>/dev/null || true)"
    if [[ "$previous_image" == "$IMAGE" ]] && curl --silent --show-error --fail --max-time 8 "$HEALTH_URL" >/dev/null; then
      record_ready_image
      echo "Already healthy on $IMAGE; no restart needed"
      return 0
    fi
  fi

  # 불변 SHA 이미지가 Pi에 이미 있다면 외부 네트워크 pull을 반복하지 않는다.
  local pull_started="$SECONDS"
  if docker image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "Immutable image already cached on Pi — pull skipped"
  else
    # pi/containerd 일시 오류는 재시도하되, 성공 시 즉시 진행한다.
    for attempt in 1 2 3 4 5; do
      if docker compose pull app; then break; fi
      if [[ "$attempt" == 5 ]]; then
        echo "Pull failed after five attempts" >&2
        return 1
      fi
      sleep "$((attempt * 4))"
    done
  fi
  echo "Image resolution took $((SECONDS-pull_started))s"
  local started="$SECONDS"
  # 이미 정상인 MariaDB를 다시 평가하거나 재생성하지 않고 앱만 갱신한다.
  # 동일 SHA로 실행 중이지만 readiness 실패라면 강제 재생성한다.
  local -a up_args=(up -d --no-deps app)
  if [[ "$previous_image" == "$IMAGE" ]]; then
    up_args=(up -d --no-deps --force-recreate app)
  fi
  if ! docker compose "${up_args[@]}"; then
    echo "Compose up failed" >&2
    rollback
    return 1
  fi
  if ! cid="$(docker compose ps -q app)" || [[ -z "$cid" ]]; then
    echo "app container missing after up" >&2
    rollback
    return 1
  fi
  if ! running="$(docker inspect -f '{{.Config.Image}}' "$cid")" || [[ "$running" != "$IMAGE" ]]; then
    echo "Image mismatch: $running != $IMAGE" >&2
    rollback
    return 1
  fi

  # readiness를 2초 단위로 확인해 불필요한 폴링 대기를 줄인다.
  for attempt in $(seq 1 180); do
    if curl --silent --show-error --fail --max-time 8 "$HEALTH_URL" >/dev/null 2>&1; then
      if ! record_ready_image; then
        rollback
        return 1
      fi
      echo "Readiness OK, startup=$((SECONDS-started))s, total=$((SECONDS-start))s, mode=$MODE"
      return 0
    fi
    sleep 2
  done
  echo "Readiness failed on $HEALTH_URL" >&2
  docker compose ps >&2 || true
  docker compose logs --tail=120 app >&2 || true

  # 이전 이미지로만 되돌린다. DB 스키마는 되돌리지 않는다.
  rollback
  return 1
}
(
  flock -w 900 9
  run_deploy
) 9>"$LOCK"
