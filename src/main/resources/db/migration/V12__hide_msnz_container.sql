-- MSNZ is a parent container. Keep its albums and child relations, hide its tab.
UPDATE unit
SET visible = 0
WHERE LOWER(small_name) = 'msnz'
   OR UPPER(unit_name) = 'MSNZ';
