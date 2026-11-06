# PR #58 모바일 임시 미리보기

개발 환경이 실행되는 동안 PR #58을 30초마다 조회하고, 빌드·검사를 통과한 최신 커밋을 같은 미리보기 주소에 반영합니다. 운영 배포, PR 푸시·머지, 환경 게시, 유료 자원 생성은 하지 않습니다.

## 준비와 실행

Node 20 이상, Python 3.12 이상, JDK 17, 로컬 Docker 소켓이 필요합니다. 기존 클라우드 환경에서는 먼저 `source /workspace/.onboarding/env.sh`로 JDK·Gradle·Java 프록시 신뢰 설정을 활성화합니다.

```bash
bash scripts/preview/setup.sh
/workspace/.onboarding/pr58-preview-tools/bin/preview preflight
/workspace/.onboarding/pr58-preview-tools/bin/preview run
```

환경 설정에 `PREVIEW_ACCESS_PASSWORD`를 12자 이상으로 직접 입력합니다. 채팅·커밋·명령행에 비밀번호를 넣지 않습니다. 이 비밀번호는 미리보기 로그인과 복사본의 기존 관리자 로그인에 사용하며 운영 비밀번호를 가져오지 않습니다.

현재 공개 연결 후보는 Tunnelmole 프로토콜입니다. `service.tunnelmole.com`의 HTTPS/WebSocket 8083 포트와 `*.tunnelmole.net`, 원본 파일을 읽을 `triples.eht3r.cloud`의 접근이 필요합니다. 프록시 환경변수와 인증서 검증을 유지합니다. 허용 목록만으로 WebSocket 지원이 입증되지는 않습니다. 실제 외부 URL이 이 게이트웨이의 health ID를 반환해야 앱을 공개합니다. 403이면 공개 단계를 중단하며 다른 경로로 우회하지 않습니다.

`run --local-only`는 루프백 내부 검증용입니다. 휴대폰 링크를 만들지 않습니다. `init-data`는 독립 데이터 복사만 실행하고, `status`는 저장된 상태를 출력합니다. `Ctrl+C`로 감독·앱·게이트웨이를 종료합니다. 복사 DB 컨테이너는 유지되며 필요하면 해당 상태 경로에서 생성된 이름의 컨테이너만 종료합니다.

## 데이터 격리와 갱신

- 상태 기본 경로는 `/workspace/.onboarding/pr58-preview`이며 저장소 밖이어야 합니다. 디렉터리는 0700, 덤프와 생성된 로컬 DB 비밀번호는 0600입니다. 운영 데이터 덤프를 저장소에 추가하지 않습니다.
- 원본 접속 설정은 기존 애플리케이션의 datasource 바인딩을 사용합니다. 필요할 때만 `PREVIEW_SOURCE_DB_HOST`, `PREVIEW_SOURCE_DB_PORT`, `PREVIEW_SOURCE_DB_NAME`, `PREVIEW_SOURCE_DB_USER`, `PREVIEW_SOURCE_DB_PASSWORD`로 별도 읽기 계정을 설정합니다. 실제 비밀번호를 로그에 출력하지 않습니다.
- 원본 MariaDB는 관리형 `proxy:8088` CONNECT를 통해 읽기 전용 일관성 트랜잭션으로 접근합니다. 원본에는 INSERT·UPDATE·DDL·테이블 잠금을 보내지 않습니다. SQL 덤프는 기본 테이블·뷰·데이터를 포함하며 운영 이벤트·트리거·프로시저는 실행하거나 복사하지 않습니다.
- 로컬 MariaDB 11.4는 별도 이름·볼륨·DB를 사용하고 `127.0.0.1:23306`에만 바인딩합니다. Spring은 이 DB와 상태 폴더의 uploads만 사용합니다. 운영으로의 업로드 동기화·원본 폴백은 비활성화합니다.
- 처음 한 번만 실제 DB를 복사합니다. 저장소 uploads, 복사된 upload_blob, DB가 참조한 원본 파일을 사용합니다. 원본 파일을 못 가져오면 `missing-files.json`을 남기고 준비 완료로 표시하지 않습니다. 운영 파일의 동시 변경까지 원자적으로 스냅샷한다는 보장은 없습니다.
- 다음 커밋은 기존 미리보기 DB와 파일을 이어서 사용합니다. 빌드는 현재 서비스를 유지한 채 별도 디렉터리에서 수행합니다. 전환 때 새 요청을 잠시 막고 기존 요청이 끝나면 앱을 종료하고 DB·파일 백업을 만듭니다. 시작 실패나 더 최신 커밋 발견 시 이전 버전과 데이터를 복구합니다. 복구 자체가 실패하면 점검 상태를 유지합니다.
- 재시작 시 미완료 배포 기록을 먼저 복구합니다. 검사 실패한 커밋은 5분 후 재시도하며 더 최신 커밋은 즉시 다음 감지 주기에 처리합니다. 임시 연결이 끊겨 주소가 바뀌면 새 주소를 외부 검증할 때까지 앱 접근을 다시 막습니다.

