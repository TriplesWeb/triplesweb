#!/usr/bin/env bash
# 관리자 비밀번호 BCrypt 해시 생성. 평문은 인자에 넣지 말고 stdin으로만 전달한다.
# 예:  printf '%s' 'your-password' | ./scripts/hash-admin-password.sh
#
# Docker Compose는 .env 의 $ 를 변수 치환하므로, Compose로 올리는 환경에서는
# 출력의 모든 $ 를 $$ 로 바꿔 CHEMISTRY_ADMIN_PASSWORD= 에 넣는다.
#   printf '%s' 'pw' | ./scripts/hash-admin-password.sh | sed 's/\$/$$/g'
# 저장소에 실값·해시를 커밋하지 않는다.
set -euo pipefail
if [[ -t 0 ]]; then
  echo "stdin으로 비밀번호를 전달하세요. 예: printf '%s' 'pw' | $0" >&2
  exit 1
fi
pw="$(cat)"
if [[ -z "$pw" ]]; then
  echo "빈 비밀번호입니다." >&2
  exit 1
fi
if ! command -v htpasswd >/dev/null 2>&1; then
  echo "htpasswd(apache2-utils / httpd-tools)가 필요합니다." >&2
  exit 1
fi
# cost 12 — 운영 해시용. Spring BCryptPasswordEncoder가 $2y$ 도 검증한다.
# macOS httpd htpasswd: -nbB -C 12
if htpasswd -nbB -C 12 x "$pw" >/dev/null 2>&1; then
  htpasswd -nbB -C 12 x "$pw" | cut -d: -f2
else
  htpasswd -nbBC 12 "" "$pw" | tr -d ':\n'
  echo
fi
