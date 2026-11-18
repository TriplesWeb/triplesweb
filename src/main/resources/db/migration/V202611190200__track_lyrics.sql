-- 앨범 트랙 가사(관리자 입력). [멤버명] 파트 태그 포함 가능
ALTER TABLE track ADD COLUMN IF NOT EXISTS lyrics LONGTEXT NULL;
