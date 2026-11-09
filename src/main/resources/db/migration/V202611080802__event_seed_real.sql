-- 실제 tripleS 공연·투어 시드 (지역 회차 구조 확인용)
-- 참고: 나무위키 tripleS/공연 및 행사, Come True 투어 일정

DELETE FROM event_stop;
DELETE FROM event;
DELETE FROM event_section;

INSERT INTO event_section (section_id, name, sort_order) VALUES
    (1, '콘서트', 0),
    (2, '팬콘', 1),
    (3, '팬미팅', 2);

-- 1) Pre-Con ASSEMBLE
INSERT INTO event (event_name, event_image, event_info, section_id) VALUES
('Pre-Con ASSEMBLE', NULL,
 '트리플에스 첫 단독 공연. 2023년 2월 13일 서울 블루스퀘어 마스터카드홀.',
 1);
SET @e := LAST_INSERT_ID();
INSERT INTO event_stop (event_id, event_date, region, venue, detail, sort_order) VALUES
(@e, '2023.02.13', '서울', '블루스퀘어 마스터카드홀', 'Pre-Con', 0);

-- 2) Authentic (1st World Tour) — 동일 투어명 아래 미국·호주·서울
INSERT INTO event (event_name, event_image, event_info, section_id) VALUES
('Authentic', NULL,
 '트리플에스 1st World Tour. LOVElution 미국, EVOLution 호주, 서울 본공연으로 이어진 첫 월드투어.',
 1);
SET @e := LAST_INSERT_ID();
INSERT INTO event_stop (event_id, event_date, region, venue, detail, sort_order) VALUES
(@e, '2023.09.24', '애틀랜타', 'Tabernacle', 'LOVElution in US', 0),
(@e, '2023.09.26', '시카고', 'Riviera Theatre', 'LOVElution in US', 1),
(@e, '2023.09.28', '뉴욕', 'The Town Hall', 'LOVElution in US', 2),
(@e, '2023.09.30', '타이슨스', 'Capital One Hall', 'LOVElution in US', 3),
(@e, '2023.10.02', '애크런', 'Goodyear Theater', 'LOVElution in US', 4),
(@e, '2023.10.04', '캔자스시티', 'Uptown Theater', 'LOVElution in US', 5),
(@e, '2023.10.07', '휴스턴', 'Bayou Music Center', 'LOVElution in US', 6),
(@e, '2023.10.09', '포트워스', 'Will Rogers Auditorium', 'LOVElution in US', 7),
(@e, '2023.10.14', '로스앤젤레스', 'Pasadena Civic Auditorium', 'LOVElution in US', 8),
(@e, '2023.12.20', '멜버른', '170 Russell', 'EVOLution in Australia', 9),
(@e, '2023.12.21', '시드니', 'Home The Venue', 'EVOLution in Australia', 10),
(@e, '2023.12.23', '브리즈번', 'The Zoo', 'EVOLution in Australia', 11),
(@e, '2024.02.02~2024.02.04', '서울', '블루스퀘어 마스터카드홀', 'Authentic in Seoul (Eve-Con 포함)', 12);

-- 3) Girls Never Stop
INSERT INTO event (event_name, event_image, event_info, section_id) VALUES
('Girls Never Stop', NULL,
 '2024 tripleS FAN-CON 〈Girls Never Stop〉.',
 2);
SET @e := LAST_INSERT_ID();
INSERT INTO event_stop (event_id, event_date, region, venue, detail, sort_order) VALUES
(@e, '2024.07.06~2024.07.07', '서울', '이화여자대학교 삼성홀', 'FAN-CON', 0);

-- 4) アンタイトル (∞! 일본 팬콘)
INSERT INTO event (event_name, event_image, event_info, section_id) VALUES
('アンタイトル', NULL,
 'tripleS ∞! 1st Fan Concert in Japan "アンタイトル".',
 2);
SET @e := LAST_INSERT_ID();
INSERT INTO event_stop (event_id, event_date, region, venue, detail, sort_order) VALUES
(@e, '2024.11.26', '오사카', 'Zepp Namba', '∞! Fan Concert', 0),
(@e, '2024.11.27~2024.11.28', '도쿄', 'Zepp DiverCity', '∞! Fan Concert', 1);

