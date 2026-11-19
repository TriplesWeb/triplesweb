# Raspberry Pi 운영 Blue-Green 배포

## 목적과 범위

기존 Docker Compose `app`은 포트 8090을 직접 점유해, 교체 후 Spring Boot가 정상 기동할 때까지 사이트가 중단됩니다. 이 변경은 운영 호스트 한 대에서 **두 앱 슬롯과 Nginx 프록시**를 사용해 새 앱을 사전 예열하고, 정상일 때만 연결을 전환합니다.

- 서비스 주소: 기존 `8090` 그대로 (Cloudflare Tunnel 등의 연결 정보 변경 없음)
- DB: 기존 MariaDB 컨테이너 및 볼륨 유지
- 평상시: 운영 앱 슬롯 하나와 Nginx 프록시만 실행
- 전환 중: Blue/Green 앱 2개를 잠시 함께 실행하고, 준비되지 않으면 전환하지 않음
- 신규 앱이 /actuator/health/readiness와 /main에 정상 응답한 후 `nginx -t` + `nginx -s reload` + 프록시 응답 검사
- Nginx는 기존 연결을 완만히 종료하지만, 긴 WebSocket·스트리밍·업로드 연결까지 **완전 무중단은 보장하지 않습니다.**

## 1. 최초 전환 전에 반드시 확인

1. 운영 Pi의 여유 메모리가 기본 **1,400 MB 이상**이어야 합니다. 부족하면 기존 앱은 유지하고 배포가 실패하도록 설정되어 있습니다. 기본 슬롯당 `mem_limit: 1800m`입니다. 서버에 다른 서비스가 많다면 수치를 실제 메모리 사용량에 맞게 조정해야 합니다.
2. 기존 포트 8090은 최초 전환에서 **잠깐 중단**됩니다. 새 Blue 앱을 먼저 띄워 건강검사를 끝낸 뒤 기존 `app`을 멈추고 Nginx를 8090에 올리는 순서입니다.
3. 새 코드에서는 스테이징 슬롯의 Hibernate 자동 DDL을 `none`으로 강제합니다. 스키마 변경은 Flyway에 의존합니다.
4. **Flyway:** 첫 배포 후 migration 폴더의 Git tree SHA를 `bluegreen/migration-tree`로 기록합니다. 이후 이 값이 변경되면 **기본적으로 배포를 차단**하고 운영 앱을 유지합니다. 하위 호환성 검증을 마친 경우에만 GitHub Actions Variable `BG_ALLOW_SCHEMA_CHANGE=true`를 일시적으로 설정해 진행합니다. 기존 앱과 신규 앱이 동시에 사용하는 동안 파괴적 DB 마이그레이션을 수행하면 안 됩니다.
5. **중복 작업:** 기존 시스템에는 `@Scheduled` 주기 작업, 외부 API 수집, 앱 시작 시 캐시 초기화가 있습니다. Blue/Green 예열 중 두 앱이 같은 DB·외부 API를 잠시 이용하므로 작업의 멱등성·중복 실행에 특히 주의해야 합니다. 현재 구성은 스케줄러의 분산 락을 제공하지 않습니다.
6. 두 앱의 로그인 세션은 기본적으로 프로세스 메모리를 공유하지 않으므로 기존 세션이 끊어질 수 있습니다. 세션까지 무중단이 필요하면 Redis/Spring Session 등 별도 세션 저장소가 필요합니다.
7. 운영 데이터 `./uploads`, `./data`는 공유합니다. 실시간 업로드·장시간 다운로드 요청과 전환 시점은 주의가 필요합니다.

## 2. 구성

- `docker-compose.yml`: 기존 MariaDB. 기존 앱은 `legacy` 프로필로 이동해 신규 배포에서는 실수로 8090을 다시 점유하지 않습니다. **복구 이외에는 legacy 앱을 시작하지 않습니다.**
- `docker-compose.bluegreen.yml`: `app_blue`, `app_green`, `proxy`; 포트 공개는 proxy의 8090만.
- `scripts/deploy-pi-bluegreen.sh`: 메모리/DB/마이그레이션 검사, 불변 SHA 이미지 pull, 비활성 슬롯 사전 기동, readiness, Nginx 전환/롤백, 기존 슬롯 drain 후 정지.
- `bluegreen/active.conf`: 호스트에 런타임으로 생성되는 Nginx 설정. 디렉터리를 bind mount하므로 파일 교체 뒤 `nginx -s reload`로 반영합니다.
- `bluegreen/active-slot`, `bluegreen/migration-tree`: 활성 슬롯 및 마지막 승인된 마이그레이션 트리.
- `bluegreen/active.conf.previous`: 바로 전 전환의 upstream 설정으로, 문제 시 수동 롤백 근거입니다.

