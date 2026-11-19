# CI 및 운영 배포 (Raspberry Pi 단일 서버)

## 간소화한 워크플로

1. PR 생성/수정 → **CI tests**만 실행. 개발 서버에 배포하지 않는다.
2. CI는 배포 스크립트 정적 검증을 먼저 수행하고 Java 테스트+WAR 패키징, CSS+브라우저 검사를 각각 병렬 수행한다. Qodana는 CI 안에서 **한 번만** 실행한다.
3. PR이 `main`에 머지되면 **Deploy to Raspberry Pi (prod)**가 자동으로 실행된다.
4. 매일 04:00 KST(UTC 전날 19:00) 일정 및 수동 실행은 실패/누락 복구용. 운영 컨테이너가 해당 SHA이며 readiness에 성공하면 빌드와 재시작을 생략한다.

기존 `즉시 배포` 코멘트 검사 방식은 폐지한다. 모든 머지가 운영 배포 대상이다. GitHub `main`에 직접 푸시하지 말고 PR을 머지한다.

## 빌드와 검사 최적화

- 이미지: GitHub 네이티브 ARM64 러너 사용, GitHub Actions Buildx 캐시 `scope=triplesweb-arm64` 공유
- `.dockerignore`에서 대용량 uploads를 제외. chemistry 자산은 이미지에 포함하지 않고 Pi의 `uploads/chemistry`만 rsync로 증분 업데이트
- 운영 Pi 배포 전에 현재 실행 중 이미지 태그와 `/actuator/health/readiness` 확인
- Java 단일 Gradle 호출 `./gradlew test bootWar`로 테스트와 패키징 공유
- CSS/JavaScript/Chrome 검사를 별도 Job으로 병렬화
- 중복 Gradle 실행(`ObjektLiveApiTest` 외부 네트워크 재실행), 별도 WAR Job, 중복 Qodana workflow 제거
- 오래된 PR 커밋의 CI 검사는 취소
- 배포 시 전체 uploads `chown -R`/`chmod -R`, `rsync --delete`, 무조건적인 `docker image prune` 제거
- Pi에서는 pull 재시도 및 배포 잠금을 사용하고 다운로드·시작 시간을 기록

외부 `ObjektLiveApiTest`는 네트워크의 순간적인 상태에 좌우되므로 PR 필수 검증에서 제외했다. 외부 API 가용성이 중요한 변경은 필요 시 따로 수동 검증한다.

## 개발 서버 폐지

- 워크플로 `deploy-dev.yml`, Compose `docker-compose.dev.yml`, 개발 DB 초기화 코드 제거
- **첫 운영 배포 때** `scripts/retire-pi-dev.sh`가 `PI_DEV_DEPLOY_PATH` 또는 `PI_DEPLOY_PATH-dev` 아래 개발 Compose의 app 컨테이너를 중지·제거
- 개발 스택이 `app` 이외의 서비스를 정의하면 중지하지 않고 실패해 운영 MariaDB 등 오작동을 방지
- 삭제 금지: 운영 MariaDB, 운영 이미지·업로드·데이터, 개발 DB와 개발 데이터 파일. 개발 컨테이너를 내리면 CPU/RAM이 해제되지만 기존 이미지는 디스크에 남을 수 있음
- 추가로 디스크를 회수하려면 실제 컨테이너와 볼륨 소유권을 점검한 뒤 별도 수동 정리가 필요함

## 실패 시

- 풀 실패 시 5회 재시도, 이미지 변경 후 readiness가 실패하면 이전 앱 이미지로 되돌리기를 시도
- DB 스키마 마이그레이션은 자동으로 롤백하지 않는다. 위험한 DB 변경을 배포할 때는 역호환성을 보장해야 한다.
- 운영 Pi 배포는 앱 이미지 교체와 Compose 업데이트만 수행하며 DB 내용을 자동 복사하지 않는다.

## 검증

```bash
bash scripts/tests/deployment-static.test.sh
MYSQL_ROOT_PASSWORD=ci MYSQL_PASSWORD=ci docker compose -f docker-compose.yml config --quiet
./gradlew test bootWar --no-daemon --stacktrace
./scripts/setup-css.sh --check
```

## 필수 GitHub 설정

`PI_DEPLOY_PATH`, `PI_HOST`, `PI_USER`, `PI_SSH_KEY`, `TS_OAUTH_CLIENT_ID`, `TS_OAUTH_SECRET`, GHCR 자격증명(옵션), `PI_DEV_DEPLOY_PATH`(기존 개발 경로가 기본값과 다른 경우).

운영 프로세스는 `main` 머지 후 자동 배포되며, 요청 없이 `main`에 직접 커밋하지 않는다.
