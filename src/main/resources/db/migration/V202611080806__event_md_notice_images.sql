-- MD 카테고리 접힘 영역에 판매 공지 이미지(!URL) 예시
UPDATE event_stop s
JOIN event e ON e.event_id = s.event_id
SET s.md_list = CONCAT_WS('\n',
        '# Official MD',
        '!/images/image-upload-pending.png',
        '응원봉|45000',
        '슬로건|15000',
        '포토카드 세트|20000',
        '키링|18000',
        '티셔츠|39000',
        '# MD Benefit',
        '!/images/image-upload-pending.png',
        '특전 포토카드',
        '랜덤 스티커')
WHERE e.event_name = 'tripleS Come True'
  AND s.region = '서울';

UPDATE event_stop s
JOIN event e ON e.event_id = s.event_id
SET s.md_list = CONCAT_WS('\n',
        '# Official MD',
        '!/images/image-upload-pending.png',
        '응원봉|45000',
        '슬로건|15000',
        '포스터|12000',
        '# Lucky Draw Event',
        '!/images/image-upload-pending.png',
        '럭키드로우 티켓|5000',
        '# MD Benefit',
        '!/images/image-upload-pending.png',
        '특전 포토카드')
WHERE e.event_name = 'Authentic'
  AND s.region = '서울';
