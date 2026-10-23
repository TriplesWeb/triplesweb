-- 앨범 목록용 관리자 정의 구분(섹션)
CREATE TABLE IF NOT EXISTS album_section (
    section_id   BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(120) NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (section_id),
    CONSTRAINT uk_album_section_name UNIQUE (name)
);

-- 이미 수동/부분 적용된 DB에서도 통과하도록 멱등 처리
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'album'
      AND COLUMN_NAME = 'section_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE album ADD COLUMN section_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'album'
      AND CONSTRAINT_NAME = 'fk_album_section'
);
SET @sql := IF(@fk_exists = 0,
    'ALTER TABLE album ADD CONSTRAINT fk_album_section FOREIGN KEY (section_id) REFERENCES album_section (section_id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
