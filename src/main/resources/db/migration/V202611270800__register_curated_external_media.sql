-- 배지전쟁 시즌 1~4, 마법소녀 정하연, 13년 차 신인 김채연에서 확인한 외부 콘텐츠.
ALTER TABLE media ADD COLUMN IF NOT EXISTS curated_external BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TEMPORARY TABLE tmp_curated_external_media (
    youtube_id VARCHAR(32) NOT NULL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    duration VARCHAR(32) NOT NULL,
    views BIGINT NOT NULL,
    published_on DATE NOT NULL,
    artist_names VARCHAR(240) NOT NULL
) DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_curated_external_media (youtube_id, title, duration, views, published_on, artist_names) VALUES
    ('Yb0GtMc9Axc', '얼굴이 재밌어요,,근데 예능 달란트도 받아버린 배지에 눈 돌아간 소녀들｜Strong Girl:배지전쟁 EP.1｜Strong Girl:Badge War｜tripleS(트리플에스)', '30:17', 1764264, '2023-12-07', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유'),
    ('tWpMyqOV4F4', '삽질하다 진짜 싸운 아이돌｜Strong Girl:배지전쟁 EP.2｜Strong Girl:Badge War｜tripleS(트리플에스)', '33:44', 1324172, '2023-12-14', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유'),
    ('u7hlgjKC0Fs', '네가 ‘중도 포기할래’ 한 마디 했잖아? 그럼 이딴 결말 안 나왔어｜Strong Girl:배지전쟁 EP.3｜Strong Girl:Badge War｜tripleS(트리플에스)', '33:07', 858032, '2023-12-21', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유'),
    ('X8Emckj8yVA', '서바이벌에 반전이 빠지면 안 되지｜Strong Girl:배지전쟁 EP.4｜Strong Girl:Badge War｜tripleS(트리플에스)', '39:53', 1085718, '2023-12-28', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유'),
    ('H3Eidd3h4CE', '리얼리티도 투표로 결정되는 민주주의 아이돌 그룹 | tripleS(트리플에스) | LOVElution | EVOLution', '5:36', 146854, '2023-10-18', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유'),
    ('shuISGtyxIg', '배지전쟁 시즌2 기원 1일차 | EN JP CN | SIGNAL 240112', '12:04', 101188, '2024-01-12', ''),
    ('oebAcA5VTDw', '[Teaser] 24배 독기 품고 돌아온 24인 완전체 서바이벌｜배지전쟁 완전판:Girls Never Die｜Badge War 2｜tripleS(트리플에스)', '1:55', 332690, '2024-05-13', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유,린,주빈,정하연,박시온,김채원,설린,서아,지연'),
    ('aT-Euv1qj8s', '배지에 돌아버린 48개의 눈동자｜배지전쟁 완전판:Girls Never Die EP.1｜Badge War 2｜tripleS(트리플에스)', '39:48', 1780646, '2024-05-24', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유,린,주빈,정하연,박시온,김채원,설린,서아,지연'),
    ('aJMTlJUF_Qg', '인간적으로 머리끄덩이는 잡지 맙시다｜배지전쟁 완전판:Girls Never Die EP.2｜Badge War 2｜tripleS(트리플에스)', '35:28', 1535472, '2024-05-31', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유,린,주빈,정하연,박시온,김채원,설린,서아,지연'),
    ('O2yErIi6CGM', '어디서도 못 본 쇠파민 폭발 전개, 24인 걸그룹이 받는 극악무도한 벌칙｜배지전쟁 완전판:Girls Never Die EP.3｜Badge War 2｜tripleS(트리플에스)', '32:46', 1269945, '2024-06-07', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유,린,주빈,정하연,박시온,김채원,설린,서아,지연'),
    ('F2OiGrCFv3c', '독방에 갇힌 24인, 내 승부욕이 저것까지 이기나?｜배지전쟁 완전판:Girls Never Die EP.4｜Badge War 2｜tripleS(트리플에스)', '49:06', 1096646, '2024-06-14', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유,린,주빈,정하연,박시온,김채원,설린,서아,지연'),
    ('HEi18Clrc4k', '[Behind] 배지전쟁2 안 봤냐? 1theK 하드 탈탈 털어온 미공개S파일｜배지전쟁 완전판:Girls Never Die | Badge War 2｜tripleS(트리플에스)', '18:12', 349625, '2024-06-21', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유,린,주빈,정하연,박시온,김채원,설린,서아,지연'),
    ('iX_w4mzBIJE', '[Teaser] 아니 근데 진짜 트리플에스가 단체로 입수를 하더라니까?｜배지전쟁3｜Badge War 3 : No Badge No Power｜tripleS(트리플에스)', '2:12', 149772, '2025-05-21', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유,린,주빈,정하연,박시온,김채원,설린,서아,지연'),
    ('5qOrdPsMh_M', '트리플에스를 찾아온 딱지맨(?) "게임에 참가하시겠습니까?"｜배지전쟁3 EP.0｜Badge War 3 : No Badge No Power｜tripleS(트리플에스)', '5:01', 157842, '2025-05-26', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유,린,주빈,정하연,박시온,김채원,설린,서아,지연'),
    ('7JI5sO3GOwM', '''깨어'' 보니 제주도에서 배지전쟁 참가 중⛰️⚔️｜배지전쟁3 EP.1｜Badge War 3 : No Badge No Power｜tripleS(트리플에스)', '56:58', 1625525, '2025-05-30', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유,린,주빈,정하연,박시온,김채원,설린,서아,지연'),
    ('BXHHCDxufNM', '대놓고 싸움 조장하는 매운맛 룰이 추가되었습니다🔥｜배지전쟁3 EP.2｜Badge War 3 : No Badge No Power｜tripleS(트리플에스)', '47:28', 926609, '2025-06-06', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유,린,주빈,정하연,박시온,김채원,설린,서아,지연'),
    ('uMEAOR57SlY', '여자 48명이 모여 크게 한판 싸웠습니다💥｜배지전쟁3 EP.3｜Badge War 3 : No Badge No Power｜tripleS(트리플에스)', '56:23', 2041169, '2025-06-13', '윤서연,정혜린,이지우,김채연,김유연,김수민,김나경,공유빈,카에데,서다현,코토네,곽연지,니엔,박소현,신위,마유,린,주빈,정하연,박시온,김채원,설린,서아,지연'),
    ('5-T99rLSncg', '역대급 스케일, 소름 돋는 서사, 반전과 눈물이 함께한 최종 우승자는?｜배지전쟁3 EP.4｜Badge War 3 : No Badge No Power｜tripleS(트리플에스)', '1:01:51', 789007, '2025-06-20', ''),
    ('0hNuXQ0-FZM', '24인조 걸그룹 목소리만 듣고 멤버 이름 맞히기👂🏻 #tripleS #트리플에스 #BadgeWar3 #배지전쟁3', '1:00', 8457145, '2025-06-04', ''),
    ('gr6--nVQAS0', '배지에 진심인 tripleS의 배지전쟁3 비하인드 1 | EN JP CN | SIGNAL 250628', '17:19', 113775, '2025-06-28', ''),
    ('nS0Y3bKr3QI', '마지막까지 치열했던 황금배지의 주인공🥇🦢 | EN JP CN | SIGNAL 250704', '17:54', 93746, '2025-07-04', ''),
    ('ZwEVCXTdevw', '[Main Trailer] 상처 고난 또 눈물 ⚔️ 24인 역대급 서바이벌의 귀환 l 배지전쟁4 : Trust No One｜Badge War4｜tripleS(트리플에스)', '2:28', 117290, '2026-08-10', ''),
    ('DkbbkyHGfzs', '[Character Trailer] 🚨국제범죄수사대 요원 프로필 유출🚨l tripleS ''World Wild Women''｜배지전쟁 OST｜배지전쟁4 : Trust No One', '2:47', 242353, '2026-08-17', ''),
    ('M2ky7c5kIXo', '"나 몰아간 사람들 다 죽었으면 좋겠어" 국내 최초, 24인 대규모 마피아 게임｜배지전쟁4 EP.1｜Badge War 4 : Trust No One｜tripleS(트리플에스)', '1:21:05', 1433754, '2026-08-21', '공유빈,곽연지,김나경,김수민,김유연,김채연,김채원,니엔,린,마유,박소현,박시온,서다현,서아,설린,신위,윤서연,이지우,정하연,정혜린,주빈,지연,카에데,코토네'),
    ('gTnnzlARDZI', '첫 번째 살인의 밤, 정체를 드러낸 스파이｜배지전쟁4 EP.2｜Badge War 4 : Trust No One｜tripleS(트리플에스)', '1:20:41', 983249, '2026-08-28', ''),
    ('Z78ttVYqg58', '그래, 고개를 들어 스파이의 정체를 확인하세요｜배지전쟁4 EP.3｜Badge War 4 : Trust No One｜tripleS(트리플에스)', '1:36:29', 644256, '2026-09-04', ''),
    ('7znp7cWwTNs', '마침내 밝혀지는 마피아 게임의 최종 우승자, 요원 VS 스파이 황금 배지의 주인공은?｜배지전쟁4 EP.4｜Badge War 4 : Trust No One｜tripleS(트리플에스)', '2:15:32', 452239, '2026-09-11', ''),
    ('Qc_hauPxiCQ', '2026년 tripleS(트리플에스) 운명전쟁', '12:30', 191021, '2026-04-23', ''),
    ('qr7DVfzSeJc', '＜배지전쟁4＞EP.1 Commentary : moon ｜Badge War 4 : Trust No One｜tripleS(트리플에스)', '33:15', 56244, '2026-08-26', ''),
    ('ktqfjqImdKs', '＜배지전쟁4＞EP.2 Commentary : sun ｜Badge War 4 : Trust No One｜tripleS(트리플에스)', '29:18', 64809, '2026-09-02', ''),
    ('6jCMyCMx9yE', '＜배지전쟁4＞EP.3 Commentary : neptune｜Badge War 4 : Trust No One｜tripleS(트리플에스)', '26:43', 80427, '2026-09-09', ''),
    ('omb-vjG_hf4', '＜배지전쟁4＞EP.4 Commentary : zenith ｜Badge War 4 : Trust No One｜tripleS(트리플에스)', '30:22', 71765, '2026-09-16', ''),
    ('0GQnwODHS3k', '내가 아이돌이었으면 배지전쟁은 꼭 하고 싶음 | 배지전쟁 4 Behind | SIGNAL 260921', '23:12', 114444, '2026-09-21', ''),
    ('p9PGpE6-ViU', '마지막까지 배지전쟁에 얼마나 진심인지 감도 안 옴…｜배지전쟁 4 Behind | SIGNAL 260923', '21:15', 73110, '2026-09-23', ''),
    ('fPQcD4FhCec', '진짜 중의 진짜를 찾아라 | 배지전쟁4 외전 : WHO''S MVP｜tripleS(트리플에스)', '0:42', 37521, '2026-10-06', ''),
    ('wfaL-baiNUo', '요원 VS 스파이, 진짜 MVP는 누구?! | 배지전쟁4 외전 : WHO''S MVP｜tripleS(트리플에스)', '40:36', 90440, '2026-10-07', ''),
    ('Mh6i-RbXMmo', '😉 하요이 보고 싶어? 📢 함성 소리 작다 임마들아, 라우럴 ⤴ [마법소녀 정하연] l 비하인드 EP.6', '9:19', 10969, '2026-11-24', '정하연'),
    ('z3QzdQOjoc4', '👆재능은 애매하지 않다...🧐 하요이 배우 데뷔 기원 1일 차 🙏 [마법소녀 정하연] l 코리아엔터아카데미 l EP20 [EN]', '16:53', 197065, '2026-11-17', '정하연'),
    ('AaHW9tBPYTg', '하요이 폼미💥 게임은 기세잖아, 안 그래? 🔥⚡ [마법소녀 정하연] l 라이엇PC방 l EP19', '16:15', 187349, '2026-11-10', '정하연'),
    ('KH1CqP3yc8w', '시켜 줘.. 임마들의 명예 요리사 🥘🍛 [마법소녀 정하연] l 나나방콕 l EP18', '19:45', 236415, '2026-10-27', '정하연'),
    ('8Bcwva9O4AQ', '👩‍🍳 먹짱(?) 하요이코어 그냥 느끼자 🥄🍨 (feat.모찌즈) [마법소녀 정하연] l 비하인드', '11:23', 19960, '2026-10-20', '정하연'),
    ('xqWOLvDLpjI', '마법소녀는 나야 둘이 될 수 없어 ~ 👯‍♀️🤷‍️️ [마법소녀 정하연] l 과일에 반하다, 프루타 l EP17', '18:54', 315181, '2026-10-13', '정하연'),
    ('bO7Xsd8ghUo', '과일 깎는 하요이 실존? 💖👀 임마들은 반할 수박에 🍉 (진짜 반하진🥭❌) [마법소녀 정하연] l 과일에 반하다, 프루타 l EP16 [EN]', '18:57', 267875, '2026-10-06', '정하연'),
    ('Iq63t-BBMLU', '🫵🏻 하요이, 너 치킨 튀기는 거 재능 있어 계속해 🐔🍗[마법소녀 정하연] l 슈퍼크리스피 l EP15', '18:07', 308589, '2026-09-29', '정하연'),
    ('csW38MHxe5g', '하요이가 이렇게 커여우면 임마들은 어떡하라고, 어떻하라고, 우뜩하라고 💖🎬 [마법소녀 정하연] l 비하인드', '9:20', 171228, '2026-09-22', '정하연'),
    ('1t-NaqKq2Bo', '이거 마시면 오늘부터 임마 되는 거다? 😉 [마법소녀 정하연] l 야화 혼술바 l EP14 [EN]', '15:34', 224870, '2026-09-15', '정하연'),
    ('9itQVKZh5AQ', '하요이 손은 약손~🤗 하요이한테 관리받을 파티원 구함 (O/N) [마법소녀 정하연] l 약손명가 l EP13', '15:47', 292936, '2026-09-08', '정하연'),
    ('Iu-JvMXm3WM', '🎀파자마 + 맛있는 거 = 걸스 파티 근본 조합이지👯‍♀ [마법소녀 정하연] l MAY FLOWER l EP12 [EN]', '18:26', 339725, '2026-09-01', '정하연'),
    ('Aj5j2cdKwiM', '🤡임마들 한정 정통삐 하요이 왔다🎈 웃을 준비 됐지? [마법소녀 정하연] l 비하인드 EP.3', '7:52', 44266, '2026-08-25', '정하연'),
    ('5bRvVs_H4RU', '🥢🔥하요이가 상여자 샤브샤브 낋이는 법 알려줄게 [마법소녀 정하연] l 샤브온당 l EP11', '16:02', 359175, '2026-08-18', '정하연'),
    ('y5ZBJBarwrU', '치킨이 맛있어서 라을라 🍗 [마법소녀 정하연] l 치킨집 알바 편', '20:53', 285657, '2026-08-11', '정하연'),
    ('oyZPX2DjyGU', '🏁무면허로 레이싱? 책없쾌 그 자체🥴💥 [마법소녀 정하연] l 심 레이싱 l EP9', '16:38', 352809, '2026-08-04', '정하연'),
    ('eDLIFUQTGlk', '마법소녀 정하연 정상 영업합니다  [마법소녀 정하연]  l 비하인드 2', '10:12', 76299, '2026-07-28', '정하연'),
    ('EyG9xqX3ohI', '😧보겜카에서 왜 이리 무리해¿ [마법소녀 정하연] l EP 8', '20:05', 339994, '2026-07-21', '정하연'),
    ('Td_9l7KxNjA', '🐟🫧횟집에~서 누가 돌아왔게~ 하연이랑 같이먹어연 [마법소녀 정하연] | EP7', '15:31', 321211, '2026-07-14', '정하연'),
    ('NnDBfOYw7mY', '🔆7월의 하요이👉🏻세차했음청년 [마법소녀 정하연] l EP 6', '14:48', 193394, '2026-07-07', '정하연'),
    ('cPkqQSTZHKQ', '하연쌤도 힘들잖아 지쳤다잖아 [마법소녀 정하연] l 어린이집 l EP5', '15:35', 259530, '2026-06-30', '정하연'),
    ('DcTZ3E45BZ8', '특급 마법소녀의 삶이 어떠냐고? 다이죠부이데쇼 [마법소녀 정하연] l 비하인드 모음zip', '8:38', 47660, '2026-06-23', '정하연'),
    ('L5iGAvkdmY4', '개팔자가 상팔자라더니 [마법소녀 정하연] l 펫워크 l EP 4', '13:49', 245899, '2026-06-09', '정하연'),
    ('q6LEb8bLopI', '프랩이 힘들어도 또 먹고 싶은 곱창집입니다 [마법소녀 정하연] l 순삭곱창 l EP 3 [EN]', '14:39', 301414, '2026-06-02', '정하연'),
    ('P8LD9UN0a00', '어서 와~ 룸 투어는 처음이지?😘 마법소녀 홈웨어 A to Z [마법소녀 정하연] l 쥬니쥬 l EP.2 [EN]', '18:37', 259277, '2026-05-19', '정하연'),
    ('mML18z4B7Wo', '❝지치~고 병~든~♪❞ 나 대신 일할 임마 구함🫠 [마법소녀 정하연] l 오프뷰티 l EP.1 [EN]', '16:59', 273205, '2026-05-12', '정하연'),
    ('UwIBqvUGd58', '어 난데 마법소녀 변신 가방 공유한다🤫💨 [마법소녀 정하연] l EP.0', '2:38', 58173, '2026-05-05', '정하연'),
    ('EdWGpA5ZyJk', '🤔내 알고리즘이 알려준 마법소녀의 규칙✍🏻 [마법소녀 정하연] l 2차 티저', '0:39', 138158, '2026-04-30', '정하연'),
    ('gkRebr_9yTk', '💫 특급 마법소녀의 첫 출근? 이지까까브로〰︎ [마법소녀 정하연] l 1차 티저', '1:21', 170025, '2026-04-28', '정하연'),
    ('DRB_vCLbu7A', '[ENG] 아침 민낯 김채연', '38:05', 183214, '2026-11-26', '김채연'),
    ('DkMMmhf2dos', '[ENG] 채연이랑 여행갈래? 구례! 그럼 들어와', '57:41', 386407, '2026-11-19', '김채연'),
    ('QCVBz5QxJUk', '[ENG] 김채연 각성모드', '27:42', 115113, '2026-11-12', '김채연'),
    ('XeWldWYLuOA', '[ENG] 결혼하고 싶어지면 어떡하죠?ㅠㅠ', '27:19', 701961, '2026-11-05', '김채연'),
    ('QzWeFX41QqA', '[ENG] 고삐 풀린 김채연', '30:48', 703569, '2026-10-29', '김채연,윤서연'),
    ('K6W3qd2OLhA', '[ENG] 저 옷 못 입나요?', '37:23', 553028, '2026-10-22', '김채연'),
    ('o6CWaCjkI98', '[ENG] 김채연 지금 저점일까 고점일까', '30:16', 1540645, '2026-10-15', '김채연,마유'),
    ('IE0hEAgT2UY', '[ENG] 입덕하실래요?', '26:01', 496259, '2026-10-08', '김채연'),
    ('pdDiaihL1Y8', '[ENG] 김채연 민낯 공개', '34:52', 1454145, '2026-10-01', '김채연'),
    ('192jWKtEUg8', '[ENG] 오랜만에 외쳐 볼까요? 돌려 돌려 돌림판!', '48:40', 1683075, '2026-09-24', '김채연'),
    ('znDFxVolabk', '[ENG] 혹시 저희 아시나요?', '30:06', 1225672, '2026-09-17', '김채연,김유연'),
    ('Wj2ex4_FYDM', '[ENG] 유튜버 김채연으로 시작합니다 (W. 기안84)', '37:40', 2517033, '2026-09-10', '김채연');

-- 기존 행의 ID, 설명, 멤버 연결을 유지하고 빠진 메타데이터만 보완한다.
UPDATE media m
JOIN tmp_curated_external_media curated ON BINARY m.youtube_id = BINARY curated.youtube_id
SET m.tab = 'external',
    m.curated_external = TRUE,
    m.media_title = IF(TRIM(m.media_title) = '', curated.title, m.media_title),
    m.media_dur = IF(TRIM(m.media_dur) IN ('', '0:00'), curated.duration, m.media_dur),
    m.view_count = IF(m.view_count = 0, curated.views, m.view_count),
    m.published_on = COALESCE(m.published_on, curated.published_on);

INSERT INTO media (youtube_id, tab, media_title, media_dur, view_count, published_on, curated_external)
SELECT curated.youtube_id, 'external', curated.title, curated.duration, curated.views, curated.published_on, TRUE
FROM tmp_curated_external_media curated
WHERE NOT EXISTS (
    SELECT 1 FROM media m WHERE BINARY LOWER(m.youtube_id) = BINARY LOWER(curated.youtube_id)
);

INSERT INTO media_artist (media_id, artist_id)
SELECT m.id, a.artist_id
FROM tmp_curated_external_media curated
JOIN media m ON BINARY m.youtube_id = BINARY curated.youtube_id
JOIN artist a ON FIND_IN_SET(BINARY a.name, BINARY curated.artist_names) > 0
WHERE NOT EXISTS (
    SELECT 1 FROM media_artist existing WHERE existing.media_id = m.id
);

DROP TEMPORARY TABLE tmp_curated_external_media;
