# CI 및 운영 배포 (Raspberry Pi 단일 서버)

## 간소화한 워크플로

1. PR 생성/수정 → **CI tests**만 실행. 개발 서버에 배포하지 않는다.
2. CI는 배포 스크립트 정적 검증을 먼저 수행하고 Java 테스트+WAR 패키징과 해당 테스트가 생성하는 HTML 의존 브라우저 검사를 같은 Job에서 실행하고, CSS+JavaScript 및 Qodana는 병렬 Job으로 검사한다. Qodana는 CI 안에서 **한 번만** 실행한다.
3. PR이 `main`에 머지되면 **Deploy to Raspberry Pi (prod)**가 자동으로 실행된다.
4. 매일 04:00 KST(UTC 전날 19:00) 일정 및 수동 실행은 실패/누락 복구용. 운영 컨테이너가 해당 SHA이며 readiness에 성공하면 빌드와 재시작을 생략한다.

기존 `즉시 배포` 코멘트 검사 방식은 폐지한다. 모든 머지가 운영 배포 대상이다. GitHub `main`에 직접 푸시하지 말고 PR을 머지한다.

## 단일 앱 배포

운영 앱은 같은 Pi의 8090 포트에서 실행합니다. Actions가 Pi에 이미지를 준비하고 MariaDB 상태를 확인한 다음, 기존 앱을 정상 종료합니다. 새 앱은 기동 중 Flyway를 실행하고 readiness와 홈 응답을 검사합니다. 기존 앱 종료부터 새 앱 준비까지 사이트가 중단됩니다. Blue-Green 전환 및 이전 프록시/슬롯 자동 정리는 지원하지 않습니다. MariaDB와 업로드 마운트는 유지합니다.

Flyway 마이그레이션이 바뀐 배포도 같은 순서로 실행합니다. 이전 앱이 종료된 상태에서 DB를 변경합니다. 기동에 실패하면 마이그레이션 트리가 이전 정상 배포와 동일한 경우에만 이전 앱으로 자동 복구를 시도합니다. 트리가 달라진 실패는 Actions와 Flyway 로그를 확인해 원인을 고친 뒤 재배포합니다. DB 자체는 자동으로 되돌리지 않습니다.

운영 앱 교체는 `Deploy to Raspberry Pi (prod)`의 자동 또는 수동 실행을 사용합니다. GitHub Actions 변수 `APP_MEMORY_LIMIT`, `APP_STOP_TIMEOUT_SECONDS`, `APP_READY_TIMEOUT_SECONDS`의 기본값은 각각 1800 MB, 600초, 360초입니다.

운영 Actions의 앱 기동 경로는 `OBJEKT_MINT_CHAIN_ENABLED=true`와 `FLYWAY_ENABLED=true`를 전달합니다. 공용 Compose의 기본값은 둘 다 `false`입니다. 같은 이미지의 배포 생략, 신규 기동, 이전 이미지 복구는 컨테이너의 두 플래그와 `/cosmo/objekt/pulse`의 불리언 `chainMints: true`를 모두 확인합니다. pulse 검증에는 Pi의 Python 3을 사용합니다.

운영 앱 로그는 Pi의 `$PI_DEPLOY_PATH/logs/triplesweb.log`에 저장되어 컨테이너를 교체해도 남습니다. 날짜가 바뀌거나 파일이 10MB에 도달하면 gzip으로 회전하며, 30일 이내라도 압축 이력이 1GB를 넘으면 오래된 파일부터 삭제합니다. 배포 디렉터리에서 현재 로그는 `tail -F logs/triplesweb.log`, 압축 이력은 `zcat logs/triplesweb.log.YYYY-MM-DD.0.gz`로 확인합니다.

## PR 변경 영역별 선택적 CI

PR의 synthetic merge commit과 대상 `main`을 비교해 `scripts/ci-path-filter.py`가 경로를 판정합니다. 이름을 변경한 파일은 이전 경로·새 경로 모두 검증하고, 알 수 없는 앱 경로는 안전을 위해 양쪽 검사를 수행합니다.

| 변경 위치 | 실행 검사 |
|---|---|
| `src/main/java/**`, `src/test/java/**`, `src/main/resources/db/**`, Gradle 설정 | Backend: `test bootWar` + Qodana |
| `src/main/resources/static/**`, `scripts/css/**`, `package*.json`, CSS 빌드 스크립트 | Frontend: CSS drift + JS 테스트 + 독립 Playwright 브라우저 검증 |
| `src/main/resources/templates/**`, `Dockerfile`, CI 워크플로, 새 경로 분류 로직 | 양쪽 검사 + Java 생성 HTML이 필요한 브라우저 통합 검증 |
| Markdown/문서/배포 전용 스크립트 | 필수 배포 설정 검사 + 경로 분류기 단위검사만 |

백엔드 전용 PR에서 프론트 CSS·JS 검사는 스킵하며, 프론트 전용 PR에서는 Gradle·WAR·Qodana를 스킵합니다. `required` 상태 검사는 항상 실행되며 **실행해야 하는 Job이 생략되면 실패**, 생략해야 하는 Job이 예상과 다르게 실행돼도 실패로 처리합니다.

