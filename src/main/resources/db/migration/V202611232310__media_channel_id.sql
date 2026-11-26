-- 이전 미리보기 DB에 이미 적용된 경우에도 기존 채널 정보를 유지한다.
ALTER TABLE media
    ADD COLUMN IF NOT EXISTS channel_id VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_media_channel_id ON media (channel_id);