-- 5) tripleS Come True (2nd World Tour) — 다도시 대표 예시
INSERT INTO event (event_name, event_image, event_info, section_id) VALUES
('tripleS Come True', NULL,
 '트리플에스 2nd World Tour. 2025년 1월~3월, 동남아·북미·서울 등 4개국 공연.',
 1);
SET @e := LAST_INSERT_ID();
INSERT INTO event_stop (event_id, event_date, region, venue, detail, sort_order) VALUES
(@e, '2025.01.11', '방콕', 'JJ Hall, JJ Mall', 'Southeast Asia', 0),
(@e, '2025.01.18', '자카르타', 'The Kasablanka Hall', '취소', 1),
(@e, '2025.02.01', '올랜도', 'The Plaza Live', 'North America', 2),
(@e, '2025.02.03', '브루클린', 'Brooklyn Paramount', 'North America', 3),
(@e, '2025.02.05', '몬트리올', 'MTELUS', 'North America', 4),
(@e, '2025.02.07', '토론토', 'Danforth Music Hall', 'North America', 5),
(@e, '2025.02.09', '시카고', 'Radius', 'North America', 6),
(@e, '2025.02.11', '휴스턴', 'House of Blues', 'North America', 7),
(@e, '2025.02.14', '템피', 'The Marquee', 'North America', 8),
(@e, '2025.02.16', '로스앤젤레스', 'The Wiltern', 'North America', 9),
(@e, '2025.03.29~2025.03.30', '서울', '이화여자대학교 삼성홀', 'World Tour in Seoul', 10);

-- 6) A Live 25
INSERT INTO event (event_name, event_image, event_info, section_id) VALUES
('A Live 25', NULL,
 '2025 tripleS Concert 〈A Live 25〉.',
 1);
SET @e := LAST_INSERT_ID();
INSERT INTO event_stop (event_id, event_date, region, venue, detail, sort_order) VALUES
(@e, '2025.07.12~2025.07.13', '서울', '장충체육관', NULL, 0),
(@e, '2025.07.26', '도쿄', 'Kanadevia Hall', NULL, 1),
(@e, '2025.08.02', '라스베이거스', 'The Venetian Theatre', NULL, 2);

-- 7) @% (Alphie World Tour)
INSERT INTO event (event_name, event_image, event_info, section_id) VALUES
('@%', NULL,
 'tripleS Alphie World Tour @%. 캐나다·서울·타이베이 등.',
 1);
SET @e := LAST_INSERT_ID();
INSERT INTO event_stop (event_id, event_date, region, venue, detail, sort_order) VALUES
(@e, '2025.09.03', '토론토', 'Danforth Music Hall', NULL, 0),
(@e, '2025.09.06', '밴쿠버', 'The Centre', NULL, 1),
(@e, '2025.10.11~2025.10.12', '서울', '티켓링크 1075 씨어터', NULL, 2),
(@e, '2025.11.09', '타이베이', 'Legacy Tera', NULL, 3);

-- 8) My Secret New Zone (2026 Asia)
INSERT INTO event (event_name, event_image, event_info, section_id) VALUES
('My Secret New Zone', NULL,
 '2026 tripleS OT 24 Concerts in Asia 〈My Secret New Zone〉.',
 1);
SET @e := LAST_INSERT_ID();
INSERT INTO event_stop (event_id, event_date, region, venue, detail, sort_order) VALUES
(@e, '2026.02.28~2026.03.01', '서울', '경희대학교 평화의 전당', 'Asia Tour', 0);

-- 팬미팅 샘플
INSERT INTO event (event_name, event_image, event_info, section_id) VALUES
('GIRLS NEVER…… FAN MEETING in HONG KONG', NULL,
 '2024년 홍콩 팬미팅.',
 3);
SET @e := LAST_INSERT_ID();
INSERT INTO event_stop (event_id, event_date, region, venue, detail, sort_order) VALUES
(@e, '2024.08.20', '홍콩', NULL, '출연: 김채연, 김나경, 니엔, 신위, 정하연, 김채원', 0);

INSERT INTO event (event_name, event_image, event_info, section_id) VALUES
('tripleS Shanghai Fan Meeting - First Contact', NULL,
 '2025년 상하이 팬미팅.',
 3);
SET @e := LAST_INSERT_ID();
INSERT INTO event_stop (event_id, event_date, region, venue, detail, sort_order) VALUES
(@e, '2025.03.22', '상하이', NULL, '출연: 카에데, 코토네, 신위, 린', 0);
