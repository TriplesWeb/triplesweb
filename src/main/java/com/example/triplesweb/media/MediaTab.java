package com.example.triplesweb.media;

import lombok.Getter;

/**
 * 미디어 아카이브 UI 탭(MV, SIGNAL 등)의 URL 키와 한글 표시명.
 */
@Getter
public enum MediaTab {
    MV("mv","MV"),
    SIGNAL("signal","SIGNAL"),
    BEHIND("behind","비하인드"),
    SECRET("secret","시크릿베이스"),
    CONTENTS("contents","기타 컨텐츠");

    private final String key;
    private final String kor;
    MediaTab(String key, String kor) { this.key = key; this.kor = kor; }

    public static MediaTab from (String raw) {
        if (raw == null) {
            return MV;
        }
        String k = raw.toLowerCase();
        for (MediaTab t : values()) {
            if (t.key.equals(k)) {
                return t;
            }
        }
        return MV;
    }
}
