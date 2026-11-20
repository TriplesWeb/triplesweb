-- 케미 페어/그룹 이미지 URL을 /uploads/chemistry/chem → /uploads/images/chemistry/legacy-chem 으로 통일.
-- 서버 디스크 파일은 scripts/migrate-chemistry-photos-on-pi.sh 로 먼저(또는 직후) 복사한다.

UPDATE chemistry
SET image_url = REPLACE(image_url, '/uploads/chemistry/chem/', '/uploads/images/chemistry/legacy-chem/')
WHERE image_url LIKE '/uploads/chemistry/chem/%';

UPDATE chemistry
SET images_json = REPLACE(images_json, '/uploads/chemistry/chem/', '/uploads/images/chemistry/legacy-chem/')
WHERE images_json LIKE '%/uploads/chemistry/chem/%';

UPDATE chemistry_group
SET image_url = REPLACE(image_url, '/uploads/chemistry/chem/', '/uploads/images/chemistry/legacy-chem/')
WHERE image_url LIKE '/uploads/chemistry/chem/%';

UPDATE chemistry_group
SET images_json = REPLACE(images_json, '/uploads/chemistry/chem/', '/uploads/images/chemistry/legacy-chem/')
WHERE images_json LIKE '%/uploads/chemistry/chem/%';
