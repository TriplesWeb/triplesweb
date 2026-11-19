# 배포 구조 (Pi)

## 동작 정책

- PR 생성/커밋: GitHub ARM64 러너에서 이미지 빌드, GHCR에 SHA 태그로 푸시한 후 Pi 개발 서버에 적용
- PR이 수정되거나 닫히면 오래된 빌드 결과는 배포하지 않음. 빌드는 PR 단위로 취소 가능하며, 개발 배포만 전역 직렬화함
- 운영: 매일 04:00 KST(UTC 전날 19:00) 또는 수동 실행. PR에 지정한 긴급 배포 표시가 있을 때만 머지 직후 배포
- `main` 직접 푸시 금지, PR 머지는 사용자가 수행

## 독립성

- Pi 하드웨어 및 MariaDB 프로세스(운영 Docker network)는 공유
- 운영 DB `triples` / 개발 DB `triples_dev` 별도 스키마 및 권한 제한 사용자 `triplesweb_preview`
- 개발 uploads, data: `/opt/stacks/triplesweb-dev/uploads`, `/opt/stacks/triplesweb-dev/data`
- 운영 uploads, data: `/opt/stacks/triplesweb/uploads`, `/opt/stacks/triplesweb/data`
- `uploads/chemistry`만 개발 서버에 읽기 전용 마운트. 운영 파일의 수정·삭제는 개발에서 불가능함
- 첫 개발 배포 때 `scripts/ensure-dev-db.sh`가 개발 DB를 생성하고 **운영 테이블 구조와 Flyway 이력만** 복사한다. 운영 레코드/개인정보/이미지 원본은 복사하지 않음
- 최초에 개발 DB에 실제 데이터가 없으므로 운영과 동일한 콘텐츠가 필요한 테스트는 별도의 익명화된 시드 데이터가 필요함

## 실행 전 요구사항

- Pi에서 Docker Engine, Compose, `flock`, `openssl`, `rsync`, `curl` 필요
- 운영 MariaDB 컨테이너가 실행 중이고 `MARIADB_ROOT_PASSWORD`가 컨테이너 환경에 있어야 함
- Pi 배포 사용자는 기존과 같이 Docker/SSH 접근 가능해야 함
- GitHub Secrets: `PI_DEPLOY_PATH`, (선택) `PI_DEV_DEPLOY_PATH`, `PI_HOST`, `PI_USER`, `PI_SSH_KEY`, Tailscale OAuth 자격증명, GHCR 자격증명

## 수동 초기화

Pi의 저장소 checkout에서 실행:

```bash
bash scripts/setup-pi-dev.sh /opt/stacks/triplesweb /opt/stacks/triplesweb-dev
```

새 개발 DB가 비어 있지 않은데 `.dev-db-ready` 파일이 없는 경우 **자동 초기화하지 않고 실패**한다. 부분 복제/수동 구축된 데이터베이스를 임의로 덮어쓰지 않기 위함이다. 이 경우 DB 상태를 먼저 확인하고 수동 복구한다.

## 성능 및 안정성

- Docker 이미지에 chemistry 자산을 번들하지 않음. 배포 시 운영 파일로만 증분 동기화
- 이미지를 GHCR에서 가져온 뒤 운영/개발 **공통 Pi 파일 잠금**으로 Pull/Compose/Readiness를 직렬화
- 이미지 pull 실패는 최대 5회 재시도
- `/actuator/health/readiness`로 Spring이 트래픽을 받을 준비가 됐는지 검사. 각 배포에서 pull 및 startup 소요 시간 출력
- 배포 중 실패 시 이전 이미지로 컨테이너 롤백을 시도하지만 DB 스키마는 자동으로 되돌리지 않음
- 반복적인 재귀 chown/chmod, 전체 uploads rsync --delete, 매 배포 Docker prune 제거. 권한 오류는 해당 경로의 소유권만 개별 복구
- 장기간 미사용 Docker 이미지는 관리자가 디스크 사용량 확인 후 유지보수 시간에 정리

## 위험·롤백

1. **처음 적용**: 개발 DB는 스키마만 복제되므로 기존 개발 사이트의 데이터가 사라진 것처럼 보일 수 있다(실제 운영 데이터는 변경되지 않음).
2. `flock`은 Docker 이미지 Pull·컨테이너 갱신 단계만 보호한다. 운영 DB의 마이그레이션은 별도의 호환성 검증이 필요하다.
3. 긴급 복구: 해당 환경의 `.env` IMAGE를 직전 성공 SHA 이미지로 변경하고, 공유 잠금 아래 `docker compose up -d app` 실행. 단, Flyway 마이그레이션의 역호환성은 사전에 확인해야 한다.
4. 개발 서버를 운영 DB로 다시 연결하는 방식의 롤백은 **금지**한다.

## 검증

```bash
bash scripts/tests/deployment-static.test.sh
MYSQL_PASSWORD=ci MYSQL_ROOT_PASSWORD=ci docker compose -f docker-compose.yml config --quiet
MYSQL_PASSWORD=ci docker compose -f docker-compose.dev.yml config --quiet
```
