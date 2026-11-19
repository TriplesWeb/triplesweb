-- 기타/팬메이드(FANCAM) 출처 제거: 기존 값은 NULL로 정리
UPDATE archive SET source = NULL WHERE UPPER(source) IN ('FANCAM', 'FAN_MADE', 'FANMADE', 'OTHER', 'UNCLASSIFIED');
