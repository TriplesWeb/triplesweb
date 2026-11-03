# 발행량 실시간 기록 · Abstract 이전 수집안

화면의 **발행량**은 Abstract 체인 인덱서가 아니라 objekt.top이 제공하는
**현재 살아있는(living) 공급량**이다. Abstract 이전(Polygon 시대)까지 포함한
**ever-minted**를 복원하려면 소스와 저장을 분리해야 한다.

이 문서는 협업자 DB에 Flyway 테이블을 추가하지 않는다. 1차 persist는
별 PR에서만 연다.

## 현재 경로

| 용도 | 서비스 | 원천 | 저장 |
| --- | --- | --- | --- |
| 정렬용 mintCount | `ObjektMarketDataService.refreshMintCounts` | `GET /collections/rarity` 60초 | 메모리 스냅샷만 |
| 카드·모달 발행량 | `ObjektSerialSupplyService` | `GET /collections/{slug}/serials` | TTL 30초, 실패 시 2분 stale, rarity 첫 페인트 유지 |
| Transfer/소유 | `AbstractObjektIndexer` (기본 OFF) | Abstract RPC `eth_getLogs` from block 6800000 | MariaDB (발행량 미사용) |

시리얼 집합 규칙은 `OBJEKT_DATA_READS.md`와 같다. 고유 시리얼만 세고
`spun`·최대 시리얼·토큰 ID는 발행량에 넣지 않는다.

## 실시간 기록 구상

“실시간”은 체인 멤풀이 아니라 **이미 쓰는 objekt.top rarity를 유실 없이 남기는 것**이
1차다. 카드 모달의 serials 조회는 요청 시에만 일어나므로, 목록 정렬과 같은
주기의 rarity 스냅샷이 시계열의 기준이 된다.

```
objekt.top /collections/rarity  ──60s──►  메모리 mintCounts
                                      └──►  objekt_mint_snapshot (source=rarity)
카드 모달 serials                 ──TTL──►  메모리 + source=serials 행
Abstract Transfer mint(from=0x0) ──OFF──►  living 증분 (후속, 인덱서 ON일 때만)
Polygon archive                   ──1회──►  source=polygon-archive (덮어쓰지 않음)
```

권장 순서:

1. **living persist** — rarity 스케줄이 성공한 뒤에 `(slug, source=rarity, count, observed_at)`만
   append. 잘못된 응답은 지금처럼 스냅샷을 교체하지 않고, 행도 쓰지 않는다.
2. **serials 보강** — 모달/백필이 완전한 serials를 받으면 `source=serials`와
   `spun_count`·`ranges_json`을 같이 남긴다. UI 기본값은 둘 중 최신 living.
3. **증분 알림** — 같은 slug의 직전 rarity 대비 증가분만 로그/관리 화면에 표시.
   감소는 spun·소각·인덱스 정정일 수 있으니 발행량으로 되돌리지 않는다.
4. **Abstract mint 로그**는 인덱서를 켜기 전에는 쓰지 않는다. 켜더라도 living
   숫자 권위는 objekt.top에 두고, 체인은 “이번에 새로 찍힌 토큰” 보조용이다.

목표 스키마는 그대로다. 후속 PR의 Flyway에서만 만든다.

```sql
CREATE TABLE objekt_mint_snapshot (
  slug            VARCHAR(128) NOT NULL,
  source          VARCHAR(32)  NOT NULL, -- rarity | serials | polygon-archive | ...
  count           BIGINT       NOT NULL,
  spun_count      BIGINT       NULL,
  max_serial      INT          NULL,
  ranges_json     MEDIUMTEXT   NULL,
  observed_at     TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (slug, source, observed_at)
);
```

## Abstract 이전 Polygon — 가져올 수 있는지

**계약은 아직 Polygon에 있다.** 2025-04-18 COSMO가 Abstract로 옮긴 뒤에도
`0xA4B37bE40F7b231Ee9574c4b16b7DDb7EAcDC99B` 바이트코드가
`eth_getCode`로 읽힌다 (publicnode · drpc에서 확인). Transfer 토픽은 표준
ERC-721 `Transfer`와 같다.

