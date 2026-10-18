# triplesweb

tripleS 팬 커뮤니티 웹사이트 (Spring Boot 3.5 + Mustache + MariaDB).

## 문서

- **[프로젝트 구조 / 인수인계 가이드](docs/PROJECT_STRUCTURE.md)** — 스택, 레이어·URL 매핑, ERD·시퀀스
- **[도메인별 역할·기능·흐름](docs/DOMAINS.md)** — 회원 / 게시판 / tripleS / 미디어 / 아카이브 / 일정 / 샵 / 메인
- **[레이어 가이드](docs/LAYER_ARCHITECTURE.md)** — C→S→R→E는 권장(비강제). Must / Should / Optional
- PDF: [`docs/PROJECT_STRUCTURE.pdf`](docs/PROJECT_STRUCTURE.pdf) (생성: `python3 docs/md_to_pdf.py`)
- **[Pi Docker 배포 체크리스트](docs/DEPLOY_PI.md)** — Tailscale + GHCR + Secrets

---

## 케미명 데이터 추가 (GitHub 웹만으로)

페이지: `/triples/chemistry`  
로컬 프로그램·터미널 없이 **GitHub 웹사이트**에서 이미지 업로드 + 두 파일 수정으로 추가할 수 있습니다.  
`main`에 머지되면 Actions가 배포하며, 케미 이미지도 Pi `uploads/chemistry`로 동기화됩니다.

### 관련 파일

| 역할 | 경로 |
|------|------|
| 표(매트릭스) 셀 | [`src/main/resources/static/js/chemistry-matrix.json`](src/main/resources/static/js/chemistry-matrix.json) |
| 모달 설명·이미지·링크 | [`src/main/resources/static/js/chemistry.js`](src/main/resources/static/js/chemistry.js) 안의 `chemistryDescriptions` |
| 케미 이미지 | [`uploads/chemistry/chem/S{번호}/케미명.png`](uploads/chemistry/chem/) |

멤버 순서(행·열 인덱스, 0부터)는 다음과 같습니다.

| 인덱스 | S | 이름 | 인덱스 | S | 이름 |
|--------|---|------|--------|---|------|
| 0 | S1 | 윤서연 | 12 | S13 | 니엔 |
| 1 | S2 | 정혜린 | 13 | S14 | 박소현 |
| 2 | S3 | 이지우 | 14 | S15 | 신위 |
| 3 | S4 | 김채연 | 15 | S16 | 마유 |
| 4 | S5 | 김유연 | 16 | S17 | 린 |
| 5 | S6 | 김수민 | 17 | S18 | 주빈 |
| 6 | S7 | 김나경 | 18 | S19 | 정하연 |
| 7 | S8 | 공유빈 | 19 | S20 | 박시온 |
| 8 | S9 | 카에데 | 20 | S21 | 김채원 |
| 9 | S10 | 서다현 | 21 | S22 | 설린 |
| 10 | S11 | 코토네 | 22 | S23 | 서아 |
| 11 | S12 | 곽연지 | 23 | S24 | 지연 |

예: **윤서연 × 정혜린** → 행 `0`, 열 `1` (그리고 대칭으로 행 `1`, 열 `0`).

### 1. 이미지 올리기 (GitHub)

1. 레포에서 [`uploads/chemistry/chem/`](uploads/chemistry/chem/) 로 이동합니다.
2. 이미지가 들어갈 **S 폴더**를 엽니다.  
   - 관례: **S 번호가 더 작은 멤버** 폴더에 둡니다. (예: 윤서연·정혜린 → `S1/`)
3. **Add file → Upload files** 로 PNG를 올립니다.
4. 파일명은 **케미명과 동일**하게 맞춥니다. (예: `꼬꼬마즈.png`)
5. Commit 메시지 예: `케미 이미지 추가: 꼬꼬마즈`

폴더가 없으면 **Create new file**로 `uploads/chemistry/chem/S1/.gitkeep` 같은 파일을 만들어 폴더를 만든 뒤, 다시 Upload files를 쓰면 됩니다.

### 2. 표 셀 수정 (`chemistry-matrix.json`)

1. [`chemistry-matrix.json`](src/main/resources/static/js/chemistry-matrix.json) 을 연 뒤 **Edit this file**(연필 아이콘)을 누릅니다.
2. `matrix`는 `24 × 24` 배열입니다. `matrix[행][열]`을 찾아 `empty`를 케미 셀로 바꿉니다.

