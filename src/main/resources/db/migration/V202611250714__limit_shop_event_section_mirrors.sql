-- 현재 이벤트와 구분명이 일치하는 샵 구분에만 적용한다.
-- fk_shop_section의 ON DELETE SET NULL로 소속 굿즈는 기타로 이동한다.
DELETE FROM shop_section
WHERE BINARY LOWER(TRIM(name)) NOT IN ('콘서트', '팬콘', '팬미팅')
  AND EXISTS (
      SELECT 1
      FROM event_section
      WHERE BINARY LOWER(TRIM(event_section.name)) = BINARY LOWER(TRIM(shop_section.name))
  );
