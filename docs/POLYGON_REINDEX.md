# Polygon 시대 Objekt 재인덱싱 실험

Abstract 이전 발행량(ever-minted)을 **우리가 다시 인덱싱할 수 있는지**를
로컬에서 슬라이스로 확인했다. 협업자 MariaDB·Flyway·GitHub `main`·CI
워크플로는 건드리지 않는다.

living 발행량 UX와 섞지 않는다. 결과는 `source=polygon-archive` 후보일
뿐이다. 화면 숫자는 여전히 objekt.top rarity/serials다.

## 결론

**짧은 구간은 다시 인덱싱할 수 있다.** genesis→2025-04-18 전체는 공개 RPC만으로도
로그는 읽히지만, 토큰마다 Cosmo 메타데이터를 붙이면 수 시간~수 일이 걸린다.
전체 덤프가 있으면 그걸 쓰는 편이 맞고, 없으면 이 슬라이스 파이프라인을
키워서 mint 로그만 모은 뒤 메타데이터는 나중에 붙이면 된다.

| 확인 | 결과 |
| --- | --- |
| 계약 | Polygon `0xA4B37bE40F7b231Ee9574c4b16b7DDb7EAcDC99B` (tripleS), ARTMS `0x0fB69F54bA90f17578a59823E09e5a1f8F3FA200` |
| tripleS start block | `29388703` (`teamreflex/cosmo-db` `checkpoint/polygon`) |
| Subsquid gateway | `https://v2.archive.subsquid.io/network/polygon-mainnet/height` 는 살아 있음 (~96665814). **worker 요청은 2026-11 기준 403 `CREDENTIALS_INVALID`** — [portal.sqd.dev](https://portal.sqd.dev) API 키 필요 |
| 공개 RPC 로그 | QuickNode `rpc-mainnet.matic.quiknode.pro`, Tenderly `polygon.gateway.tenderly.co` 가 32M·60M·70M 구간을 반환. 창이 50_000 블록이면 HTTP 413 |
| Cosmo v1 `objekt/v1/token/{id}` | **503** |
| Cosmo v3 `bff/v3/objekts/nft-metadata/{id}` | 200. 시즌/멤버/컬렉션/클래스만. 카드 시리얼(`objektNo`) 없음 |
| 로컬 저장 | 이 VM의 Postgres `apollo-objekts`. 협업자 DB 아님 |

## 성공한 슬라이스 (블록 70_000_000–70_000_100)

Apollo `checkpoint/polygon` 인덱서를 클론한 뒤, SQD 게이트웨이 대신 RPC만
쓰도록 바꾸고 Cosmo v3 매퍼를 붙였다. Docker 없이 apt PostgreSQL 16.

프로세서 로그 (UTC 11:42, 한국시간 2026-11-03 20:42 KST):

- 데이터 소스: chain RPC
- 처리량: 약 6 blocks/sec, 38 items/sec
- 종료 높이: `70000100`

로컬 Postgres 집계:

| 테이블 | 행 |
| --- | ---: |
| objekt | 170 |
| transfer | 188 |
| collection | 156 |
| mint (`from=0x0`) | 89 (tripleS 80, ARTMS 9) |

알려진 토큰 `9203435` 는 `divine01-jiwoo-106z` 로 들어갔다. Cosmo v3 이름
`Divine01 JiWoo 106Z #9203435` 와 같다. `serial` 컬럼은 v3에 시리얼이 없어 0.

같은 구간을 `scripts/polygon-objekt-slice.py` 로도 재현한다. squid/Postgres
없이 Transfer JSONL만 뽑는다.

## 더 옛 구간

QuickNode 10_000블록 창:

| 구간 | Transfer |
| --- | ---: |
| 29_388_703–29_398_703 (start+10k) | 0 (배포 직후 민팅 없음) |
| 32_000_000–32_010_000 | 13, 전부 mint |
| 40_000_000–40_000_050 | 1 |
| 60_000_000–60_000_050 | 5 |
| 70_000_000–70_000_100 | 170 |

32M 첫 민트 예: 블록 `32000021`, 토큰 `173729`, Cosmo v3
`Atom01 YooYeon 100Z #173729`. 공개 RPC가 옛 로그를 잘라내지 않았다는 뜻이다.

## 전체 재인덱싱이 무거운 이유

대략 29.4M → 마이그레이션 직전 ~70.5M 이면 약 4천만 블록이다.

- `eth_getLogs` 를 10k 창으로 나누면 약 4천 요청. 빈 구간은 초 단위.
- 토큰 ID가 32M의 17만번대에서 70M의 920만번대까지 올라간다. 고유 토큰이
  수백만이면 Cosmo v3를 붙이는 시간이 병목이다.
- SQD 키가 있으면 로그 수집은 빨라지지만 메타데이터 문제는 남는다.
- 카드 시리얼은 이 API로는 복원되지 않는다. ever-minted 를 컬렉션 slug 단위
  개수로만 쓸 때는 traits로 충분하고, 시리얼 범위가 필요하면 Apollo 덤프나
  다른 인덱스가 필요하다.

그래서 **슬라이스 재인덱싱은 가능**, **전체 + 시리얼 복원은 덤프가 여전히
현실적**이다.

## 재현

협업자 DB 금지. 로컬에서만:

```bash
python3 scripts/polygon-objekt-slice.py --self-test
python3 scripts/polygon-objekt-slice.py \
  --from-block 70000000 --to-block 70000100 \
  --mints-only --cosmo --out /tmp/polygon-70m-mints.jsonl
```

20_000 블록을 넘는 구간은 `--i-understand-full-scan` 이 필요하다.

공식 인덱서를 쓰려면 `teamreflex/cosmo-db` 의 `checkpoint/polygon` 브랜치를
클론하고, `setGateway` 를 빼거나 SQD 키를 넣은 뒤 Cosmo URL을 v3로 바꾼다.
`ENABLE_GRAVITY=false`, `setBlockRange` 로 상한을 반드시 건다.

## 다음에 할 일

1. living persist 테이블은 별 PR. 이 실험 결과를 그 테이블에 바로 넣지 않는다.
2. 전체 스캔을 돌리기 전에 SQD 키 또는 Alchemy/Allnodes 아카이브 RPC를 정한다.
3. Apollo Discord 덤프가 생기면 `source=polygon-archive` 로만 대조한다.
