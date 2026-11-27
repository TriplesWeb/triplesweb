package com.example.triplesweb.media;

import com.example.triplesweb.util.MediaOrdering;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SIGNAL 공개 시기를 숙소 기준으로 나눈다.
 * 가평 HAUS → 성수/여의도/남산 → 모두 HAUS → 24명 데뷔 이후.
 */
public enum MediaSignalEra {
    GAPYEONG("gapyeong", "가평 HAUS", LocalDate.of(2022, 11, 30)),
    SEONGSU("seongsu", "성수/여의도/남산", LocalDate.of(2023, 7, 2)),
    ALL_HAUS("allHaus", "모두 HAUS", LocalDate.of(2024, 5, 10)),
    DEBUT24("debut24", "24명 데뷔 이후", null);

    private static final Pattern TITLE_DAY = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    private final String key;
    private final String label;
    private final LocalDate until;

    MediaSignalEra(String key, String label, LocalDate until) {
        this.key = key;
        this.label = label;
        this.until = until;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public static MediaSignalEra fromKey(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim();
        for (MediaSignalEra era : values()) {
            if (era.key.equalsIgnoreCase(key)) return era;
        }
        return null;
    }

    public static MediaSignalEra from(String publishedOn, String title) {
        LocalDate day = MediaOrdering.parseDate(publishedOn);
        if (day == null) day = dateFromTitle(title);
        if (day == null) return DEBUT24;
        if (!day.isAfter(GAPYEONG.until)) return GAPYEONG;
        if (!day.isAfter(SEONGSU.until)) return SEONGSU;
        if (!day.isAfter(ALL_HAUS.until)) return ALL_HAUS;
        return DEBUT24;
    }

    static LocalDate dateFromTitle(String title) {
        if (title == null || title.isBlank()) return null;
        Matcher matcher = TITLE_DAY.matcher(title);
        while (matcher.find()) {
            try {
                return LocalDate.parse(matcher.group(1), YYMMDD);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    public static boolean matches(String publishedOn, String title, String eraKey) {
        MediaSignalEra wanted = fromKey(eraKey);
        if (wanted == null) return true;
        return from(publishedOn, title) == wanted;
    }

    @Override
    public String toString() {
        return key + "/" + label.toLowerCase(Locale.ROOT);
    }
}
