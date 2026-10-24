package com.example.triplesweb.util;

import com.example.triplesweb.entity.Artist;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private static final List<String> KOREAN_ORDER = List.of(
            "윤서연", "정혜린", "이지우", "김채연", "김유연", "김수민",
            "김나경", "공유빈", "카에데", "서다현", "코토네", "곽연지",
            "니엔", "박소현", "신위", "마유", "린", "주빈",
            "정하연", "박시온", "김채원", "설린", "서아", "지연"
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
            if (!hasProfileImage(artist)) continue;
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
            // 프로필 사진이 없는 API 멤버 값은 '이미지 업로드 예정' 버튼이 되므로 피커에서 뺀다.
            if (!hasProfileImage(matched)) continue;
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

    /**
     * 선택한 멤버가 오브젝트 문구에 포함되는지 본다.
     * 솔로 카드는 이름(윤서연)이고, 유닛 카드는 {@code S1 X S9}처럼 번호만 적혀 있다.
     * S1은 번호 토큰으로만 맞추고 S10·S11과는 겹치지 않는다.
     */
    public static boolean objektIncludesMember(String objektText, String selectedMember) {
        Set<String> aliases = memberAliases(selectedMember);
        if (aliases.isEmpty() || objektText == null || objektText.isBlank()) return false;
        Set<String> tokens = tokens(objektText);
        for (String alias : aliases) {
            if (tokens.contains(alias)) return true;
        }
        return false;
    }

    static Set<String> memberAliases(String selectedMember) {
        Set<String> selectedTokens = tokens(selectedMember);
        Set<String> aliases = new LinkedHashSet<>();
        for (String token : selectedTokens) {
            if (token.length() >= 2 && !"x".equals(token)) aliases.add(token);
        }
        for (int i = 0; i < ENGLISH_ORDER.size(); i++) {
            String english = ENGLISH_ORDER.get(i);
            String korean = normalize(KOREAN_ORDER.get(i));
            String number = "s" + (i + 1);
            if (selectedTokens.contains(english) || selectedTokens.contains(korean) || selectedTokens.contains(number)) {
                aliases.add(english);
                aliases.add(korean);
                aliases.add(number);
            }
        }
        aliases.remove("x");
        return aliases;
    }

    static Set<String> tokens(String value) {
        Set<String> out = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return out;
        String lower = value.toLowerCase(Locale.ROOT);
        for (String part : lower.split("[^a-z0-9가-힣]+")) {
            if (!part.isEmpty()) out.add(part);
        }
        return out;
    }

    private static boolean hasProfileImage(Artist artist) {
        if (artist == null) return false;
        String image = artist.getProfileImage();
        if (image == null || image.isBlank()) return false;
        String trimmed = image.trim();
        return !trimmed.equals(ImageUrls.PLACEHOLDER)
                && !trimmed.endsWith("/images/image-upload-pending.png");
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]", "");
    }
}
