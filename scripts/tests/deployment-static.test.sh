#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

# 작업 스크립트의 문법 및 개발 서버 자동 배포 제거를 확인한다.
bash -n scripts/deploy-pi-app.sh scripts/retire-pi-dev.sh
test ! -e .github/workflows/deploy-dev.yml
test ! -e .github/workflows/qodana_code_quality.yml
test ! -e docker-compose.dev.yml
test ! -e scripts/setup-pi-dev.sh
test ! -e scripts/ensure-dev-db.sh
! grep -q 'COPY uploads/chemistry' Dockerfile
! grep -q 'COPY scripts/docker-entrypoint.sh' Dockerfile
! grep -q 'docker image prune -f' .github/workflows/deploy-main.yml
! grep -q 'resolve-main:' .github/workflows/deploy-main.yml
! grep -q '즉시 배포' .github/workflows/deploy-main.yml
grep -q '  pull_request:' .github/workflows/ci-tests.yml
grep -q '  push:' .github/workflows/deploy-main.yml
grep -q '    branches: \[main\]' .github/workflows/deploy-main.yml
grep -q 'Retire obsolete Pi development container' .github/workflows/deploy-main.yml
grep -q '/actuator/health/readiness' scripts/deploy-pi-app.sh
grep -q 'cache-from: type=gha' .github/workflows/deploy-main.yml
echo 'Production-only deployment assertions passed'
