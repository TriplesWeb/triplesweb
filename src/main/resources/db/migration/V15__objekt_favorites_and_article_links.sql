CREATE TABLE IF NOT EXISTS objekt_favorite (
    id BIGINT NOT NULL AUTO_INCREMENT,
    visitor_key VARCHAR(100) NOT NULL,
    objekt_key VARCHAR(255) NOT NULL,
    collection_id VARCHAR(255),
    member_name VARCHAR(100),
    thumbnail_image VARCHAR(1000),
    front_image VARCHAR(1000),
    back_image VARCHAR(1000),
    season_name VARCHAR(100),
    class_name VARCHAR(100),
    collection_no VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_objekt_favorite_visitor_objekt UNIQUE (visitor_key, objekt_key)
);

CREATE TABLE IF NOT EXISTS article_objekt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    objekt_key VARCHAR(255) NOT NULL,
    collection_id VARCHAR(255),
    member_name VARCHAR(100),
    thumbnail_image VARCHAR(1000),
    front_image VARCHAR(1000),
    back_image VARCHAR(1000),
    season_name VARCHAR(100),
    class_name VARCHAR(100),
    collection_no VARCHAR(100),
    PRIMARY KEY (id),
    CONSTRAINT uk_article_objekt UNIQUE (article_id, objekt_key),
    CONSTRAINT fk_article_objekt_article FOREIGN KEY (article_id) REFERENCES article(id) ON DELETE CASCADE
);
