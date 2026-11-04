-- 바이츠는 이름 시드 + 정적 이미지만 사용. 이모지·업로드 컬럼 제거
SET @emoji_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'artist'
      AND COLUMN_NAME = 'bite_emoji'
);
SET @sql := IF(@emoji_exists > 0,
    'ALTER TABLE artist DROP COLUMN bite_emoji',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @image_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'artist'
      AND COLUMN_NAME = 'bite_image'
);
SET @sql := IF(@image_exists > 0,
    'ALTER TABLE artist DROP COLUMN bite_image',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
