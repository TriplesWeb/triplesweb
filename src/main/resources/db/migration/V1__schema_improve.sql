-- 좋아요 테이블 제거 (Article.likes_count만 사용)
DROP TABLE IF EXISTS article_like;

-- 미사용 회원 테이블 제거 (구·신 네이밍 모두)
DROP TABLE IF EXISTS Members;
DROP TABLE IF EXISTS members;

-- Media: JSON 대신 DB — 스키마 재생성 (CamelCaseToUnderscores 기준)
DROP TABLE IF EXISTS Media;
DROP TABLE IF EXISTS media;
CREATE TABLE media (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    youtube_id    VARCHAR(32)  NOT NULL,
    tab           VARCHAR(32)  NOT NULL,
    media_title   VARCHAR(255) NOT NULL,
    media_dur     VARCHAR(32)  NOT NULL,
    view_count    BIGINT       NOT NULL DEFAULT 0,
    description   TEXT         NULL,
    sort_order    BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_media_youtube (youtube_id),
    KEY idx_media_tab_sort (tab, sort_order)
);
