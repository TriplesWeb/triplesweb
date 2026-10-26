# 오브젝트 데이터 조회

## 데이터 원천과 책임

| 데이터 | 원천 | 읽기 방식 |
| --- | --- | --- |
| 전체 카탈로그 | objekt.top | 주기적으로 검증 후 DB·메모리 스냅샷 교체. 페이지에서는 저장된 목록만 읽음 |
| 컬렉션별 발행량 | objekt.top rarity | 한 번에 갱신. 잘못된 응답·중복 slug·장애 시 마지막 스냅샷 유지 |
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

전체 Abstract 수집기는 기본 비활성화 설정을 유지한다. 이 조회 기능은 DB 스키마를
추가하거나 전체 계정·전체 체인을 백그라운드로 수집하지 않는다.

## 검증

`node scripts/tests/objekt-detail.test.cjs`

`node scripts/tests/objekt-transfers.test.cjs`

`./gradlew test --tests '*ObjektMarketDataServiceTest' --tests '*ObjektTransferServiceTest' --tests '*CosmoCollectionServiceTest' --tests '*ObjektFeaturesTest'`

PR의 Objekt data reads workflow도 동일 검사를 수행한다. 실제 외부 서비스 가용성과
인덱스 데이터 정확성은 로컬 fixture 테스트가 보장하지 않는다.