```bash
python3 scripts/ci-path-filter.py --self-test
git diff --name-only --no-renames -z HEAD^1 HEAD | python3 scripts/ci-path-filter.py --stdin-z
```

CI 설정만 수정된 PR을 머지하면 `main` push의 즉시 운영 배포는 건너뜁니다. 다만 매일 새벽 4시 정기 작업은 별도 최신 커밋 확인을 수행하므로 운영 배포가 다시 발생할 수 있습니다.

## 빌드와 검사 최적화

- 이미지: GitHub 네이티브 ARM64 러너 사용, GitHub Actions Buildx 캐시 `scope=triplesweb-arm64` 공유
- `.dockerignore`에서 대용량 uploads를 제외. 케미 매트릭스 UI(logo·symbols)는 WAR static(`/images/chemistry`). 페어 사진은 Pi `uploads/images/chemistry`만 사용
- 운영 Pi 배포 전에 현재 실행 중 이미지 태그와 `/actuator/health/readiness` 확인
- Java 단일 Gradle 호출 `./gradlew test bootWar`로 테스트와 패키징 공유
- Java 생성 HTML이 필요한 Chrome 검사는 Gradle 테스트 Job에서 이어 실행해 파일 공유를 보장하며, 독립 CSS/JavaScript 검사는 병렬 Job으로 유지
- 중복 Gradle 실행(`ObjektLiveApiTest` 외부 네트워크 재실행), 별도 WAR Job, 중복 Qodana workflow 제거
- 오래된 PR 커밋의 CI 검사는 취소
- 배포 시 전체 uploads `chown -R`/`chmod -R`, `rsync --delete`, 무조건적인 `docker image prune` 제거
- Pi에서는 pull 재시도 및 배포 잠금을 사용하고 다운로드·시작 시간을 기록

## 운영 배포 추가 최적화

- 사전점검에서 기존 개발 앱 중지(최초 1회), 운영 SHA/readiness 확인을 **한 번의 Pi SSH 접속**으로 검사
- 배포 시 `migrate-chemistry-photos-on-pi.sh`로 옛 `uploads/chemistry/chem` → `uploads/images/chemistry` 멱등 복사 (원본 유지). Flyway가 DB URL을 같은 경로로 치환
- PNG 등의 압축된 정적 이미지는 `rsync -a`로 중복 압축을 방지. 운영 업로드 루트에 삭제 옵션을 쓰지 않음
- GHCR에 해당 `main` 커밋 SHA 이미지가 이미 있으면, 운영 배포 실패/재시도 상황에서 **Docker 재빌드·재푸시 생략**
- Pi에도 동일한 불변 SHA 이미지가 남아 있으면 `docker compose pull` 자체를 생략
- 실행 중인 이미지가 목표 SHA와 같지만 readiness가 실패하면 `--force-recreate app`으로 앱만 재시작해 장애 복구를 시도
- Docker Compose `up -d --no-deps app`으로 기존 MariaDB 컨테이너를 다시 평가·변경하지 않음. readiness는 2초 주기로 확인
- Docker CSS 빌드에는 필요한 `scripts/*.cjs`, `setup-css.sh`, `scripts/css`만 복사해 배포 스크립트 수정에 따른 CSS 이미지 캐시 무효화를 방지
- 캐시 푸시 서버 오류만으로 운영 배포가 실패하지 않도록 GHA 캐시 내보내기에 `ignore-error=true` 적용

**주의:** 실제 운영 속도 향상 수치는 이 PR이 머지된 후 성공한 최초 배포와 기존 실행 로그를 비교해야 확인할 수 있습니다. Spring Boot 자체 기동 시간은 애플리케이션 초기화와 Pi 성능에 달려 있어 스크립트만으로 단축된다고 보장할 수 없습니다.


외부 `ObjektLiveApiTest`는 네트워크의 순간적인 상태에 좌우되므로 PR 필수 검증에서 제외했다. 외부 API 가용성이 중요한 변경은 필요 시 따로 수동 검증한다.

## 개발 서버 폐지

- 워크플로 `deploy-dev.yml`, Compose `docker-compose.dev.yml`, 개발 DB 초기화 코드 제거
- **첫 운영 배포 때** `scripts/retire-pi-dev.sh`가 `PI_DEV_DEPLOY_PATH` 또는 `PI_DEPLOY_PATH-dev` 아래 개발 Compose의 app 컨테이너를 중지·제거
- 개발 스택이 `app` 이외의 서비스를 정의하면 중지하지 않고 실패해 운영 MariaDB 등 오작동을 방지
- 삭제 금지: 운영 MariaDB, 운영 이미지·업로드·데이터, 개발 DB와 개발 데이터 파일. 개발 컨테이너를 내리면 CPU/RAM이 해제되지만 기존 이미지는 디스크에 남을 수 있음
- 추가로 디스크를 회수하려면 실제 컨테이너와 볼륨 소유권을 점검한 뒤 별도 수동 정리가 필요함

## 실패 시

- 이미지 준비는 최대 5회 시도합니다. 준비에 실패하면 실행 중인 앱을 유지합니다.
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
