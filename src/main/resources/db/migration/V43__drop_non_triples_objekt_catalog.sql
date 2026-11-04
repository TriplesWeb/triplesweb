-- 오브젝트 카탈로그는 tripleS만 유지한다. ARTMS·IDNTT 등 잔여 행을 지운다.
DELETE FROM objekt_catalog
 WHERE artist IS NOT NULL AND LOWER(TRIM(artist)) <> 'triples';
