-- 유닛 폴더 조회(WHERE unit_id = ?)를 위한 인덱스
CREATE INDEX IF NOT EXISTS idx_archive_unit_id ON archive (unit_id);
