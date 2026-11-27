-- 공개 채널 구독자와 영상 조회수가 모두 1만 이상인 원본 해시태그 영상만 등록한다.
-- 운영 배포 전에 공유 DB를 사용하는 로컬 앱도 같은 물리 컬럼을 읽을 수 있도록 컬럼명을 유지한다.
ALTER TABLE media CHANGE COLUMN IF EXISTS curated_tab curated_external BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TEMPORARY TABLE tmp_verified_hashtag_media (
    youtube_id VARCHAR(32) NOT NULL PRIMARY KEY,
    channel_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    duration VARCHAR(32) NOT NULL,
    views BIGINT NOT NULL,
    published_on DATE NOT NULL,
    artist_names VARCHAR(240) NOT NULL
) DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_verified_hashtag_media (youtube_id, channel_id, title, duration, views, published_on, artist_names) VALUES
    ('bufoKYZXpKg', 'UCqq-ovGE01ErlXakPihhKDA', 'tripleS(트리플에스) - Girls Never Die | 수트댄스 | Suit Dance | Performance | 4K', '3:39', 7071502, '2024-05-09', ''),
    ('Xvn984haczI', 'UCiwQRG2sCcfjKkgxMEdJGPg', '[#전참시] 전참시 역대 최다 카메라 & 오디오 동원!😂 트리플에스 숙소 생활 대공개✨ㅣ#트리플에스 MBC240713방송', '22:32', 3053977, '2024-07-14', ''),
    ('YUsNlprz-lA', 'UCEIi7zFR_wE23jFncVtd6-A', 'tripleS(트리플에스) Visionary Vision ''Hit the Floor'' (4K) | STUDIO CHOOM ORIGINAL', '3:27', 1830133, '2024-10-24', ''),
    ('ErYrBN98hdo', 'UCSSkHIU1-nL_FeCjeZ_Xtvg', '트리플에스 김채연, 보고만 있어도 미소가 절로 나는 현시점 워터밤 최고의 유망주 │ 노빠꾸탁재훈 시즌4 EP.29', '21:44', 2011057, '2026-05-27', '김채연'),
    ('kjdeK_daT8M', 'UCiwQRG2sCcfjKkgxMEdJGPg', '[#전참시] 45인승 버스로 소화하는 완전체 스케줄✨ 2열 종대로 각 맞춰 움직이는 트리플에스😆ㅣ#트리플에스 MBC240713방송', '23:17', 1738003, '2024-07-15', ''),
    ('ZVHa_9ubhR8', 'UCtfyAiqf095_0_ux8ruwGfA', '치어리더 채연이 말아주는 홀리몰리 아웃송 직캠 [성수기 미방분] #Shorts', '0:18', 2722343, '2026-04-11', '김채연'),
    ('g6_AeqpvFcI', 'UCtfyAiqf095_0_ux8ruwGfA', '치어리더 트리플에스(tripleS) 채연 아웃송 직캠 [성수기 미방분] #Shorts', '0:18', 2465420, '2026-04-13', '김채연'),
    ('b0dWyMiSsb4', 'UC1Rz7a_DWtsE9cQwSyElE0Q', '돌아온 트리플에스 김채연', '20:17', 1372192, '2025-11-30', '김채연'),
    ('f4jbfoAncZo', 'UCtfyAiqf095_0_ux8ruwGfA', '승리 요정 김채연 LG트윈스 아웃송 직캠 [성수기 미방분] #shorts', '0:16', 1955207, '2026-04-10', '김채연'),
    ('LAoCP7_mHVs', 'UCtfyAiqf095_0_ux8ruwGfA', '대한민국 월드컵 16강 가능?⚽️🏆 축알못 김채연의 월드컵 정복기 [성수기 EP.33] | 월드컵 | 축구 | 국가대표', '26:47', 251481, '2026-04-24', '김채연'),
    ('wq-z6UvYFG4', 'UCtfyAiqf095_0_ux8ruwGfA', '트리플에스 김채연이 말아주는 LG트윈스 NEW 아웃송 [성수기] #shorts', '0:17', 569587, '2026-04-09', '김채연'),
    ('sE0Fq62VTjE', 'UCtfyAiqf095_0_ux8ruwGfA', '이선민 재산 플러팅에 멘붕 온 김채연 [성수기] #shorts', '0:23', 792559, '2026-04-22', '김채연'),
    ('Ce_StyiRk2o', 'UCtfyAiqf095_0_ux8ruwGfA', '🇵🇹호날두도 반할 김채연 표 세리머니 [성수기] #shorts', '0:29', 678999, '2026-04-24', '김채연'),
    ('X6gtiWu0508', 'UCritGVo7pLJLUS8wEu32vow', '[SY컴퍼니] #유연 사직서 공유의 건 ​⁠​⁠@triplescosmos #YooYeon #트리플에스 #tripleS', '0:55', 578267, '2026-09-13', '김유연'),
    ('Z2LtG_vu9to', 'UCG1yCJ0i5iAH1lixElahYUw', '아저씨들은 몰라요 #김유연', '0:52', 81580, '2026-10-02', '김유연'),
    ('Fon37scZ01o', 'UCuz_Y5rkcRd6kTGqLO0VgxA', '트리플에스 유연의 수능 공략법ㅋㅋ#트리플에스#김유연#미미미누', '0:12', 53561, '2026-09-28', '김유연'),
    ('Sts-kP3zq1I', 'UCF3UEVPt5zZTlBVJwDG317g', '비비X김나경, 자매가 동반 출연하면 생기는 일｜아는 형님｜JTBC 250125 방송', '25:40', 1401614, '2025-02-07', '김나경'),
    ('t_GDWaeSrxg', 'UCe52oeb7Xv_KaJsEzcKXJJg', '[#최애직캠] tripleS msnz GONG YUBIN (트리플에스 미소녀즈 공유빈) – Christmas Alone | 쇼! 음악중심 | MBC251129', '3:25', 12524, '2025-12-01', '공유빈'),
    ('GsxYzDJocj4', 'UCvEEeBssb4XxIfWWIB8IjMw', '#tripleS #유빈 #카에데 님과 마주친 순간 fly high🦋 #KISSOFLIFE #HANEUL #트리플에스 #Sticky #Sticky_Challenge #Shorts', '0:26', 881828, '2024-08-25', '공유빈,카에데'),
    ('Tl6q9h2rv0c', 'UCEpFoWeCMCo5z3EvWaz6hQQ', '#Cherish_CHALLENGE with #카에데 🤍#Kaede #カエデ #tripleS #트리플에스 #トリプルS #MOKA #모카 #ILLIT #아일릿 #Cherish', '0:21', 321182, '2024-11-04', '카에데'),
    ('RoSUnTLgeYw', 'UC8PPjyqCQJtNfEwHBcq_uxw', '🩶👀 #MOYA with #tripleS #Kaede #Kotone @triplescosmos #LNGSHOT #4SHOVILLE', '0:42', 344397, '2026-06-01', '카에데,코토네'),
    ('wqrTiWdxpGY', 'UCHYMuI_gpM-iA3RfgGZ28yw', '축 쳐지는 주말 이 영상으로 날려버려요🌏 #galaxykids #tripleS #jupiter #mercury #코토네 #신위', '0:15', 113541, '2024-07-06', '코토네,신위'),
    ('viFuyBpZ_1k', 'UCcTq_itv43G05dGVydRTJew', '코토네, 나경님과 I Like Something Casual 🌟😊 #triples #트리플에스 #코토네 #나경 #로시', '0:32', 37254, '2023-10-14', '김나경,코토네'),
    ('v3zh5rhKMxY', 'UCyc9sUCxELTDK9vELO5Fzeg', '#tripleS #EVOLution #코토네 님과 함께 ba-ba-ba-ba-ba bombarda🖤 #션 #Billlie #빌리 #DANG! #DANG_Challenge', '0:26', 33375, '2023-10-26', '코토네'),
    ('0TlAJQXfA4I', 'UCwOSPKpkwK4ZN0NJZ7rWpIQ', '블링블링💎 #트리플에스 #코토네 님과 떠나요~! 😆 #Geenius #Voyage_Challenge', '0:23', 19323, '2024-02-06', '코토네'),
    ('4t0JPaly_fU', 'UCah-d4pYWd5UT6xFbUZgFtg', '이런 누추한 곳에 귀한 분이🤩 트리플에스(tripleS) 곽연지의 본격 김유연 기 살리기 프로젝트🔥｜극과극 리뷰 Lady & Gentleman 8화', '14:02', 372983, '2025-05-06', '김유연,곽연지'),
    ('fF91qh7fg4g', 'UCkrDEGNM3mqZXHk2MfnOjMw', '#착젤싫_Challenge with #니엔 🪽 #최예나 #YENA #tripleS #착하다는말이제일싫어 #YENA_Being_a_good_girl_hurts', '0:17', 280890, '2025-08-08', '니엔'),
    ('jkAwL3mHCrA', 'UC-we351yXoKcODFHiKrC84Q', '본 적 없는 Way #tripleS #니엔 선배님과 챌린지💖 #WaveWay #웨이브웨이 #SAYMYNAME #세이마이네임 #SMN #SOHA #소하 #트리플에스 #Nien', '0:23', 76004, '2024-11-05', '니엔'),
    ('iLkjgCYfoE4', 'UCkJ1rbOrsyPfBuHNfnLPm-Q', '거칠게 좀 더 TRIGGER❤️ with #tripleS #니엔 #신위 #THEBOYZ #더보이즈 #SUNWOO #선우 #導火線 #도화선 #TRIGGER', '0:23', 74129, '2024-11-09', '니엔,신위'),
    ('TtOq761VlLU', 'UCG1yCJ0i5iAH1lixElahYUw', '넉까지 마.피아 with. 넉살, #러블루션 #서연 & #니엔 | 배성재의 텐 | 20230906 | @triplescosmos', '1:28:45', 34903, '2023-09-06', '윤서연,니엔'),
    ('0osOv7_liq8', 'UCfkXDY7vwkcJ8ddFGz8KusA', '#MAESTRO 🪄 with #신위 #Xinyu #트리플에스 #tripleS #준 #JUN #SEVENTEEN #세븐틴 #마에스트로 #SVT_MAESTRO', '0:20', 1681591, '2024-05-11', '신위'),
    ('y8wDy9HnE7s', 'UCdVD0MsYecQaIE5Ru-pOIQQ', '#tripleS #신위 도 Fly Up‼️#RIIZE #라이즈 #SUNGCHAN #성찬#트리플에스 #XINYU #RISEandREALIZE #FlyUp #ODYSSEY', '0:12', 849576, '2025-05-26', '신위'),
    ('uxwbGQV_IWQ', 'UCSAp0Yl9S0Zq5uDqE6im_XQ', '#tripleS #신위 님과 ''Feel the POP'' 💘 @triplescosmos', '0:19', 143374, '2024-05-17', '신위'),
    ('YTNuvVYPixM', 'UC8C6QOPDVYwmuaSBZksefdg', '#tripleS #신위 #하연 님과 #마음따라뛰는건멋지지않아 ❔ #TWS #한진 #신유', '0:29', 241909, '2025-05-20', '신위,정하연'),
    ('EusvUCCb_uA', 'UC8whlOg70m2Yr3qSMjUhh0g', 'TAP🩵🤎👉 #tripleS #트리플에스 #VisionaryVision #HyeRin #혜린 #Xinyu #신위 #Kep1er #휴닝바히에 #TIPITAPchallenge', '0:21', 64127, '2024-11-07', '정혜린,신위'),
    ('Fu7YyaKM9l4', 'UCmq9jjpHA69PwAwlA0DwOsg', '#tripleS #신위 선배님의 오늘 하루는 Vacation! FREEZE - 🧊', '0:30', 90911, '2025-06-12', '신위'),
    ('s_1J9drou5o', 'UCyc9sUCxELTDK9vELO5Fzeg', '#tripleS #린 #신위 님과 함께해서 기분 좋은 갸르릉 (ฅ^·͈༝·͈^ฅ) #하루나 #션 #Billlie #빌리 #트리플에스 #기억사탕챌린지 #memory_challenge', '0:27', 17789, '2024-11-01', '신위,린'),
    ('m-nlfqX7rp0', 'UC0novDS2VRX59Trmbf83I3g', '현실은 영화처럼 쉽지가 않더라 🎬🥹 #트리플에스 #신위 #니엔 님✨​ ⁨@triplescosmos #shorts', '0:46', 26920, '2026-01-17', '니엔,신위'),
    ('14rptXtm_nU', 'UC0novDS2VRX59Trmbf83I3g', '#유키 #ICHILLIN #주니 #tripleS #신위 님에게 꼭 전하고 싶은 말 ✏️💌 @ICHILLIN @triplescosmos #shorts', '0:19', 27379, '2026-04-22', '신위'),
    ('F4GDkaAUzEo', 'UCHD1jo5RhijLfx5-0Ehe_cg', 'It''s not a waste of time😎#tripleS #트리플에스 #Mayu #마유 #Nien #니엔 #andTEAM #앤팀 #RunWild #NICHOLAS #MAKI', '0:23', 329619, '2025-05-31', '니엔,마유'),
    ('1B8GqLrT9Hs', 'UCXbRURMKT3H_w8dT-DWLIxA', '온 세상이 하얗게❄️ with #트리플에스 #마유 #채원', '0:21', 377116, '2025-12-11', '마유,김채원'),
    ('Nw3zdbRR2o8', 'UC1rQlcqyynQ-_XLRZSAaREQ', '[tripleS 마유] Love Wave OST ''Polaroid'' 발매 인터뷰', '0:23', 113370, '2024-11-14', '김나경,마유,박시온,김채원'),
    ('zrqvKhGdb4E', 'UCtfyAiqf095_0_ux8ruwGfA', '트리플에스 마유가 분노한 이유🔥[성수기] #shorts', '0:31', 54635, '2026-03-31', '김채연,마유'),
    ('FJSdZU76be0', 'UC1rQlcqyynQ-_XLRZSAaREQ', '[ENG] tripleS(나경, 마유, 시온, 채원) ''Polaroid'' 인터뷰 (interview)', '3:19', 24015, '2024-12-05', '김나경,마유,박시온,김채원'),
    ('Wd2Bbh319-k', 'UCKNZsAeQXpvI-Mpoc0ZKhsA', '[ALLIVE] tripleS(박시온X주빈X서다현X김채연) - Baby Flower | #MBCRADIO #정오의희망곡김신영입니다 #tripleS | MBC 260603 방송', '3:19', 57875, '2026-06-03', '김채연,서다현,주빈,박시온'),
    ('1IsDID3Ec3c', 'UCHWhn9xw7x8NPPnV6eWQDQA', '#SERVE with #지연 & #설린 선배님 ✨ @triplescosmos #XLOV #WUMUTI #tripleS #JiYeon #Sullin #I_God', '0:28', 358811, '2026-06-10', '설린,지연'),
    ('Ux28gc_WorA', 'UCOB_HGqE2t0XcACau0JxGdg', '선배가 위험할 때 트리플에스 의리', '2:03', 2218279, '2026-10-21', ''),
    ('CaB4ztTPyNQ', 'UCpBlDSUyOpP1WqWVJxZpGLg', '[#MMA2024] tripleS (트리플에스) - Girls Never Die (MMA ver.) | #tripleS #트리플에스', '6:29', 1099775, '2024-11-30', ''),
    ('adRTpmnOti4', 'UCRmm8763aqO0CoeLCA2jNSA', '연예인 치아가 하얗고 예쁜 이유 (feat.트리플에스 유연) [연성대학교 치기공과]ㅣ전과자 ep.124 [EN/IN]', '31:50', 1376379, '2026-06-04', '김유연'),
    ('GCF7jrZR7wA', 'UCZFglJIUsaCPRE_3yzr8zzg', '여름아 부탁해🤍🩵 #김유연 #tripleS #트리플에스 #볼빨간사춘기 #BOL4 #여름아부탁해 #PleaseSummer', '0:41', 1515661, '2026-07-30', '김유연'),
    ('PYtyJFOusrM', 'UCtfyAiqf095_0_ux8ruwGfA', '트리플에스(tripleS) 김채연 신민재 선수 응원 직캠 [성수기 미방분] #Shorts', '0:14', 453779, '2026-04-14', '김채연'),
    ('p2FHzrsyAUM', 'UCtfyAiqf095_0_ux8ruwGfA', '트리플에스 김채연이 눈물 흘린 이유 [성수기] #shorts', '0:56', 309306, '2026-05-03', '윤서연,김채연,김수민,공유빈,곽연지,마유'),
    ('SqhuerWKYkw', 'UC7eHynJ83ypIf_586YR5lqQ', '채연님과 갑자기❓#김채연 #ChaeYeon #트리플에스 #tripleS #아이오아이 #IOI #IOI_LOOP #갑자기 #Suddenly', '0:23', 297356, '2026-05-23', '김채연'),
    ('lpSwGjGg1v8', 'UChhKBlh_wvspTh5n4mL0b5g', 'act like a #tripleS #트리플에스 #NaKyoung #나경 #Kaede #카에데 🎸#BOYNEXTDOOR #운학 #I_Feel_Good_Challenge', '0:19', 356763, '2025-05-31', '김나경,카에데'),
    ('oGIvSwepSQA', 'UCJEER74X9kBenMT_x9iK9Mw', 'Can you hear my 🆘 ? #트리플에스 #카에데 #연지 선배님과 함께한 #SOS_Challenge', '0:21', 140347, '2024-11-05', '카에데,곽연지'),
    ('HpezSWBtmAo', 'UCMmcz1ybSoAD-GUlDx_7H2A', 'HEART CRUSH!💗💥with #카에데 #IRISÉ #아이리제 #HEARTCRUSH #HEARTCRUSHCHALLENGE #tripleS #트리플에스 #Kaede', '0:30', 66737, '2026-08-16', '카에데'),
    ('izdVUYE47Uc', 'UCwoSFXX8kraikB-WClx2pVQ', '#AHOF 은 #tripleS #니엔 선배님과의 챌린지를 좋아해🤥 ​@triplescosmos #아홉 #SHUAIBO #장슈아이보 #피노키오는거짓말을싫어해', '0:24', 50008, '2025-11-08', '니엔'),
    ('QsjbJpOPihQ', 'UCX9VHpN62jkqCCntHxymPOA', '#許念慈 衝浪速度太快了吧🤣#tripleS #Nien #迷因 #bad #她很壞 #魔法少女', '0:24', 15427, '2026-09-14', '니엔'),
    ('_a625KytWPg', 'UCgiJf4Qcigccr9lAXAA9hFQ', '#tripleS #신위 선배님과 Ooh, I love it🍋🤍 #트리플에스 #Xinyu #HITGS #힛지스 #SOURPATCH #사워패치', '0:24', 13935, '2025-05-19', '신위'),
    ('kuGrZBNfors', 'UCSAp0Yl9S0Zq5uDqE6im_XQ', '#멋쟁이챌린지 with 멋쟁이 #tripleS #마유 #하연 님😎', '0:08', 57678, '2026-06-04', '마유,정하연'),
    ('jP4s3WQ1aq4', 'UCqq-ovGE01ErlXakPihhKDA', '트리플에스 에볼루션이 원더케이 Suit Dance에! | tripleS EVOLution | 마유 수민 연지 | 수트댄스 [1theK Preview] #shorts', '0:27', 17093, '2023-10-10', '김수민,곽연지,마유'),
    ('cAAHFhxtX0U', 'UCHzW_7Mf8G0JxP4b0LoAd7A', '일본어를 못하는 일본인(?) #tripleS #트리플에스 #마유 #김유연 (EN/JP)', '0:29', 21738, '2026-06-01', '김유연,마유'),
    ('coHEbpWq5qg', 'UCtfyAiqf095_0_ux8ruwGfA', '트리플에스 나경과 주빈이 입은 스커트 알려드립니다✨ [이옷 money?] #shorts #tripleS', '0:52', 11247, '2024-08-21', '김나경,주빈'),
    ('ybR7M06CIZ0', 'UChXC6ok0LaZIpf-50SIFf0Q', 'Call Me Stunner🤙💥 w. #주빈 #시온 #tripleS', '0:26', 14538, '2026-08-10', '주빈,박시온'),
    ('g4DfcOosQb4', 'UCHWhn9xw7x8NPPnV6eWQDQA', '#지연 선배님과 함께 𝗕𝗶𝗶𝗶:-𝗣 𝗕𝗶𝗶𝗶:-𝗣💓@triplescosmos #XLOV #WUMUTI #tripleS #tripleS_msnz #JiYeon', '0:23', 813388, '2025-12-04', '지연'),
    ('RdFnKSCnAGg', 'UCRZVxIpIbf6iLTU0e5MLyHA', 'The LaLaLaLaLaLaLa reference 😝 #tripleS #트리플에스 #ANDLESS #kpop #nyc', '1:08', 474869, '2026-11-21', ''),
    ('bM-lUJd-xfQ', 'UCRZVxIpIbf6iLTU0e5MLyHA', 'The new 25th member? 😝 #tripleS #트리플에스 #ANDLESS #nyc #kpop', '0:15', 20541, '2026-11-20', ''),
    ('5y_XfjwmMTU', 'UC7i6r7XiJO5i9H_rgIeZTSA', '[2026 TMA] 오늘 트리플에스가 말아주는 레모네이드 54000잔을 마실거야 🍋', '0:26', 28000, '2026-11-20', ''),
    ('6KcPmB6YfZ4', 'UC6vdeY4HiiAIBsb4JpYex3Q', '채연이 어딨게?', '0:27', 81341, '2026-10-03', '김채연');

DELETE seed FROM tmp_verified_hashtag_media seed
JOIN media m ON BINARY LOWER(m.youtube_id) = BINARY LOWER(seed.youtube_id);

INSERT INTO media (youtube_id, channel_id, tab, media_title, media_dur, view_count, published_on, sort_order, curated_external)
SELECT youtube_id, channel_id, 'external', title, duration, views, published_on,
       YEAR(published_on) * 10000 + MONTH(published_on) * 100 + DAY(published_on), TRUE
FROM tmp_verified_hashtag_media;

INSERT INTO media_artist (media_id, artist_id)
SELECT m.id, a.artist_id
FROM tmp_verified_hashtag_media seed
JOIN media m ON BINARY m.youtube_id = BINARY seed.youtube_id
JOIN artist a ON FIND_IN_SET(BINARY a.name, BINARY seed.artist_names) > 0;

DROP TEMPORARY TABLE tmp_verified_hashtag_media;
