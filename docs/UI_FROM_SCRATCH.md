# tripleS Web 2.0: 독립 디자인 재설계 (비교 브랜치)

- Base: main @ d5bacea9c76172f6ffc3bad35ed626f3425db62c
- Branch: feature/ui-from-scratch-20261103
- Status: **비교용 WIP**. 현재는 홈과 새 v2 셸만 새 디자인으로 전환. 다른 화면은 다음 단계에서 교체해야 한다.
- 목표: 기능과 데이터는 1:1 보존, 비주얼 시스템 및 화면 구조는 원점에서 재설계. 기존 CSS/마스코트/헤더 디자인을 새로운 UI에 재사용하지 않는다.

## 설계 결정

1. **팬덤 고유 용어를 변경하지 않는다.** Objekt는 수집 가능한 디지털 굿즈, Gravity는 COMO로 활동 방향에 참여하는 투표, DIMENSION은 멤버 조합으로 활동하는 그룹·유닛 개념이다. 출처: https://www.triplescosmos.com/cosmo/terms-of-usage 및 공식 멤버 프로필의 DIMENSION 섹션.
2. **공식 앱과 본 사이트 기능은 구별한다.** 이 사이트의 Gravity는 공식 결과 읽기, Objekt 컬렉션은 닉네임 기반 조회, 교환/거래는 게시판이다. 직접 공식 투표/온체인 전송 기능으로 소개하지 않는다.
3. **/triples/overview는 의도적으로 예약한 신규 그룹 입문 경로.** 기존 소개 페이지를 복원하지 않고 향후 별도로 구현한다. 미구현 상태를 버그로 집계하지 않는다.
4. **기존 백엔드/DB/API/주소는 보호한다.** UI 개발을 위해 라우트/DTO/API 의미를 바꾸지 않는다. DOM ID 및 data attribute는 JS 기능 계약으로 취급하고 해당 페이지 이관 시 검사한다.
5. **색상/간격/서체/헤더/카드/애니메이션은 레거시에서 가져오지 않는다.** 새로운 UI는 v2-site.css만 읽는다. WIP 동안 레거시 페이지를 유지하는 것은 기능 안전을 위한 임시 상태이며 최종 설계가 아니다.

## PR #30 최적화 철학: 완료 조건

- 서버 측 초기 HTML에서 핵심 정보가 즉시 보일 것(SSR first paint)
- 공통 자산은 작고 정적으로 제공하고, 페이지 전용 코드는 해당 페이지에서만 요청
- 목록(Objekt, 컬렉션, 기록)은 서버 측 페이징·추가 로딩 유지. 초기 수만 건 로딩 금지
- 큰 이미지의 width/height·aspect-ratio 지정과 지연 로딩을 유지하되, 첫 화면의 중요 이미지에만 높은 우선순위
- 동영상 iframe은 실제 클릭 이후 생성, 외부 스크립트는 가능한 제거하거나 지연 로딩
- 서버의 gzip·Cache-Control·Mustache 캐시·Objekt/Gavity 데이터 캐시·아티스트 스냅샷 유지
- 모달/상세/멤버 선택 UI는 상호작용 시 지연 로딩하되 기존 검색·상세·등록 동작 유지
- CSS/JS의 캐시 무효화 버전 관리, Playwright·Spring 회귀 검사 유지

## 최종 전환 대상

- 공통: 신규 header / footer / navigation / theme / search / modal / filter / empty/error state
- 홈: SSR 영상 파사드·앨범·공지·월간 스케줄·사진·영상
- tripleS: 멤버·유닛(DIMENSION)·앨범·케미씃트리 (추후 /triples/overview)
- COSMO: Objekt 도감·상세·발행량·찜·시리얼 소유/이력·닉네임 컬렉션·프로필·전송 내역
- Gravity: 공식 목록/결과/상세/연결 상태
- 스케줄·공식 미디어·파일 아카이브
- 교환/판매 글 목록·상세·작성·수정, Objekt have/want 피커, 관리자 화면
- 굿즈·정보·공지 및 남아 있는 지원 경로

## 검증 전략

1. 각 페이지의 기존 링크·폼 이름·API·검색/정렬·권한·업로드·취소 동작을 기록한다.
2. 새로운 템플릿으로 대체한 뒤 동일 시나리오를 데스크톱/모바일 테스트한다.
3. 콜드·웜 초기 HTML TTFB, LCP, CLS, JS 크기, CSS 크기, 요청 수를 동일 환경에서 main과 비교한다.
4. 프로토타입이 완성되기 전에는 main에 병합하지 않는다. 두 버전을 독립 배포해 브라우저에서 직접 비교한 후 채택한다.

## 첫 작업 변경

- v2 HTML head는 레거시 CSS/웹폰트/아이콘 폰트를 포함하지 않는다.
- 홈은 MainController의 기존 model(noticePosts, albumHref, recentPhotos, recentVideos, schedulesJson)을 그대로 쓴다.
- v2는 외부 FullCalendar를 사용하지 않는 가벼운 월간 일정 뷰를 탑재한다.
- v2 홈의 공통 헤더/홈 디자인은 최초 비교 대상이며, 다른 페이지의 스타일 전면 교체는 아직 완료되지 않았다.
