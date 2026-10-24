-- 멤버 싸인(서명) 이미지 URL
SET @sig_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'artist'
      AND COLUMN_NAME = 'signature_image'
);
SET @sql := IF(@sig_exists = 0,
    'ALTER TABLE artist ADD COLUMN signature_image VARCHAR(512) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
