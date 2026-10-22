-- Acid Eyes / ∞! / Alphie 유닛 이미지 경로 보정 (빈 src → 깨진 이미지)
UPDATE unit
SET unit_image = '/uploads/images/acideyes.jpg'
WHERE LOWER(small_name) = 'acideyes'
  AND (unit_image IS NULL OR unit_image = '');

UPDATE unit
SET unit_image = '/uploads/images/hatchi.jpg'
WHERE LOWER(small_name) = 'hatchi'
  AND (unit_image IS NULL OR unit_image = '');

UPDATE unit
SET unit_image = '/uploads/images/alphie.jpg'
WHERE LOWER(small_name) = 'alphie'
  AND (unit_image IS NULL OR unit_image = '');

-- 유니코드 파일명 앨범 커버 → ASCII 경로 (일부 환경에서 깨짐 방지)
UPDATE album
SET album_image = '/uploads/images/lovelution-album.jpg'
WHERE album_image LIKE '%ↀ%'
   OR album_name = 'ↀ';

UPDATE album
SET album_image = '/uploads/images/evolution-album.jpg'
WHERE album_image LIKE '%⟡%'
   OR album_name = '⟡';
