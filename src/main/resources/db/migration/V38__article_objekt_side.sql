ALTER TABLE article_objekt
    ADD COLUMN side VARCHAR(10) NOT NULL DEFAULT 'HAVE';

UPDATE article_objekt SET side = 'HAVE' WHERE side IS NULL OR side = '';
