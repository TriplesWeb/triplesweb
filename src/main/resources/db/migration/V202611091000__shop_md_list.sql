-- MD 공지는 공연(event_stop)이 아니라 샵 카드에서 관리한다.
ALTER TABLE shop
    ADD COLUMN md_list LONGTEXT NULL AFTER cover_image;

SET SESSION group_concat_max_len = 1024 * 1024;

-- 기존 회차 md_list를 샵으로 이전 (지역 헤더로 구분)
UPDATE shop s
LEFT JOIN (
    SELECT
        es.event_id,
        GROUP_CONCAT(
            CONCAT_WS(
                '\n',
                CONCAT(
                    '# ',
                    CASE
                        WHEN es.region IS NOT NULL AND TRIM(es.region) <> ''
                            THEN CONCAT(e.event_name, ' in ', TRIM(es.region))
                        ELSE e.event_name
                    END
                ),
                TRIM(es.md_list)
            )
            ORDER BY es.sort_order ASC, es.stop_id ASC
            SEPARATOR '\n'
        ) AS md_blob
    FROM event_stop es
    INNER JOIN event e ON e.event_id = es.event_id
    WHERE es.md_list IS NOT NULL
      AND TRIM(es.md_list) <> ''
    GROUP BY es.event_id
) src ON src.event_id = s.event_id
SET s.md_list = src.md_blob;
