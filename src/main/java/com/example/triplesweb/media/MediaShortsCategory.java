package com.example.triplesweb.media;

import com.example.triplesweb.util.MemberPortraitOptions;

import java.util.regex.Pattern;

public enum MediaShortsCategory {
    COLLABORATION("collaboration", "챌린지 품앗이"),
    CHALLENGE("challenge", "챌린지"),
    SIGNAL_PREVIEW("signal-preview", "시그널 미리보기");

    private static final Pattern SIGNAL = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])SIGNAL(?![\\p{L}])|시그널");
    private static final Pattern COLLABORATION_CUE = Pattern.compile("(?i)challenge|챌린지|\\bwith\\b|\\bw/");

    private final String key;
    private final String label;

    MediaShortsCategory(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public static MediaShortsCategory fromKey(String raw) {
        if (raw == null) return null;
        String key = raw.strip();
        for (MediaShortsCategory category : values()) {
            if (category.key.equalsIgnoreCase(key)) return category;
        }
        return null;
    }

    public static MediaShortsCategory fromVideo(String title, String channelId) {
        String text = title == null ? "" : title;
        if (SIGNAL.matcher(text).find()) return SIGNAL_PREVIEW;
        if (channelId != null && MediaHashtags.officialCollaborationChannels().contains(channelId)) return COLLABORATION;
        if (COLLABORATION_CUE.matcher(text).find()
                && MediaHashtags.taggedOtherKpopAct(text, null, MemberPortraitOptions.koreanOrder())) {
            return COLLABORATION;
        }
        return CHALLENGE;
    }
}
