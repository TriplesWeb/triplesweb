-- 로컬/공유 DB에 이미 컬럼이 있을 수 있어 IF NOT EXISTS로 둔다.
ALTER TABLE track ADD COLUMN IF NOT EXISTS youtube_video_id VARCHAR(20) NULL;

-- 공식 채널 발매곡/MV 영상 ID (https://www.youtube.com/watch?v=...)
UPDATE track SET youtube_video_id = 'lH6ZnRjvu5s' WHERE youtube_video_id IS NULL AND (title = '+(82)' OR title LIKE '%+(82)%');
UPDATE track SET youtube_video_id = '0cZ7o0Wn_dc' WHERE youtube_video_id IS NULL AND title = 'Generation';
UPDATE track SET youtube_video_id = '3TQd2ahq6oU' WHERE youtube_video_id IS NULL AND title = 'Rising';
UPDATE track SET youtube_video_id = '2tda_TCjz8w' WHERE youtube_video_id IS NULL AND title = 'Girls Never Die';
UPDATE track SET youtube_video_id = 'KMythzAnNK0' WHERE youtube_video_id IS NULL AND title = 'Invincible';
UPDATE track SET youtube_video_id = 'aKyt6Ahxwss' WHERE youtube_video_id IS NULL AND LOWER(title) = 'hit the floor';
UPDATE track SET youtube_video_id = 'xYXqVV4biGY' WHERE youtube_video_id IS NULL AND (title = 'Touch' OR title LIKE 'Touch+%');
UPDATE track SET youtube_video_id = 'uE_T_t6s_Go' WHERE youtube_video_id IS NULL AND title = 'Cherry Talk';
UPDATE track SET youtube_video_id = 'OvtoY0lfYJ8' WHERE youtube_video_id IS NULL AND (title = 'Girls'' Capitalism' OR title LIKE 'Girls%Capitalism%');
UPDATE track SET youtube_video_id = 'PqKZctzEW1o' WHERE youtube_video_id IS NULL AND title = 'Door';
UPDATE track SET youtube_video_id = 's3IfM-u5hVc' WHERE youtube_video_id IS NULL AND title = 'Just Do It';
UPDATE track SET youtube_video_id = '9bfWvaQskLU' WHERE youtube_video_id IS NULL AND (title = '내적 댄스' OR title LIKE '내적 댄스%');
UPDATE track SET youtube_video_id = 'uqJ6GDJSyfY' WHERE youtube_video_id IS NULL AND (title = 'Andless' OR title LIKE 'Andless%');
UPDATE track SET youtube_video_id = 'pTycfmVzdl8' WHERE youtube_video_id IS NULL AND (title LIKE '깨어%' OR title LIKE '%Are you Alive%' OR title LIKE '%Are You Alive%');
