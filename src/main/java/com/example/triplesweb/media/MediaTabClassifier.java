package com.example.triplesweb.media;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 유튜브 등록 형식과 제목·채널로 공식 미디어 탭을 고른다.
 * 쇼츠는 다른 탭보다 먼저 두고, 직캠은 외부·공식 분류보다 앞선다.
 * 공식 채널이 아니면 외부 콘텐츠다. SIGNAL LIVE는 기타 콘텐츠로 보낸다.
 * 비하인드·시크릿베이스는 SIGNAL 표기보다 앞선다.
 * 공식 MV 재생목록에서 제목이 MV로 끝나는 영상만 MV로 분류한다.
 */
public final class MediaTabClassifier {
    private static final Pattern MV_SUFFIX = Pattern.compile("\\bM/?V\\s*$", Pattern.CASE_INSENSITIVE);

    private MediaTabClassifier() {}

    public static String tabFor(String title) {
        return tabFor(title, null, true);
    }

    public static String tabFor(String title, String duration) {
        return tabFor(title, duration, true);
    }

    public static String tabFor(String title, String duration, boolean officialChannel) {
        return tabFor(title, duration, officialChannel, false);
    }

    public static String tabFor(String title, String duration, boolean officialChannel, boolean musicShow) {
        return tabFor(title, duration, officialChannel, musicShow, false, false, false);
    }

    public static String tabFor(
            String title,
            String duration,
            boolean officialChannel,
            boolean musicShow,
            boolean inOfficialMvPlaylist,
            boolean namuExternal,
            boolean platformShort
    ) {
        if (platformShort) return MediaTab.SHORTS.getKey();
        if (inOfficialMvPlaylist) return correctMvTab(MediaTab.MV.getKey(), title);
        if (musicShow || (!officialChannel && isMusicShow(title) && !isFancam(title))) {
            return MediaTab.MUSIC.getKey();
        }
        if (isFancam(title)) return MediaTab.FANCAM.getKey();
        if (namuExternal) return MediaTab.EXTERNAL.getKey();
        if (!officialChannel) return MediaTab.EXTERNAL.getKey();
        if (title == null || title.isBlank()) return MediaTab.CONTENTS.getKey();
        String upper = title.toUpperCase(Locale.ROOT);
        String compact = upper.replace(" ", "");
        if (compact.contains("SIGNALLIVE") || title.contains("시그널 라이브")) {
            return MediaTab.CONTENTS.getKey();
        }
        if (upper.contains("REACTION") || title.contains("리액션")) return MediaTab.BEHIND.getKey();
        if (upper.contains("BEHIND") || title.contains("비하인드")) return MediaTab.BEHIND.getKey();
        if (upper.contains("SECRET BASE") || upper.contains("SECRETBASE") || title.contains("시크릿베이스")) {
            return MediaTab.SECRET.getKey();
        }
        if (upper.contains("SIGNAL")) return MediaTab.SIGNAL.getKey();
        if (isOfficialMv(upper, title)) return MediaTab.BEHIND.getKey();
        if (looksLikeMv(upper)) return MediaTab.BEHIND.getKey();
        return MediaTab.CONTENTS.getKey();
    }

    public static String correctMvTab(String tab, String title) {
        if (!MediaTab.MV.getKey().equals(tab)) return tab;
        return title != null && MV_SUFFIX.matcher(title.strip()).find() ? tab : MediaTab.BEHIND.getKey();
    }

    public static boolean isOfficialChannel(String channelId, String channelTitle, String configuredId) {
        if (channelTitle != null && channelTitle.trim().matches("(?i)triple\\s*s\\s*official")) return true;
        if (configuredId == null || configuredId.isBlank() || channelId == null || channelId.isBlank()) return false;
        return configuredId.trim().equalsIgnoreCase(channelId.trim());
    }

    public static boolean legacyOfficial(String storedChannelId, String configuredId) {
        if (storedChannelId == null || storedChannelId.isBlank()) return true;
        return isOfficialChannel(storedChannelId, null, configuredId);
    }

    public static boolean isMusicShow(String title) {
        if (title == null || title.isBlank()) return false;
        String upper = title.toUpperCase(Locale.ROOT);
        String compact = upper.replace(" ", "");
        return title.contains("뮤직뱅크")
                || title.contains("인기가요")
                || title.contains("음악중심")
                || title.contains("엠카운트다운")
                || title.contains("쇼챔피언")
                || title.contains("더쇼")
                || compact.contains("MUSICBANK")
                || compact.contains("INKIGAYO")
                || compact.contains("MUSICCORE")
                || compact.contains("MCOUNTDOWN")
                || compact.contains("SHOWCHAMPION")
                || compact.contains("THESHOW")
                || compact.contains("SIMPLYKPOP");
    }

    public static boolean isFancam(String title) {
        if (title == null || title.isBlank()) return false;
        String upper = title.toUpperCase(Locale.ROOT);
        String compact = upper.replace(" ", "");
        return title.contains("직캠")
                || title.contains("풀캠")
                || title.contains("아이컨택")
                || title.contains("페이스캠")
                || title.contains("세로캠")
                || title.contains("안방1열")
                || compact.contains("FANCAM")
                || compact.contains("FULLCAM")
                || compact.contains("FACECAM")
                || upper.contains("EYE CONTACT")
                || compact.contains("K-CHOREO")
                || compact.contains("KCHOREO");
    }

    private static boolean isOfficialMv(String upper, String title) {
        return upper.contains("OFFICIAL MV")
                || upper.contains("OFFICIAL M/V")
                || upper.contains("OFFICIAL MUSIC VIDEO")
                || title.contains("공식 MV")
                || title.contains("공식 M/V");
    }

    private static boolean looksLikeMv(String upper) {
        if (upper.contains("M/V")) return true;
        return upper.matches(".*\\bMV\\b.*");
    }
}
