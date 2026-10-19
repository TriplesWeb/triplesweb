package com.example.triplesweb.entity;

/**
 * 표 칸 케미명 글자색.
 * <ul>
 *   <li>{@link #NONE} — 미선택 (기본 흰색)</li>
 *   <li>{@link #UNDECIDED} — 미정 (보라)</li>
 *   <li>{@link #RECENT} — 최근에 정해짐 (분홍)</li>
 *   <li>{@link #FAN} — 팬이 정함 (하늘)</li>
 * </ul>
 */
public enum ChemistryLabelTone {
    NONE,
    UNDECIDED,
    RECENT,
    FAN;

    public static ChemistryLabelTone fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return ChemistryLabelTone.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
