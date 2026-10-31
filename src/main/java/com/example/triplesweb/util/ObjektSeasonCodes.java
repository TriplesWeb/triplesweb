package com.example.triplesweb.util;

import java.util.Locale;
import java.util.Map;

/**
 * 시즌 짧은 검색 코드. Cream 02 401A → {@code cc401}처럼
 * 시즌 별칭과 번호 숫자만 이어 검색한다.
 */
public final class ObjektSeasonCodes {
    private static final Map<String, String> CODES = Map.of(
            "atom01", "a",
            "binary01", "b",
            "cream01", "c",
            "divine01", "d",
            "ever01", "e",
            "atom02", "aa",
            "binary02", "bb",
            "cream02", "cc"
    );

    private ObjektSeasonCodes() {}

    /** {@code Cream02} / {@code Cream 02} → {@code cc}. 없으면 빈 문자열. */
    public static String code(String season) {
        if (season == null || season.isBlank()) return "";
        return CODES.getOrDefault(season.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", ""), "");
    }

    /**
     * 검색 haystack에 붙일 토큰. 예: season={@code Cream02}, collectionNo={@code 401A}
     * → {@code "cc cc401"}.
     */
    public static String searchTokens(String season, String collectionNo) {
        String seasonCode = code(season);
        if (seasonCode.isEmpty()) return "";
        String digits = collectionNo == null ? "" : collectionNo.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return seasonCode;
        return seasonCode + " " + seasonCode + digits;
    }
}
