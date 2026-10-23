ALTER TABLE media ADD COLUMN artist_names TEXT NULL;
ALTER TABLE schedule ADD COLUMN artist_names TEXT NULL;
CREATE TABLE IF NOT EXISTS objekt_description (
    id BIGINT NOT NULL AUTO_INCREMENT,
    objekt_key VARCHAR(255) NOT NULL,
    description TEXT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_objekt_description_key UNIQUE (objekt_key)
);