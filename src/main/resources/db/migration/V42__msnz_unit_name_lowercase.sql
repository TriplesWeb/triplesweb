-- 참여 유닛 표시명 MSNZ → msnz
UPDATE unit
SET unit_name = 'msnz'
WHERE UPPER(unit_name) = 'MSNZ';
