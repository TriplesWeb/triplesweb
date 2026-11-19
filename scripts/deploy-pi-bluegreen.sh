#!/usr/bin/env bash
# Run on the Pi from /opt/stacks/triplesweb. Never shut down an active slot
# until its replacement is healthy AND Nginx has switched successfully.
set -euo pipefail
: "${IMAGE:?IMAGE must be an immutable GHCR SHA tag}"
: "${MIGRATIONS_TREE:?MIGRATIONS_TREE required}"
APP_PORT="${APP_PORT:-8090}"
BG_MIN_AVAILABLE_MB="${BG_MIN_AVAILABLE_MB:-1400}"
BG_READY_TIMEOUT_SECONDS="${BG_READY_TIMEOUT_SECONDS:-360}"
BG_DRAIN_SECONDS="${BG_DRAIN_SECONDS:-30}"
export APP_PORT COMPOSE_PROFILES=bluegreen
COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.bluegreen.yml)
BASE_COMPOSE=(docker compose --profile legacy -f docker-compose.yml)
STATE_DIR=bluegreen
STATE_FILE="$STATE_DIR/active-slot"
MIGRATION_FILE="$STATE_DIR/migration-tree"
NGINX_CONF="$STATE_DIR/active.conf"
LOCK=/tmp/triplesweb-docker-deploy.lock

# Clean up only the unpromoted candidate on failure; never stop a live upstream.
CANDIDATE_SLOT=""
cleanup_failed_candidate() {
  local result="$?"
  if (( result != 0 )) && [[ -n "$CANDIDATE_SLOT" ]]; then
    printf '[bluegreen] Failed candidate cleanup: app_%s\n' "$CANDIDATE_SLOT" >&2
    "${COMPOSE[@]}" stop -t 10 "app_$CANDIDATE_SLOT" >/dev/null 2>&1 || true
  fi
}
trap cleanup_failed_candidate EXIT

log() { printf '[bluegreen] %s\n' "$*" >&2; }
fail() { log "ERROR: $*"; exit 1; }
is_running() {
  local cid="$1"
  [[ -n "$cid" ]] && [[ "$(docker inspect -f '{{.State.Running}}' "$cid" 2>/dev/null || true)" == "true" ]]
}
container_of() { "${COMPOSE[@]}" ps -q "$1" 2>/dev/null || true; }
proxy_healthy() { curl -fsS --connect-timeout 2 --max-time 5 "http://127.0.0.1:$APP_PORT/actuator/health/readiness" >/dev/null 2>&1; }
live_slot() { sed -n 's/^# ACTIVE_SLOT=//p' "$NGINX_CONF" 2>/dev/null | head -n 1; }

write_proxy_config() {
  local slot="$1" output="$2"
  [[ "$slot" == blue || "$slot" == green ]] || fail "Invalid slot: $slot"
  cat > "$output" <<EOF
# ACTIVE_SLOT=$slot
map \$http_x_forwarded_proto \$bg_forwarded_proto {
    default \$scheme;
    ~*^https$ https;
}
map \$http_upgrade \$bg_connection_upgrade {
    default upgrade;
    '' close;
}
server {
    listen 80;
    server_name _;
    client_max_body_size 10g;
    client_body_timeout 600s;
    proxy_connect_timeout 10s;
    proxy_read_timeout 600s;
    proxy_send_timeout 600s;
    location / {
        proxy_pass http://app_${slot}:8080;
        proxy_http_version 1.1;
        # Stream large photos and videos without Nginx temp-file buffering.
        proxy_request_buffering off;
        proxy_buffering off;
        proxy_set_header Host \$http_host;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$bg_forwarded_proto;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection \$bg_connection_upgrade;
        add_header X-Bluegreen-Slot $slot always;
    }
}
EOF
}

