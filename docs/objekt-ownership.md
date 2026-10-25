# 오브젝트 시리얼 조회와 발행량

- 시리얼→토큰: [Objekt Tracker 공개 API](https://objekt.top/api/v1)의 `GET /collections/{slug}/serials/{serial}/transfers`.
- 컬렉션 대조: COSMO `GET /bff/v3/objekts/nft-metadata/{tokenId}`의 Member / Season / Collection 속성.
- 현재 소유자: Abstract RPC의 ERC-721 `ownerOf(tokenId)`를 `latest` 블록에서 조회한다. 인덱스의 소유자 주소만으로 현재 소유자를 확정하지 않는다.
- COSMO 메타데이터 이름의 `#숫자`는 토큰 ID일 수 있다. 이를 시리얼로 해석하지 않는다. 시리얼 매칭은 공개 인덱스 기준이며 인덱스 반영 지연이 있을 수 있다.
- 비공개, 미발견, 소각, 외부 서비스 오류는 구분한다. 검증 실패 시 과거 소유자를 대신 표시하지 않는다. 닉네임은 검증된 지갑과 일치하는 이력에 있는 경우에만 표시한다.
- 발행량: `GET /collections/rarity`를 시작 1초 뒤부터 5분마다 한 번 호출한다. 완전한 메모리 스냅샷을 교체하며 실패 시 이전 값을 유지한다. 개별 카드/목록 요청에서 외부 API나 DB 전체 재기록을 실행하지 않는다.
- 발행량 미확인은 0으로 취급하지 않으며, 많은순/적은순 모두 마지막에 배치한다. 동률은 slug/id로 정렬하여 페이지 순서를 일정하게 유지한다.

설정: `objekt.api.base-url`, `cosmo.api.base-url`, `abstract.indexer.rpc-url`, `abstract.indexer.contract`, `objekt.mints.refresh-ms`, `objekt.mints.initial-delay-ms`.
기존 전체 Transfer 인덱서는 활성화할 필요가 없다. DB 스키마 변경도 없다.

찜은 기존 브라우저 세션 기준이다. 추가·해제는 명시적인 상태를 저장하고, UUID/slug/collectionId로 저장된 기존 찜을 함께 인식·해제한다. 목록에서 없어진 카드도 찜 목록에서 삭제할 수 있다.
