-- 출연진이 유닛 구성과 달라도 수동 선택한 소속 유닛을 배지에 쓰기 위해 저장한다.
ALTER TABLE archive
    ADD COLUMN unit_id BIGINT NULL;
