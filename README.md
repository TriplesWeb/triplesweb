# triplesweb

tripleS 팬 커뮤니티 웹사이트 (Spring Boot 3.5 + Mustache + MariaDB).

## 문서

- **[프로젝트 구조 / 인수인계 가이드](docs/PROJECT_STRUCTURE.md)** — 스택, 레이어·URL 매핑, ERD·시퀀스
- **[도메인별 역할·기능·흐름](docs/DOMAINS.md)** — 회원 / 게시판 / tripleS / 미디어 / 아카이브 / 일정 / 샵 / 메인
- **[레이어 가이드](docs/LAYER_ARCHITECTURE.md)** — C→S→R→E는 권장(비강제). Must / Should / Optional
- PDF: [`docs/PROJECT_STRUCTURE.pdf`](docs/PROJECT_STRUCTURE.pdf) (생성: `python3 docs/md_to_pdf.py`)
- **[Pi Docker 배포 체크리스트](docs/DEPLOY_PI.md)** — Tailscale + GHCR + Secrets

---


## COSMO Gravity 연동

- 목록: `/cosmo/gravity` (기존 `/gravity` 경로도 호환)
- 상세: `/cosmo/gravity/{gravityId}`
- 로컬 `.env`에 본인 계정의 유효한 `COSMO_ACCESS_TOKEN`을 설정하면 서버가 COSMO API에서 목록·상세·후보 정보를 조회합니다.
- 그래비티 페이지는 tripleS 전용입니다. 공개 집계 목록은 `GRAVITY_APOLLO_TRIPLES_POLL_IDS` 또는 `gravity.apollo-triples-poll-ids`에 등록된 Poll ID만 허용합니다. 스크린샷에서 tripleS 그래비티 페이지의 공개 집계 ID로 확인된 `267`을 기본 허용 목록에 넣었습니다.
- 공개 집계 API 프록시: `GET /api/cosmo/gravity/aggregate?pollId={pollId}`. Apollo가 공개한 집계만 조회하며 투표/COMO 전송은 하지 않습니다. 목록에서 입력한 Poll ID는 tripleS 허용 목록과 대조하고, 허용되지 않은 ID는 거부합니다.
- 그래비티 상세 페이지(`/cosmo/gravity/{gravityId}`)는 tripleS 소속이 확인된 ID만 조회합니다. COSMO 토큰 없이 상세 페이지 집계를 연결하려면 `.env`의 `GRAVITY_APOLLO_TRIPLES_POLL_MAP` 또는 `gravity.apollo-triples-poll-map`에 검증된 `GravityID:PollID` 쌍을 쉼표로 구분해 등록해야 합니다. 기존 `233:274`(IDNTT 그래비티) 매핑은 제거했습니다. COSMO 토큰이 있으면 tripleS Gravity 목록에서 Poll ID 소속을 검증합니다.
- 후보별 득표/순위는 Apollo 응답의 `finalized.comoPerCandidate`가 있고 전체 투표가 공개된 경우에만 표시합니다. 최종 공개 전에는 개별 상위 투표 기록의 후보 ID도 화면에서 숨깁니다. 이 규칙은 tripleS 그래비티에만 적용됩니다.
- 후보별 최종 COMO 합계는 Apollo가 모든 투표 공개를 확인한 뒤에만 표시됩니다. 토큰이 없으면 후보명 대신 후보 번호를 표시합니다.
- 토큰은 만료될 수 있으므로 실제 값을 GitHub 저장소나 `.env.example`에 넣지 마세요. 현재 구성은 액세스 토큰 자동 갱신을 구현하지 않았습니다.
- 공개되지 않은 후보별 COMO 집계는 임의의 숫자로 채우지 않습니다. 토큰이 없거나 외부 API 요청이 실패하면 기존 아카이브 요약과 안내 메시지를 표시합니다.
- 이 연동은 조회 전용이며, 실제 투표나 COMO 전송을 실행하지 않습니다.

## 케미명 데이터

페이지: `/triples/chemistry`  
**데이터 소스는 MariaDB `Chemistry` 테이블**입니다. 프론트는 `GET /api/chemistry`만 사용합니다.

| 역할 | 경로 |
|------|------|
| 초기 시드(기존 정적 데이터) | [`src/main/resources/data/chemistry-seed.json`](src/main/resources/data/chemistry-seed.json) |
| API | `/api/chemistry` (`ChemistryApiController`) |
| 정적 케미 이미지 | [`uploads/chemistry/chem/`](uploads/chemistry/chem/) |
| 관리자 업로드 이미지 | `uploads/images/chemistry/` |

시드는 기본 비활성입니다. `CHEMISTRY_SEED_ENABLED=true`(또는 `chemistry.seed.enabled=true`)로 기동할 때만  
없는 페어를 삽입하며, 이미 있는 행은 덮어쓰지 않습니다. 삭제는 DB에서 **하드 삭제**합니다.

### 추가·수정 방법 (권장)

1. `.env`에 `CHEMISTRY_ADMIN_PASSWORD`를 설정합니다.
2. `/triples/chemistry`에서 **관리자** 로그인합니다.
3. 빈 칸(+)으로 추가하거나, 기존 셀을 열어 수정·삭제합니다.
4. 이미지는 편집 폼에서 업로드하거나, 기존처럼 `uploads/chemistry/chem/...` 경로를 쓸 수 있습니다.

DB에 활성 케미가 하나도 없으면 표 위에 **「등록된 케미명이 없어요」** 안내가 표시됩니다. (멤버 격자·대각선 S 심볼은 그대로 보입니다.)

## 빠른 시작 (로컬)

```bash
cp .env.example .env   # 최초 1회 — DB/API 값 채우기 (.gitignore)
./gradlew bootRun      # .env를 자동으로 읽어 export 불필요
```

