-- 유닛 상징색(주황·금색 등)과 겹치지 않도록 출처 기본색을 쿨톤으로 맞춘다.
UPDATE archive_source SET color = '#00f5d4' WHERE code = 'COSMO_ROOM';
UPDATE archive_source SET color = '#a78bfa' WHERE code = 'COSMO_TALK';
UPDATE archive_source SET color = '#60a5fa' WHERE code = 'OFFICIAL_SNS';
UPDATE archive_source SET color = '#5eead4' WHERE code = 'POLAROID';
UPDATE archive_source SET color = '#2dd4bf' WHERE code = 'TEASER_BEHIND';
UPDATE archive_source SET color = '#f472b6' WHERE code = 'MAGAZINE';
UPDATE archive_source SET color = '#38bdf8' WHERE code = 'EXTERNAL';
