-- V26의 변수 기반 rename이 적용되지 않은 채 V27이 camelCase(mediaType 등)만 삭제하면
-- Hibernate가 조회하는 media_type / file_type 이 없어 article 상세가 500이 난다.
-- 사용자 변수 없이, 없는 snake_case 컬럼만 추가한다.

ALTER TABLE `article_file` ADD COLUMN IF NOT EXISTS `original_name` VARCHAR(300) NOT NULL DEFAULT '';
ALTER TABLE `article_file` ADD COLUMN IF NOT EXISTS `saved_name` VARCHAR(400) NOT NULL DEFAULT '';
ALTER TABLE `article_file` ADD COLUMN IF NOT EXISTS `url` VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE `article_file` ADD COLUMN IF NOT EXISTS `size` BIGINT NOT NULL DEFAULT 0;
ALTER TABLE `article_file` ADD COLUMN IF NOT EXISTS `file_type` VARCHAR(255) NOT NULL DEFAULT 'application/octet-stream';
ALTER TABLE `article_file` ADD COLUMN IF NOT EXISTS `media_type` VARCHAR(255) NOT NULL DEFAULT 'OTHER';

UPDATE `article_file`
SET `media_type` = CASE
    WHEN `file_type` LIKE 'image/%'
        OR LOWER(`saved_name`) REGEXP '\\.(jpg|jpeg|png|gif|webp|bmp)$' THEN 'IMAGE'
    WHEN `file_type` LIKE 'video/%'
        OR LOWER(`saved_name`) REGEXP '\\.(mp4|mov|m4v|webm|mkv|avi)$' THEN 'VIDEO'
    WHEN `media_type` IS NULL OR `media_type` = '' THEN 'OTHER'
    ELSE `media_type`
END
WHERE `media_type` IS NULL OR `media_type` = '' OR `media_type` = 'OTHER';