운영 배포는 `main` 머지 후 자동이며 기존 `GHCR_TOKEN`, `PI_*`, `TS_*` 시크릿을 사용합니다. 기존 운영 DB·업로드는 삭제하지 않습니다.

## 3. 배포 순서

```text
기존 app(:8090) 또는 Nginx → 활성 Blue/Green 슬롯
               │
        새 비활성 슬롯 시작
               │
      내부 readiness + /main 검증
               │
           검증 성공?
        ┌──────┴──────┐
       아니요         예
        │             │
  후보 슬롯 종료    Nginx 설정 테스트
  기존 앱 유지      graceful reload
                      │
                 프록시 응답 검증
                      │
                 성공 시 기존 슬롯 drain
                      │
                 기존 슬롯 중지
```

후보 기동에 실패하거나 메모리가 부족하면 기존 앱과 프록시는 그대로 두고 GitHub Actions가 실패로 종료됩니다. 전환이 실패하면 이전 upstream을 복원하며, 롤백 검증이 불확실하면 **두 앱을 유지하고 수동 대응**이 필요하다고 표시합니다.

## 4. 배포 상태 확인

Pi에서 다음 명령은 서비스 상태를 읽기만 합니다.

```bash
cd /opt/stacks/triplesweb
cat bluegreen/active-slot
docker compose --profile bluegreen -f docker-compose.yml -f docker-compose.bluegreen.yml ps
curl -fsS http://127.0.0.1:8090/actuator/health/readiness
curl -sSI http://127.0.0.1:8090/main | grep -i X-Bluegreen-Slot
free -h
```

애플리케이션을 수동으로 재배포하려고 `docker compose up -d`, `down` 또는 `--remove-orphans`를 실행하지 마세요. 배포 스크립트의 잠금과 상태 관리가 우회됩니다.

## 5. 긴급 롤백

바로 직전 버전의 컨테이너가 중지된 상태로 남아 있다면, 기존 컨테이너를 먼저 기동해 readiness 확인 후 Nginx upstream을 이전 버전으로 재전환할 수 있습니다.

```bash
cd /opt/stacks/triplesweb
# 준비: 현재 활성 슬롯과 이전 설정 확인
cat bluegreen/active-slot
head -n 1 bluegreen/active.conf.previous
docker compose --profile bluegreen -f docker-compose.yml -f docker-compose.bluegreen.yml ps -a
```

아래 조치는 **운영자 검토 후** 수행합니다.

1. `active.conf.previous`가 가리키는 슬롯의 중지된 컨테이너를 확인하고 `docker start <container-id>`로 기동
2. 해당 컨테이너 IP의 `/actuator/health/readiness` 및 `/main` 검증
3. `cp bluegreen/active.conf.previous bluegreen/active.conf` 및 `docker compose --profile bluegreen -f docker-compose.yml -f docker-compose.bluegreen.yml exec -T proxy nginx -t`
4. 검증 성공하면 `docker compose --profile bluegreen -f docker-compose.yml -f docker-compose.bluegreen.yml exec -T proxy nginx -s reload`
5. 프록시의 `X-Bluegreen-Slot`·readiness가 복원됐는지 확인한 뒤 `bluegreen/active-slot`을 실제 슬롯으로 정정

**DB 마이그레이션은 롤백되지 않습니다.** 문제가 스키마 변경 때문이라면 이미지 전환만으로는 복구되지 않을 수 있습니다. 또한 이미 비활성 슬롯을 다음 버전으로 재생성했다면 직전 컨테이너가 남아 있지 않을 수 있습니다.

## 6. 검증 및 조정 변수

```bash
bash scripts/tests/deployment-static.test.sh
bash scripts/tests/bluegreen-static.test.sh
```

선택 변수:

| 이름 | 기본값 | 목적 |
|---|---|---|
| `BG_MIN_AVAILABLE_MB` | 1400 | 새 앱 준비 전에 필요한 Pi의 MemAvailable |
| `BG_SLOT_MEMORY_LIMIT` | 1800m | 앱 슬롯당 컨테이너 메모리 상한 |
| `BG_READY_TIMEOUT_SECONDS` | 360 | 새 버전 준비 최대 대기 |
| `BG_DRAIN_SECONDS` | 30 | 트래픽 전환 후 이전 앱 종료까지의 대기 |
| `BG_ALLOW_SCHEMA_CHANGE` | false | **검토된** Flyway 변경만 예외적으로 허용 |

위 변수는 모두 GitHub 저장소 **Settings → Secrets and variables → Actions → Variables**에서 조절할 수 있습니다. 기본값을 유지할 때는 별도 등록이 필요 없습니다. `BG_ALLOW_SCHEMA_CHANGE`는 안전성 검토를 마친 배포에만 true로 설정하고 완료 후 false로 되돌려야 합니다. 실제 성능 개선 폭은 최초/차기 운영 배포 로그에서 준비 시간과 전환 시간을 분리해 측정하세요.
