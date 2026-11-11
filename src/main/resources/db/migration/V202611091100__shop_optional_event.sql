-- 공연 없는 단독 MD 판매를 위해 event_id를 선택으로 두고, 카드 이름(title)을 추가한다.
ALTER TABLE shop
    ADD COLUMN title VARCHAR(200) NULL AFTER id;

UPDATE shop s
INNER JOIN event e ON e.event_id = s.event_id
SET s.title = e.event_name
WHERE s.title IS NULL;

UPDATE shop
SET title = CONCAT('굿즈리스트 #', id)
WHERE title IS NULL OR TRIM(title) = '';

ALTER TABLE shop
    MODIFY COLUMN title VARCHAR(200) NOT NULL;

ALTER TABLE shop
    DROP FOREIGN KEY fk_shop_event;

ALTER TABLE shop
    MODIFY COLUMN event_id BIGINT NULL;

ALTER TABLE shop
    ADD CONSTRAINT fk_shop_event FOREIGN KEY (event_id)
        REFERENCES event (event_id) ON DELETE SET NULL;
