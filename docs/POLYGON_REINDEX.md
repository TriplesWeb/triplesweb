# Polygon 시대 Objekt mint 로그

Abstract 이전 ever-minted 원천은 **mint 로그(`Transfer` from `0x0`)** 만으로
충분하다. Cosmo 메타데이터·카드 시리얼·전체 transfer 히스토리는 이 단계에
넣지 않는다. 협업자 MariaDB·Flyway·GitHub `main`·CI 워크플로는 건드리지
않는다. 화면 living 발행량(objekt.top)과도 섞지 않는다.

## 수집 결과

`scripts/polygon-objekt-mints.py` 가 공개 RPC(Tenderly → QuickNode)로
10_000블록 창, 워커 5개를 써서 **블록 29,388,703–70,370,000** 을 스캔했다.

한국시간 2026-11-03 20:54–21:15 KST, **20분 15초**, 창 6,732개, HTTP 오류 없이
종료.

| | 건수 | 첫 mint 블록 | 마지막 mint 블록 | tokenId 범위 |
| --- | ---: | ---: | ---: | --- |
| tripleS | 5,705,174 | 31,385,244 | 70,361,022 | 1 – 9,416,482 |
| ARTMS | 1,487,882 | 44,055,120 | 70,360,862 | 675,115 – 9,416,474 |
| **합계** | **7,193,056** | | | |

- 계약: tripleS `0xA4B37bE40F7b231Ee9574c4b16b7DDb7EAcDC99B`, ARTMS `0x0fB69F54bA90f17578a59823E09e5a1f8F3FA200`
- 토픽: ERC-721 `Transfer` + `from=0x0`
- 로컬 파일: `/tmp/polygon-mints/mints.jsonl` (1.6GB), gzip 218MB
- 한 줄: `{artist, block, tokenId, to, tx, contract}`
- 알려진 토큰 포함: `1` (첫 tripleS), `173729`, `9203435`, `9416482` (마지막 tripleS)
- 70,370,000 이후 10k 창은 mint 0. Polygon 민팅은 70,361,022에서 끝

JSONL 순서는 워커 완료 순이라 블록 순이 아니다. 블록 순이 필요하면
`block` 으로 정렬한다.

사이트 `/cosmo/objekt` 헤드는 이 총량만 보여 준다 (`data/polygon-mints.json`).
카드·모달 발행량은 여전히 objekt.top living 값이다.

## 재현

```bash
python3 scripts/polygon-objekt-mints.py --self-test
python3 scripts/polygon-objekt-mints.py \
  --workers 5 \
  --out /tmp/polygon-mints/mints.jsonl
```

이미 있으면 체크포인트(`--checkpoint`)를 보고 빈 창만 이어서 돈다.
기본 구간은 tripleS start `29388703` 부터 마지막 mint 다음 `70370000` 까지.
창이 25,000 블록이면 공개 RPC가 HTTP 413을 낸다. 메타데이터(`--cosmo`)는
이 스크립트에 없다.

작은 구간 검증용은 `scripts/polygon-objekt-slice.py` 를 쓴다.

## 쓰지 않은 것

| 소스 | 이유 |
| --- | --- |
| Subsquid gateway worker | 2026-11 기준 403, portal API 키 필요 |
| Cosmo `objekt/v1/token/{id}` | 503 |
| Cosmo v3 nft-metadata | mint 로그에는 필요 없음. 시리얼도 없음 |
| 협업자 MariaDB | 금지. 로컬 JSONL만 |

컬렉션 slug별 ever-minted가 필요해지면 mint `tokenId`에 메타데이터를
나중에 붙인다. 지금은 로그가 원천이다.
