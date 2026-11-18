#!/usr/bin/env bash
# Runs on the Pi. stdout is reserved for the final JSON result.
set -Eeuo pipefail
umask 077

operation=${1:?operation required}
production=${2:?production path required}
pr=${3:?PR number required}
image=${4:-}
sha=${5:-}
[[ $production =~ ^/[a-zA-Z0-9_./-]+$ && $production != *..* ]] || exit 2
[[ $pr =~ ^[1-9][0-9]{0,8}$ ]] || exit 2
[[ $operation == deploy || $operation == cleanup ]] || exit 2
if [[ $operation == deploy ]]; then
  [[ $image =~ ^ghcr\.io/[a-z0-9._/-]+:pr-[0-9]+-[a-f0-9]{40}$ ]] || exit 2
  [[ $sha =~ ^[a-f0-9]{40}$ ]] || exit 2
fi

root="${production%/}/.previews"
mkdir -p "$root"
exec 9>"$root/.lock"
flock -x -w 45 9 || { echo 'Another preview operation still holds the Pi deployment lock' >&2; exit 1; }
dir="$root/pr-$pr"

remove_generation() {
  local generation=$1 name
  [[ $generation =~ ^g[0-9]+-[0-9]+$ ]] || return 1
  name="twpr${pr}${generation}"
  local container
  for container in "${name}-app" "${name}-db"; do
    if docker inspect "$container" >/dev/null 2>&1; then docker rm -fv "$container" >/dev/null; fi
  done
  if docker network inspect "$name" >/dev/null 2>&1; then docker network rm "$name" >/dev/null; fi
  rm -rf -- "$dir/$generation"
}

if [[ $operation == cleanup ]]; then
  if [[ -f $dir/port ]]; then
    read -r port < "$dir/port"
    [[ $port =~ ^[0-9]+$ ]] && ((port >= 20000 && port <= 20999)) || exit 2
    tailscale serve --https="$port" off >&2
  fi
  if [[ -d $dir ]]; then
    for path in "$dir"/g[0-9]*-[0-9]*; do
      [[ -d $path ]] || continue
      remove_generation "${path##*/}"
    done
    rm -f -- "$dir/port" "$dir/active" "$dir/snapshot-at"
    rmdir -- "$dir"
  fi
  printf '{"status":"cleaned"}\n'
  exit 0
fi

mkdir -p "$dir"
if [[ -f $dir/port ]]; then
  read -r port < "$dir/port"
  [[ $port =~ ^[0-9]+$ ]] && ((port >= 20000 && port <= 20999)) || exit 2
else
  port=''
  for ((candidate=20000; candidate<=20999; candidate++)); do
    reserved=false
    for existing in "$root"/pr-*/port; do
      [[ -f $existing ]] || continue
      [[ $(<"$existing") != "$candidate" ]] || reserved=true
    done
    if ! $reserved && ! ss -H -ltn "sport = :$candidate" | grep -q .; then
      port=$candidate
      break
    fi
  done
  [[ -n $port ]] || { echo 'No preview HTTPS ports available' >&2; exit 1; }
  printf '%s\n' "$port" > "$dir/port"
fi

old=''
if [[ -f $dir/active ]]; then
  read -r old < "$dir/active"
  [[ $old =~ ^g[0-9]+-[0-9]+$ && -d $dir/$old ]] || exit 1
fi
generation="g$(date +%s)-$$"
name="twpr${pr}${generation}"
candidate="$dir/$generation"
mkdir -p "$candidate/uploads" "$candidate/data"
old_stopped=false
committed=false
switched=false
rollback() {
  local status=$?
  trap - EXIT
  if ! $committed; then
    if $switched; then
      if [[ -n $old ]]; then
        old_backend=$(docker port "twpr${pr}${old}-app" 8080/tcp | head -n1 | sed 's/.*://')
        tailscale serve --bg --https="$port" "http://127.0.0.1:$old_backend" >&2 || true
      else
        tailscale serve --https="$port" off >&2 || true
      fi
    fi
    remove_generation "$generation" || true
    if $old_stopped; then docker start "twpr${pr}${old}-app" >/dev/null || true; fi
  fi
  exit "$status"
}
trap rollback EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

