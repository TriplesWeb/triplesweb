# Raspberry Pi Docker 배포 (Tailscale + GitHub Actions)

요약·시크릿 표는 [README.md](../README.md)의 **Raspberry Pi 4 Docker CI/CD** 섹션을 보세요. 여기는 체크리스트입니다.

## 사전 조건

- Pi 4, **64-bit** OS, Docker + Compose
- Pi가 Tailscale에 연결됨 (`tag:pi` 권장)
- GitHub 레포에 Actions Secrets 등록됨

## 체크리스트

1. [ ] Tailscale OAuth client (`auth_keys` + `tag:ci`) → `TS_OAUTH_*`
2. [ ] ACL: `tag:ci` → `tag:pi`:22
3. [ ] deploy SSH 키 → `PI_SSH_KEY` / Pi `authorized_keys`
4. [ ] `PI_HOST` / `PI_USER` / `PI_DEPLOY_PATH`
5. [ ] `/opt/stacks/triplesweb`에 `.env` + `uploads/` + `data/` (`APP_PORT=8090`)
6. [ ] GHCR: 패키지를 Public으로 두거나, Secret `GHCR_TOKEN`(PAT `read:packages`) 등록  
       (워크플로가 배포 직전에 Pi에서 `docker login` 수행)
7. [ ] `main` 푸시 후 Actions 성공, Pi에서 `docker compose ps` 확인
