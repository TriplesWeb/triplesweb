-- 유닛 목록 노출 여부 (예: MSNZ 상위 유닛 숨김)
ALTER TABLE unit
    ADD COLUMN visible TINYINT(1) NOT NULL DEFAULT 1;

UPDATE unit
SET visible = 0
WHERE LOWER(small_name) = 'msnz'
   OR UPPER(unit_name) = 'MSNZ';

-- 앨범 구성 유형 (isAlbum2 하드코딩 대체)
ALTER TABLE album
    ADD COLUMN album_type VARCHAR(16) NOT NULL DEFAULT 'SUBUNIT';

UPDATE album
SET album_type = 'FULL_10'
WHERE album_name = 'ASSEMBLE';

UPDATE album
SET album_type = 'FULL_24'
WHERE unit_id IS NULL
  AND album_name <> 'ASSEMBLE';
