-- 케미 다중 사진: URL 배열 JSON. image_url은 첫 장(호환)으로 유지.
ALTER TABLE chemistry
    ADD COLUMN images_json TEXT NULL;

ALTER TABLE chemistry_group
    ADD COLUMN images_json TEXT NULL;

UPDATE chemistry
SET images_json = CONCAT('["', REPLACE(image_url, '"', '\\"'), '"]')
WHERE image_url IS NOT NULL
  AND image_url <> ''
  AND (images_json IS NULL OR images_json = '');

UPDATE chemistry_group
SET images_json = CONCAT('["', REPLACE(image_url, '"', '\\"'), '"]')
WHERE image_url IS NOT NULL
  AND image_url <> ''
  AND (images_json IS NULL OR images_json = '');
