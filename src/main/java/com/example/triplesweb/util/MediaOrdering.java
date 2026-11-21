package com.example.triplesweb.util;

import com.example.triplesweb.dto.MediaDto;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 공식 미디어 목록의 날짜·길이·조회수 정렬. */
public final class MediaOrdering {
    private MediaOrdering() {}

    public static void sort(List<MediaDto> videos, String sort) {
        Comparator<MediaDto> newestId = Comparator.comparing(MediaOrdering::id, Comparator.reverseOrder());
        Comparator<MediaDto> oldestId = Comparator.comparing(MediaOrdering::id);
        switch (sort == null ? "latest" : sort) {
            case "oldest" -> videos.sort(Comparator
                    .comparing(MediaOrdering::published, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(oldestId));
            case "views" -> videos.sort(Comparator
                    .comparingLong(MediaOrdering::views).reversed()
                    .thenComparing(newestId));
            case "short" -> videos.sort(Comparator
                    .comparing(MediaOrdering::seconds, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(oldestId));
            case "long" -> videos.sort(Comparator
                    .comparing(MediaOrdering::seconds, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(newestId));
            default -> videos.sort(Comparator
                    .comparing(MediaOrdering::published, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(newestId));
        }
    }

    public static boolean isShort(String duration) {
        Integer seconds = durationSeconds(duration);
        return seconds != null && seconds > 0 && seconds <= 60;
    }

    public static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.length() >= 10 && (value.charAt(4) == '.' || value.charAt(4) == '/')) {
            value = value.substring(0, 10).replace('.', '-').replace('/', '-');
        }
        try {
            return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public static Integer durationSeconds(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.trim().split(":");
        try {
            if (parts.length == 2) return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600
                        + Integer.parseInt(parts[1]) * 60
                        + Integer.parseInt(parts[2]);
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return null;
    }

    public static String memberKey(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private static LocalDate published(MediaDto video) {
        return video == null ? null : parseDate(video.getPublishedOn());
    }

    private static long views(MediaDto video) {
        return video == null || video.getViews() == null ? 0L : video.getViews();
    }

    private static Integer seconds(MediaDto video) {
        return video == null ? null : durationSeconds(video.getDuration());
    }

    private static long id(MediaDto video) {
        return video == null || video.getId() == null ? 0L : video.getId();
    }
}
