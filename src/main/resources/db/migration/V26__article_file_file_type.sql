-- SavedMeta.fileType → file_type 컬럼 누락 보정 (article_file / shop_image)
-- 증상: Unknown column 'f1_0.file_type' in 'SELECT' on article detail

SET @db := DATABASE();

-- ---------- article_file ----------
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'article_file') = 0,
        'SELECT 1',
        IF(
            (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'article_file' AND COLUMN_NAME = 'file_type') = 0,
            'ALTER TABLE article_file ADD COLUMN file_type VARCHAR(255) NOT NULL DEFAULT ''application/octet-stream''',
            'SELECT 1'
        )
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'article_file' AND COLUMN_NAME = 'file_type') = 0
        OR (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'article_file' AND COLUMN_NAME = 'media_type') = 0,
        'SELECT 1',
        'UPDATE article_file SET file_type = CASE'
            ' WHEN UPPER(COALESCE(media_type, '''')) IN (''IMAGE'', ''PHOTO'') THEN ''image/jpeg'''
            ' WHEN UPPER(COALESCE(media_type, '''')) = ''VIDEO'' THEN ''video/mp4'''
            ' ELSE COALESCE(NULLIF(file_type, ''''), ''application/octet-stream'') END'
            ' WHERE file_type IS NULL OR file_type = '''' OR file_type = ''application/octet-stream'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- shop_image ----------
SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shop_image') = 0,
        'SELECT 1',
        IF(
            (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shop_image' AND COLUMN_NAME = 'file_type') = 0,
            'ALTER TABLE shop_image ADD COLUMN file_type VARCHAR(255) NOT NULL DEFAULT ''application/octet-stream''',
            'SELECT 1'
        )
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shop_image' AND COLUMN_NAME = 'file_type') = 0
        OR (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shop_image' AND COLUMN_NAME = 'media_type') = 0,
        'SELECT 1',
        'UPDATE shop_image SET file_type = CASE'
            ' WHEN UPPER(COALESCE(media_type, '''')) IN (''IMAGE'', ''PHOTO'') THEN ''image/jpeg'''
            ' WHEN UPPER(COALESCE(media_type, '''')) = ''VIDEO'' THEN ''video/mp4'''
            ' ELSE COALESCE(NULLIF(file_type, ''''), ''application/octet-stream'') END'
            ' WHERE file_type IS NULL OR file_type = '''' OR file_type = ''application/octet-stream'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