브라우저: http://localhost:8080/main

`bootRun`은 프로젝트 루트 `.env`를 로드하고, `MYSQL_*`를 `SPRING_DATASOURCE_*`로 매핑합니다. Docker Compose도 같은 `.env`를 씁니다.

DB를 서버와 같이 쓰면 **파일도 같이** 보여야 합니다.

- **실시간:** 로컬 `bootRun` 업로드/삭제 시 Tailscale로 서버 `uploads/`에 push (기동 시 서버→로컬 pull). `.env`의 `UPLOAD_SYNC_*` 참고.
- **배포:** `uploads/`를 Actions가 서버로 rsync. `images/chemistry` 등 런타임 업로드 경로는 `--delete`에서 보호해 관리자 업로드가 지워지지 않게 합니다. 배포 전후 `chown`·`chmod a+rwX`로 권한 충돌을 피합니다.

```bash
./scripts/setup-git-hooks.sh   # pre-commit이 uploads를 자동 git add
```

자세한 실행·권한·체크리스트는 [프로젝트 구조 문서](docs/PROJECT_STRUCTURE.md)의 **§11 로컬 개발 빠른 시작**을 참고하세요.

---

## Raspberry Pi 4 Docker CI/CD (Tailscale SSH)

`main` 푸시 시 GitHub Actions가 **linux/arm64** 이미지를 빌드해 GHCR에 올리고, Tailscale으로 Pi에 SSH해 `docker compose pull && up`까지 자동 실행합니다.

```text
push → Actions (buildx arm64) → ghcr.io → Tailscale join → SSH → compose pull/up
```

### 1. GitHub Secrets

레포 **Settings → Secrets and variables → Actions**에 다음을 추가합니다.

| Secret | 설명 |
|--------|------|
| `TS_OAUTH_CLIENT_ID` | Tailscale OAuth client ID (`auth_keys` write + `tag:ci`) |
| `TS_OAUTH_SECRET` | Tailscale OAuth client secret |
| `PI_HOST` | Tailscale MagicDNS 또는 `100.x` 주소 (예: `raspberrypi.tailXXXX.ts.net`) |
| `PI_USER` | SSH 사용자 |
| `PI_SSH_KEY` | deploy 전용 **private** SSH 키 전체 |
| `PI_DEPLOY_PATH` | Pi의 compose 디렉터리 (`/opt/stacks/triplesweb`) |

Packages 쓰기는 워크플로의 `GITHUB_TOKEN` + `packages: write`로 처리합니다.  
Pi pull이 `unauthorized`면 (1) GHCR 패키지 visibility를 **Public**으로 바꾸거나 (2) Secret `GHCR_TOKEN`(PAT, `read:packages`)을 추가하세요. 배포 스텝이 Pi에서 `docker login` 후 pull합니다.

### 2. Tailscale ACL

OAuth client가 `tag:ci`로 ephemeral 노드를 만들고, 그 노드가 Pi의22로 붙을 수 있게 ACL을 둡니다. Pi에는 `tag:pi`(또는 기존 태그)를 붙입니다.

```json
{
  "tagOwners": {
    "tag:ci": ["autogroup:admin"],
    "tag:pi": ["autogroup:admin"]
  },
  "grants": [
    {
      "src": ["tag:ci"],
      "dst": ["tag:pi"],
      "ip": ["22"]
    }
  ]
}
```

기존 ACL 문법(`acls`)을 쓰는 경우에도 `tag:ci` → `tag:pi`:22 허용을 동일하게 추가하면 됩니다.

### 3. Pi 최초 1회 설정

64-bit Raspberry Pi OS + Docker + Compose + (이미 쓰는) Tailscale 기준입니다.

```bash
# Docker (미설치 시)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER"
# 재로그인 후 docker 확인

# 기존 stacks 패턴과 동일 경로
mkdir -p /opt/stacks/triplesweb/{uploads,data}
cd /opt/stacks/triplesweb

# 레포에서 docker-compose.yml, .env 복사 후
# .env: IMAGE, MYSQL_*, YOUTUBE_API_KEY, APP_PORT=8090
# (Pi에서 8080/8081은 vaultwarden 등이 사용 중)

# GHCR (패키지가 private인 경우)
echo "$GHCR_PAT" | docker login ghcr.io -u YOUR_GITHUB_USER --password-stdin

# GitHub Secret PI_SSH_KEY에는 Mac의 ~/.ssh/pi_ed25519 (또는 deploy 전용 키) 등록
```

첫 기동(이미지 푸시 전·후):

```bash
cd /opt/stacks/triplesweb
docker compose pull   # 이미지가 있을 때
docker compose up -d
```

앱: `http://<pi-lan-or-tailscale-ip>:8090/main`

### 4. 이후 자동 배포

1. `main`에 푸시 (또는 Actions에서 **Build and deploy to Raspberry Pi** 수동 실행)
2. Actions가 arm64 빌드 → GHCR 푸시 → Tailscale 조인 → `PI_HOST`로 SSH
3. Pi에서 `IMAGE` 갱신 후 `docker compose pull app && up -d`

워크플로: [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml)

### 5. 로컬에서 compose만 쓸 때

```bash
cp .env.example .env
# IMAGE / DB / API 키 채우기
docker compose up -d --build   # 로컬 아키텍처용으로 Dockerfile 직접 빌드할 때는
# 또는 이미지만: docker compose up -d
```

Pi(arm64) 배포 이미지는 CI가 빌드하므로, Intel/Apple Silicon Mac에서 `--platform linux/arm64` 없이 로컬 빌드하면 아키텍처가 다를 수 있습니다.
