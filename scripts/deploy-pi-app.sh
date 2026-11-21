#!/usr/bin/env bash
# Replace the production app on the Pi. MariaDB and uploads stay in place.
set -euo pipefail
: "${IMAGE:?IMAGE is required}"
: "${MIGRATIONS_TREE:?MIGRATIONS_TREE is required}"
: "${APP_PORT:?APP_PORT is required}"
export IMAGE APP_PORT COMPOSE_REMOVE_ORPHANS=false
READY_TIMEOUT_SECONDS="${APP_READY_TIMEOUT_SECONDS:-360}"
STOP_TIMEOUT_SECONDS="${APP_STOP_TIMEOUT_SECONDS:-600}"
LOCK=/tmp/triplesweb-docker-deploy.lock
MIGRATION_FILE=.deploy-migration-tree
LEGACY_MIGRATION_FILE=bluegreen/migration-tree
BASE_URL="http://127.0.0.1:$APP_PORT"
COMPOSE=(docker compose -f docker-compose.yml)

source "$(dirname "${BASH_SOURCE[0]}")/deploy-pi-image-retention.sh"

log() { printf '[deploy] %s\n' "$*" >&2; }
fail() { log "ERROR: $*"; exit 1; }
running_image() { docker inspect -f '{{.Config.Image}}' "$1"; }
is_running() { [[ -n "$1" && "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" == true ]]; }

healthy() {
  local timeout="$1" status
  curl -fsS --connect-timeout 2 --max-time "$timeout" "$BASE_URL/actuator/health/readiness" >/dev/null 2>&1 &&
    status="$(curl -fsS --connect-timeout 2 --max-time "$timeout" -o /dev/null -w '%{http_code}' "$BASE_URL/" 2>/dev/null)" &&
    [[ "$status" == 200 ]]
}

wait_until_healthy() {
  local deadline=$((SECONDS + READY_TIMEOUT_SECONDS)) remaining timeout
  while (( SECONDS < deadline )); do
    remaining=$((deadline - SECONDS))
    timeout=$((remaining < 5 ? remaining : 5))
    if healthy "$timeout"; then return 0; fi
    if (( SECONDS < deadline )); then sleep 2; fi
  done
  return 1
}

pull_image() {
  local attempt
  if docker image inspect "$IMAGE" >/dev/null 2>&1; then
    log "Target image is cached"
    return
  fi
  for attempt in 1 2 3 4 5; do
    if "${COMPOSE[@]}" pull app; then return 0; fi
    if (( attempt < 5 )); then sleep "$((attempt * 4))"; fi
  done
  fail "Image pull failed. The running app was left in place."
}

project_container() {
  docker ps -aq --filter "label=com.docker.compose.project=$1" \
    --filter "label=com.docker.compose.service=$2"
}

record_success() {
  local staged_env staged_tree
  staged_env="$(mktemp .env.deploy.XXXXXX)"
  cp -p .env "$staged_env"
  if grep -q '^IMAGE=' "$staged_env"; then
    sed -i "s|^IMAGE=.*|IMAGE=$IMAGE|" "$staged_env"
  else
    printf '\nIMAGE=%s\n' "$IMAGE" >> "$staged_env"
  fi
  mv -f "$staged_env" .env
  staged_tree="$(mktemp "${MIGRATION_FILE}.XXXXXX")"
  printf '%s\n' "$MIGRATIONS_TREE" > "$staged_tree"
  mv -f "$staged_tree" "$MIGRATION_FILE"
}

restore_previous() {
  local previous_image="$1" active_slot="$2" proxy="$3"
  if [[ -n "$active_slot" ]]; then
    docker start "$active_slot" >/dev/null && docker start "$proxy" >/dev/null && wait_until_healthy
  elif [[ -n "$previous_image" ]]; then
    IMAGE="$previous_image" "${COMPOSE[@]}" up -d --no-deps app && wait_until_healthy
  else
    return 1
  fi
}

