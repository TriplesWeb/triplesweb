-- 투어 지역·회차(스톱). 하나의 이벤트 아래 여러 지역 공연을 둔다.
CREATE TABLE IF NOT EXISTS event_stop (
    stop_id     BIGINT        NOT NULL AUTO_INCREMENT,
    event_id    BIGINT        NOT NULL,
    event_date  VARCHAR(80)   NOT NULL,
    region      VARCHAR(120)  NULL,
    venue       VARCHAR(255)  NULL,
    detail      LONGTEXT      NULL,
    sort_order  INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (stop_id),
    CONSTRAINT fk_event_stop_event FOREIGN KEY (event_id)
        REFERENCES event (event_id) ON DELETE CASCADE
);

-- 기존 단일 개최일·장소를 첫 회차로 이전
INSERT INTO event_stop (event_id, event_date, region, venue, detail, sort_order)
SELECT e.event_id, e.event_date, NULL, e.venue, '', 0
FROM event e
WHERE e.event_date IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM event_stop s WHERE s.event_id = e.event_id
  );

SET @col_date := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'event_date'
);
SET @sql := IF(@col_date > 0,
    'ALTER TABLE event DROP COLUMN event_date',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_venue := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'venue'
);
SET @sql := IF(@col_venue > 0,
    'ALTER TABLE event DROP COLUMN venue',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
