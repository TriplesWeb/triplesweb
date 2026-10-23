-- 미디어·스케줄 ↔ 아티스트 N:M 연결
CREATE TABLE IF NOT EXISTS media_artist (
    media_id  BIGINT NOT NULL,
    artist_id BIGINT NOT NULL,
    PRIMARY KEY (media_id, artist_id),
    KEY idx_media_artist_artist_id (artist_id),
    CONSTRAINT fk_media_artist_media
        FOREIGN KEY (media_id) REFERENCES media (id) ON DELETE CASCADE,
    CONSTRAINT fk_media_artist_artist
        FOREIGN KEY (artist_id) REFERENCES artist (artist_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS schedule_artist (
    schedule_id BIGINT NOT NULL,
    artist_id   BIGINT NOT NULL,
    PRIMARY KEY (schedule_id, artist_id),
    KEY idx_schedule_artist_artist_id (artist_id),
    CONSTRAINT fk_schedule_artist_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_artist_artist
        FOREIGN KEY (artist_id) REFERENCES artist (artist_id) ON DELETE CASCADE
);
