# triplesweb

tripleS 팬 커뮤니티 웹사이트 (Spring Boot 3.5 + Mustache + MariaDB).

## 문서

- **[프로젝트 구조 / 인수인계 가이드](docs/PROJECT_STRUCTURE.md)** — 스택, 레이어·URL 매핑, ERD·시퀀스
- **[도메인별 역할·기능·흐름](docs/DOMAINS.md)** — 회원 / 게시판 / tripleS / 미디어 / 아카이브 / 일정 / 샵 / 메인
- **[레이어 가이드](docs/LAYER_ARCHITECTURE.md)** — C→S→R→E는 권장(비강제). Must / Should / Optional
- **[UI 디자인 시스템 기준선](docs/UI_DESIGN_BASELINE.md)** — Tailwind 4 + daisyUI 빌드·토큰
- **[레이아웃 partial 규칙](src/main/resources/templates/layout/README.md)** — head / 토큰 / daisyUI 사용법
- PDF: [`docs/PROJECT_STRUCTURE.pdf`](docs/PROJECT_STRUCTURE.pdf) (생성: `python3 docs/md_to_pdf.py`)
- **[Pi Docker 배포 체크리스트](docs/DEPLOY_PI.md)** — Tailscale + GHCR + Secrets

## 프론트 CSS 빌드

템플릿·토큰(`scripts/css/tokens.css`)을 바꾼 뒤에는 정적 CSS를 다시 생성합니다. CI와 Docker 이미지 빌드에도 동일 명령이 들어 있습니다.

```bash
./scripts/setup-css.sh          # npm ci + build:css + bootRun (로컬)
./scripts/setup-css.sh --no-run # CSS만 (CI package / Docker)
# 또는
npm run setup:css
npm run dev                     # 동일

./scripts/setup-css.sh --check  # CI test: 빌드 + drift 검사 (서버 미기동)
```

CI PR(`ci-tests.yml`)은 `--check` / `--no-run`, main 배포 이미지(`Dockerfile`)는 `--no-run --skip-install`로 동일 스크립트를 씁니다.

수동 단계: `npm ci` → `npm run build:css` (site / triples / objekt / gravity) → `./gradlew bootRun`.

---

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
./gradlew bootRun      # IntelliJ 메인 클래스 실행과 동일하게 .env를 자동으로 읽음
```

브라우저: http://localhost:8080/main

`bootRun`과 IntelliJ의 메인 클래스 실행 모두 Spring 시작 단계에서 프로젝트 루트 `.env`를 로드하고, `MYSQL_*`를 `SPRING_DATASOURCE_*`로 매핑합니다. 별도 dotenv 플러그인은 필요 없습니다. 명령행·JVM 옵션·실제 환경변수의 같은 설정이 `.env`보다 우선합니다. Docker Compose도 같은 `.env`를 씁니다.

### IntelliJ에서 운영과 같은 DB로 실행

1. 프로젝트 SDK와 Gradle JVM을 **JDK 17**로 지정하고 Gradle 프로젝트를 새로고침합니다.
2. 프로젝트 루트에 `.env.example`을 복사한 `.env`를 두고, 운영 DB의 `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`를 입력합니다. 값에 공백이나 `#`이 들어가면 전체를 따옴표로 감쌉니다.
3. PC의 Tailscale에 로그인하고 서버와 같은 Tailnet에 연결합니다.
4. 공유 실행 구성 **tripleS (공유 DB)**를 선택해 실행합니다. 기존 실행 구성을 사용한다면 메인 클래스는 `com.example.triplesweb.triplesWebApplication`, 작업 디렉터리는 프로젝트 루트로 지정합니다. 기본 URL은 http://localhost:8080/main 입니다.

`.env`가 있으면 기본 프로파일은 `local`이고, `SPRING_PROFILES_ACTIVE`를 지정하면 그 설정이 우선합니다. Docker는 기존 환경변수 설정을 사용하며, `.env`가 없으면 이 로더는 설정을 바꾸지 않습니다.

#### DB 연결 시간 초과가 발생할 때

`SocketTimeoutException: Connect timed out`이 Flyway 초기화에서 발생하면 먼저 **PC → 운영 DB의 TCP 연결**을 확인합니다. Flyway를 끄거나 Hibernate로 운영 스키마를 강제로 생성해서 해결하지 않습니다.

Windows PowerShell:

```powershell
tailscale status
tailscale ping <MYSQL_HOST의 Tailscale 주소>
Test-NetConnection <MYSQL_HOST의 Tailscale 주소> -Port 3306
```

macOS/Linux:

```bash
tailscale status
tailscale ping <MYSQL_HOST의 Tailscale 주소>
nc -vz -w 5 <MYSQL_HOST의 Tailscale 주소> 3306
```

