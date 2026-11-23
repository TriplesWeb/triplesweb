CREATE TABLE archive_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    label VARCHAR(64) NOT NULL,
    color CHAR(7) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_archive_source_code UNIQUE (code)
);

INSERT INTO archive_source (code, label, color, sort_order, visible) VALUES
    ('COSMO_ROOM', '코스모룸', '#00f5d4', 10, TRUE),
    ('COSMO_TALK', '코스모톡/프롬', '#a78bfa', 20, TRUE),
    ('OFFICIAL_SNS', '공식 SNS', '#60a5fa', 30, TRUE),
    ('POLAROID', '폴라로이드', '#5eead4', 40, TRUE),
    ('TEASER_BEHIND', '티저/비하인드', '#2dd4bf', 50, TRUE),
    ('MAGAZINE', '화보/매거진', '#f472b6', 60, TRUE),
    ('EXTERNAL', '외부 컨텐츠/방송', '#38bdf8', 70, TRUE);
