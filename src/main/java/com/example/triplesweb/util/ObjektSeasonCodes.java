package com.example.triplesweb.util;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private static final Set<String> KNOWN_CODES = new HashSet<>(CODES.values());

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

    /** 시즌 코드+번호 합성어. Cream02 + 401A → {@code cc401}. */
    public static String composite(String season, String collectionNo) {
        String seasonCode = code(season);
        if (seasonCode.isEmpty()) return "";
        String digits = collectionNo == null ? "" : collectionNo.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? "" : seasonCode + digits;
    }

    /**
     * 짧은 시즌 코드는 토큰 완전 일치만, {@code cc401} 형태는 합성어 contains,
     * 그 외는 기본 haystack contains.
     */
    public static boolean matches(String query, String baseHaystack, String season, String collectionNo) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        String seasonCode = code(season);
        String fused = composite(season, collectionNo);
        if (KNOWN_CODES.contains(q)) {
            return q.equals(seasonCode) || q.equals(fused);
        }
        if (!fused.isEmpty() && fused.contains(q)) return true;
        String base = baseHaystack == null ? "" : baseHaystack.toLowerCase(Locale.ROOT);
        return base.contains(q);
    }
}
