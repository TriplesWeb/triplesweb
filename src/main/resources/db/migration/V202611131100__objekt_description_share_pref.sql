CREATE TABLE IF NOT EXISTS objekt_description_share_pref (
    season VARCHAR(64) NOT NULL,
    collection_no VARCHAR(32) NOT NULL,
    members VARCHAR(1024) NOT NULL DEFAULT '',
    numbers VARCHAR(2048) NOT NULL DEFAULT '',
    PRIMARY KEY (season, collection_no)
);
