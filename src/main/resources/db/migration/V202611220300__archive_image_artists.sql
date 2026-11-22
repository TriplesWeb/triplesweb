-- 사진 게시글의 장별 출연 멤버. images_json 과 같은 순서의 문자열 배열.
ALTER TABLE archive
    ADD COLUMN image_artists_json TEXT NULL;
