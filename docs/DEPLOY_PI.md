# Raspberry Pi Docker 운영 배포 체크리스트

자세한 흐름과 장애 대응은 [docs/DEPLOYMENT.md](DEPLOYMENT.md), 시크릿 설정은 [README.md](../README.md)를 참고하세요.

## 역할

| 환경 | 경로 | 포트 | 반영 시점 |
|------|------|------|-----------|
| **GitHub PR 검사** | Actions CI 러너 | 외부 공개 없음 | PR 생성/커밋 시 |
| **운영** | `/opt/stacks/triplesweb` | 8090 | `main` 머지 직후 / 04:00 KST 복구 / 수동 |

Pi 개발 서버는 폐지했으며 PR에서는 Docker 개발 컨테이너를 생성하지 않습니다.

## 사전 조건

- Pi ARM64, Docker + Compose, Tailscale 연결
- Actions Secrets: `TS_OAUTH_CLIENT_ID`, `TS_OAUTH_SECRET`, `PI_SSH_KEY`, `PI_HOST`, `PI_USER`, `PI_DEPLOY_PATH`
- 운영 `/opt/stacks/triplesweb`에 `.env`, `uploads/`, `data/` 존재
- GHCR Public 또는 `GHCR_TOKEN` 사용

## 확인

1. [ ] PR 생성/커밋 → `CI tests`에서 `required` 통과
2. [ ] Qodana 단일 검사, Java 테스트/WAR 패키징과 브라우저/CSS 검사 병렬 실행
3. [ ] Ruleset `main-ci`에 PR 필수 및 `required` 상태 검사 유지
4. [ ] PR을 사용자가 머지 → `Deploy to Raspberry Pi (prod)` 자동 실행
5. [ ] GitHub ARM64 이미지 빌드 및 GHCR 푸시, Pi pull 성공
6. [ ] 운영 앱 `/actuator/health/readiness` 정상, `:8090` 접속 확인
7. [ ] 첫 배포 때 기존 개발 Compose `app`만 종료되고 운영 MariaDB 유지
8. [ ] 추후 04:00 KST 점검은 같은 SHA에 대해 재시작 생략

## 개발 앱 컨테이너 폐지 시 주의

`scripts/retire-pi-dev.sh`는 기존 개발 Compose가 `app`만 정의하는 경우에만 중지하고 `--remove-orphans`로 컨테이너를 정리합니다. DB 테이블·업로드·볼륨·이미지 파일의 자동 삭제는 수행하지 않습니다. 기존 개발 데이터 삭제가 필요하면 별도 백업 후 직접 처리하세요.
