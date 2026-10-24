-- 3인 이상 케미 (멤버 집합 단위)
CREATE TABLE IF NOT EXISTS chemistry_group (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_key VARCHAR(255) NOT NULL,
    members_json TEXT NOT NULL,
    label VARCHAR(120) NOT NULL,
    label_en VARCHAR(120) NULL,
    description TEXT NOT NULL,
    description_en TEXT NULL,
    image_url VARCHAR(512) NULL,
    links_json TEXT NULL,
    label_tone VARCHAR(20) NOT NULL DEFAULT 'NONE',
    CONSTRAINT uk_chemistry_group_member_key UNIQUE (member_key)
);
