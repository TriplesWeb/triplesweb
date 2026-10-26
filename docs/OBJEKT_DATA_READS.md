# 오브젝트 데이터 조회

## 데이터 원천과 책임

| 데이터 | 원천 | 읽기 방식 |
| --- | --- | --- |
| 전체 카탈로그 | objekt.top | 기본 1분마다 검증 후 DB·메모리 스냅샷 교체. 페이지에서는 저장된 목록만 읽음 |
| 컬렉션별 발행량 | objekt.top rarity | 기본 1분마다 갱신. 잘못된 응답·중복 slug·장애 시 마지막 스냅샷 유지 |
| 공개 계정 보유 목록 | COSMO 닉네임 확인 + objekt.top | 전체 페이지를 검증한 뒤 10분 캐시. 커서 반복·과도한 페이지는 실패 처리 |
| 카드별 전송 내역 | objekt.top serial transfers | 사용자가 시리얼 조회 시 읽음. 20건씩 화면 페이지. 현재 소유자 조회와 최대 30초 동안 응답 공유 |
| 계정별 전송 내역 | COSMO 닉네임 확인 + objekt.top transfers | 보유 목록을 먼저 읽지 않고 upstream 커서로 한 페이지만 조회. 자동 반복 수집 없음 |
| 현재 소유자 | 인덱스의 시리얼 매핑 + COSMO 메타데이터 대조 + Abstract ownerOf | RPC 검증은 매번 수행. 실패 시 과거 인덱스 소유자를 현재 소유자로 표시하지 않음 |
| 연결된 본인 계정 | COSMO 인증 보유 API | 기존 관리자 명시적 동기화 유지. 공개 카탈로그/타 계정 데이터와 혼합하지 않음 |

## 조회 화면

- 카드: `/cosmo/objekt/detail/{key}`의 **카드별 전송 내역**에서 시리얼을 제출한다.
- 계정: `/cosmo/transfers?nickname=...`. 프로필과 보유 목록에서 연결한다.
- 계정 JSON: `/api/objekt/transfers?nickname=...`.
  다음 페이지는 응답의 `cursorTimestamp`, `cursorId`를 그대로 보낸다.
- 카드 JSON: `/cosmo/objekt/detail/{key}/transfers?serial=...&page=0`.
- 목록 모달에서 전체 상세 페이지를 열면 카드 전송 내역 조회를 사용할 수 있다.

`ObjektIndexClient`는 전송 HTTP 규약과 타임아웃을 담당한다.
카드의 응답 캐시는 최대 256개·30초이며 동일 키의 중복 호출을 합친다.
계정별 응답은 캐시하지 않으며 브라우저/API는 `no-store`를 사용한다.
오래된 캐시를 장애 시 전송 내역 대신 제공하지 않는다. `hide`는 내용을 숨기고
오류·미발견과 구분한다. 다음 페이지 오류는 기존 화면과 커서를 보존해 재시도할 수 있다.

## 정확성의 한계

전송 내역의 닉네임은 인덱스가 제공하는 공개 이름이며, 전송 당시의 닉네임이나
현재 소유자를 확정하는 근거가 아니다. 계정 화면의 보냄/받음은 전송 주소로 판정한다.
공개 응답은 표시용 필드만 제공하며 지갑 주소·토큰 ID·원본 응답을 전달하지 않는다.
카드 시리얼 매핑은 인덱스에 의존한다. 일부 신규 시리얼은 추정일 수 있으므로
COSMO에서 검증한 실제 시리얼이라고 주장하지 않는다. 인덱스 반영 지연도 있을 수 있다.

## 전체 활동의 지속 기록

`objekt.ingest.enabled`의 기본값은 `true`다. 공개 `/activity?type=all` 피드를
`objekt.ingest.poll-ms`(기본 5000ms) 간격으로 읽는다. 한 번에 최근 활동 최대 3페이지,
과거 활동 1페이지를 처리하고 각각 독립 커서를 DB에 저장한다. 최근 활동은 완료된
주기의 시작 시점을 저장하고 60초를 겹쳐 다시 읽는다. 프로세스 재시작·페이지 오류 시
마지막 성공 커서부터 재시도한다. 과거 스캔은 맨 끝까지 진행한 뒤 하루마다 다시 훑는다.
동시 실행 인스턴스도 커서 버전을 비교해 같은 페이지를 중복 완료하지 않는다.
활성화를 원하지 않으면 `OBJEKT_INGEST_ENABLED=false`로 설정한다.

전송 ID를 기본 키로 저장해 겹친 페이지를 중복 기록하지 않는다. `objekt_observed_token`의
`indexed_owner`는 **공개 인덱스가 마지막으로 보고한 전송 수신자**다. 체인에서 확인한
현재 소유자가 아니다. `reported_serial`은 인덱스가 제공한 값이고, 본인 인증 COSMO
보유 목록 동기화에서 `objektNo`를 받은 경우에만 `confirmed_serial`에 별도로 저장한다.
인덱스가 시리얼을 제공하지 않으면 빈 값으로 두며 토큰 ID로 추론하지 않는다.

운영 점검 시 `objekt_ingest_cursor`의 `live`/`backfill` 행에서 `last_success_ms`,
`watermark_ms`, `complete`, `cursor_time`을 확인한다. 새 카드가 보이지 않는 경우
이 값과 수집 로그를 먼저 확인한다. `backfill.complete=true`는 인덱스 피드의 현재
끝까지 읽었다는 뜻이다.

수집 범위는 공개 인덱스가 실제 제공하는 활동에 한정된다. 피드의 누락·지연, 60초보다
늦게 도착한 과거 이벤트, 인덱스의 추정 시리얼까지 자동으로 바로잡는 보장은 없다.
COSMO 전 계정의 확정 시리얼 전체 목록과도 동일하지 않다. Abstract 전체 로그 수집기는
계속 비활성화 상태다. 카드별·계정별 화면은 기존 요청 기반 조회를 사용한다.

## 검증

`node scripts/tests/objekt-detail.test.cjs`

`node scripts/tests/objekt-transfers.test.cjs`

`./gradlew test --tests '*ObjektMarketDataServiceTest' --tests '*ObjektTransferServiceTest' --tests '*CosmoCollectionServiceTest' --tests '*ObjektFeaturesTest' --tests '*ObjektActivityClientTest' --tests '*ObjektIngestStoreTest'`

PR의 Objekt data reads workflow도 동일 검사를 수행한다. 실제 외부 서비스 가용성과
인덱스 데이터 정확성은 로컬 fixture 테스트가 보장하지 않는다.