check_memory() {
  local available_kb
  # Compose accepts mem_limit even when the host kernel cannot enforce it.
  # Refuse staging rather than risk the host OOM killer taking down production.
  [[ "$(docker info --format '{{.MemoryLimit}}')" == true ]] ||
    fail "Docker memory limits unsupported. Enable the host memory cgroup before Blue-Green deployment. Active site remains online."
  available_kb="$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)"
  [[ "${available_kb:-0}" =~ ^[0-9]+$ ]] || fail "Cannot read MemAvailable"
  if (( available_kb < BG_MIN_AVAILABLE_MB * 1024 )); then
    fail "Pi free memory insufficient: $((available_kb/1024)) MB < $BG_MIN_AVAILABLE_MB MB. Active site remains online."
  fi
  log "Available memory: $((available_kb/1024)) MB (required ≥ $BG_MIN_AVAILABLE_MB MB)"
}

check_database() {
  local cid
  cid="$("${BASE_COMPOSE[@]}" ps -q mariadb 2>/dev/null || true)"
  is_running "$cid" || fail "MariaDB not running. Refusing staging deploy."
  [[ "$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid")" == healthy ]] || fail "MariaDB not healthy."
}

check_migrations() {
  if [[ -f "$MIGRATION_FILE" ]]; then
    local previous
    previous="$(cat "$MIGRATION_FILE")"
    if [[ "$previous" != "$MIGRATIONS_TREE" && "${BG_ALLOW_SCHEMA_CHANGE:-false}" != true ]]; then
      fail "Flyway migrations changed; require explicit BG_ALLOW_SCHEMA_CHANGE=true after backward-compatibility review. Old site remains online."
    fi
  else
    log "First Blue-Green deployment: no migration marker. Review bootstrap Flyway compatibility before merging."
  fi
}

pull_if_needed() {
  local slot="$1"
  if docker image inspect "$IMAGE" >/dev/null 2>&1; then
    log "Immutable SHA image already cached on Pi"
    return 0
  fi
  for attempt in 1 2 3 4 5; do
    if "${COMPOSE[@]}" pull "app_$slot"; then return 0; fi
    if (( attempt == 5 )); then fail "Image pull failed five times"; fi
    sleep "$((attempt * 4))"
  done
}

wait_candidate() {
  local slot="$1" cid ip attempt home_status
  cid="$(container_of "app_$slot")"
  is_running "$cid" || fail "Candidate app_$slot did not start"
  ip="$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$cid")"
  [[ "$ip" =~ ^[0-9.]+$ ]] || fail "Cannot resolve candidate container IPv4"
  log "Warming app_$slot at $ip:8080"
  for (( attempt=0; attempt<BG_READY_TIMEOUT_SECONDS; attempt+=2 )); do
    if ! is_running "$cid"; then
      docker logs --tail=100 "$cid" >&2 || true
      fail "Candidate exited during warmup"
    fi
    if curl -fsS --connect-timeout 2 --max-time 5 "http://$ip:8080/actuator/health/readiness" >/dev/null 2>&1 &&
       home_status="$(curl -fsS --connect-timeout 2 --max-time 5 -o /dev/null -w '%{http_code}' "http://$ip:8080/" 2>/dev/null)" &&
       [[ "$home_status" == 200 ]]; then
      log "Candidate app_$slot readiness and home page checks passed"
      return 0
    fi
    sleep 2
  done
  docker logs --tail=100 "$cid" >&2 || true
  fail "Candidate timed out after $BG_READY_TIMEOUT_SECONDS seconds. Active site was not stopped."
}

proxy_points_to() {
  local slot="$1" headers
  headers="$(mktemp)"
  if curl -fsS -D "$headers" -o /dev/null --connect-timeout 2 --max-time 8 \
      "http://127.0.0.1:$APP_PORT/actuator/health/readiness" &&
      grep -Eiq "^X-Bluegreen-Slot: $slot[[:space:]]*$" "$headers"; then
    rm -f "$headers"
    return 0
  fi
  rm -f "$headers"
  return 1
}

wait_proxy_slot() {
  local slot="$1" i
  for i in 1 2 3 4 5 6 7 8 9 10; do
    if proxy_points_to "$slot"; then return 0; fi
    sleep 2
  done
  return 1
}

