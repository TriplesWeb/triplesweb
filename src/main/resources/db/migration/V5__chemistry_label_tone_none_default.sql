-- 기본은 미선택(NONE, 흰색 글자). V4 기본 UNDECIDED는 의도적 분류가 아니므로 NONE으로 되돌림.
UPDATE chemistry SET label_tone = 'NONE' WHERE label_tone = 'UNDECIDED';

ALTER TABLE chemistry
    ALTER COLUMN label_tone SET DEFAULT 'NONE';
