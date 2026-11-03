# V2 미리보기 및 main 비교

브랜치: feature/ui-from-scratch-20261103. 운영 main과 데이터/배포를 공유하지 않는다.

## 미리보기 실행

1. 운영 디렉터리와 **다른 경로**에 새 브랜치 checkout.
2. cp .env.preview.example .env.preview 후 두 비밀번호 변경.
3. docker compose --env-file .env.preview -f docker-compose.preview.yml up -d --build
4. 같은 컴퓨터에서 http://127.0.0.1:8091 로 접속.
5. 원격 Tailscale 미리보기는 .env.preview 의 PREVIEW_BIND_IP 를 Pi의 Tailscale IPv4로 지정 후 recreate. Tailscale 접속 기기에서 http://<Pi Tailscale IP>:8091 로 접속.
6. 운영 버전은 실제 운영 포트(예: :8090)에서 열어 비교.
7. 종료: docker compose --env-file .env.preview -f docker-compose.preview.yml down
8. 테스트 데이터까지 삭제하려면 의도적으로 down -v 사용.

미리보기는 별도의 MariaDB/업로드/데이터 Docker volume을 사용한다. 운영 DB/업로드를 마운트하지 않으며 COSMO 토큰·백그라운드 인덱싱·업로드 동기화가 비활성이다. DB가 비어 있으므로 실데이터가 필요한 화면은 테스트용 가상 데이터 또는 비식별화된 복사본을 넣어야 한다. 기존 데이터와의 비교를 위해 운영 DB 쓰기 계정으로 직접 연결하지 말 것.

## 자동 캡처 비교

Playwright Chromium이 설치된 환경에서:
MAIN_URL=http://127.0.0.1:8090 V2_URL=http://127.0.0.1:8091 NODE_PATH=/path/to/node_modules node scripts/tests/v2-visual-compare.browser.cjs

모바일 390px/데스크톱 1280px, 주요 URL별 screenshots + report.json을 build/v2-compare에 생성. 페이지 에러, 가로 오버플로, HTTP 오류를 수집한다. 그룹 입문 /triples/overview는 계획된 미구현 페이지이므로 오류로 분류하지 않는다.

기능별 구형 CSS는 새 구현 완료 및 브라우저 검증 전까지 임시 호환 자산이다. 현재의 공통 v2 스킨이 적용되더라도 모든 화면의 독립 컴포넌트 재작성이 완료된 것은 아니다.


## GitHub Actions 정적 디자인 미리보기 (서버 없이 가능)

1. 디자인 브랜치에서 템플릿/CSS/JS가 변경되어 푸시되면 **V2 design preview screenshots** 작업이 자동 실행됨
2. GitHub > Actions > **V2 design preview screenshots** 에서 완료한 실행 선택
3. Artifacts에서 **triplesweb-v2-screen-previews** 다운로드
4. ZIP 압축을 풀고 index.html을 열면 주요 화면의 모바일(390px) / 데스크톱(1280px) 캡처를 한 페이지에서 비교 가능

이 방식은 Mock Mustache fixture의 HTML/CSS를 Chromium으로 렌더링하므로 서버·운영 DB·업로드/작성 기능은 사용하지 않는다. 외부 이미지가 없는 경우 빈 이미지가 나올 수 있다. 실제 기능 확인은 Docker 미리보기에서 해야 한다. Preview workflow는 디자인 브랜치의 화면 코드 변경 시에만 실행되며 main 자동 배포에는 영향을 주지 않는다. default 브랜치에 워크플로가 없다면 workflow_dispatch 수동 실행이 표시되지 않을 수 있다.
