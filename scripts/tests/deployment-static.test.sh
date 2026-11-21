#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

# Syntax and single-production-environment invariants
for script in scripts/tests/deployment-static.test.sh scripts/deploy-pi-app.sh scripts/deploy-pi-image-retention.sh scripts/retire-pi-dev.sh; do
  bash -n "$script"
done

reject_match() {
  local status=0
  grep "$@" || status=$?
  if [[ "$status" != 1 ]]; then
    printf 'Forbidden pattern matched or grep failed: %s\n' "$*" >&2
    return 1
  fi
}
test ! -e .github/workflows/deploy-dev.yml
test ! -e .github/workflows/qodana_code_quality.yml
test ! -e docker-compose.dev.yml
test ! -e scripts/setup-pi-dev.sh
test ! -e scripts/ensure-dev-db.sh
reject_match -Fq 'COPY uploads/chemistry' Dockerfile
reject_match -Fq 'COPY scripts/docker-entrypoint.sh' Dockerfile
reject_match -Fq 'docker image prune -f' .github/workflows/deploy-main.yml
reject_match -Fq 'resolve-main:' .github/workflows/deploy-main.yml
reject_match -Fq '즉시 배포' .github/workflows/deploy-main.yml
grep -Fq '  pull_request:' .github/workflows/ci-tests.yml
grep -Fq '  push:' .github/workflows/deploy-main.yml
grep -Fq '    branches: [main]' .github/workflows/deploy-main.yml
grep -Fq 'retire-pi-dev.sh' .github/workflows/deploy-main.yml
grep -Fq '/actuator/health/readiness' scripts/deploy-pi-app.sh
grep -Fq 'cache-from: type=gha' .github/workflows/deploy-main.yml
grep -Fq 'up -d --no-deps app' scripts/deploy-pi-app.sh
reject_match -Eq '^[[:space:]]*docker compose up -d[[:space:]]*$' scripts/deploy-pi-app.sh
grep -Fq 'docker image inspect "$IMAGE"' scripts/deploy-pi-app.sh
grep -Fq -- '--force-recreate app' scripts/deploy-pi-app.sh
grep -Fq 'docker buildx imagetools inspect' .github/workflows/deploy-main.yml
grep -Fq "steps.published.outputs.found != 'true'" .github/workflows/deploy-main.yml
reject_match -Fq 'HEAD:uploads/chemistry' .github/workflows/deploy-main.yml
reject_match -Fq 'chemistry_sync' .github/workflows/deploy-main.yml
reject_match -Fq 'uploads/chemistry/' .github/workflows/deploy-main.yml
grep -Fq 'migrate-chemistry-photos-on-pi.sh' .github/workflows/deploy-main.yml
reject_match -Fq 'rsync -az' .github/workflows/deploy-main.yml
reject_match -Fq 'COPY scripts ./scripts' Dockerfile
grep -Fq 'COPY scripts/setup-css.sh scripts/build-*css.cjs' Dockerfile
grep -Fq -- "- '.github/**'" .github/workflows/deploy-main.yml
python3 scripts/ci-path-filter.py --self-test
test ! -e docker-compose.bluegreen.yml
test ! -e scripts/deploy-pi-bluegreen.sh
grep -Fq 'bash ./deploy-pi-app.sh' .github/workflows/deploy-main.yml
grep -Fq '${APP_PORT:-8090}:8080' docker-compose.yml
grep -Fq 'SPRING_JPA_HIBERNATE_DDL_AUTO: none' docker-compose.yml
reject_match -Fq 'profiles: [legacy]' docker-compose.yml
grep -Fq 'flock -w 900' scripts/deploy-pi-app.sh
reject_match -Fq -- '--remove-orphans' scripts/deploy-pi-app.sh
grep -Fq 'server.shutdown=graceful' src/main/resources/application.properties
grep -Fq 'spring.session.store-type=jdbc' src/main/resources/application.properties
python3 scripts/tests/deployment-behavior.test.py
echo 'Production-only deployment assertions passed'
