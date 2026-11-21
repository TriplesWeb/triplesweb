package com.example.triplesweb.util;

import java.text.Normalizer;
import java.util.Locale;

/** 제목 검색. 한글 조합형과 영문 대소문자를 같은 문자열로 맞춘 뒤 포함 여부를 본다. */
public final class MediaSearch {
    private MediaSearch() {}

    public static boolean matches(String title, String query) {
        String needle = fold(query);
        if (needle.isEmpty()) return true;
        return fold(title).contains(needle);
    }

    static String fold(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        StringBuilder folded = new StringBuilder(normalized.length());
        normalized.toLowerCase(Locale.ROOT).codePoints().forEach(code -> {
            if (code == '\u200b' || code == '\ufeff' || Character.isISOControl(code)) return;
            folded.appendCodePoint(code);
        });
        return folded.toString().replaceAll("\\s+", " ").trim();
    }
}
