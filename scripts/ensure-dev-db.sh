#!/usr/bin/env bash
# Pi에서 개발 DB/업로드를 최초 1회 분리한다. 운영 DB에는 쓰지 않는다.
set -euo pipefail
PROD_DIR="${1:?production stack directory is required}"
DEV_DIR="${2:?development stack directory is required}"
DEV_DB=triples_dev
DEV_USER=triplesweb_preview
MARKER="${DEV_DIR}/.dev-db-ready"

[[ -f "${PROD_DIR}/.env" ]] || { echo "Production .env missing" >&2; exit 1; }
mkdir -p "${DEV_DIR}/uploads" "${DEV_DIR}/data"
umask 077
touch "${DEV_DIR}/.env"
chmod 600 "${DEV_DIR}/.env"

get_env() {
  sed -n "s/^${1}=//p" "${2}" | tail -n1
}
set_env() {
  local key="$1" value="$2" file="${DEV_DIR}/.env"
  if grep -q "^${key}=" "$file"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$file"
  else
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
}
PROD_DB="$(get_env MYSQL_DATABASE "${PROD_DIR}/.env")"
PROD_DB="${PROD_DB:-triples}"
[[ "$PROD_DB" =~ ^[a-zA-Z0-9_]+$ ]] || { echo "Invalid production database name" >&2; exit 1; }
[[ "$PROD_DB" != "$DEV_DB" ]] || { echo "Development database equals production" >&2; exit 1; }

PASS="$(get_env MYSQL_PASSWORD "${DEV_DIR}/.env")"
OLD_USER="$(get_env MYSQL_USER "${DEV_DIR}/.env")"
if [[ "$OLD_USER" != "$DEV_USER" || ! "$PASS" =~ ^[0-9a-f]{64}$ ]]; then
  PASS="$(openssl rand -hex 32)"
fi

PROD_PROJECT="$(basename "$PROD_DIR")"
NETWORK="$(docker network ls --format '{{.Name}}' | grep -Fx "${PROD_PROJECT}_triples" || true)"
[[ -n "$NETWORK" ]] || { echo "Production network ${PROD_PROJECT}_triples missing" >&2; exit 1; }
set_env MYSQL_DATABASE "$DEV_DB"
set_env MYSQL_USER "$DEV_USER"
set_env MYSQL_PASSWORD "$PASS"
set_env MYSQL_HOST "mariadb"
set_env MYSQL_PORT "3306"
set_env COMPOSE_NETWORK "$NETWORK"
set_env UPLOADS_DIR "${DEV_DIR}/uploads"
set_env DATA_DIR "${DEV_DIR}/data"
set_env PROD_CHEMISTRY_DIR "${PROD_DIR}/uploads/chemistry"
set_env APP_PORT "8091"
set_env SPRING_JPA_HIBERNATE_DDL_AUTO "none"

DB_CONTAINER="$(cd "$PROD_DIR" && docker compose ps -q mariadb)"
[[ -n "$DB_CONTAINER" ]] || { echo "Production MariaDB container not found" >&2; exit 1; }

mariadb_root() {
  docker exec -i "$DB_CONTAINER" sh -c 'export MYSQL_PWD="$MARIADB_ROOT_PASSWORD"; exec mariadb -uroot' 
}
# 개발 사용자 계정은 개발 DB에만 GRANT한다. 비밀번호는 임의 생성 64자리 hex.
printf "CREATE DATABASE IF NOT EXISTS \`%s\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\nCREATE USER IF NOT EXISTS '%s'@'%%' IDENTIFIED BY '%s';\nALTER USER '%s'@'%%' IDENTIFIED BY '%s';\nGRANT ALL PRIVILEGES ON \`%s\`.* TO '%s'@'%%';\n" \
  "$DEV_DB" "$DEV_USER" "$PASS" "$DEV_USER" "$PASS" "$DEV_DB" "$DEV_USER" | mariadb_root

if [[ ! -f "$MARKER" ]]; then
  COUNT="$(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='%s';\n" "$DEV_DB" | mariadb_root | tail -n1)"
  if [[ "$COUNT" != "0" ]]; then
    echo "Dev schema has tables but marker is missing. Refusing to overwrite; inspect manually." >&2
    exit 1
  fi
  HAVE_HISTORY="$(printf "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='%s' AND table_name='flyway_schema_history';\n" "$PROD_DB" | mariadb_root | tail -n1)"
  [[ "$HAVE_HISTORY" == "1" ]] || { echo "Production Flyway history missing; schema-only bootstrap unsupported" >&2; exit 1; }
  echo "Initializing dev DB from production SCHEMA ONLY and Flyway metadata (no content/data)"
  docker exec "$DB_CONTAINER" bash -euo pipefail -c '
    export MYSQL_PWD="$MARIADB_ROOT_PASSWORD"
    mariadb-dump -uroot --single-transaction --no-data --skip-triggers --skip-lock-tables "$1" | mariadb -uroot "$2"
    mariadb-dump -uroot --no-create-info --skip-triggers --skip-lock-tables "$1" flyway_schema_history | mariadb -uroot "$2"
  ' _ "$PROD_DB" "$DEV_DB"
  printf 'source=%s; schema-only; initialized=%s\n' "$PROD_DB" "$(date -u +%FT%TZ)" > "$MARKER"
  chmod 600 "$MARKER"
fi
echo "Development DB isolated: $DEV_DB (schema only), files: $DEV_DIR/uploads"
