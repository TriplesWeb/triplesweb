-- 멤버 프로필: 데뷔일 제거 → 공개일·숙소 입주일
ALTER TABLE artist
    ADD COLUMN reveal_date VARCHAR(40) NULL,
    ADD COLUMN dorm_entry_date VARCHAR(40) NULL;

UPDATE artist
SET reveal_date = debut_date
WHERE debut_date IS NOT NULL AND TRIM(debut_date) <> '';

UPDATE artist
SET reveal_date = ''
WHERE reveal_date IS NULL;

ALTER TABLE artist
    MODIFY COLUMN reveal_date VARCHAR(40) NOT NULL;

ALTER TABLE artist
    DROP COLUMN debut_date;
