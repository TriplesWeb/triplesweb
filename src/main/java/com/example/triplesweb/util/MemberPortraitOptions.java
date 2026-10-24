package com.example.triplesweb.util;

import com.example.triplesweb.entity.Artist;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 멤버 초상화 피커(아티스트 탭 스타일)용 옵션 맵을 만든다.
 */
public final class MemberPortraitOptions {

    private static final List<String> ENGLISH_ORDER = List.of(
            "seoyeon", "hyerin", "jiwoo", "chaeyeon", "yooyeon", "soomin",
            "nakyoung", "yubin", "kaede", "dahyun", "kotone", "yeonji",
            "nien", "sohyun", "xinyu", "mayu", "lynn", "joobin",
            "hayeon", "shion", "chaewon", "sullin", "seoah", "jiyeon"
    );

    private MemberPortraitOptions() {
    }

    /**
     * Artist 테이블 순서(artistId)로 S1~S24 피커 옵션을 만든다.
     * value는 한글 이름(케미 등), label은 표시명.
     */
    public static List<Map<String, Object>> fromArtists(List<Artist> artists, boolean englishLabels) {
        List<Artist> ordered = artists.stream()
                .sorted(Comparator.comparing(Artist::getArtistId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        List<Map<String, Object>> out = new ArrayList<>(ordered.size());
        int frame = 1;
        for (Artist artist : ordered) {
            if (artist == null || artist.getName() == null || artist.getName().isBlank()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("value", artist.getName());
            row.put("label", englishLabels && artist.getEngName() != null && !artist.getEngName().isBlank()
                    ? artist.getEngName()
                    : artist.getName());
            row.put("image", artist.getDisplayProfileImage());
            row.put("color", artist.getColor() != null && !artist.getColor().isBlank()
                    ? artist.getColor()
                    : "#00f5d4");
            row.put("frame", Math.min(frame, 24));
            row.put("artistId", artist.getArtistId());
            out.add(row);
            frame++;
        }
        return out;
    }

    /**
     * 오브젝트 API 멤버 값(영/한 혼재)에 Artist 프로필을 매칭한다.
     * value는 원본 옵션 문자열을 유지해 기존 필터 쿼리와 호환된다.
     */
    public static List<Map<String, Object>> forObjektFilter(List<String> memberOptions, List<Artist> artists) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (memberOptions == null) return out;

        for (String option : memberOptions) {
            if (option == null || option.isBlank()) continue;
            Artist matched = matchArtist(option, artists);
            int frame = matched != null && matched.getArtistId() != null
                    ? matched.getArtistId().intValue()
                    : preferredIndex(option) + 1;
            if (frame < 1) frame = 1;
            if (frame > 24) frame = 24;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("value", option);
            row.put("label", option);
            row.put("image", matched != null
                    ? matched.getDisplayProfileImage()
                    : ImageUrls.PLACEHOLDER);
            row.put("color", matched != null && matched.getColor() != null && !matched.getColor().isBlank()
                    ? matched.getColor()
                    : "#00f5d4");
            row.put("frame", frame);
            out.add(row);
        }
        return out;
    }

    private static Artist matchArtist(String option, List<Artist> artists) {
        if (artists == null || artists.isEmpty()) return null;
        String normalized = normalize(option);
        for (Artist artist : artists) {
            if (artist == null) continue;
            String ko = normalize(artist.getName());
            String en = normalize(artist.getEngName());
            if (!ko.isEmpty() && (normalized.equals(ko) || normalized.contains(ko) || ko.contains(normalized))) {
                return artist;
            }
            if (!en.isEmpty() && (normalized.equals(en) || normalized.contains(en) || en.contains(normalized))) {
                return artist;
            }
        }
        return null;
    }

    private static int preferredIndex(String value) {
        String normalized = normalize(value);
        for (int i = 0; i < ENGLISH_ORDER.size(); i++) {
            if (normalized.equals(ENGLISH_ORDER.get(i)) || normalized.contains(ENGLISH_ORDER.get(i))) {
                return i;
            }
        }
        return 0;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]", "");
    }
}
