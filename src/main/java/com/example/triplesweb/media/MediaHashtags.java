package com.example.triplesweb.media;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 제목·설명에서 트리플에스 언급·해시태그·핸들을 찾는다.
 * 비공식 수집은 지정 방송국·official 채널만 남기고, 다른 가수 콘텐츠는 제외한다.
 */
public final class MediaHashtags {
    private static final Pattern TRIPLES_WORD = Pattern.compile("(?i)(^|[^A-Z0-9])TRIPLE\\s*S([^A-Z0-9]|$)");
    private static final Pattern TAG = Pattern.compile("[#@]([\\p{L}\\p{N}_]+)");
    private static final Pattern OTHER_KPOP = Pattern.compile(
            "(?i)(?<![A-Z0-9])("
                    + "NCT\\s*WISH|NCT\\s*127|NCT\\s*DREAM|NCT|WAYV|"
                    + "AESPA|BLACKPINK|BTS|SEVENTEEN|STRAY\\s*KIDS|ENHYPEN|"
                    + "NEWJEANS|LE\\s*SSERAFIM|LESSERAFIM|G-?IDLE|GIDLE|"
                    + "ITZY|TWICE|RED\\s*VELVET|EXO|RIIZE|BOYNEXTDOOR|"
                    + "ZEROBASEONE|ZB1|ILLIT|KISS\\s*OF\\s*LIFE|BABYMONSTER|"
                    + "NMIXX|IVE|TXT|ATEEZ|THE\\s*BOYZ|TREASURE|IZ\\*?ONE|"
                    + "ALPHA\\s*DRIVE\\s*ONE|ALPHADRIVEONE|ALD1|"
                    + "HEARTS2HEARTS|IZNA|KICKFLIP|CORTIS|AHOF|IDNTT|"
                    + "CLOSE\\s*YOUR\\s*EYES|MEOVV|UNIS|KEP1ER|STAYC|FROMIS_?9|"
                    + "FIFTY\\s*FIFTY|TWS|P1HARMONY|CRAVITY|EVNNE|NEXZ|"
                    + "XG|ARTMS|LOOSSEMBLE|VIVIZ|BILLLIE|RESCENE|KIIIKIII|"
                    + "ALLDAY\\s*PROJECT|GOT7|MONSTA\\s*X|SHINEE|MAMAMOO|"
                    + "APINK|OH\\s*MY\\s*GIRL|WJSN|GFRIEND|I\\.O\\.I|WANNA\\s*ONE"
                    + ")(?![A-Z0-9])"
                    + "|엔시티\\s*위시|엔시티|에스파|아이브|블랙핑크|방탄소년단|세븐틴|"
                    + "스트레이키즈|엔하이픈|뉴진스|르세라핌|있지|트와이스|레드벨벳|"
                    + "엑소|라이즈|보이넥스트도어|제로베이스원|아일릿|베이비몬스터|"
                    + "엔믹스|에이티즈|더보이즈|알파드라이브원|하츠투하츠|이즈나|"
                    + "킥플립|미야오|유니스|케플러|스테이씨|프로미스나인");

    private MediaHashtags() {}

    public static boolean mentionsTripleS(String title, String description) {
        return mentions(title) || mentions(description);
    }

    public static boolean mentions(String text) {
        if (text == null || text.isBlank()) return false;
        if (text.contains("트리플에스")) return true;
        String compact = text.toUpperCase(Locale.ROOT).replace(" ", "");
        if (compact.contains("#TRIPLES") || compact.contains("@TRIPLES")) return true;
        return TRIPLES_WORD.matcher(text.toUpperCase(Locale.ROOT)).find();
    }

    public static boolean mentionsGroupOutsideTags(String title) {
        return title != null && mentions(TAG.matcher(title).replaceAll(" "));
    }

    public static boolean looksFanMade(String title, String description) {
        String text = ((title == null ? "" : title) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);
        String compact = text.replace(" ", "").replace("-", "");
        return text.contains("팬메이드")
                || compact.contains("fanmade")
                || text.contains("팬에디트")
                || compact.contains("fanedit")
                || compact.contains("unofficial")
                || text.contains("비공식");
    }

    /**
     * 지정 방송국·official이 아닌 업로더. 탭과 무관하게 제외한다.
     */
    public static boolean unknownUploader(
            String channelId,
            String channelTitle,
            Collection<String> allowedChannelIds,
            Collection<String> allowedHandles
    ) {
        if (isAllowedChannel(channelId, channelTitle, allowedChannelIds, allowedHandles)) return false;
        return !channelNameEndsWithOfficial(channelTitle);
    }

    public static boolean looksOffChannelBroadcast(
            String title,
            String channelId,
            String channelTitle,
            Collection<String> allowedChannelIds,
            Collection<String> allowedHandles
    ) {
        return unknownUploader(channelId, channelTitle, allowedChannelIds, allowedHandles);
    }

    /**
     * 다른 K-pop 가수가 제목의 주인공이거나, 트리플에스 언급 없이 다른 가수 콘텐츠·해시태그만 겹치는 경우.
     */
    public static boolean overlapsOtherKpopAct(String title, String description) {
        boolean titleOther = mentionsOtherKpopAct(title);
        if (titleOther && !mentions(title)) return true;
        if (!mentionsOtherKpopAct(title) && !mentionsOtherKpopAct(description)) return false;
        return !mentionsTripleS(title, description);
    }