# Stop writes to the previous preview while taking its database and file snapshot.
if [[ -n $old ]]; then
  docker stop "twpr${pr}${old}-app" >/dev/null
  old_stopped=true
  source_db="twpr${pr}${old}-db"
  source_files="$dir/$old"
  snapshot_at=$(<"$dir/snapshot-at")
else
  [[ -d $production/uploads ]] || { echo 'Production uploads directory not found' >&2; exit 1; }
  source_db=$(cd "$production" && docker compose ps -q mariadb)
  [[ -n $source_db ]] || { echo 'Production MariaDB container not found' >&2; exit 1; }
  source_files="$production"
  snapshot_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
fi

# Leave space for the operating service, including while both generations exist.
files_kib=$(du -sk "$source_files/uploads" | awk '{print $1}')
db_kib=$(docker exec "$source_db" du -sk /var/lib/mysql | awk '{print $1}')
docker_root=$(docker info --format '{{.DockerRootDir}}')
file_free=$(df -Pk "$candidate" | awk 'NR == 2 {print $4}')
docker_free=$(df -Pk "$docker_root" | awk 'NR == 2 {print $4}')
[[ $files_kib =~ ^[0-9]+$ && $db_kib =~ ^[0-9]+$ && $file_free =~ ^[0-9]+$ && $docker_free =~ ^[0-9]+$ ]] || exit 1
if [[ $(stat -c %d "$candidate") == "$(stat -c %d "$docker_root")" ]]; then
  ((file_free > files_kib + 2 * db_kib + 1048576)) || { echo 'Insufficient disk space for preview and 1 GiB reserve' >&2; exit 1; }
else
  ((file_free > files_kib + db_kib + 524288 && docker_free > db_kib + 524288)) || { echo 'Insufficient disk space for preview and 512 MiB reserves' >&2; exit 1; }
fi
cp -a --reflink=auto "$source_files/uploads/." "$candidate/uploads/"
[[ ! -f $source_files/data/videos.json ]] || cp -a "$source_files/data/videos.json" "$candidate/data/"

# Stage the compressed dump on the Pi so the source and import can fail independently.
# SQL errors can include data values, so only the stage and exit status go to CI.
mkdir "$candidate/snapshot"
snapshot_scope=full
if [[ -z $old ]]; then snapshot_scope=site; fi
echo 'Exporting database snapshot' >&2
if ! docker exec "$source_db" sh -c '
  export MYSQL_PWD="$MARIADB_ROOT_PASSWORD"
  scope=$1
  set --
  if [ "$scope" = site ]; then
    # The observer continuously rebuilds these indexes from its external source.
    # Keep their schema so migrations still run against the snapshot.
    for table in objekt_observed_transfer objekt_observed_token; do
      set -- "$@" "--ignore-table-data=$MARIADB_DATABASE.$table"
    done
  fi
  exec timeout -k 10s 8m mariadb-dump -uroot --single-transaction --quick --skip-lock-tables \
    --hex-blob --max-allowed-packet=1G "$@" -- "$MARIADB_DATABASE"
' sh "$snapshot_scope" 2>"$candidate/snapshot/export.err" | timeout -k 10s 9m gzip -1 > "$candidate/snapshot/db.sql.gz"; then
  if grep -qi 'access denied' "$candidate/snapshot/export.err"; then
    echo 'Source database rejected the snapshot credentials' >&2
  fi
  echo 'Database snapshot export failed or exceeded the time limit' >&2
  exit 1
fi
rm -f -- "$candidate/snapshot/export.err"

password=$(openssl rand -hex 24)
docker network create --internal "$name" >/dev/null
docker run -d --restart unless-stopped --name "${name}-db" --network "$name" --network-alias db \
  -e MARIADB_ROOT_PASSWORD="$password" -e MARIADB_DATABASE=triples \
  -v "$candidate/snapshot:/preview-snapshot:ro" mariadb:11.4 \
  --max-allowed-packet=1G --skip-log-bin >/dev/null
