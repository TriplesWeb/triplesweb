# Abstract 이전 발행량 수집안

화면의 **발행량**은 Abstract 체인 인덱서가 아니라 objekt.top이 제공하는
**현재 살아있는(living) 공급량**이다. Abstract 이전(Polygon 시대)까지 포함한
**ever-minted**를 복원하려면 소스와 저장을 분리해야 한다.

## 현재 경로

| 용도 | 서비스 | 원천 | 저장 |
| --- | --- | --- | --- |
| 정렬용 mintCount | `ObjektMarketDataService.refreshMintCounts` | `GET /collections/rarity` | 메모리 스냅샷만 |
| 카드·모달 발행량 | `ObjektSerialSupplyService` | `GET /collections/{slug}/serials` | 짧은 TTL 캐시 |
| Transfer/소유 | `AbstractObjektIndexer` (기본 OFF) | Abstract RPC `eth_getLogs` | MariaDB (발행량 미사용) |

시리얼 집합 규칙은 `OBJEKT_DATA_READS.md`와 같다. 고유 시리얼만 세고
`spun`·최대 시리얼·토큰 ID는 발행량에 넣지 않는다.

## 목표 스키마 초안

```sql
-- 후속 PR에서 Flyway로 추가. living 과 archive 를 source 로 구분한다.
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

UI 기본값은 `source IN ('rarity','serials')`의 최신 living 값이다.
`polygon-archive`는 ever-minted 전용이며 rarity로 덮어쓰지 않는다.

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

| 후보 소스 | 방법 | 비고 |
| --- | --- | --- |
| Apollo `polygon` 브랜치 / MEGA 덤프 | 컬렉션별 mint·transfer SQL/CSV | 구조화된 아카이브 |
| kuiperdog/cosmo-indexer · Nova | Polygon GraphQL·주간 mint 통계 | 호스팅 여부에 의존 |
| Polygon `eth_getLogs` mint (`from=0x0`) | 구 계약·블록 구간 스캔 | 비용 큼, 권위 있음 |
| 커뮤니티 스프레드시트 | one-shot import | provenance 태그 필수 |

import 시 `source=polygon-archive`로만 넣고, 화면 living 발행량과 섞지 않는다.
필요하면 툴팁·관리 화면에서 ever-minted만 따로 보여 준다.

## 병합·구현 순서

1. UI/보드 PR(#37)과 분리한다. 대용량 백필이 병합을 막지 않게 한다.
2. #37이 `main`에 들어간 뒤 `cursor/objekt-mint-snapshot-ae55`에서
   테이블 + 1차 persist 잡을 연다.
3. 2·3차는 데이터가 준비된 뒤 별 PR로 진행한다.

## 하지 않을 것

- 발행량 UX를 Abstract Transfer 인덱서 ON에 종속시키지 않는다.
- `spun` 또는 max serial을 living 발행량으로 바꾸지 않는다.
- archive count로 rarity 스냅샷을 덮어쓰지 않는다.
