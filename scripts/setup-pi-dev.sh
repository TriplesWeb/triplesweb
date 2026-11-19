#!/usr/bin/env bash
# Run this ON the Pi from a checkout of triplesweb.
# The dev schema is cloned from prod schema only; never copy prod content.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
PROD_DIR="${1:-/opt/stacks/triplesweb}"
DEV_DIR="${2:-/opt/stacks/triplesweb-dev}"

[[ -f "${PROD_DIR}/docker-compose.yml" ]] || { echo "Production stack missing" >&2; exit 1; }
mkdir -p "$DEV_DIR"
cp "${REPO_DIR}/docker-compose.dev.yml" "${DEV_DIR}/docker-compose.yml"
cp "${SCRIPT_DIR}/ensure-dev-db.sh" "${DEV_DIR}/ensure-dev-db.sh"
cp "${SCRIPT_DIR}/deploy-pi-app.sh" "${DEV_DIR}/deploy-pi-app.sh"
bash "${DEV_DIR}/ensure-dev-db.sh" "$PROD_DIR" "$DEV_DIR"
echo "Development initialized: $DEV_DIR"
echo "No production rows or uploads were copied. PR deployment will populate the app image."
