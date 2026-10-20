-- 멤버 탭 하단 컨텐츠(추후 채울 영역) + 그룹 소개 문구 저장
ALTER TABLE artist
    ADD COLUMN solo_content TEXT NULL,
    ADD COLUMN broadcast_content TEXT NULL;

CREATE TABLE site_content (
    content_key   VARCHAR(64)  NOT NULL,
    content_value LONGTEXT     NOT NULL,
    PRIMARY KEY (content_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
