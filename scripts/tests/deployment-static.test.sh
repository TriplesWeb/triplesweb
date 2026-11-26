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
grep -Fxq 'ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-XX:InitialRAMPercentage=5", "-XX:+ExitOnOutOfMemoryError", "-XX:+UseStringDeduplication", "-jar", "/app/app.war"]' Dockerfile
grep -Fq -- "- '.github/**'" .github/workflows/deploy-main.yml
python3 scripts/ci-path-filter.py --self-test
test ! -e docker-compose.bluegreen.yml
test ! -e scripts/deploy-pi-bluegreen.sh
reject_match -Eiq 'bluegreen|app_blue|app_green|active_slot|project_container' scripts/deploy-pi-app.sh
grep -Fq 'bash ./deploy-pi-app.sh' .github/workflows/deploy-main.yml
grep -Fq -- '${MARIADB_TUNING_CNF:-./docker/mariadb/empty.cnf}:/etc/mysql/conf.d/tuning.cnf:ro' docker-compose.yml
grep -Fq '${APP_PORT:-8090}:8080' docker-compose.yml
grep -Fq 'SPRING_JPA_HIBERNATE_DDL_AUTO: none' docker-compose.yml
grep -Fq './logs:/app/logs' docker-compose.yml
grep -Fq 'LOGGING_FILE_NAME: /app/logs/triplesweb.log' docker-compose.yml
grep -Fq "LOGGING_LOGBACK_ROLLINGPOLICY_FILENAMEPATTERN: '/app/logs/triplesweb.log.%d{yyyy-MM-dd}.%i.gz'" docker-compose.yml
grep -Fq 'LOGGING_LOGBACK_ROLLINGPOLICY_MAXHISTORY: "30"' docker-compose.yml
grep -Fq 'LOGGING_LOGBACK_ROLLINGPOLICY_TOTALSIZECAP: 1GB' docker-compose.yml
grep -Fq 'LOGGING_LOGBACK_ROLLINGPOLICY_MAXFILESIZE: 10MB' docker-compose.yml
grep -Fq 'LOGGING_LOGBACK_ROLLINGPOLICY_CLEANHISTORYONSTART: "true"' docker-compose.yml
grep -Fq 'driver: json-file' docker-compose.yml
grep -Fq 'max-size: "10m"' docker-compose.yml
grep -Fq 'max-file: "3"' docker-compose.yml
grep -Fq 'mkdir -p -m 0750 logs' scripts/deploy-pi-app.sh
deploy_workflow=.github/workflows/deploy-main.yml
grep -Fq "if: github.ref == 'refs/heads/main'" "$deploy_workflow"
grep -Fxq '          ref: ${{ github.sha }}' "$deploy_workflow"
grep -Fq 'test "$(git rev-parse HEAD)" = "$GITHUB_SHA"' "$deploy_workflow"
grep -Eq '^[[:space:]]+docker/mariadb$' "$deploy_workflow"
sync_step="$(awk '
  /^      - name: Preflight / { exit }
  /^      - name: / { syncing = ($0 == "      - name: Sync production Compose files") }
  syncing { print }
' "$deploy_workflow")"
reject_match -Eq '^[[:space:]]+if:' <<< "$sync_step"
grep -Fq "ssh pi \"mkdir -p '\${PI_DEPLOY_PATH}/docker/mariadb'\"" <<< "$sync_step"
grep -Fq 'scp docker/mariadb/empty.cnf "pi:${PI_DEPLOY_PATH}/docker/mariadb/empty.cnf"' <<< "$sync_step"
grep -Fq 'scp docker-compose.yml "pi:${PI_DEPLOY_PATH}/docker-compose.yml"' <<< "$sync_step"
reject_match -Fq 'profiles: [legacy]' docker-compose.yml
grep -Fq 'flock -w 900' scripts/deploy-pi-app.sh
reject_match -Fq -- '--remove-orphans' scripts/deploy-pi-app.sh
grep -Fq 'server.shutdown=graceful' src/main/resources/application.properties
grep -Fq 'spring.session.store-type=jdbc' src/main/resources/application.properties
python3 scripts/tests/deployment-behavior.test.py
echo 'Production-only deployment assertions passed'
