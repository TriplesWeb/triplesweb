package com.example.triplesweb.media;

import java.util.Locale;

/**
 * 유튜브 제목으로 공식 미디어 탭을 고른다.
 * SIGNAL / 비하인드 / 시크릿베이스를 MV보다 먼저 보고, 나머지에서만 MV 키워드를 적용한다.
 */
public final class MediaTabClassifier {
    private MediaTabClassifier() {}

    public static String tabFor(String title) {
        if (title == null || title.isBlank()) return MediaTab.CONTENTS.getKey();
        String upper = title.toUpperCase(Locale.ROOT);
        if (upper.contains("SIGNAL")) return MediaTab.SIGNAL.getKey();
        if (upper.contains("BEHIND") || title.contains("비하인드")) return MediaTab.BEHIND.getKey();
        if (upper.contains("SECRET BASE") || upper.contains("SECRETBASE") || title.contains("시크릿베이스")) {
            return MediaTab.SECRET.getKey();
        }
        if (isMv(upper)) return MediaTab.MV.getKey();
        return MediaTab.CONTENTS.getKey();
    }

    private static boolean isMv(String upper) {
        if (upper.contains("OFFICIAL MV") || upper.contains("OFFICIAL M/V")) return true;
        if (upper.contains("M/V")) return true;
        return upper.matches(".*['\"“”‘’].+['\"“”‘’].*\\bMV\\b.*")
                || upper.matches(".*\\bMV\\b.*");
    }
}
