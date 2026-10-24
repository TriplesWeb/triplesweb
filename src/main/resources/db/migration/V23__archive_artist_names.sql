-- Add member tags to the media archive. Older installations may have created archive via Hibernate.
CREATE TABLE IF NOT EXISTS archive (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    original_name VARCHAR(300) NOT NULL,
    saved_name VARCHAR(400) NOT NULL,
    url VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    file_type VARCHAR(255) NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);
ALTER TABLE archive ADD COLUMN artist_names TEXT NULL;