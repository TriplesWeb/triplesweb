-- 멤버 바이츠 이름 (카드 헤더 표시용). 이미지는 정적 /images/artist/bite/{id}.png
SET @bite_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'artist'
      AND COLUMN_NAME = 'bite'
);
SET @sql := IF(@bite_exists = 0,
    'ALTER TABLE artist ADD COLUMN bite VARCHAR(80) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
