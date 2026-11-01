#!/usr/bin/env bash
# 프론트 CSS 의존성 설치 + 정적 번들 생성 + Spring bootRun
# Usage (repo root):
#   ./scripts/setup-css.sh                 # npm ci + build:css + bootRun
#   ./scripts/setup-css.sh --no-run        # CSS만 (서버 미기동) — CI package / Docker
#   ./scripts/setup-css.sh --check         # 빌드 후 git drift 검사 — CI test
#   ./scripts/setup-css.sh --skip-install  # 이미 npm ci 된 환경 (Docker 레이어)
#   ./scripts/setup-css.sh --install       # lockfile 없을 때 npm install
set -euo pipefail

cd "$(dirname "$0")/.."

DO_CHECK=false
USE_INSTALL=false
SKIP_INSTALL=false
DO_RUN=true
for arg in "$@"; do
  case "$arg" in
    --check) DO_CHECK=true; DO_RUN=false ;;
    --install) USE_INSTALL=true ;;
    --skip-install) SKIP_INSTALL=true ;;
    --no-run) DO_RUN=false ;;
    --run) DO_RUN=true ;; # 하위 호환 (기본이 이미 실행)
    -h|--help)
      cat <<'EOF'
프론트 CSS 설치·빌드 후 Spring Boot 실행

  ./scripts/setup-css.sh                 # npm ci + build:css + bootRun (로컬 기본)
  ./scripts/setup-css.sh --no-run        # CSS만 (CI package / Docker)
  ./scripts/setup-css.sh --check         # 빌드 + drift 검사 (CI test, 서버 미기동)
  ./scripts/setup-css.sh --skip-install  # npm ci 생략 (Docker 캐시 레이어용)
  ./scripts/setup-css.sh --install       # npm ci 대신 npm install
  npm run setup:css / npm run dev        # package.json 진입점 (동일)
EOF
      exit 0
      ;;
    *)
      echo "알 수 없는 옵션: $arg" >&2
      echo "사용법: ./scripts/setup-css.sh [--install] [--skip-install] [--check] [--no-run]" >&2
      exit 1
      ;;
  esac
done

if ! command -v node >/dev/null 2>&1; then
  echo "Node.js가 필요합니다 (권장 >= 20)." >&2
  exit 1
fi
if ! command -v npm >/dev/null 2>&1; then
  echo "npm이 필요합니다." >&2
  exit 1
fi

NODE_MAJOR="$(node -p "process.versions.node.split('.')[0]")"
if [[ "$NODE_MAJOR" -lt 20 ]]; then
  echo "Node.js 20 이상이 필요합니다 (현재: $(node -v))." >&2
  exit 1
fi

if [[ ! -f package.json ]]; then
  echo "package.json을 찾을 수 없습니다. 저장소 루트에서 실행하세요." >&2
  exit 1
fi

if [[ "$SKIP_INSTALL" == true ]]; then
  echo "==> skip npm install (--skip-install)"
elif [[ "$USE_INSTALL" == true ]] || [[ ! -f package-lock.json ]]; then
  echo "==> npm install"
  npm install
else
  echo "==> npm ci"
  npm ci
fi

echo "==> npm run build:css"
npm run build:css

if [[ "$DO_CHECK" == true ]]; then
  echo "==> CSS drift check"
  # build:css를 방금 돌렸으므로 생성물이 깨끗해야 한다.
  if ! git diff --quiet -- \
    src/main/resources/static/css/site.css \
    src/main/resources/static/css/triples.css \
    src/main/resources/static/css/objekt.css \
    src/main/resources/static/css/gravity.css; then
    echo "생성 CSS가 워킹트리와 다릅니다. 변경분을 커밋하세요." >&2
    git --no-pager diff --stat -- \
      src/main/resources/static/css/site.css \
      src/main/resources/static/css/triples.css \
      src/main/resources/static/css/objekt.css \
      src/main/resources/static/css/gravity.css
    exit 1
  fi
  echo "CSS drift check passed."
fi

echo "CSS setup complete."

if [[ "$DO_RUN" == true ]]; then
  if [[ ! -x ./gradlew ]]; then
    echo "./gradlew 를 찾을 수 없거나 실행 권한이 없습니다." >&2
    exit 1
  fi
  echo "==> ./gradlew bootRun"
  echo "브라우저: http://localhost:8080/main"
  exec ./gradlew bootRun
fi
