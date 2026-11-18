-- 앨범 상세 커버 아래에 노출할 뮤직비디오(YouTube) ID
ALTER TABLE album ADD COLUMN IF NOT EXISTS music_video_youtube_id VARCHAR(20) NULL;
