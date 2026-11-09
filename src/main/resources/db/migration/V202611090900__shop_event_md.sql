-- 기존 이커머스 샵을 공연 MD 리스트로 교체한다.
DROP TABLE IF EXISTS shop_image;
DROP TABLE IF EXISTS shop;

CREATE TABLE shop_section (
    section_id   BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(120) NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (section_id),
    CONSTRAINT uk_shop_section_name UNIQUE (name)
);

CREATE TABLE shop (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    event_id      BIGINT        NOT NULL,
    section_id    BIGINT        NULL,
    sales_period  VARCHAR(80)   NOT NULL,
    cover_image   VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_shop_event UNIQUE (event_id),
    CONSTRAINT fk_shop_event FOREIGN KEY (event_id)
        REFERENCES event (event_id) ON DELETE CASCADE,
    CONSTRAINT fk_shop_section FOREIGN KEY (section_id)
        REFERENCES shop_section (section_id) ON DELETE SET NULL
);

INSERT INTO shop_section (section_id, name, sort_order) VALUES
(1, '콘서트', 0),
(2, '팬미팅', 1);

-- MD가 있는 시드 이벤트를 샵 카드로 등록
INSERT INTO shop (event_id, section_id, sales_period, cover_image)
SELECT e.event_id, 1, '2024.06.15~2025.03.01', e.event_image
FROM event e
WHERE e.event_name = 'tripleS Come True';

INSERT INTO shop (event_id, section_id, sales_period, cover_image)
SELECT e.event_id, 1, '2023.10.14~2023.10.15', e.event_image
FROM event e
WHERE e.event_name = 'Authentic';
