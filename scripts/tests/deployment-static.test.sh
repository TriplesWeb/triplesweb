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
# 운영 DB는 건드리지 않고 앱 컨테이너만 갱신해야 한다.
grep -q -- 'docker compose up -d --no-deps app' scripts/deploy-pi-app.sh
! grep -q 'docker compose up -decho 'Production-only deployment assertions passed'
 scripts/deploy-pi-app.sh
# 동일 SHA 이미지가 이미 게시되어 있으면 컨테이너 복구 시 재빌드하지 않는다.
grep -q 'docker buildx imagetools inspect' .github/workflows/deploy-main.yml
grep -q "steps.published.outputs.found != 'true'" .github/workflows/deploy-main.yml
# chemistry 폴더는 Git tree와 Pi 마커가 달라진 경우만 증분 동기화한다.
grep -q 'HEAD:uploads/chemistry' .github/workflows/deploy-main.yml
grep -q 'chemistry_sync' .github/workflows/deploy-main.yml
! grep -q 'rsync -az' .github/workflows/deploy-main.yml
# CSS 빌드에 배포/운영 스크립트를 포함하지 않아 빌드 캐시를 보존한다.
! grep -q 'COPY scripts ./scripts' Dockerfile
grep -Fq 'COPY scripts/setup-css.sh scripts/build-*css.cjs' Dockerfile
echo 'Production-only deployment assertions passed'
