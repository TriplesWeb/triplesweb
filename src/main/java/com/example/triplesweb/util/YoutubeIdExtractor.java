package com.example.triplesweb.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube URL(youtu.be, watch, embed, shorts) 또는 순수 video ID에서 동영상 ID를 추출한다.
 */
public class YoutubeIdExtractor {

    // 11자리 유튜브 비디오 ID만 정확하게 낚아채는 정규식
    private static final Pattern YOUTUBE_ID_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?.*v=|embed/|v/|shorts/)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
    );

    public static String extract(String urlOrId) {
        if (urlOrId == null || urlOrId.isBlank()) return "";
        String trimmed = urlOrId.trim();

        if (trimmed.matches("^[a-zA-Z0-9_-]{11}$")) {
            return trimmed;
        }

        Matcher matcher = YOUTUBE_ID_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1); // M2ky7c5kIXo 만 깔끔하게 추출 (?si=...는 버림)
        }

        return "";
    }
}