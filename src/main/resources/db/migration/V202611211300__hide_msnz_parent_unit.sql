-- msnz 상위 컨테이너는 하위 DIMENSION만 선택·목록에 쓴다.
UPDATE unit
SET visible = 0
WHERE LOWER(TRIM(small_name)) = 'msnz'
   OR LOWER(TRIM(unit_name)) = 'msnz';
