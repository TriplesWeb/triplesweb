#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
bash -n scripts/ensure-dev-db.sh scripts/deploy-pi-app.sh scripts/setup-pi-dev.sh
! grep -q 'COPY uploads/chemistry' Dockerfile
! grep -q 'COPY scripts/docker-entrypoint.sh' Dockerfile
! grep -q 'docker image prune -f' .github/workflows/deploy-dev.yml
! grep -q 'docker image prune -f' .github/workflows/deploy-main.yml
grep -q 'PROD_CHEMISTRY_DIR.*:ro' docker-compose.dev.yml
grep -q '/actuator/health/readiness' scripts/deploy-pi-app.sh
grep -q "pull_request:" .github/workflows/deploy-dev.yml
grep -q "0 19 \* \* \*" .github/workflows/deploy-main.yml
echo 'Deployment static checks passed'
