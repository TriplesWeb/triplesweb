# COSMO Gravity 연결

현재 `/cosmo/gravity`와 `/gravity`는 같은 실제 API 목록을 표시한다.
`/cosmo/gravity/{gravityId}?pollId={pollId}`는 COSMO 공식 결과만 직접 조회해 표시한다.
Gravity ID, COSMO poll ID, 온체인 poll ID는 서로 다를 수 있다.
정적 데모 수치와 가상의 과거 ID 매핑은 제거했다.

읽기 전용 JSON API:
- `GET /api/gravity?artistId=tripleS` (artms, idntt도 지원)
- `GET /api/gravity/{gravityId}`
- `GET /api/gravity/polls/{pollId}`
- `GET /api/gravity/polls/{pollId}/aggregated` (종료된 기능: 외부 호출 없이 410 반환)

입력 오류는 400, 없는 upstream 항목은 404, 인증·설정 미완료는 503,
그 밖의 외부 연결 오류는 502로 처리한다. 토큰 등록·갱신은 공개 API로 제공하지 않는다.

## 초기 인증 (운영자)

이제 토큰을 다른 프로그램에서 복사할 필요 없이 `/cosmo/gravity/connect`에서 연결할 수 있다.

1. 상단 `관리자` 버튼을 눌러 케미명 페이지와 같은 비밀번호 모달에서 로그인한다 (`CHEMISTRY_ADMIN_PASSWORD`).
   케미명 페이지에서 이미 로그인했다면 세션·토큰을 공유하므로 재입력이 필요 없다.
   관리자에게만 `COSMO 계정 연결` 버튼이 표시되고 로그아웃도 케미명과 함께 적용된다.
2. 본인이 사용하는 COSMO 등록 이메일로 인증번호를 요청한다.
3. 이메일로 받은 6자리 인증번호를 입력한다.
4. 서버가 Privy 세션을 COSMO access/refresh token으로 교환하고 서버 전용 파일에 저장한다.
   그 뒤의 Gravity 조회는 저장된 access token을 쓰며 만료 시 자동 갱신한다.

인증번호 전송·검증은 관리자만 실행할 수 있다. 인증 요청은 세션에 묶이고 10분 후 만료한다.
재전송은 60초 간격, 검증은 요청당 최대 5회다. 성공한 인증번호 요청은 재사용할 수 없다.
브라우저에는 사이트 관리자 토큰만 전달되며 COSMO/Privy 토큰은 전달하지 않는다.
비밀번호·인증번호는 로그에 기록하거나 파일로 저장하지 않는다.

실제 최초 인증은 운영자가 이메일 인증을 완료해야 한다. 웹 프로젝트의 QR 로그인은
쇼핑몰 세션 인증용이며 Gravity Bearer 토큰과 다르므로, cosmo-tui에서 확인한
Privy 이메일 OTP → `/users/login-by-privy` 방식을 독립적으로 구현했다.
이메일 인증의 `login-or-sign-up` 모드는 참조 클라이언트와 같다. 기존 COSMO 등록 이메일을 사용한다.
지갑·개인키·투표·이체 기능은 사용하지 않는다.

COSMO 공개 앱 프로토콜의 Privy app ID와 AES 키를 기본값으로 사용한다.
이 값은 개인 계정 토큰이 아니며 자체 생성한 랜덤 키로 대체할 수 없다.
앱 규약이 변경되면 `COSMO_PRIVY_APP_ID`, `COSMO_KEY` 환경 변수로 새 값을 지정할 수 있다.
Privy가 추가 인증을 요구하거나 요청을 거절하면 실패를 표시하며, 이를 우회하지 않는다.

대안으로 `.env`에 `COSMO_ACCESS_TOKEN`, `COSMO_REFRESH_TOKEN`을 설정하거나 다음 가져오기 도구를 쓸 수 있다.

```sh
python3 scripts/setup-cosmo.py --from-file "$HOME/.config/cosmo-tui/auth.json"
```

같은 refresh token을 다른 클라이언트와 동시에 갱신하지 않는다. 인증 파일이 있으면 환경 변수보다 우선한다.

## Raspberry Pi / Compose

- 운영 호스트의 기존 `./data` 볼륨 안에 `cosmo/credentials.json`을 배치한다.
  `scripts/setup-cosmo.py --output /실제배포경로/data/cosmo/credentials.json`을 쓸 수 있다.
- 파일 권한은 600, 폴더는 운영 계정과 컨테이너만 접근 가능하게 설정한다.
- 새 코드 배포 후 `/cosmo/gravity/connect`에서 관리자가 이메일 인증을 완료한다.
  COSMO가 공개 프로토콜 키를 바꾼 경우에만 운영 `.env`에 `COSMO_KEY`를 지정한다.
  Compose는 인증 파일 위치를 `/app/data/cosmo/credentials.json`으로 고정한다.
- `data/cosmo/`와 `.env`는 Git에서 제외된다. `uploads` 아래에는 절대 저장하지 않는다.
- 갱신된 토큰 쌍은 같은 파일에 원자적으로 저장되어 컨테이너 재시작 후에도 유지된다.
  현재 구현은 앱 인스턴스 1개를 전제로 한다. 여러 인스턴스라면 공유 저장소와 분산 잠금이 필요하다.
- 토큰 파일을 수동 교체할 때는 앱을 중지하고 교체한 후 재시작한다. 실행 중인 앱은 메모리 값을 사용한다.

## 동작 및 오류

