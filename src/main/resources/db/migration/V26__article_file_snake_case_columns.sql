-- Legacy article_file / shop_image embed columns used camelCase (fileType, …).
-- Hibernate CamelCaseToUnderscoresNamingStrategy inserts snake_case (file_type, …),
-- which left NOT NULL camelCase columns unset → MariaDB 1364.

-- article_file ----------------------------------------------------------------
SET @db = DATABASE();

-- fileType → file_type (rename, or merge+drop when both exist)
SET @has_camel = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'article_file' AND COLUMN_NAME = 'fileType'
);
SET @has_snake = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'article_file' AND COLUMN_NAME = 'file_type'
);

SET @sql = IF(@has_camel = 1 AND @has_snake = 0,
  'ALTER TABLE `article_file` CHANGE COLUMN `fileType` `file_type` VARCHAR(255) NOT NULL',
  IF(@has_camel = 1 AND @has_snake = 1,
    'UPDATE `article_file` SET `file_type` = COALESCE(NULLIF(`file_type`, \'\'), `fileType`) WHERE `fileType` IS NOT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@has_camel = 1 AND @has_snake = 1,
  'ALTER TABLE `article_file` DROP COLUMN `fileType`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- originalName → original_name
SET @has_camel = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'article_file' AND COLUMN_NAME = 'originalName'
);
SET @has_snake = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'article_file' AND COLUMN_NAME = 'original_name'
);
SET @sql = IF(@has_camel = 1 AND @has_snake = 0,
  'ALTER TABLE `article_file` CHANGE COLUMN `originalName` `original_name` VARCHAR(300) NOT NULL',
  IF(@has_camel = 1 AND @has_snake = 1,
    'UPDATE `article_file` SET `original_name` = COALESCE(NULLIF(`original_name`, \'\'), `originalName`) WHERE `originalName` IS NOT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF(@has_camel = 1 AND @has_snake = 1,
  'ALTER TABLE `article_file` DROP COLUMN `originalName`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- savedName → saved_name
SET @has_camel = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'article_file' AND COLUMN_NAME = 'savedName'
);
SET @has_snake = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'article_file' AND COLUMN_NAME = 'saved_name'
);
SET @sql = IF(@has_camel = 1 AND @has_snake = 0,
  'ALTER TABLE `article_file` CHANGE COLUMN `savedName` `saved_name` VARCHAR(400) NOT NULL',
  IF(@has_camel = 1 AND @has_snake = 1,
    'UPDATE `article_file` SET `saved_name` = COALESCE(NULLIF(`saved_name`, \'\'), `savedName`) WHERE `savedName` IS NOT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF(@has_camel = 1 AND @has_snake = 1,
  'ALTER TABLE `article_file` DROP COLUMN `savedName`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mediaType → media_type
SET @has_camel = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'article_file' AND COLUMN_NAME = 'mediaType'
);
SET @has_snake = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'article_file' AND COLUMN_NAME = 'media_type'
);
SET @sql = IF(@has_camel = 1 AND @has_snake = 0,
  'ALTER TABLE `article_file` CHANGE COLUMN `mediaType` `media_type` VARCHAR(50) NOT NULL',
  IF(@has_camel = 1 AND @has_snake = 1,
    'UPDATE `article_file` SET `media_type` = COALESCE(NULLIF(`media_type`, \'\'), `mediaType`) WHERE `mediaType` IS NOT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF(@has_camel = 1 AND @has_snake = 1,
  'ALTER TABLE `article_file` DROP COLUMN `mediaType`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- shop_image (same embed legacy risk) -----------------------------------------
SET @has_camel = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shop_image' AND COLUMN_NAME = 'fileType'
);
SET @has_snake = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shop_image' AND COLUMN_NAME = 'file_type'
);
SET @sql = IF(@has_camel = 1 AND @has_snake = 0,
  'ALTER TABLE `shop_image` CHANGE COLUMN `fileType` `file_type` VARCHAR(255) NOT NULL',
  IF(@has_camel = 1 AND @has_snake = 1,
    'UPDATE `shop_image` SET `file_type` = COALESCE(NULLIF(`file_type`, \'\'), `fileType`) WHERE `fileType` IS NOT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF(@has_camel = 1 AND @has_snake = 1,
  'ALTER TABLE `shop_image` DROP COLUMN `fileType`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_camel = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shop_image' AND COLUMN_NAME = 'originalName'
);
SET @has_snake = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shop_image' AND COLUMN_NAME = 'original_name'
);
SET @sql = IF(@has_camel = 1 AND @has_snake = 0,
  'ALTER TABLE `shop_image` CHANGE COLUMN `originalName` `original_name` VARCHAR(300) NOT NULL',
  IF(@has_camel = 1 AND @has_snake = 1,
    'UPDATE `shop_image` SET `original_name` = COALESCE(NULLIF(`original_name`, \'\'), `originalName`) WHERE `originalName` IS NOT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF(@has_camel = 1 AND @has_snake = 1,
  'ALTER TABLE `shop_image` DROP COLUMN `originalName`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_camel = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shop_image' AND COLUMN_NAME = 'savedName'
);
SET @has_snake = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shop_image' AND COLUMN_NAME = 'saved_name'
);
SET @sql = IF(@has_camel = 1 AND @has_snake = 0,
  'ALTER TABLE `shop_image` CHANGE COLUMN `savedName` `saved_name` VARCHAR(400) NOT NULL',
  IF(@has_camel = 1 AND @has_snake = 1,
    'UPDATE `shop_image` SET `saved_name` = COALESCE(NULLIF(`saved_name`, \'\'), `savedName`) WHERE `savedName` IS NOT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF(@has_camel = 1 AND @has_snake = 1,
  'ALTER TABLE `shop_image` DROP COLUMN `savedName`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_camel = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shop_image' AND COLUMN_NAME = 'mediaType'
);
SET @has_snake = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'shop_image' AND COLUMN_NAME = 'media_type'
);
SET @sql = IF(@has_camel = 1 AND @has_snake = 0,
  'ALTER TABLE `shop_image` CHANGE COLUMN `mediaType` `media_type` VARCHAR(50) NOT NULL',
  IF(@has_camel = 1 AND @has_snake = 1,
    'UPDATE `shop_image` SET `media_type` = COALESCE(NULLIF(`media_type`, \'\'), `mediaType`) WHERE `mediaType` IS NOT NULL',
    'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF(@has_camel = 1 AND @has_snake = 1,
  'ALTER TABLE `shop_image` DROP COLUMN `mediaType`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
