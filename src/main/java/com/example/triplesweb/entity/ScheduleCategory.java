package com.example.triplesweb.entity;

import java.util.Locale;

public enum ScheduleCategory {
    AUTO("자동 분류", "#00f5d4"),
    FANSIGN("팬사인회", "#a78bfa"),
    BROADCAST("방송 스케줄", "#fb923c"),
    ANNIVERSARY("기념일", "#00f5d4"),
    RELEASE("발매", "#f87171"),
    EVENT("행사", "#7dd3fc"),
    OTHER("기타", "#00f5d4");

    private final String label;
    private final String color;
    ScheduleCategory(String label, String color) { this.label=label; this.color=color; }
    public String getLabel() { return label; }
    public String getColor() { return color; }

    /** 기존 일정은 저장 내용을 바꾸지 않고 제목으로 분류한다. 명시한 분류가 우선한다. */
    public ScheduleCategory resolve(String title) {
        if (this != AUTO) return this;
        String value = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (contains(value, "팬사인", "팬싸", "팬싸인", "fansign", "영통", "영상통화")) return FANSIGN;
        if (contains(value, "생일", "주년", "기념일", "birthday", "anniversary", "🎂")) return ANNIVERSARY;
        if (contains(value, "발매", "컴백", "release", "comeback")) return RELEASE;
        if (contains(value, "방송", "라디오", "뮤직뱅크", "음악중심", "인기가요", "엠카", "더쇼", "쇼챔", "signal", "broadcast")) return BROADCAST;
        if (contains(value, "행사", "공연", "콘서트", "페스티벌", "축제", "festival", "concert")) return EVENT;
        return OTHER;
    }
    private static boolean contains(String value, String... words) {
        for (String word:words) if(value.contains(word)) return true;
        return false;
    }
}