```json
{
  "type": "chem",
  "label": "꼬꼬마즈",
  "person": "윤서연",
  "key": "윤서연과 정혜린의 케미명",
  "cross": false
}
```

| 필드 | 설명 |
|------|------|
| `label` | 표에 보이는 이름. 두 줄이면 `"열일즈\\n햄햄다즈"` |
| `person` | 모달 데이터를 찾을 때 쓰는 **기준 멤버** (보통 행 쪽 이름) |
| `key` | `chemistry.js` 설명 객체의 키. 형식: `"멤버A과 멤버B의 케미명"` / `"멤버A와 멤버B의 케미명"` (조사 과/와는 기존 항목과 맞출 것) |
| `cross` | 특별한 강조가 필요하면 `true`, 보통은 `false` |

3. **대칭 셀**도 같이 채웁니다. (A×B와 B×A)  
   - B행 셀의 `person`은 B, `key`는 `"B과 A의 케미명"` 형태로 맞춥니다.
4. Commit 합니다.

대각선(`type: "self"`)은 건드리지 마세요.

### 3. 모달 설명 수정 (`chemistry.js`)

1. [`chemistry.js`](src/main/resources/static/js/chemistry.js) 를 **Edit** 합니다.
2. `chemistryDescriptions` 안에서 **기준 멤버** 객체에 항목을 추가합니다.

```js
"윤서연": {
  // ...기존 항목...
  "윤서연과 정혜린의 케미명": {
    description: "케미가 생긴 배경을 적습니다. 강조할 말은 **이렇게** 감싸면 링크/하이라이트가 됩니다. 따옴표 '강조어'도 별도 강조됩니다.",
    image: "/uploads/chemistry/chem/S1/꼬꼬마즈.png",
    links: {
      "이렇게": "https://youtu.be/예시"
    }
  }
}
```

| 필드 | 설명 |
|------|------|
| `description` | 본문. `<br>`은 넣지 않아도 됩니다(화면에서 자동으로 자연 줄바꿈). |
| `image` | `/uploads/chemistry/chem/S{n}/파일명.png` |
| `links` | (선택) `description`의 `**텍스트**`와 키가 같으면 하이퍼링크 |

3. 대칭 쪽(예: `"정혜린"` → `"정혜린과 윤서연의 케미명"`)에도 같은 `description` / `image`를 넣거나, 한쪽만 넣고 표를 한쪽 `person`만 쓰도록 통일해도 됩니다. **표 셀의 `person` + `key`가 가리키는 경로에 반드시 데이터가 있어야** 모달이 열립니다.
4. Commit 합니다. JSON 문법처럼 **쉼표**에 주의하세요(마지막 항목 뒤 쉼표, 중괄호 짝).

### 4. 저장·배포

- **Commit directly to `main`**: 푸시와 함께 Actions 배포가 돌아갑니다.
- 또는 **Create a new branch → Propose changes → Pull request** 로 리뷰 후 머지해도 됩니다.

배포 후 `/triples/chemistry`에서 셀을 눌러 이미지·설명이 나오는지 확인합니다.

### 자주 하는 실수

- 이미지 경로/파일명과 `image:` 문자열이 다름 (대소문자·확장자·S 폴더 번호)
- 표 `key`와 `chemistryDescriptions` 키가 一字라도 다름
- 대칭 셀만 채우고 설명 객체를 안 넣음 → 클릭 시 모달 데이터 없음
- `chemistry-matrix.json`을 깨진 JSON으로 저장 → 표 전체가 안 뜸 (GitHub에서 Commit 전에 괄호·쉼표 재확인)

## 빠른 시작 (로컬)

```bash
cp .env.example .env   # 최초 1회 — DB/API 값 채우기 (.gitignore)
./gradlew bootRun      # .env를 자동으로 읽어 export 불필요
```

브라우저: http://localhost:8080/main

`bootRun`은 프로젝트 루트 `.env`를 로드하고, `MYSQL_*`를 `SPRING_DATASOURCE_*`로 매핑합니다. Docker Compose도 같은 `.env`를 씁니다.

DB를 서버와 같이 쓰면 **파일도 같이** 보여야 합니다.

- **실시간:** 로컬 `bootRun` 업로드/삭제 시 Tailscale로 서버 `uploads/`에 push (기동 시 서버→로컬 pull). `.env`의 `UPLOAD_SYNC_*` 참고.
- **배포:** `uploads/` 전체가 커밋에 포함되면 Actions가 서버로 rsync. 배포 전후 `chown`·`chmod a+rwX`로 권한 충돌을 피합니다.

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
