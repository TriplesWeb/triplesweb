package com.example.triplesweb.entity;

import java.util.Locale;

/** 아카이브 자료가 올라온 출처. */
public enum ArchiveSource {
    COSMO_ROOM("코스모룸"),
    COSMO_TALK("코스모톡/프롬"),
    OFFICIAL_SNS("공식 SNS"),
    POLAROID("폴라로이드"),
    TEASER("티저"),
    MAGAZINE("화보/매거진"),
    EXTERNAL("외부 컨텐츠/방송");

    private final String label;

    ArchiveSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getSlug() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ArchiveSource parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        for (ArchiveSource source : values()) {
            if (source.name().equalsIgnoreCase(value) || source.getSlug().equalsIgnoreCase(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("출처를 선택하세요.");
    }
}