remember_success() {
  local slot="$1" tmp="$STATE_FILE.tmp.$$"
  printf '%s\n' "$slot" > "$tmp"
  mv -f "$tmp" "$STATE_FILE"
  printf '%s\n' "$MIGRATIONS_TREE" > "$MIGRATION_FILE"
  # Keep legacy rollback metadata in .env, but do not restart the old app.
  if grep -q '^IMAGE=' .env; then
    sed -i "s|^IMAGE=.*|IMAGE=$IMAGE|" .env
  else
    printf 'IMAGE=%s\n' "$IMAGE" >> .env
  fi
  log "Promoted app_$slot successfully"
}

stage_candidate() {
  local slot="$1"
  check_memory
  check_database
  check_migrations
  if [[ "$slot" == blue ]]; then export BG_BLUE_IMAGE="$IMAGE"; else export BG_GREEN_IMAGE="$IMAGE"; fi
  # A failed previous candidate is not allowed to interfere with the active slot.
  "${COMPOSE[@]}" stop "app_$slot" >/dev/null 2>&1 || true
  pull_if_needed "$slot"
  CANDIDATE_SLOT="$slot"
  "${COMPOSE[@]}" up -d --no-deps "app_$slot"
  wait_candidate "$slot"
}

bootstrap_proxy() {
  local legacy_cid previous_image
  legacy_cid="$("${BASE_COMPOSE[@]}" ps -q app 2>/dev/null || true)"
  is_running "$legacy_cid" || fail "No active legacy app found. Manual recovery required."
  previous_image="$(docker inspect -f '{{.Config.Image}}' "$legacy_cid")"
  log "Legacy app serves $APP_PORT. Warm the new blue slot before the first port transfer."
  stage_candidate blue
  write_proxy_config blue "$NGINX_CONF.tmp.$"
  mv -f "$NGINX_CONF.tmp.$" "$NGINX_CONF"
  # Download and validate Nginx while the legacy site is still serving traffic.
  # Compose run does not publish port 8090 without --service-ports.
  log "Pre-pulling Nginx and testing its config before first cutover"
  "${COMPOSE[@]}" pull proxy
  "${COMPOSE[@]}" run --rm --no-deps proxy nginx -t
  log "First cutover: stopping legacy app to release host port $APP_PORT"
  "${BASE_COMPOSE[@]}" stop app
  # Restore legacy app automatically if the Nginx proxy cannot serve traffic.
  if ! "${COMPOSE[@]}" up -d --no-deps proxy || ! wait_proxy_slot blue; then
    log "First cutover failed: stopping proxy and restoring legacy app"
    "${COMPOSE[@]}" stop proxy >/dev/null 2>&1 || true
    # IMAGE in this process points to the candidate and overrides .env. Start
    # the exact original container; Compose up could recreate it with new code.
    docker start "$legacy_cid" || log "CRITICAL: original legacy container could not be restarted"
    fail "Initial Nginx cutover failed; legacy restart attempted"
  fi
  # Proxy is now serving blue; never auto-stop it if metadata write fails.
  CANDIDATE_SLOT=""
  remember_success blue
  cleanup_after_deploy "$previous_image"
  log "First cutover finished. Next deployments will warm the inactive slot."
}

