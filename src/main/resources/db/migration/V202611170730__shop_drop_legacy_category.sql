-- 구 이커머스 shop.category(NOT NULL, 기본값 없음)가 남아 INSERT가 실패한다.
-- 현재 Shop 엔티티는 category를 쓰지 않으므로 컬럼을 제거한다.
ALTER TABLE shop DROP COLUMN IF EXISTS category;
