-- 앨범 ↔ 아티스트(솔로·멀티 멤버) N:M 연결
CREATE TABLE IF NOT EXISTS album_artist (
    album_id  BIGINT NOT NULL,
    artist_id BIGINT NOT NULL,
    PRIMARY KEY (album_id, artist_id),
    KEY idx_album_artist_artist_id (artist_id),
    CONSTRAINT fk_album_artist_album
        FOREIGN KEY (album_id) REFERENCES album (album_id) ON DELETE CASCADE,
    CONSTRAINT fk_album_artist_artist
        FOREIGN KEY (artist_id) REFERENCES artist (artist_id) ON DELETE CASCADE
);
