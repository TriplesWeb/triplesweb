-- 유튜브 설명이 VARCHAR(255) 등에 남아 있으면 INSERT 시 1406 발생 → TEXT로 확장
ALTER TABLE media MODIFY COLUMN description TEXT NULL;
