-- 숙소 입주일은 공개일과 같이 필수
UPDATE artist
SET dorm_entry_date = ''
WHERE dorm_entry_date IS NULL OR TRIM(dorm_entry_date) = '';

ALTER TABLE artist
    MODIFY COLUMN dorm_entry_date VARCHAR(40) NOT NULL;
