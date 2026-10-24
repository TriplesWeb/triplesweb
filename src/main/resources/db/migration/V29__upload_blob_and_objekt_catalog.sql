CREATE TABLE IF NOT EXISTS upload_blob (
    relative_path VARCHAR(500) NOT NULL PRIMARY KEY,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    data LONGBLOB NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS objekt_catalog (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    external_id VARCHAR(80) NULL,
    created_at VARCHAR(40) NULL,
    slug VARCHAR(200) NULL,
    collection_id VARCHAR(120) NOT NULL,
    season VARCHAR(80) NULL,
    member VARCHAR(200) NULL,
    artist VARCHAR(80) NULL,
    collection_no VARCHAR(40) NULL,
    objekt_class VARCHAR(80) NULL,
    thumbnail_image VARCHAR(500) NULL,
    front_image VARCHAR(500) NULL,
    back_image VARCHAR(500) NULL,
    background_color VARCHAR(40) NULL,
    text_color VARCHAR(40) NULL,
    on_offline VARCHAR(40) NULL,
    has_audio VARCHAR(20) NULL,
    original_front_image VARCHAR(500) NULL,
    original_back_image VARCHAR(500) NULL,
    front_media VARCHAR(500) NULL,
    band_image_url VARCHAR(500) NULL,
    search_text TEXT NULL,
    season_key VARCHAR(80) NULL,
    class_key VARCHAR(80) NULL,
    number_value INT NOT NULL DEFAULT 2147483647,
    UNIQUE KEY uk_objekt_catalog_collection (collection_id),
    KEY idx_objekt_catalog_artist (artist)
);
