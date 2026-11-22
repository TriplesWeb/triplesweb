package com.example.triplesweb.util;

import java.util.Locale;

/**
 * 아카이브 목록 정렬. 기본은 최신순({@link #NEWEST}).
 */
public enum ArchiveSort {
    NEWEST,
    OLDEST,
    TITLE,
    FIRST_MEMBER,
    LAST_MEMBER;

    public static ArchiveSort from(String raw) {
        if (raw == null || raw.isBlank()) return NEWEST;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "oldest" -> OLDEST;
            case "title" -> TITLE;
            case "first-member" -> FIRST_MEMBER;
            case "last-member" -> LAST_MEMBER;
            default -> NEWEST;
        };
    }

    public String key() {
        return switch (this) {
            case NEWEST -> "newest";
            case OLDEST -> "oldest";
            case TITLE -> "title";
            case FIRST_MEMBER -> "first-member";
            case LAST_MEMBER -> "last-member";
        };
    }

    public boolean isMemberSort() {
        return this == FIRST_MEMBER || this == LAST_MEMBER;
    }
}
