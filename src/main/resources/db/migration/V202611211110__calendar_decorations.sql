CREATE TABLE IF NOT EXISTS calendar_decoration (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    kind VARCHAR(24) NOT NULL,
    event_date DATE NOT NULL,
    unit_name VARCHAR(80) NULL,
    description VARCHAR(500) NULL,
    image_url VARCHAR(1000) NULL,
    color VARCHAR(16) NOT NULL DEFAULT '#00f5d4',
    PRIMARY KEY (id),
    INDEX idx_calendar_decoration_date (event_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
