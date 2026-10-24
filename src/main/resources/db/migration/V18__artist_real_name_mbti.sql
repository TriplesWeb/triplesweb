-- 멤버 본명·MBTI (선택 입력)
SET @real_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'artist'
      AND COLUMN_NAME = 'real_name'
);
SET @sql := IF(@real_exists = 0,
    'ALTER TABLE artist ADD COLUMN real_name VARCHAR(120) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @mbti_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'artist'
      AND COLUMN_NAME = 'mbti'
);
SET @sql := IF(@mbti_exists = 0,
    'ALTER TABLE artist ADD COLUMN mbti VARCHAR(16) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