## 접속과 제한

로그인 후 `/_preview`에서 사이트와 버전 상태를 확인합니다. `GET /_preview/status`는 실행 커밋·감지 커밋·단계·실패 요약을 반환하며 인증이 필요합니다. 로그인 쿠키는 앱에 전달하지 않고 기존 앱 세션·CSRF·요청 본문과 파일은 보존합니다. 세션은 8시간이며 게이트웨이가 재시작되면 다시 로그인합니다.

터널 어댑터는 전체 요청/응답을 버퍼링하며 각각 **64 MiB**로 제한합니다. 1GB 파일과 10GB multipart 설정을 가진 원래 앱의 전송 용량을 보장하지 않습니다. Range는 전달하지만 SSE·WebSocket 앱 기능은 지원하지 않습니다. 실제 외부 터널에서 파일·쿠키·메서드·Range 검증을 마쳐야 외부 지원으로 표시할 수 있습니다.

운영 COSMO 토큰·자격 파일과 YouTube 키는 상속하지 않습니다. 해당 연동이 필요하면 미리보기용 `PREVIEW_COSMO_ACCESS_TOKEN`, `PREVIEW_COSMO_REFRESH_TOKEN`, `PREVIEW_COSMO_KEY`, `PREVIEW_YOUTUBE_API_KEY`를 별도로 설정합니다. 계정 연동은 로컬 DB 복사만으로 제공되지 않습니다.

## 검증

```bash
npm --prefix scripts/preview test
/workspace/.onboarding/pr58-preview-tools/venv/bin/python -m unittest discover -s scripts/preview/test -p 'test_*.py' -v
PREVIEW_DB_TESTS=1 /workspace/.onboarding/pr58-preview-tools/venv/bin/python -m unittest discover -s scripts/preview/test -p 'test_database.py' -v
# Supervisor.build가 만든, built.json을 가진 PR 릴리스 경로를 지정합니다.
PREVIEW_APP_TESTS=1 PREVIEW_TEST_RELEASE=/path/to/built/pr-commit /workspace/.onboarding/pr58-preview-tools/venv/bin/python -m unittest discover -s scripts/preview/test -p 'test_app.py' -v
```

DB 통합 검사는 임시 로컬 컨테이너와 볼륨에서 생성 컬럼·바이너리·한글·뷰의 복사와 복원을 검증한 뒤 자신이 만든 자원만 삭제합니다. 네트워크 차단·원본 DB 장애·미설정 비밀번호를 실제 기능 성공으로 취급하지 않습니다.

WAR 검사는 운영 원본 대신 ORM으로 만든 현재 스키마와 Flyway 기준선의 로컬 fixture를 사용합니다. 이 저장소의 기존 마이그레이션은 빈 DB의 최초 생성용이 아니므로 실제 서비스에서는 원본 스키마와 Flyway 이력을 함께 복사해야 합니다. 원본 복사가 안 될 때 빈 DB나 테스트 DB로 몰래 대체하지 않습니다.

터널 프로토콜 참고: [Tunnelmole MIT 클라이언트](https://github.com/robbie-cahill/tunnelmole-client). 이 어댑터는 원본 클라이언트의 사용자 홈 쓰기나 직접 외부 연결 대신 환경의 상태 폴더와 명시적인 HTTPS 프록시를 사용합니다.
