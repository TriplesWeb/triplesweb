-- 출연진=유닛 구성 완전일치 글의 unit_id 백필은 ArchiveUnitIdBackfillRunner 가 1회 수행한다.
CREATE TABLE IF NOT EXISTS app_one_shot (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
