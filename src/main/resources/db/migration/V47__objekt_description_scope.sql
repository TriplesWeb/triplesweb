-- 시즌·번호 설명이 멤버마다 달라 공통 키를 쓰지 않는 묶음.
CREATE TABLE IF NOT EXISTS objekt_description_scope (
    season VARCHAR(64) NOT NULL,
    collection_no VARCHAR(32) NOT NULL,
    PRIMARY KEY (season, collection_no)
);
