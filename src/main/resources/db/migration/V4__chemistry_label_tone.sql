-- 케미명 표기 색상: UNDECIDED(미정) / RECENT(최근) / FAN(팬)
ALTER TABLE chemistry
    ADD COLUMN label_tone VARCHAR(20) NOT NULL DEFAULT 'UNDECIDED';
