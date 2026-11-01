# UI 디자인 시스템 기준선 (Phase 0)

- 기록일: 2026-11-01
- 목적: Tailwind 빌드 통일·토큰·daisyUI 도입 전후 비교용

## 생성 CSS 용량

| 파일 | bytes |
|------|------:|
| site.css | 53252 |
| triples.css | 120456 |
| objekt.css | 29525 |
| gravity.css | 41867 |
| site-ui.css | 7462 |
| site-header.css | 8589 |
| site-mobile.css | 11364 |
| style.css (핸드 소스) | 14002 |

## 빌드

- Tailwind 3.4.17, 스크립트 4개 (`build-site/triples/objekt/gravity-css.cjs`)
- `package.json` 없음 → `npx --package=tailwindcss@3.4.17` 임시 설치
- CDN Tailwind: 템플릿에 없음 (`GravityControllerTest` 검증)

## Playwright 회귀 (CI)

- `site-header.browser.cjs`
- `objekt-account-ui`, `objekt-modal`, `objekt-mobile`, `objekt-artist-gravity`
- `objekt-member-selection`, `objekt-season-schedule`, `profile-board`

## 임의 hex 클래스 빈도 (템플릿, 상위)

| 클래스 조각 | 대략 횟수 |
|-------------|----------:|
| text-[#b9cac4] | 62 |
| border-[#3a4a46] | 46 |
| text-[#00f5d4] | 43 |
| text-[#e3e2e8] | 40 |
| bg-[#0d0e12] | 22 |
| bg-[#00f5d4] | 17 |
| border-[#00f5d4] | 13 |
| bg-[#16181f] | 12 |

## 화면 스냅샷

로컬 서버 기동 후 수동 비교용 경로:

- `/main`, `/triples/artist`, `/triples/album`, `/triples/chemistry`
- `/cosmo/objekt`, `/cosmo/gravity`, `/article/info`, `/shop`

## Phase 1–4 적용 후 (동일 일자)

| 파일 | before (bytes) | after (bytes) |
|------|---------------:|--------------:|
| site.css | 53252 | ~128672 |
| triples.css | 120456 | ~215677 |
| objekt.css | 29525 | ~128518 |
| gravity.css | 41867 | ~143617 |

용량 증가는 Tailwind 4 유틸 + daisyUI(선택된 컴포넌트·테마) 포함 때문. CDN Play는 사용하지 않으며 CI는 `./scripts/setup-css.sh --check` / `--no-run`, Docker는 `--no-run --skip-install`로 WAR에 포함한다.

### 운영 명령

```bash
./scripts/setup-css.sh                        # npm ci + 4개 번들 + bootRun
./scripts/setup-css.sh --no-run               # CSS만 (CI package)
./scripts/setup-css.sh --check                # CI test drift
./scripts/setup-css.sh --no-run --skip-install # Docker css stage
npm run setup:css                             # 동일 (package.json)
npm run check:css                # 재빌드 + drift 검사(로컬)
```
