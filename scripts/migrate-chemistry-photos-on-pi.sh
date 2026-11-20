#!/usr/bin/env bash
# 운영/로컬: 옛 케미 페어 사진을 uploads/images/chemistry/legacy-chem 으로 복사한다.
# DB URL 치환(Flyway V202611210800)과 함께 쓴다. 원본 chemistry/chem 은 검증 전까지 유지.
set -euo pipefail

ROOT="${1:-.}"
SRC="${ROOT}/uploads/chemistry/chem"
DST="${ROOT}/uploads/images/chemistry/legacy-chem"

if [[ ! -d "$SRC" ]]; then
  echo "skip: source missing: $SRC"
  exit 0
fi

mkdir -p "$DST"
rsync -a --no-owner --no-group "$SRC"/ "$DST"/
echo "Copied $SRC -> $DST"
find "$DST" -type f | wc -l | awk '{print "files:", $1}'
