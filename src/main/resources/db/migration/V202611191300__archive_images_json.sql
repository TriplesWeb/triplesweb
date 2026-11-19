-- 아카이브 사진 게시글의 다중 이미지 URL 목록. file(url)은 표지(첫 장) 호환용.
ALTER TABLE archive
    ADD COLUMN images_json TEXT NULL;

UPDATE archive
SET images_json = CONCAT('["', REPLACE(url, '"', '\\"'), '"]')
WHERE url IS NOT NULL
  AND url <> ''
  AND (images_json IS NULL OR images_json = '');
