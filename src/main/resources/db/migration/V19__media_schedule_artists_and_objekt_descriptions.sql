-- media_artist / schedule_artist 연결 테이블은 main의 V13__artist_media_and_schedule_links.sql에서 생성된다.
-- 이 마이그레이션은 오브젝트 설명만 추가한다.
CREATE TABLE IF NOT EXISTS objekt_description (
    id BIGINT NOT NULL AUTO_INCREMENT,
    objekt_key VARCHAR(255) NOT NULL,
    description TEXT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_objekt_description_key UNIQUE (objekt_key)
);