-- 케미 페어/그룹 이미지 URL을 /uploads/chemistry/chem → /uploads/images/chemistry 으로 통일.
-- 서버 디스크 파일은 scripts/migrate-chemistry-photos-on-pi.sh 로 먼저(또는 직후) 복사한다.

UPDATE chemistry
SET image_url = REPLACE(image_url, '/uploads/chemistry/chem/', '/uploads/images/chemistry/')
WHERE image_url LIKE '/uploads/chemistry/chem/%';

UPDATE chemistry
SET images_json = REPLACE(images_json, '/uploads/chemistry/chem/', '/uploads/images/chemistry/')
WHERE images_json LIKE '%/uploads/chemistry/chem/%';

UPDATE chemistry_group
SET image_url = REPLACE(image_url, '/uploads/chemistry/chem/', '/uploads/images/chemistry/')
WHERE image_url LIKE '/uploads/chemistry/chem/%';

UPDATE chemistry_group
SET images_json = REPLACE(images_json, '/uploads/chemistry/chem/', '/uploads/images/chemistry/')
WHERE images_json LIKE '%/uploads/chemistry/chem/%';

-- 중간안(legacy-chem)으로 바뀐 값이 있으면 같은 경로로 정리
UPDATE chemistry
SET image_url = REPLACE(image_url, '/uploads/images/chemistry/legacy-chem/', '/uploads/images/chemistry/')
WHERE image_url LIKE '/uploads/images/chemistry/legacy-chem/%';

UPDATE chemistry
SET images_json = REPLACE(images_json, '/uploads/images/chemistry/legacy-chem/', '/uploads/images/chemistry/')
WHERE images_json LIKE '%/uploads/images/chemistry/legacy-chem/%';

UPDATE chemistry_group
SET image_url = REPLACE(image_url, '/uploads/images/chemistry/legacy-chem/', '/uploads/images/chemistry/')
WHERE image_url LIKE '/uploads/images/chemistry/legacy-chem/%';

UPDATE chemistry_group
SET images_json = REPLACE(images_json, '/uploads/images/chemistry/legacy-chem/', '/uploads/images/chemistry/')
WHERE images_json LIKE '%/uploads/images/chemistry/legacy-chem/%';
