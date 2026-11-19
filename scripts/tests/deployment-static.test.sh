#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

# Syntax and single-production-environment invariants
bash -n scripts/tests/deployment-static.test.sh scripts/deploy-pi-app.sh scripts/retire-pi-dev.sh
test ! -e .github/workflows/deploy-dev.yml
test ! -e .github/workflows/qodana_code_quality.yml
test ! -e docker-compose.dev.yml
test ! -e scripts/setup-pi-dev.sh
test ! -e scripts/ensure-dev-db.sh
! grep -Fq 'COPY uploads/chemistry' Dockerfile
! grep -Fq 'COPY scripts/docker-entrypoint.sh' Dockerfile
! grep -Fq 'docker image prune -f' .github/workflows/deploy-main.yml
! grep -Fq 'resolve-main:' .github/workflows/deploy-main.yml
! grep -Fq '즉시 배포' .github/workflows/deploy-main.yml
grep -Fq '  pull_request:' .github/workflows/ci-tests.yml
grep -Fq '  push:' .github/workflows/deploy-main.yml
grep -Fq '    branches: [main]' .github/workflows/deploy-main.yml
grep -Fq 'retire-pi-dev.sh' .github/workflows/deploy-main.yml
grep -Fq '/actuator/health/readiness' scripts/deploy-pi-app.sh
grep -Fq 'cache-from: type=gha' .github/workflows/deploy-main.yml
grep -Fq 'docker compose up -d --no-deps app' scripts/deploy-pi-app.sh
! grep -Eq '^[[:space:]]*docker compose up -d[[:space:]]*$' scripts/deploy-pi-app.sh
grep -Fq 'docker image inspect "$IMAGE"' scripts/deploy-pi-app.sh
grep -Fq -- '--force-recreate app' scripts/deploy-pi-app.sh
grep -Fq 'docker buildx imagetools inspect' .github/workflows/deploy-main.yml
grep -Fq "steps.published.outputs.found != 'true'" .github/workflows/deploy-main.yml
grep -Fq 'HEAD:uploads/chemistry' .github/workflows/deploy-main.yml
grep -Fq 'chemistry_sync' .github/workflows/deploy-main.yml
! grep -Fq 'rsync -az' .github/workflows/deploy-main.yml
! grep -Fq 'COPY scripts ./scripts' Dockerfile
grep -Fq 'COPY scripts/setup-css.sh scripts/build-*css.cjs' Dockerfile
echo 'Production-only deployment assertions passed'
