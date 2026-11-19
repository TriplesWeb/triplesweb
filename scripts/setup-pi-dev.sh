#!/usr/bin/env bash
# Run ON the Raspberry Pi (after prod stack is already up).
# Creates /opt/stacks/triplesweb-dev that shares prod MariaDB + uploads + data.
set -euo pipefail

PROD_DIR="${1:-/opt/stacks/triplesweb}"
DEV_DIR="${2:-/opt/stacks/triplesweb-dev}"

echo "==> Prod dir: ${PROD_DIR}"
echo "==> Dev dir:  ${DEV_DIR}"

if [[ ! -f "${PROD_DIR}/docker-compose.yml" || ! -f "${PROD_DIR}/.env" ]]; then
  echo "ERROR: prod stack missing at ${PROD_DIR} (need docker-compose.yml + .env)"
  exit 1
fi

if ! docker compose -f "${PROD_DIR}/docker-compose.yml" --project-directory "${PROD_DIR}" ps --status running 2>/dev/null | grep -q mariadb; then
  # Fallback: any running container named *mariadb* in prod project
  if ! docker ps --format '{{.Names}}' | grep -qE 'triplesweb.*mariadb|mariadb'; then
    echo "WARN: could not confirm prod mariadb is running. Continue anyway."
  fi
fi

mkdir -p "${DEV_DIR}"
cd "${DEV_DIR}"

if [[ ! -f docker-compose.yml ]]; then
  if [[ -f docker-compose.dev.yml ]]; then
    cp docker-compose.dev.yml docker-compose.yml
  else
    echo "ERROR: copy docker-compose.dev.yml from the repo into ${DEV_DIR} first, e.g.:"
    echo "  scp docker-compose.dev.yml pi@host:${DEV_DIR}/docker-compose.yml"
    exit 1
  fi
fi

# Detect compose network used by prod (default project = directory name)
PROD_PROJECT="$(basename "${PROD_DIR}")"
DEFAULT_NETWORK="${PROD_PROJECT}_triples"
NETWORK="$(docker network ls --format '{{.Name}}' | grep -E "^${DEFAULT_NETWORK}$" || true)"
if [[ -z "${NETWORK}" ]]; then
  NETWORK="$(docker network ls --format '{{.Name}}' | grep -E '_triples$' | head -n1 || true)"
fi
if [[ -z "${NETWORK}" ]]; then
  echo "ERROR: could not find prod compose network (*_triples). Is prod up?"
  docker network ls
  exit 1
fi
echo "==> Shared network: ${NETWORK}"

if [[ ! -f .env ]]; then
  echo "==> Creating .env from prod (APP_PORT=8091, shared paths)"
  # Copy DB/API secrets from prod; override deploy-specific keys
  grep -E '^(MYSQL_|YOUTUBE_|CHEMISTRY_|COSMO_|SPRING_JPA_)' "${PROD_DIR}/.env" > .env || true
  {
    echo "IMAGE=ghcr.io/triplesweb/triplesweb:latest"
    echo "APP_PORT=8091"
    echo "MYSQL_HOST=mariadb"
    echo "MYSQL_PORT=3306"
    echo "COMPOSE_NETWORK=${NETWORK}"
    echo "UPLOADS_DIR=${PROD_DIR}/uploads"
    echo "DATA_DIR=${PROD_DIR}/data"
  } >> .env
  # Ensure MYSQL_DATABASE / USER present if grep missed defaults
  grep -q '^MYSQL_DATABASE=' .env || echo "MYSQL_DATABASE=triples" >> .env
  grep -q '^MYSQL_USER=' .env || echo "MYSQL_USER=root" >> .env
else
  echo "==> .env already exists — leave as-is"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker not installed. Set up prod first (scripts/setup-pi.sh)."
  exit 1
fi

echo
echo "==> Next:"
echo "  1. GitHub Secret PI_DEV_DEPLOY_PATH = ${DEV_DIR}"
echo "  2. (선택) Actions Variable PI_DEV_PUBLIC_URL = NPG로 연결한 개발 도메인"
echo "  3. PR이 열리면 deploy-dev.yml이 이 경로로 이미지를 올린다"
echo "  4. 수동 기동: cd ${DEV_DIR} && docker compose pull app && docker compose up -d"
echo
echo "App (local): http://127.0.0.1:8091/main"
echo "Done."
