ALTER TABLE calendar_decoration
    ADD COLUMN repeat_yearly BOOLEAN NOT NULL DEFAULT FALSE;

-- Seed the ten confirmed unit anniversaries once. Existing records are preserved.
INSERT INTO calendar_decoration
    (title, kind, event_date, unit_name, description, color, repeat_yearly)
SELECT seed.title, 'UNIT', seed.event_date, seed.unit_name,
       CONCAT(seed.unit_name, ' 기념일'), '#00f5d4', TRUE
FROM (
    SELECT 'AAA 기념일' AS title, DATE('2022-10-28') AS event_date, 'AAA' AS unit_name
    UNION ALL SELECT '크리스탈아이즈(KRE) 기념일', DATE('2023-05-04'), '크리스탈아이즈(KRE)'
    UNION ALL SELECT '러블루션 기념일', DATE('2023-08-17'), '러블루션'
    UNION ALL SELECT '에볼루션 기념일', DATE('2023-10-11'), '에볼루션'
    UNION ALL SELECT 'NXT 기념일', DATE('2023-12-23'), 'NXT'
    UNION ALL SELECT 'Aria 기념일', DATE('2024-01-15'), 'Aria'
    UNION ALL SELECT 'Glow 기념일', DATE('2024-06-21'), 'Glow'
    UNION ALL SELECT 'VV 기념일', DATE('2024-10-23'), 'VV'
    UNION ALL SELECT '∞! 기념일', DATE('2024-11-20'), '∞!'
    UNION ALL SELECT 'MSNZ(미소녀즈) 기념일', DATE('2025-11-24'), 'MSNZ(미소녀즈)'
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM calendar_decoration existing
    WHERE existing.title = seed.title
      AND existing.event_date = seed.event_date
);
