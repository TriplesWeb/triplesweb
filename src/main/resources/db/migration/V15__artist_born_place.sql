-- 출생지(born_place) 추가. 출신지(birth_place)는 선택 입력 가능. (멱등)
SET @born_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'artist'
      AND COLUMN_NAME = 'born_place'
);
SET @sql := IF(@born_exists = 0,
    'ALTER TABLE artist ADD COLUMN born_place VARCHAR(255) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE artist
    MODIFY COLUMN birth_place VARCHAR(255) NULL;
