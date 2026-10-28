-- 기존 일정은 제목 기반 자동 분류를 유지하며 관리자가 분류를 지정할 수 있다.
ALTER TABLE schedule ADD COLUMN IF NOT EXISTS category VARCHAR(24) NOT NULL DEFAULT 'AUTO';