run_deploy() {
  [[ -f .env && -f docker-compose.yml ]] || fail "Compose and .env files must exist"
  [[ "$READY_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ && "$STOP_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] ||
    fail "Deployment timeouts must be positive integers"
  "${COMPOSE[@]}" config --quiet

  local db project current previous_image="" previous_tree="" active_slot="" proxy blue green
  local id candidate start
  local -a legacy_containers=() running_slots=()
  db="$("${COMPOSE[@]}" ps -q mariadb)"
  [[ -n "$db" && "$(docker inspect -f '{{.State.Health.Status}}' "$db")" == healthy ]] ||
    fail "MariaDB is not healthy. The running app was left in place."
  project="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$db")"
  [[ -n "$project" && "$project" != '<no value>' ]] || fail "Cannot identify the production Compose project"

  proxy="$(project_container "$project" proxy)"
  blue="$(project_container "$project" app_blue)"
  green="$(project_container "$project" app_green)"
  for id in "$proxy" "$blue" "$green"; do
    [[ "$id" != *$'\n'* ]] || fail "Multiple legacy containers for a single Compose service"
    if [[ -n "$id" ]]; then legacy_containers+=("$id"); fi
  done
  for id in "$blue" "$green"; do
    if is_running "$id"; then running_slots+=("$id"); fi
  done

  current="$("${COMPOSE[@]}" ps -q app)"
  if is_running "$proxy"; then
    [[ -z "$current" ]] || fail "Both the old proxy and single app are running. Resolve the port ownership first."
    local headers slot
    headers="$(curl -fsS --connect-timeout 2 --max-time 5 -D - -o /dev/null \
      "$BASE_URL/actuator/health/readiness" 2>/dev/null || true)"
    slot="$(awk 'tolower($1) == "x-bluegreen-slot:" {gsub(/\r/, "", $2); print $2; exit}' <<< "$headers")"
    # A failed old site can still be replaced. Use the on-disk route for recovery.
    if [[ -z "$slot" && -f bluegreen/active.conf ]]; then
      slot="$(sed -n 's/^# ACTIVE_SLOT=//p' bluegreen/active.conf | head -n 1)"
    fi
    case "$slot" in
      blue) active_slot="$blue" ;;
      green) active_slot="$green" ;;
    esac
    if is_running "$active_slot"; then previous_image="$(running_image "$active_slot")"; fi
  elif [[ -n "$current" ]]; then
    previous_image="$(running_image "$current")"
  fi

  if [[ -f "$MIGRATION_FILE" ]]; then
    previous_tree="$(cat "$MIGRATION_FILE")"
  elif [[ -n "$proxy" && -f "$LEGACY_MIGRATION_FILE" ]]; then
    previous_tree="$(cat "$LEGACY_MIGRATION_FILE")"
  fi
  if [[ -n "$current" && "$previous_image" == "$IMAGE" && "$previous_tree" == "$MIGRATIONS_TREE" ]] && healthy 5; then
    log "Already healthy on $IMAGE"
    return
  fi

  pull_image
  start="$SECONDS"
  if (( ${#running_slots[@]} )); then
    log "Stopping legacy application slots before starting the single app"
    if ! docker stop -t "$STOP_TIMEOUT_SECONDS" "${running_slots[@]}" >/dev/null; then
      docker start "${running_slots[@]}" >/dev/null || true
      fail "Could not stop all legacy application slots"
    fi
  fi
  if is_running "$proxy" && ! docker stop -t 10 "$proxy" >/dev/null; then
    if (( ${#running_slots[@]} )); then docker start "${running_slots[@]}" >/dev/null || true; fi
    fail "Could not release the production port from the old proxy"
  fi
  if [[ -n "$current" ]]; then
    "${COMPOSE[@]}" stop -t "$STOP_TIMEOUT_SECONDS" app
  fi

  log "Starting $IMAGE on port $APP_PORT"
  if "${COMPOSE[@]}" up -d --no-deps --force-recreate app &&
    candidate="$("${COMPOSE[@]}" ps -q app)" && [[ -n "$candidate" ]] &&
    [[ "$(running_image "$candidate")" == "$IMAGE" ]] && wait_until_healthy; then
    record_success
    if (( ${#legacy_containers[@]} )); then
      docker rm "${legacy_containers[@]}" >/dev/null || log "Could not remove stopped legacy containers"
    fi
    log "Deployment healthy after $((SECONDS - start))s"
    cleanup_after_deploy "$previous_image"
    return
  fi

  "${COMPOSE[@]}" logs --tail=120 app >&2 || true
  "${COMPOSE[@]}" stop -t 10 app || fail "Could not stop the failed app. Inspect the app and database before recovery."
  if [[ "$previous_tree" == "$MIGRATIONS_TREE" && "$previous_image" != "$IMAGE" ]] &&
    restore_previous "$previous_image" "$active_slot" "$proxy"; then
    fail "New app failed. The previous app is serving again."
  fi
  fail "New app failed. No healthy rollback was verified. Check Flyway and the app logs before recovery."
}

exec 9>"$LOCK"
flock -w 900 9 || fail "Another production deployment holds the lock"
run_deploy
