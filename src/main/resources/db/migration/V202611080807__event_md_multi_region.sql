-- 굿즈는 지역마다 다르므로 여러 회차에 MD 시드 + MSNZ 타이베이 회차

-- My Secret New Zone: 타이베이 회차 추가 (없을 때만)
INSERT INTO event_stop (event_id, event_date, region, venue, detail, sort_order, setlist, md_list)
SELECT e.event_id,
       '2026.03.14~2026.03.15',
       '타이베이',
       'Taipei Nangang Exhibition Center',
       'Asia Tour',
       1,
       NULL,
       NULL
FROM event e
WHERE e.event_name = 'My Secret New Zone'
  AND NOT EXISTS (
      SELECT 1 FROM event_stop s
      WHERE s.event_id = e.event_id AND s.region = '타이베이'
  );

-- MSNZ 타이베이 굿즈
UPDATE event_stop s
JOIN event e ON e.event_id = s.event_id
SET s.md_list = CONCAT_WS('\n',
        '# Official MD',
        '!/images/image-upload-pending.png',
        '응원봉|45000',
        '슬로건|15000',
        '타이베이 한정 포토카드|20000',
        '# MD Benefit',
        '!/images/image-upload-pending.png',
        '특전 포토카드')
WHERE e.event_name = 'My Secret New Zone'
  AND s.region = '타이베이';

-- MSNZ 서울 굿즈 (카테고리 구성이 지역마다 다름)
UPDATE event_stop s
JOIN event e ON e.event_id = s.event_id
SET s.md_list = CONCAT_WS('\n',
        '# Official MD',
        '!/images/image-upload-pending.png',
        '응원봉|45000',
        '슬로건|15000',
        '서울 한정 키링|18000',
        '# Lucky Draw Event',
        '!/images/image-upload-pending.png',
        '럭키드로우 티켓|5000',
        '# MD Benefit',
        '!/images/image-upload-pending.png',
        '특전 포토카드')
WHERE e.event_name = 'My Secret New Zone'
  AND s.region = '서울';

-- Come True: 방콕·LA도 지역별 굿즈 (서울과 구성 다르게)
UPDATE event_stop s
JOIN event e ON e.event_id = s.event_id
SET s.md_list = CONCAT_WS('\n',
        '# Official MD',
        '!/images/image-upload-pending.png',
        '응원봉|45000',
        '슬로건|15000',
        '방콕 한정 포스터|12000',
        '# MD Benefit',
        '!/images/image-upload-pending.png',
        '특전 포토카드')
WHERE e.event_name = 'tripleS Come True'
  AND s.region = '방콕';

UPDATE event_stop s
JOIN event e ON e.event_id = s.event_id
SET s.md_list = CONCAT_WS('\n',
        '# Official MD',
        '!/images/image-upload-pending.png',
        '응원봉|45000',
        '티셔츠|39000',
        'LA 한정 슬로건|18000',
        '# Lucky Draw Event',
        '!/images/image-upload-pending.png',
        '럭키드로우 티켓|5000')
WHERE e.event_name = 'tripleS Come True'
  AND s.region = '로스앤젤레스';
