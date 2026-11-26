-- 김유연과 공유빈의 생년월일을 올바른 값으로 정정한다.
UPDATE artist
SET birth_date = CASE eng_name
    WHEN 'yooyeon' THEN '2001-02-09'
    WHEN 'yubin' THEN '2005-02-03'
    ELSE birth_date
END
WHERE eng_name IN ('yooyeon', 'yubin');
