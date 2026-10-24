-- V26 used information_schema + user variables; on some MariaDB/Flyway setups those
-- checks no-op'd and left NOT NULL camelCase duplicates beside snake_case columns.
-- Hibernate already inserts into file_type / original_name / … so dropping camelCase is safe.

ALTER TABLE `article_file` DROP COLUMN IF EXISTS `fileType`;
ALTER TABLE `article_file` DROP COLUMN IF EXISTS `originalName`;
ALTER TABLE `article_file` DROP COLUMN IF EXISTS `savedName`;
ALTER TABLE `article_file` DROP COLUMN IF EXISTS `mediaType`;

ALTER TABLE `shop_image` DROP COLUMN IF EXISTS `fileType`;
ALTER TABLE `shop_image` DROP COLUMN IF EXISTS `originalName`;
ALTER TABLE `shop_image` DROP COLUMN IF EXISTS `savedName`;
ALTER TABLE `shop_image` DROP COLUMN IF EXISTS `mediaType`;
