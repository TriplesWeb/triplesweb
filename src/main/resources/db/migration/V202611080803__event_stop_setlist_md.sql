-- 지역 회차별 셋리스트·MD 리스트
SET @col_setlist := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'event_stop'
      AND COLUMN_NAME = 'setlist'
);
SET @sql := IF(@col_setlist = 0,
    'ALTER TABLE event_stop ADD COLUMN setlist LONGTEXT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_md := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'event_stop'
      AND COLUMN_NAME = 'md_list'
);
SET @sql := IF(@col_md = 0,
    'ALTER TABLE event_stop ADD COLUMN md_list LONGTEXT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Come True 서울 회차에 예시 데이터 (구조 확인용)
UPDATE event_stop s
JOIN event e ON e.event_id = s.event_id
SET s.setlist = CONCAT_WS('\n',
        'Rising',
        'Girls Never Die',
        'Generation',
        'Invincible',
        'Are You Alive',
        'Cry Baby',
        'Door',
        'Hit the Floor',
        'Beam',
        'Atmosphere',
        'Heartbeat',
        '24'),
    s.md_list = CONCAT_WS('\n',
        '응원봉',
        '슬로건',
        '포토카드 세트',
        '키링',
        '티셔츠')
WHERE e.event_name = 'tripleS Come True'
  AND s.region = '서울';

-- Authentic 서울 회차 예시
UPDATE event_stop s
JOIN event e ON e.event_id = s.event_id
SET s.setlist = CONCAT_WS('\n',
        'Rising',
        'New Look',
        'Colorful',
        'Generation',
        'Girls Never Die'),
    s.md_list = CONCAT_WS('\n',
        '응원봉',
        '슬로건',
        '포스터')
WHERE e.event_name = 'Authentic'
  AND s.region = '서울';