    static boolean mentionsOtherKpopAct(String text) {
        if (text == null || text.isBlank()) return false;
        return OTHER_KPOP.matcher(text).find();
    }

    /**
     * 제목·설명의 #/@에 트리플에스·멤버가 아닌 다른 가수명이 있다.
     * 음방 프로그램 태그는 무시한다.
     */
    public static boolean taggedOtherKpopAct(String title, String description, Collection<String> memberNames) {
        for (String tag : tags(title, description)) {
            if (isBroadcastTag(tag) || mentions(tag) || matchesMember(tag, memberNames)) continue;
            if (mentionsOtherKpopAct(tag)) return true;
        }
        return false;
    }

    public static boolean hasGroupOrMemberTag(String title, String description, Collection<String> memberNames) {
        for (String tag : tags(title, description)) {
            if (mentions(tag) || matchesMember(tag, memberNames)) return true;
        }
        return false;
    }

    /**
     * 다른 가수 Official 계정이 트리플에스 또는 멤버 해시태그를 단 쇼츠.
     */
    public static boolean otherArtistOfficialShorts(
            String title,
            String description,
            String channelTitle,
            Collection<String> memberNames
    ) {
        if (!channelNameEndsWithOfficial(channelTitle)) return false;
        if (channelTitle != null && channelTitle.trim().matches("(?i)triple\\s*s\\s*official")) return false;
        if (!mentionsOtherKpopAct(channelTitle)) return false;
        if (looksFanMade(title, description)) return false;
        return hasGroupOrMemberTag(title, description, memberNames);
    }

    static List<String> tags(String title, String description) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectTags(values, title);
        collectTags(values, description);
        return new ArrayList<>(values);
    }

    private static void collectTags(Set<String> values, String text) {
        if (text == null || text.isBlank()) return;
        Matcher matcher = TAG.matcher(text);
        while (matcher.find()) {
            String tag = matcher.group(1);
            if (tag != null && !tag.isBlank()) values.add(tag);
        }
    }

    private static boolean isBroadcastTag(String tag) {
        if (tag == null || tag.isBlank()) return false;
        String compact = compact(tag);
        return compact.contains("MCOUNTDOWN")
                || compact.contains("MUSICBANK")
                || compact.contains("INKIGAYO")
                || compact.contains("MUSICCORE")
                || compact.contains("SHOWCHAMPION")
                || compact.contains("THESHOW")
                || compact.contains("SIMPLYKPOP")
                || compact.contains("KPOP")
                || compact.contains("SHORTS")
                || compact.contains("YOUTUBE")
                || tag.contains("엠카운트다운")
                || tag.contains("뮤직뱅크")
                || tag.contains("인기가요")
                || tag.contains("음악중심")
                || tag.contains("쇼챔피언")
                || tag.contains("더쇼");
    }

    private static boolean matchesMember(String tag, Collection<String> memberNames) {
        if (tag == null || tag.isBlank() || memberNames == null) return false;
        String compactTag = compact(tag);
        String folded = tag.toLowerCase(Locale.ROOT);
        for (String name : memberNames) {
            if (name == null || name.isBlank()) continue;
            String needle = name.trim();
            if (needle.length() < 2) continue;
            if (folded.contains(needle.toLowerCase(Locale.ROOT))) return true;
            String compactName = compact(needle);
            if (compactName.length() >= 2 && compactTag.contains(compactName)) return true;
        }
        return false;
    }

    public static boolean channelNameEndsWithOfficial(String channelTitle) {
        if (channelTitle == null || channelTitle.isBlank()) return false;
        String trimmed = channelTitle.trim();
        return trimmed.matches("(?i).*official");
    }

    /**
     * 트리플에스를 언급하고, 지정 채널(1theK·SBS/MBC/KBS/Mnet·M2 등)·official 계정인 경우만 남긴다.
     * 아무 팬 계정의 음방 직캠이나 #tripleS 영상은 넣지 않는다.
     */
    public static boolean keepKnownSource(
            String title,
            String description,
            String channelId,
            String channelTitle,
            Collection<String> allowedChannelIds,
            Collection<String> allowedHandles
    ) {
        if (!mentionsTripleS(title, description)) return false;
        if (looksFanMade(title, description)) return false;
        if (overlapsOtherKpopAct(title, description)) return false;
        if (isAllowedChannel(channelId, channelTitle, allowedChannelIds, allowedHandles)) return true;
        return channelNameEndsWithOfficial(channelTitle);
    }

    public static boolean isAllowedChannel(
            String channelId,
            String channelTitle,
            Collection<String> allowedChannelIds,
            Collection<String> allowedHandles
    ) {
        if (channelId != null && !channelId.isBlank() && allowedChannelIds != null) {
            String id = channelId.trim();
            for (String allowed : allowedChannelIds) {
                if (allowed != null && allowed.equalsIgnoreCase(id)) return true;
            }
        }
        if (channelTitle == null || channelTitle.isBlank() || allowedHandles == null) return false;
        String titleKey = compact(channelTitle);
        if (titleKey.length() < 4) return false;
        for (String handle : allowedHandles) {
            String key = compact(handle);
            if (key.length() < 4) continue;
            if (titleKey.contains(key) || key.contains(titleKey)) return true;
        }
        return false;
    }

    static String compact(String value) {
        if (value == null) return "";
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
