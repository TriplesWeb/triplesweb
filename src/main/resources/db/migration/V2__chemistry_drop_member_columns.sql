-- Chemistry: 레거시 membera/memberb(또는 member_a/member_b) 제거 → Artist FK만 사용

SET @has_legacy := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'chemistry'
      AND LOWER(COLUMN_NAME) IN ('membera', 'memberb', 'member_a', 'member_b')
);

-- 레거시 컬럼이 있을 때만 unique/컬럼 DROP
SET @sql := IF(@has_legacy > 0,
    'ALTER TABLE chemistry DROP INDEX uk_chemistry_pair',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chemistry' AND LOWER(COLUMN_NAME) = 'membera'
);
SET @sql := IF(@col > 0, 'ALTER TABLE chemistry DROP COLUMN membera', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chemistry' AND LOWER(COLUMN_NAME) = 'memberb'
);
SET @sql := IF(@col > 0, 'ALTER TABLE chemistry DROP COLUMN memberb', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chemistry' AND LOWER(COLUMN_NAME) = 'member_a'
);
SET @sql := IF(@col > 0, 'ALTER TABLE chemistry DROP COLUMN member_a', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chemistry' AND LOWER(COLUMN_NAME) = 'member_b'
);
SET @sql := IF(@col > 0, 'ALTER TABLE chemistry DROP COLUMN member_b', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- artist FK 컬럼 보장
SET @col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chemistry' AND COLUMN_NAME = 'artist_a_id'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE chemistry ADD COLUMN artist_a_id BIGINT NULL, ADD COLUMN artist_b_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE chemistry
    MODIFY COLUMN artist_a_id BIGINT NOT NULL,
    MODIFY COLUMN artist_b_id BIGINT NOT NULL;

SET @idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'chemistry'
      AND INDEX_NAME = 'uk_chemistry_pair'
);
SET @sql := IF(@idx = 0,
    'ALTER TABLE chemistry ADD CONSTRAINT uk_chemistry_pair UNIQUE (artist_a_id, artist_b_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
