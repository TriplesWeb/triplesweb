-- 이벤트 목록용 관리자 정의 구분(섹션)
CREATE TABLE IF NOT EXISTS event_section (
    section_id   BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(120) NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (section_id),
    CONSTRAINT uk_event_section_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS event (
    event_id    BIGINT        NOT NULL AUTO_INCREMENT,
    event_name  VARCHAR(255)  NOT NULL,
    event_image VARCHAR(1000) NULL,
    event_date  VARCHAR(40)   NOT NULL,
    venue       VARCHAR(255)  NULL,
    event_info  LONGTEXT      NOT NULL,
    section_id  BIGINT        NULL,
    PRIMARY KEY (event_id),
    CONSTRAINT uk_event_name UNIQUE (event_name),
    CONSTRAINT fk_event_section FOREIGN KEY (section_id)
        REFERENCES event_section (section_id) ON DELETE SET NULL
);