- Windows에서 `401 Unauthorized: Tailscale already in use by ...`가 나오면 다른 Windows 사용자가 Tailscale 설정을 소유한 상태입니다. 이 오류는 관리 명령의 권한 오류이므로, DB 접근 여부는 `Test-NetConnection` 결과로 별도 확인합니다. 소유한 사용자 세션에서 연결 Tailnet을 확인하고, 필요하면 해당 세션에서 Tailscale을 종료한 뒤 현재 사용자로 다시 연결합니다. 무인 모드가 켜져 있다면 소유한 사용자만 설정을 변경할 수 있습니다. [공식 안내](https://tailscale.com/docs/how-to/run-unattended#troubleshooting)를 참고합니다.
- Tailscale ping이 실패하면 로그인 상태, 서버 온라인 상태, Tailnet 접근 권한을 확인합니다.
- ping은 되지만 TCP 3306이 실패하면 서버 방화벽/Tailscale ACL, MariaDB 컨테이너 실행 상태와 포트 공개 설정을 확인합니다. DB 포트가 다르면 위 명령의 포트도 변경합니다.
- TCP 연결은 되지만 `Access denied`가 나오면 `.env`의 DB 계정과 해당 계정의 접속 허용 범위를 확인합니다.
- IntelliJ 실행 구성에 예전 `SPRING_DATASOURCE_URL`이나 VM 옵션 `-Dspring.datasource.url=...`이 남아 있으면 `.env`보다 먼저 적용되므로 함께 확인합니다.

DB를 서버와 같이 쓰면 **파일도 같이** 보여야 합니다.

- **실시간:** 로컬 `bootRun` 업로드/삭제 시 Tailscale로 서버 `uploads/`에 push (기동 시 서버→로컬 pull). `.env`의 `UPLOAD_SYNC_*` 참고.
- **참조만:** 로컬 `./uploads`에 없으면 `upload.fallback-base-url`(기본 `https://triples.eht3r.cloud`)로 **리다이렉트**합니다. 공유 DB를 쓰는 협업자 PC에서도 멤버·유닛 이미지가 보입니다. 서버 Docker는 폴백을 비웁니다.
- **운영 배포:** GitHub Actions에서 관리하는 `uploads/chemistry/`만 증분 rsync로 동기화합니다. 전체 `uploads/`에 대한 삭제·재귀 chown/chmod는 수행하지 않습니다.

```bash
./scripts/setup-git-hooks.sh   # pre-commit이 uploads를 자동 git add
```

자세한 실행·권한·체크리스트는 [프로젝트 구조 문서](docs/PROJECT_STRUCTURE.md)의 **§11 로컬 개발 빠른 시작**을 참고하세요.

---

## Raspberry Pi 4 Docker CI/CD (Tailscale SSH)

Pi에는 **MariaDB 하나와 Nginx 프록시(8090), 활성 운영 앱 하나**를 실행합니다. 배포하는 동안에만 새 Blue/Green 앱 슬롯을 함께 기동해 정상 상태를 확인한 뒤 트래픽을 전환합니다. PR에서는 CI 검사만 수행하며, `main` 머지 후 운영을 배포합니다. **처음 Nginx로 전환할 때에는 짧은 중단이 발생할 수 있습니다.** [Blue-Green 운영/롤백 가이드](docs/BLUE_GREEN_DEPLOYMENT.md)를 먼저 확인하세요.

```text
PR open/push    → CI tests (Java/WAR, CSS/브라우저, Qodana) → 통과 시 머지 가능
main 머지       → 새 비활성 앱 예열 → readiness 확인 → Nginx reload(:8090) → 구버전 종료
매일 04:00 KST / 수동 → 동일 운영 상태라면 빌드·재시작 생략
```

### 1. GitHub Secrets / Variables

레포 **Settings → Secrets and variables → Actions**에 다음을 추가합니다.

| Secret | 설명 |
|--------|------|
| `TS_OAUTH_CLIENT_ID` | Tailscale OAuth client ID (`auth_keys` write + `tag:ci`) |
| `TS_OAUTH_SECRET` | Tailscale OAuth client secret |
| `PI_HOST` | Tailscale MagicDNS 또는 `100.x` 주소 (예: `raspberrypi.tailXXXX.ts.net`) |
| `PI_USER` | SSH 사용자 |
| `PI_SSH_KEY` | deploy 전용 **private** SSH 키 전체 |
| `PI_DEPLOY_PATH` | 운영 compose 디렉터리 (`/opt/stacks/triplesweb`) |
| `PI_DEV_DEPLOY_PATH` | 선택. **기존 개발 앱 종료용** 경로가 기본값과 다를 때만 지정 |

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

# 운영
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
# 기존 단일 앱 최초 설치(Blue-Green 이전)에만 사용
cd /opt/stacks/triplesweb && docker compose --profile legacy pull app && docker compose --profile legacy up -d app mariadb
# Blue-Green 전환 후에는 일반 docker compose up/down 대신 Actions 배포만 사용
```

- 운영: `http://<pi-lan-or-tailscale-ip>:8090/main`

### 4. 이후 자동 배포

1. **PR 검사:** [ci-tests.yml](.github/workflows/ci-tests.yml)이 Java 테스트+WAR와 HTML 의존 브라우저 검사를 한 Job에서 실행하고, CSS/JavaScript 검사와 Qodana를 병렬 실행합니다.
2. **운영:** 머지 즉시 [deploy-main.yml](.github/workflows/deploy-main.yml) 실행. 신규 앱을 준비해 Blue/Green 전환하며, 새벽 **04:00 KST** 재확인과 수동 배포도 지원합니다. 스키마 변경·메모리 부족 시 기존 앱을 유지하고 배포에 실패합니다.
3. **개발 서버 폐지:** 첫 운영 배포에서 기존 Pi 개발 앱만 종료합니다. 개발 데이터는 삭제하지 않습니다.

체크리스트: [docs/DEPLOY_PI.md](docs/DEPLOY_PI.md)

PR 머지 게이트: Ruleset **`main-ci`**의 required status check **`required`**
(`CI tests`의 배포 설정 검사 + Java/WAR + 브라우저/CSS + Qodana 집계).

### 5. 로컬에서 compose만 쓸 때

```bash
cp .env.example .env
# IMAGE / DB / API 키 채우기
docker compose --profile legacy up -d app mariadb  # 로컬 테스트/기존 단일 앱에서만
# 운영 Blue-Green 전환 후에는 legacy 앱을 시작하지 마세요.
```

Pi(arm64) 배포 이미지는 main 머지 후 운영 배포 Actions가 빌드하므로, Intel/Apple Silicon Mac에서 `--platform linux/arm64` 없이 로컬 빌드하면 아키텍처가 다를 수 있습니다.