switch_proxy() {
  local old="$1" candidate="$2" proxy_cid previous="$NGINX_CONF.previous"
  proxy_cid="$(container_of proxy)"
  is_running "$proxy_cid" || fail "Proxy not running. Refusing switch to protect active slot."
  cp -f "$NGINX_CONF" "$previous"
  write_proxy_config "$candidate" "$NGINX_CONF.tmp.$$"
  mv -f "$NGINX_CONF.tmp.$$" "$NGINX_CONF"
  if ! docker exec "$proxy_cid" nginx -t ||
     ! docker exec "$proxy_cid" nginx -s reload ||
     ! wait_proxy_slot "$candidate"; then
    log "Nginx switch failed. Restoring previous upstream."
    cp -f "$previous" "$NGINX_CONF.tmp.$$"
    mv -f "$NGINX_CONF.tmp.$$" "$NGINX_CONF"
    docker exec "$proxy_cid" nginx -s reload || true
    if ! wait_proxy_slot "$old"; then
      CANDIDATE_SLOT="" # Upstream state uncertain: preserve both containers.
      fail "CRITICAL: rollback not verified; both app slots remain running."
    fi
    fail "Promotion rejected. Old app_$old has not been stopped."
  fi
  # Old slot continues running during drain. Recheck the new upstream
  # BEFORE we finalize promotion or stop the rollback candidate.
  CANDIDATE_SLOT=""
  log "New traffic goes to app_$candidate; draining app_$old for $BG_DRAIN_SECONDS seconds"
  sleep "$BG_DRAIN_SECONDS"
  if ! proxy_points_to "$candidate"; then
    log "New upstream failed during drain; restoring previous Nginx upstream"
    cp -f "$previous" "$NGINX_CONF.tmp.$"
    mv -f "$NGINX_CONF.tmp.$" "$NGINX_CONF"
    if docker exec "$proxy_cid" nginx -t &&
       docker exec "$proxy_cid" nginx -s reload &&
       wait_proxy_slot "$old"; then
      "${COMPOSE[@]}" stop -t 10 "app_$candidate" || true
      fail "Candidate became unhealthy during drain; original slot restored"
    fi
    fail "CRITICAL: automatic rollback failed; both application slots retained for manual recovery"
  fi
  remember_success "$candidate"
  if [[ "$candidate" != "$old" ]]; then
    "${COMPOSE[@]}" stop -t 30 "app_$old" || log "WARNING: stale slot could not be stopped"
  fi
}

run_deploy() {
  mkdir -p "$STATE_DIR"
  [[ -f docker-compose.yml && -f docker-compose.bluegreen.yml && -f .env ]] ||
    fail "Compose and .env files must exist"
  local proxy_cid active next current_cid running_image
  proxy_cid="$(container_of proxy)"
  if ! is_running "$proxy_cid"; then
    if [[ -f "$STATE_FILE" ]]; then
      fail "Blue-Green state exists but proxy is not running. Refusing automatic legacy cutover."
    fi
    bootstrap_proxy
    return
  fi

  active="$(live_slot)"
  [[ "$active" == blue || "$active" == green ]] || fail "Invalid active Nginx slot"
  # A failed reload/rollback can leave disk metadata pointing at the old slot
  # while live workers still route to the candidate. Never stop either slot
  # until the actual serving upstream agrees with the on-disk configuration.
  proxy_points_to "$active" || fail "Live proxy disagrees with active config or is unhealthy; both slots preserved for manual recovery"
  # active.conf is authoritative if a previous run was interrupted mid-promotion.
  if [[ "$(cat "$STATE_FILE" 2>/dev/null || true)" != "$active" ]]; then
    printf '%s\n' "$active" > "$STATE_FILE"
  fi
  current_cid="$(container_of "app_$active")"
  is_running "$current_cid" || fail "Active app_$active not running"
  running_image="$(docker inspect -f '{{.Config.Image}}' "$current_cid")"
  if [[ "$running_image" == "$IMAGE" ]] && proxy_points_to "$active"; then
    log "Target image already active and healthy: $IMAGE"
    return
  fi
  next=green
  [[ "$active" == green ]] && next=blue
  log "Active: app_$active ($running_image); staging app_$next ($IMAGE)"
  stage_candidate "$next"
  switch_proxy "$active" "$next"
  cleanup_after_deploy "$running_image"
}

# Keep flock and EXIT cleanup trap in the same shell (no subshell state loss).
exec 9>"$LOCK"
flock -w 900 9 || fail "Another production deployment holds the lock"
# Load cleanup only after acquiring the same lock as the slot switch.
if ! source "$(dirname -- "${BASH_SOURCE[0]}")/prune-pi-deploy-images.sh"; then
  cleanup_after_deploy() { log "WARNING: image cleanup script unavailable"; }
fi
run_deploy
