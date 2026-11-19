# Raspberry Pi Docker 배포 (운영 + 개발)

요약·시크릿 표는 [README.md](../README.md)의 **Raspberry Pi 4 Docker CI/CD** 섹션을 보세요. 여기는 체크리스트입니다.

## 역할

| 스택 | 경로 | 포트 | 반영 시점 |
|------|------|------|-----------|
| **운영** | `/opt/stacks/triplesweb` | 8090 | 매일 04:00 KST / 수동 / PR 코멘트 `즉시 배포` 후 머지 |
| **개발** | `/opt/stacks/triplesweb-dev` | 8091 | PR open/동기화 시 즉시 (`deploy-dev.yml`) |

- 개발 앱은 **운영 MariaDB·uploads·data를 공유**한다 (앱 컨테이너만 분리).
- 개발 재시작은 운영 앱을 내리지 않는다. 데이터 입력 중 운영 접속 끊김을 줄이기 위함이다.
- 공유 DB이므로 PR의 Flyway 마이그레이션이 운영 DB에도 적용될 수 있다. 파괴적 마이그레이션은 주의.

## 사전 조건

- Pi 4, **64-bit** OS, Docker + Compose
- Pi가 Tailscale에 연결됨 (`tag:pi` 권장)
- GitHub 레포에 Actions Secrets 등록됨

## 체크리스트

1. [ ] Tailscale OAuth client (`auth_keys` + `tag:ci`) → `TS_OAUTH_*`
2. [ ] ACL: `tag:ci` → `tag:pi`:22
3. [ ] deploy SSH 키 → `PI_SSH_KEY` / Pi `authorized_keys`
4. [ ] `PI_HOST` / `PI_USER` / `PI_DEPLOY_PATH` (`/opt/stacks/triplesweb`)
5. [ ] `PI_DEV_DEPLOY_PATH` (`/opt/stacks/triplesweb-dev`)
6. [ ] (선택) Actions Variable `PI_DEV_PUBLIC_URL` — NPG로 연결한 개발 도메인 (PR 코멘트·에이전트 안내용)
7. [ ] 운영: `/opt/stacks/triplesweb`에 `.env` + `uploads/` + `data/` (`APP_PORT=8090`)
8. [ ] 개발: `scripts/setup-pi-dev.sh` 또는 `docker-compose.dev.yml` 복사 후 `.env` (공유 네트워크·경로)
9. [ ] GHCR: 패키지를 Public으로 두거나 Secret `GHCR_TOKEN`(PAT `read:packages`)
10. [ ] PR 푸시 → Actions **Deploy PR to Pi (dev)** 성공, `:8091` 확인
11. [ ] 머지 후 운영은 기본 즉시 안 바뀜 → 새벽 4시·수동, 또는 PR에 `즉시 배포` 코멘트 후 머지
12. [ ] Ruleset **`main-ci`**: Require PR + status check **`required`**

## 개발 스택 최초 설정 (Pi에서)

```bash
# 레포에서 복사
scp docker-compose.dev.yml scripts/setup-pi-dev.sh pi@<host>:/tmp/
ssh pi
sudo mkdir -p /opt/stacks/triplesweb-dev
sudo chown "$USER":"$USER" /opt/stacks/triplesweb-dev
cp /tmp/docker-compose.dev.yml /opt/stacks/triplesweb-dev/docker-compose.yml
bash /tmp/setup-pi-dev.sh /opt/stacks/triplesweb /opt/stacks/triplesweb-dev
```

NPG로 개발 도메인을 붙인 뒤 GitHub **Variables**에 `PI_DEV_PUBLIC_URL`을 넣으면 PR 코멘트에 링크가 달립니다.
