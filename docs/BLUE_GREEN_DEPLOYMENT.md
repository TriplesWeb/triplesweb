# Raspberry Pi 운영 Blue-Green 배포

## 목적과 범위

기존 Docker Compose `app`은 포트 8090을 직접 점유해, 교체 후 Spring Boot가 정상 기동할 때까지 사이트가 중단됩니다. 이 변경은 운영 호스트 한 대에서 **두 앱 슬롯과 Nginx 프록시**를 사용해 새 앱을 사전 예열하고, 정상일 때만 연결을 전환합니다.

- 서비스 주소: 기존 `8090` 그대로 (Cloudflare Tunnel 등의 연결 정보 변경 없음)
- DB: 기존 MariaDB 컨테이너 및 볼륨 유지
- 평상시: 운영 앱 슬롯 하나와 Nginx 프록시만 실행
- 전환 중: Blue/Green 앱 2개를 잠시 함께 실행하고, 준비되지 않으면 전환하지 않음
- 신규 앱의 readiness와 홈 화면(`/`) HTTP 200을 확인한 후 `nginx -t` + `nginx -s reload` + 프록시 응답 검사
- Nginx는 기존 연결을 완만히 종료하지만, 긴 WebSocket·스트리밍·업로드 연결까지 **완전 무중단은 보장하지 않습니다.**

## 1. 최초 전환 전에 반드시 확인

1. Docker가 메모리 제한을 지원해야 하며(`docker info --format '{{.MemoryLimit}}'` → `true`), 운영 Pi의 여유 메모리가 기본 **1,400 MB 이상**이어야 합니다. 둘 중 하나라도 충족하지 못하면 후보를 시작하지 않고 배포에 실패합니다. 기본 슬롯당 `mem_limit: 1800m`입니다. 서버에 다른 서비스가 많다면 수치를 실제 메모리 사용량에 맞게 조정해야 합니다.
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
      내부 readiness + 홈 화면 검증
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

후보 기동에 실패하거나 메모리가 부족하면 기존 앱과 프록시는 그대로 두고 GitHub Actions가 실패로 종료됩니다. 최초 전환 실패 시 `docker start`로 원래 레거시 컨테이너를 재기동합니다(새 이미지로 재생성하지 않음). 전환이 실패하면 이전 upstream을 복원하며, 롤백 검증이 불확실하면 **두 앱을 유지하고 수동 대응**이 필요하다고 표시합니다. 이후 재시도도 실제 프록시 응답과 설정 파일의 슬롯이 일치해야 후보를 준비할 수 있습니다.

## 4. 배포 상태 확인

Pi에서 다음 명령은 서비스 상태를 읽기만 합니다.

```bash
cd /opt/stacks/triplesweb
cat bluegreen/active-slot
docker compose --profile bluegreen -f docker-compose.yml -f docker-compose.bluegreen.yml ps
curl -fsS http://127.0.0.1:8090/actuator/health/readiness
curl -fsS -D - -o /dev/null http://127.0.0.1:8090/ | grep -i X-Bluegreen-Slot
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
2. 해당 컨테이너 IP의 `/actuator/health/readiness` 및 홈 화면(`/`) HTTP 200 검증
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

## 7. 실제 운영 환경 검토 (2026-11-20 KST)

SSH로 상태만 조회했으며 새 컨테이너 기동, 운영 재시작, DB 변경은 수행하지 않았습니다.

| 항목 | 관측값 | 판단 |
|---|---|---|
| 아키텍처 / CPU | aarch64 / 4코어 | 현재 ARM64 이미지 경로와 일치 |
| Docker / Compose | 29.8.1 / v5.5.1 | Compose 설치 확인 |
| OS / 커널 | Debian 13 / 6.18.50+rpt-rpi-v8 | 부팅 인자에 `cgroup_disable=memory` 확인 |
| CPU 부하 표본 | load average 약 6 (4코어), 짧은 표본에서 idle 1~26% | 다른 서비스 부하가 높아 예열 지연 가능. 실제 제한 시간 검증 필요 |
| RAM / MemAvailable | 7,818 MiB / 약 3,904 MiB | 후보 1개를 추가할 여지는 있음. 순간 표본이며 피크 부하 보장은 아님 |
| 현재 앱 RSS / 최고 RSS | 약 1,305 MiB / 약 1,305 MiB | 1,800 MiB 제한은 여유가 크지 않음. 예열 중 최대 RSS 측정 필요 |
| Swap 사용 | 약 1,463 MiB | 다른 서비스와 자원 경쟁 중. swap을 배포 여유 RAM으로 계산하지 않음 |
| 디스크 여유 | 약 66 GiB | 이미지 추가 저장 여유 있음 |
| DB / 서비스 포트 | MariaDB healthy / 8090 readiness UP | 기존 DB·포트 유지 구성과 일치 |
| 볼륨 | `/opt/stacks/triplesweb/uploads`, `/opt/stacks/triplesweb/data` | 후보 슬롯의 공유 마운트와 일치 |
| 메모리 제한 지원 | `MemoryLimit=false`, `SwapLimit=false`, `docker stats`의 메모리 값 0 | **현재 상태로 배포 불가.** 메모리 제한이 강제되지 않으므로 사전 검사에서 차단 |

**결론: 자원 용량 측면에서는 조건부로 가능하지만, 현재 운영 설정 그대로 실행할 수는 없습니다.** 먼저 운영자가 OS/커널에 맞게 memory cgroup을 활성화하고 필요한 재부팅을 계획해야 합니다. Docker의 메모리 제한은 커널 지원에 의존합니다([Docker 공식 문서](https://docs.docker.com/engine/containers/resource_constraints/)). 이 PR은 부팅 설정이나 호스트 커널을 자동 변경하지 않습니다. 활성화 후 `MemoryLimit=true`와 실제 컨테이너 메모리 통계가 확인되어야 합니다.

앱 수준의 확인도 남아 있습니다.

- `ObjektIngestStore`는 DB 행 잠금·버전 검사·중복 삽입 방지로 동시 수집 충돌을 방어합니다. 다만 두 프로세스의 외부 API 호출과 캐시 예열 비용은 중복됩니다.
- `SchemaImproveRunner`는 Hibernate `ddl-auto=none`과 별개로 시작 시 DDL을 실행합니다. Flyway 폴더 SHA 검사만으로 모든 스키마 변경을 차단할 수 없습니다. 해당 Java 초기화 코드 변경도 구버전과의 호환성 검토 대상입니다.
- `CosmoTokenProvider`의 잠금은 프로세스 내부에 한정됩니다. 공유 인증 파일의 원자적 교체는 제공하지만 두 앱의 토큰 갱신을 직렬화하지 않습니다. 겹치는 요청 중 인증 갱신 경쟁 가능성이 남습니다.
- 세션은 공유되지 않으며 기본 drain 30초보다 긴 요청은 이전 앱 종료 시 끊길 수 있습니다.

최초 실제 배포 전에는 cgroup 지원을 복구하고, 운영자가 승인한 별도 검증 환경에서 같은 이미지·설정으로 후보 예열 시 최대 메모리와 CPU, 공유 DB 초기화 및 인증 동시 요청을 확인해야 합니다. 현재 읽기 전용 점검과 CI는 실제 Nginx 전환·장애 복구 실험을 대신하지 않습니다. 머지 직후 운영 배포가 자동 시작되므로 사전 조건을 해결하기 전에는 머지하지 않습니다.
