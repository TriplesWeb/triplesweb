-- 구 이커머스 shop 컬럼이 남아 INSERT가 실패한다 (name/price NOT NULL 등).
-- 현재 MD 굿즈리스트 엔티티가 쓰지 않는 레거시 컬럼을 제거한다.
ALTER TABLE shop DROP COLUMN IF EXISTS name;
ALTER TABLE shop DROP COLUMN IF EXISTS price;
ALTER TABLE shop DROP COLUMN IF EXISTS description;
ALTER TABLE shop DROP COLUMN IF EXISTS soldout;
ALTER TABLE shop DROP COLUMN IF EXISTS category;