echo 'Waiting for preview MariaDB authentication' >&2
for ((i=0; i<60; i++)); do
  if docker exec "${name}-db" sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb -h127.0.0.1 -uroot -e "SELECT 1"' >/dev/null 2>&1; then break; fi
  if ((i == 59)); then
    echo 'Preview DB did not start or accept authenticated TCP connections' >&2
    exit 1
  fi
  sleep 2
done
echo 'Importing database snapshot' >&2
if ! docker exec "${name}-db" timeout -k 10s 12m bash -o pipefail -c '
  export MYSQL_PWD="$MARIADB_ROOT_PASSWORD"
  mariadb -uroot -e "SET GLOBAL innodb_flush_log_at_trx_commit = 2" &&
    gzip -dc /preview-snapshot/db.sql.gz | mariadb -uroot --binary-mode --max-allowed-packet=1G triples &&
    mariadb -uroot -e "SET GLOBAL innodb_flush_log_at_trx_commit = 1"
' >/dev/null 2>&1; then
  echo 'Preview database import failed or exceeded the time limit' >&2
  exit 1
fi
rm -f -- "$candidate/snapshot/db.sql.gz"

docker pull "$image" >&2
echo 'Starting preview application' >&2
docker run -d --restart unless-stopped --name "${name}-app" --network "$name" -p 127.0.0.1::8080 \
  -e SPRING_DATASOURCE_URL='jdbc:mariadb://db:3306/triples?useSSL=false' \
  -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD="$password" \
  -e SPRING_JPA_HIBERNATE_DDL_AUTO=none -e APP_SCHEDULING_ENABLED=false \
  -e MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true \
  -e ABSTRACT_INDEXER_ENABLED=false -e UPLOAD_SYNC_ENABLED=false -e UPLOAD_FALLBACK_BASE_URL= \
  -e CHEMISTRY_SEED_ENABLED=false -e SERVER_SERVLET_SESSION_COOKIE_SECURE=true \
  -e FILE_UPLOAD_DIR=/app/uploads -e MEDIA_CATALOG_PATH=/app/data/videos.json \
  -v "$candidate/uploads:/app/uploads" -v "$candidate/data:/app/data" \
  "$image" >/dev/null
backend=$(docker port "${name}-app" 8080/tcp | head -n1 | sed 's/.*://')
[[ $backend =~ ^[0-9]+$ ]] || exit 1
for ((i=0; i<120; i++)); do
  if curl --fail --silent --max-time 8 --output /dev/null "http://127.0.0.1:$backend/actuator/health/readiness"; then break; fi
  [[ $(docker inspect -f '{{.State.Running}}' "${name}-app") == true ]] || { echo 'Preview app exited during startup' >&2; exit 1; }
  ((i < 119)) || { echo 'Preview app health check timed out' >&2; exit 1; }
  sleep 2
done
curl --fail --silent --max-time 15 --output /dev/null "http://127.0.0.1:$backend/" ||
  { echo 'Preview main page did not load' >&2; exit 1; }
dns=$(tailscale status --json | python3 -c 'import json,sys; print(json.load(sys.stdin)["Self"]["DNSName"].rstrip("."))')
[[ $dns =~ ^[a-zA-Z0-9.-]+\.ts\.net$ ]] || { echo 'Pi Tailscale DNS name unavailable' >&2; exit 1; }
tailscale serve --bg --https="$port" "http://127.0.0.1:$backend" >&2
switched=true
printf '%s\n' "$snapshot_at" > "$dir/snapshot-at"
printf '%s\n' "$generation" > "$dir/active.tmp"
mv -f "$dir/active.tmp" "$dir/active"
committed=true
if [[ -n $old ]]; then remove_generation "$old" || echo 'Old preview cleanup failed' >&2; fi
printf '{"status":"ready","url":"https://%s:%s/","sha":"%s","snapshotAt":"%s"}\n' "$dns" "$port" "$sha" "$snapshot_at"
