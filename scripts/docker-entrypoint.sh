#!/bin/sh
set -eu

# 볼륨 마운트(./uploads)가 이미지 내 파일을 가리므로,
# 번들된 케미 에셋을 /app/uploads/chemistry 로 동기화한다.
BUNDLE="/opt/chemistry-bundle"
TARGET="/app/uploads/chemistry"
MARKER="${TARGET}/.bundle-version"

if [ -d "${BUNDLE}" ]; then
  mkdir -p "${TARGET}"
  BUNDLE_VER="$(cat "${BUNDLE}/.bundle-version" 2>/dev/null || echo bundled)"
  CURRENT_VER="$(cat "${MARKER}" 2>/dev/null || true)"
  if [ "${BUNDLE_VER}" != "${CURRENT_VER}" ]; then
    echo "[entrypoint] Syncing chemistry assets (${BUNDLE_VER})..."
    cp -a "${BUNDLE}/." "${TARGET}/"
    echo "${BUNDLE_VER}" > "${MARKER}"
    echo "[entrypoint] Chemistry assets ready."
  fi
fi

exec java -jar /app/app.war
