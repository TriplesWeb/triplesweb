-- 케미 영문 표기 (label/description과 독립적으로 편집)
ALTER TABLE chemistry
    ADD COLUMN label_en VARCHAR(120) NULL,
    ADD COLUMN description_en TEXT NULL;
