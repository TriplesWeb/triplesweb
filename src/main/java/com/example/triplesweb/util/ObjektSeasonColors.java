package com.example.triplesweb.util;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 시즌 칩과 같은 팔레트. Cream02·Binary02 등 카드 우상단 뱃지에 쓴다. */
public final class ObjektSeasonColors {
    private static final Map<String, String> PALETTE = Map.of(
            "atom", "#FFDD00",
            "binary", "#00FF00",
            "cream", "#FF7477",
            "divine", "#B400FF",
            "ever", "#33ECFD"
    );
    private static final Map<String, String> SECOND = Map.of(
            "atom", "#FFD13E",
            "binary", "#83FF00",
            "cream", "#FF8389"
    );
    private static final Map<String, String> ALIASES = Map.of(
            "aa", "atom02",
            "bb", "binary02",
            "cc", "cream02"
    );
    private static final Pattern SEASON = Pattern.compile("^([a-z]+)(\\d+)$");
    private static final String FALLBACK = "#A4B7B4";

    private ObjektSeasonColors() {}

    public static String colorFor(String label) {
        String key = label == null ? "" : label.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", "");
        key = ALIASES.getOrDefault(key, key);
        Matcher match = SEASON.matcher(key);
        if (!match.matches()) return FALLBACK;
        String base = PALETTE.get(match.group(1));
        if (base == null) return FALLBACK;
        int generation = Integer.parseInt(match.group(2));
        if (generation == 2 && SECOND.containsKey(match.group(1))) return SECOND.get(match.group(1));
        double amount = Math.min(0.1, Math.max(0, generation - 1) * 0.035);
        if (amount == 0) return base;
        StringBuilder out = new StringBuilder("#");
        for (int i = 1; i < 7; i += 2) {
            int value = Integer.parseInt(base.substring(i, i + 2), 16);
            int mixed = (int) Math.round(value * (1 - amount) + 255 * amount);
            out.append(String.format("%02X", mixed));
        }
        return out.toString();
    }
}
