#!/usr/bin/env bash
# 레포 클론/최초 세팅 후 한 번 실행 — 케미 업로드가 커밋·배포에 포함되도록 훅 경로 설정
set -euo pipefail
cd "$(dirname "$0")/.."
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit .githooks/pre-push
echo "git hooksPath → .githooks (pre-commit/pre-push 활성)"
