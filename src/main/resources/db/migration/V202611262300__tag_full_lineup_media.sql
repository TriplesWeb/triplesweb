-- 확인한 완전체 MV·공연·방송 영상의 등록된 멤버가 비어 있을 때만 S1~S24를 연결한다.
INSERT INTO media_artist (media_id, artist_id)
SELECT m.id, roster.artist_id
FROM media m
CROSS JOIN (
    SELECT artist_id, COUNT(*) OVER () AS member_count
    FROM artist
    WHERE name IN (
        '윤서연', '정혜린', '이지우', '김채연', '김유연', '김수민',
        '김나경', '공유빈', '카에데', '서다현', '코토네', '곽연지',
        '니엔', '박소현', '신위', '마유', '린', '주빈',
        '정하연', '박시온', '김채원', '설린', '서아', '지연'
    )
) roster
WHERE roster.member_count = 24
  AND CAST(m.youtube_id AS BINARY) IN (
    -- Girls Never Die, 깨어, Baby Flower, Andless 공식 MV 및 Andless 공식 무대.
    '2tda_TCjz8w', 'pTycfmVzdl8', '6q8UMVXhXsE', 'uqJ6GDJSyfY',
    '1v0PehR58tM', 'VORkSRD_ihc', 'hR_p621QYcw',
    'swRnA365whY', 'mD16OVDyLQU', 'HwTZTz9j2e0', 'f5vyGo8f7Oc',
    -- 전체 멤버 출연으로 명시된 외부 콘텐츠와 음악방송.
    'suhkSzFBgCY', 'HaUXySGq2DY', 'yXRkSkN9sZk', 'LMu-LA98a6o', 'VhI8qOPXfS0', 'yWX_5YpuGbc',
    'n7GxStccDw4', 'zua2EkwLpN0', '96NryRysvqE', 'xXOFtBsnEFA',
    'fFC21jy_mfE', 'zX_ROmZogkU', 'y2Zx2JpmUU8', 'b8Zo4gmH-Ig',
    'xeQVvnhImJk', 'wgUvEGKcpaA', '8vIeZQ1gF2w', 'bnFFYbOMRBI',
    'HH1Owe_6UUE', 'bPuKdMkNE5o', '-Us2XhXn254', 'I7wjiJa02yc',
    'mivd53H0Vm4', 'm6el-2QsiZ0', 'u_BMt8PTXj8', 'bA2ssl3Z364',
    '3uDnjDUsb04', 'BB_n6kgp0VU', 'YlLb8gHziPk', 'gHZtZfw2Spo',
    'XGDpiUeC9Sw', 'iDA2vvYblnU', '7ZSdYMfQ028', 'iDBy8IkfyfM',
    '0UdiyAgOu3o', '2fsXORTi0Rk', 'NTFfnRS1KJY', 'EhKTcJIKYv4'
  )
  AND NOT EXISTS (
    SELECT 1 FROM media_artist existing WHERE existing.media_id = m.id
  );
