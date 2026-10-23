-- 앨범 규격(싱글/미니/정규 등). 미지정이면 NULL
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'album'
      AND COLUMN_NAME = 'album_format'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE album ADD COLUMN album_format VARCHAR(16) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
