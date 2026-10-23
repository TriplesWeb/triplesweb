CREATE TABLE media_artist (
    media_id BIGINT NOT NULL,
    artist_id BIGINT NOT NULL,
    PRIMARY KEY (media_id, artist_id),
    CONSTRAINT fk_media_artist_media
        FOREIGN KEY (media_id) REFERENCES media (id) ON DELETE CASCADE,
    CONSTRAINT fk_media_artist_artist
        FOREIGN KEY (artist_id) REFERENCES artist (artist_id) ON DELETE CASCADE
);

CREATE INDEX idx_media_artist_artist_id ON media_artist (artist_id);

CREATE TABLE schedule_artist (
    schedule_id BIGINT NOT NULL,
    artist_id BIGINT NOT NULL,
    PRIMARY KEY (schedule_id, artist_id),
    CONSTRAINT fk_schedule_artist_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_artist_artist
        FOREIGN KEY (artist_id) REFERENCES artist (artist_id) ON DELETE CASCADE
);

CREATE INDEX idx_schedule_artist_artist_id ON schedule_artist (artist_id);
