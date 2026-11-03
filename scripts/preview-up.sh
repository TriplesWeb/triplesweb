#!/usr/bin/env bash
# Isolated preview launcher. Refuse unchanged sample secrets.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .env.preview ]]; then
  echo "먼저 cp .env.preview.example .env.preview 후 비밀번호 두 개를 변경하세요." >&2
  exit 1
fi
if grep -q 'REPLACE_WITH_' .env.preview; then
  echo "예시 비밀번호가 남아 있습니다. .env.preview 파일에서 변경하세요." >&2
  exit 1
fi
docker compose --env-file .env.preview -f docker-compose.preview.yml up -d --build
docker compose --env-file .env.preview -f docker-compose.preview.yml ps
