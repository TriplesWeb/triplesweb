-- 같은 순번·조별 갈림 무대( || ) 예시
UPDATE event_stop s
JOIN event e ON e.event_id = s.event_id
SET s.setlist = CONCAT_WS('\n',
        '# VCR 1 : OPENING VCR',
        'Baby Flower|LOVE&POP pt.1',
        'Bionic Power|Performante|24인조',
        'Non Scale|ASSEMBLE24',
        '# MENT 1 : 단체 인사 및 개인 인사',
        'Love2Love|ASSEMBLE25',
        '추리소설|ASSEMBLE|Dance Break',
        'Are You Alive|ASSEMBLE24',
        'Door|ASSEMBLE24',
        '# MENT 3 : 유닛 무대 소개',
        'Supersonic|원곡: 프로미스나인 / A조 || I CAN''T STOP ME|원곡: TWICE / B조',
        '# MENT 2 : 유닛 커버 무대 토크',
        'Hit the Floor|ASSEMBLE24',
        'Beam|ASSEMBLE24',
        'Atmosphere|ASSEMBLE24',
        '24|ASSEMBLE24|앵콜')
WHERE e.event_name = 'tripleS Come True'
  AND s.region = '서울';
