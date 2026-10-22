-- MSNZ 본체는 하위 DIMENSION(Moon/Sun/Neptune/Zenith)과 별도로 목록에 노출
UPDATE unit
SET visible = 1
WHERE LOWER(small_name) = 'msnz'
   OR UPPER(unit_name) = 'MSNZ';

-- MSNZ 본체에 하위 유닛 멤버 합집합 연결 (없으면 추가)
INSERT INTO unit_member (artist_id, unit_id)
SELECT DISTINCT um.artist_id, parent.unit_id
FROM unit parent
JOIN unit child ON child.parent_unit_id = parent.unit_id
JOIN unit_member um ON um.unit_id = child.unit_id
WHERE LOWER(parent.small_name) = 'msnz'
  AND NOT EXISTS (
        SELECT 1
        FROM unit_member x
        WHERE x.artist_id = um.artist_id
          AND x.unit_id = parent.unit_id
    );

-- Acid Eyes (AAA + KRE)
INSERT INTO unit (unit_name, small_name, unit_image, formation_date, debut_date, visible, parent_unit_id)
SELECT 'Acid Eyes', 'acideyes', NULL, '2022-12-26', '2023-01-06', 1, NULL
WHERE NOT EXISTS (SELECT 1 FROM unit WHERE LOWER(small_name) = 'acideyes');

INSERT INTO unit_member (artist_id, unit_id)
SELECT a.artist_id, u.unit_id
FROM artist a
CROSS JOIN unit u
WHERE LOWER(u.small_name) = 'acideyes'
  AND a.eng_name IN ('hyerin', 'yooyeon', 'nakyoung', 'yubin', 'seoyeon', 'jiwoo', 'chaeyeon', 'soomin')
  AND NOT EXISTS (
        SELECT 1 FROM unit_member x
        WHERE x.artist_id = a.artist_id AND x.unit_id = u.unit_id
    );

-- tripleS ∞! (Hatchi)
INSERT INTO unit (unit_name, small_name, unit_image, formation_date, debut_date, visible, parent_unit_id)
SELECT '∞!', 'hatchi', NULL, '2024-09-01', '2024-11-20', 1, NULL
WHERE NOT EXISTS (SELECT 1 FROM unit WHERE LOWER(small_name) IN ('hatchi', 'infinity'));

INSERT INTO unit_member (artist_id, unit_id)
SELECT a.artist_id, u.unit_id
FROM artist a
CROSS JOIN unit u
WHERE LOWER(u.small_name) = 'hatchi'
  AND a.eng_name IN ('jiwoo', 'chaeyeon', 'yooyeon', 'soomin', 'kotone', 'mayu', 'shion', 'chaewon')
  AND NOT EXISTS (
        SELECT 1 FROM unit_member x
        WHERE x.artist_id = a.artist_id AND x.unit_id = u.unit_id
    );

-- Alphie
INSERT INTO unit (unit_name, small_name, unit_image, formation_date, debut_date, visible, parent_unit_id)
SELECT 'Alphie', 'alphie', NULL, '2025-07-02', '2025-09-03', 1, NULL
WHERE NOT EXISTS (SELECT 1 FROM unit WHERE LOWER(small_name) = 'alphie');

INSERT INTO unit_member (artist_id, unit_id)
SELECT a.artist_id, u.unit_id
FROM artist a
CROSS JOIN unit u
WHERE LOWER(u.small_name) = 'alphie'
  AND a.eng_name IN ('yooyeon', 'kotone', 'nien', 'lynn', 'hayeon', 'shion', 'chaewon', 'jiyeon')
  AND NOT EXISTS (
        SELECT 1 FROM unit_member x
        WHERE x.artist_id = a.artist_id AND x.unit_id = u.unit_id
    );
