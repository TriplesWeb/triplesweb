-- 앨범 규격을 열거형 코드 대신 자유 입력 문구로 저장 (길이 확장)
ALTER TABLE album
    MODIFY COLUMN album_format VARCHAR(120) NULL;
