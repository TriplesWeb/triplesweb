# COSMO 공식 API → 계정별 DB 스냅샷

`GET /bff/v3/objekt-summaries?artistId=tripleS&order=newest&page=1&size=30`을
기존 `CosmoTokenProvider`의 Bearer 토큰으로 호출한다. 로그인 계정의 보유 목록이며,
전체 발행 카탈로그나 타 계정 목록으로 취급하지 않는다. 클래스 필터를 생략해 모든 클래스를 수집한다.

실제 응답은 `collections[].collection`(컬렉션 정보), `count`,
`objekts[].metadata`(`tokenId`, `collectionId`, **정확한 `objektNo`**), `inventory` 구조다.
인증 없는 v3 NFT 메타데이터에 시리얼이 없다는 것과 별개다. 누락 시리얼을 NFT ID로 대체하지 않는다.

## 저장 및 보호

- V35는 `cosmo_owned_objekt`, `cosmo_objekt_sync_state`만 추가한다.
- 계정 JWT의 `sub`를 SHA-256 해시한 키와 아티스트로 계정을 구분한다.
  이 해석은 DB 분리를 위한 것으로, COSMO API가 해당 토큰을 수락해야 저장한다.
- 전체 페이지·컬렉션 수·컬렉션별 카드 수·중복을 검증한 후 한 트랜잭션으로 저장한다.
  요약 API가 카드 일부만 반환하면 동기화를 거부한다. 누락 카드를 임의로 삭제하지 않는다.
- 같은 카드는 다시 INSERT/UPDATE하지 않으며, 사라진 카드는 해당 계정 범위에서만 삭제한다.
- 기존 `objekt_catalog`와 공개 보유 검색은 변경하지 않는다. 이 스냅샷은 별도 보관이며
  공개 닉네임 검색의 데이터 원천으로 자동 대체하지 않는다.
- 인증 토큰·리프레시 토큰·공통 암호화 키·지갑 주소·원본 응답 전체를 DB에 저장하지 않는다.
  공개 컬렉션 필드를 허용 목록으로 골라 저장한다. NFT token ID는 내부 식별자이며 인증 토큰이 아니다.
- 인증 오류 401은 한 번만 갱신·재시도한다. 계정 변경이나 API 오류 시 기존 스냅샷을 유지한다.
  토큰·키·상위 API 본문은 응답과 오류 메시지에 포함하지 않는다.

## 실행

연결 화면 `/cosmo/gravity/connect`의 **보유 오브젝트 가져오기**는 관리자 세션과 CSRF 보호를 거쳐
`POST /api/cosmo/objekts/sync`를 호출한다. 반환값은 종류 수·카드 수·변경 건수뿐이다.
일반 페이지 요청이나 주기 작업이 자동으로 동기화를 실행하지 않는다.

Windows에서 이미 실행 중인 다른 체크아웃의 연결 정보를 사용할 때:

```powershell
.\scripts\import-cosmo-objekts.ps1 -ServerDirectory '실행 중인 서버 프로젝트 경로'
```

스크립트는 지정한 서버의 `.env`에서 DB 설정과 COSMO 키 설정만 프로세스 환경으로 읽고,
서버의 인증 파일을 직접 참조한다. 원문은 인자·로그·저장소에 복사하지 않는다.
Java 가져오기 명령은 웹·스케줄러·전체 체인 인덱서를 기동하지 않고, V35의 두 테이블만
멱등 생성한 뒤 가져오기를 수행한다. 이후 일반 Flyway 실행도 `IF NOT EXISTS`로 통과한다.

이미지 파일을 복제하는 작업은 아니며 COSMO의 이미지·영상 URL을 저장한다.