| 소스 | 지금 상태 | 비고 |
| --- | --- | --- |
| Polygon `eth_getCode` | 가능 | 계약 생존 확인용 |
| Polygon `eth_getLogs` mint (`from=0x0`) | **아카이브 RPC 필요** | 무료 publicnode는 옛 구간에 personal token을 요구. drpc 무료 플랜은 10,000블록 제한 |
| Apollo `polygon` 브랜치 인덱서 | 구조는 남아 있음 | `teamreflex/cosmo-web` `polygon` 브랜치. 공개 HTTP로 덤프를 주지 않음 |
| objekt.top serials / rarity | living만 | 이주 후 살아 남은 카드. 소각·미이전 ever-minted는 빠질 수 있음 |
| Abstract indexer `start-block=6800000` | Polygon 구간 없음 | 다른 체인·다른 계약 |

결론: **예전 체인을 “읽을 수는” 있다.** 다만 무료 공개 RPC만으로는 마이그레이션
이전 로그를 통째로 긁기 어렵고, 권위 있는 ever-minted는 (1) 아카이브 키가 있는
Polygon RPC로 mint 로그만 스캔하거나 (2) Apollo/커뮤니티 덤프를
`source=polygon-archive`로 한 번 넣는 쪽이 맞다. Abstract RPC로는 복원되지 않는다.

import 시 `source=polygon-archive`로만 넣고, 화면 living 발행량과 섞지 않는다.
필요하면 툴팁·관리 화면에서 ever-minted만 따로 보여 준다.

## 3단계 수집

### 1차 — living persist (저위험)

1. 기존 rarity 스케줄 직후 `source=rarity` 행을 기록한다.
2. serials 조회 성공 시 `source=serials`와 선택적으로 `spun_count`·`ranges_json`을 기록한다.
3. 재시작해도 마지막 living 값을 감사·복구할 수 있게 한다.

### 2차 — 카탈로그 백필

1. `objekt_catalog`의 모든 slug에 serials를 순회한다.
2. unavailable·불완전 응답·rarity와 큰 불일치는 재시도 큐에 남긴다.
3. 동시성·레이트리밋은 현재 `ObjektSerialSupplyService` 한도와 맞춘다.

### 3차 — Abstract 이전 ever-minted

Polygon 시대 물량은 Abstract RPC/`start-block=6800000`으로는 복원되지 않는다.

1. 아카이브 Polygon RPC로 구 계약 mint 로그를 블록 구간 스캔한다.
2. 실패·비용이 크면 Apollo `polygon` 덤프/MEGA SQL을 같은 스키마로 넣는다.
3. 두 값이 다르면 archive 행을 두 번 남기고 living은 건드리지 않는다.

| 후보 소스 | 방법 | 비고 |
| --- | --- | --- |
| Apollo `polygon` 브랜치 / MEGA 덤프 | 컬렉션별 mint·transfer SQL/CSV | 구조화된 아카이브. 시리얼 복원에 유리 |
| 로컬 Polygon mint 로그 | `scripts/polygon-objekt-mints.py` | **7,193,056 mint 수집 완료** (tripleS 5,705,174 + ARTMS 1,487,882). 메타데이터 없음. 기록은 `POLYGON_REINDEX.md` |
| kuiperdog/cosmo-indexer · Nova | Polygon GraphQL·주간 mint 통계 | 호스팅 여부에 의존 |
| Polygon `eth_getLogs` mint (`from=0x0`) | 구 계약·블록 구간 스캔 | 10k 블록 창. 50k는 HTTP 413. Subsquid 게이트웨이는 API 키 필요 |
| 커뮤니티 스프레드시트 | one-shot import | provenance 태그 필수 |

## 병합·구현 순서

1. UI/보드 PR과 분리한다. 대용량 백필이 병합을 막지 않게 한다.
2. 테이블 + 1차 persist는 `cursor/objekt-mint-snapshot-ae55` 같은 별 브랜치에서 연다.
3. Polygon mint 로그(7,193,056건)는 로컬 JSONL로 준비됐다. 테이블에
   넣는 일과 living persist는 별 PR로 진행한다.

## 하지 않을 것

- 발행량 UX를 Abstract Transfer 인덱서 ON에 종속시키지 않는다.
- `spun` 또는 max serial을 living 발행량으로 바꾸지 않는다.
- archive count로 rarity 스냅샷을 덮어쓰지 않는다.
- 협업자 미리보기에서 Flyway/DDL로 스냅샷 테이블을 만들지 않는다.