- access token 만료 60초 전부터 refresh를 수행하며, 동시에 들어온 요청은 한 번만 갱신한다.
- 인증된 GET에서 401이면 한 번 갱신 후 재시도한다. 403이나 무한 재시도는 하지 않는다.
- 갱신: `POST /bff/v3/users/refresh-access-token`, AES-256-CBC, PKCS7,
  Base64(16-byte IV + ciphertext), `Content-Type: text/plain`, `x-cosmo-encrypted: 1`.
  암호화 응답도 처리한다. JWT exp는 갱신 시점 판단에만 쓰며 계정 인증은 COSMO가 수행한다.
- 토큰·암호화 요청/응답을 HTML, JSON API, 로그에 출력하지 않는다.
- COSMO 미설정·만료·외부 API 장애는 안내 문구로 표시한다. 가상의 결과를 대신 표시하지 않는다.
- Apollo 요청·주소 설정과 목록의 집계 조회 폼을 제거했다. 상세 페이지는 COSMO 그래비티 조회 한 번으로 공식 순위를 렌더링한다.
- 확인한 COSMO 조회 응답은 최종 결과 및 후보 정보이며 시간별 투표 내역이 없다. 시간대별 소비량·실시간 후보 누적선은 자체 온체인 수집/인덱싱이 필요하며 아직 구현하지 않았다. 미제공 데이터는 안내만 표시하고 최종 합계로 과거 시계열을 만들지 않는다.

## 참고한 프로토콜

- https://codeberg.org/djvu/cosmo-tui (참조 커밋 89ab02737d5ce8f16ac3dab9e8e5aab6f9de476e)
- https://github.com/teamreflex/cosmo-web/tree/main/packages/cosmo/src/server
- https://github.com/teamreflex/cosmo-web/blob/main/packages/cosmo/src/schema/gravity.ts

참조 프로젝트의 구현을 의존성으로 포함하지 않고 HTTP 규약에 맞춰 Java로 구현했다.
공개 앱 식별자와 프로토콜 상수는 호환성을 위해 사용한다.
실제 계정 인증 API 성공 검증은 운영자가 본인 이메일 인증을 완료한 뒤 수행해야 한다.

## Gravity 화면 리소스

Gravity 목록은 tripleS 전용이다. 외부 Tailwind 런타임 대신 미리 생성한
`src/main/resources/static/css/gravity.css`와 자체 제공 아이콘 폰트를 사용한다.
계정 연결 버튼은 목록 상단에 있다.

헤더 또는 Gravity 화면의 클래스 수정 후 저장소 루트에서
`node scripts/build-gravity-css.cjs`로 정적 CSS를 다시 생성한다.
Tailwind 3.4.17을 고정해 사용하며, 생성된 CSS도 함께 커밋한다.
공통 아이콘 부분집합 폰트의 라이선스는 `static/fonts/gravity-icons.LICENSE`에 보관한다.

관리자 비밀번호 오류(HTTP 401)는 `비밀번호가 올바르지 않습니다.`로 표시한다.
실제 네트워크 연결 실패는 별도 한국어 안내로 표시하며 브라우저 원문 오류를 노출하지 않는다.

### 이메일 인증 접근 거부

외부 인증 서버의 403은 로컬 관리자 로그인 실패와 다르다. 도메인 제한으로 단정하지 않는다. `cosmo-tui/internal/cosmo/login.go`와 동일한 Privy 앱 ID·클라이언트·Origin 및 `User-Agent: okhttp/4.9.2`를 사용한다. TUI는 기본 Go/urllib 클라이언트가 Cloudflare에 차단될 수 있다고 설명한다. Origin은 `https://auth.privy.io`이며 공식 Gravity 도메인으로 바꾸지 않는다.

실패 응답은 JSON·HTML·기타로 구분한다. JSON 오류 코드는 사전에 정의한 코드만 화면과 로그에 남긴다. 로그는 고정 API 경로·상태·응답 유형·허용된 코드만 포함하고 이메일, 인증번호, 토큰, 원문 응답은 포함하지 않는다. HTML 403은 HTML 차단 응답으로 안내하되 Cloudflare라고 단정하지 않는다. 클라이언트 설정 변경만으로 실제 인증 성공을 보장하지 않으며 이메일 수신과 토큰 교환은 본인 계정으로 검증해야 한다.

참고: https://codeberg.org/djvu/cosmo-tui/src/branch/master/internal/cosmo/login.go
COSMO Web의 QR 인증은 별도 `user-session` 쿠키를 발급하는 경로이므로 이메일 인증 토큰 발급과 구분한다: https://github.com/teamreflex/cosmo-web/blob/main/packages/cosmo/src/server/qr-auth.ts

발송 실패는 인증 challenge와 60초 재전송 대기를 생성하지 않는다. 발송 성공 뒤에만 대기를 적용하고 화면에서 남은 시간을 표시한다. 로컬 재전송 제한은 429와 Retry-After 및 retryAfterSeconds로 반환한다. 실제 이메일 수신 및 토큰 교환은 본인 계정으로 별도 확인해야 한다.

이메일 인증 전송은 `JdkClientHttpRequestFactory`를 사용한다. 기존 `SimpleClientHttpRequestFactory`의 HttpURLConnection은 Origin을 제한 헤더로 처리해 전송에서 누락한다. MockRestServiceServer의 헤더 검사만으로는 발견할 수 없으므로 로컬 HTTP 수신 서버로 실제 Origin·User-Agent·Privy 헤더 전송을 검증한다. 전역 제한 헤더 해제 옵션은 사용하지 않는다.
