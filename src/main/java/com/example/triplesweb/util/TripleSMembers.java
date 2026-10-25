package com.example.triplesweb.util;

import java.util.List;
import java.util.Map;

/**
 * 트리플에스 멤버 표시 순서·영문명·상징색 공통 소스.
 * 케미 서비스·프론트(triples-members.js)와 맞춘다.
 */
public final class TripleSMembers {

    public static final List<String> NAMES_KO = List.of(
            "윤서연", "정혜린", "이지우", "김채연", "김유연", "김수민", "김나경", "공유빈",
            "카에데", "서다현", "코토네", "곽연지", "니엔", "박소현", "신위", "마유",
            "린", "주빈", "정하연", "박시온", "김채원", "설린", "서아", "지연"
    );

    public static final Map<String, String> NAME_EN_BY_KO = Map.ofEntries(
            Map.entry("윤서연", "SeoYeon"),
            Map.entry("정혜린", "HyeRin"),
            Map.entry("이지우", "JiWoo"),
            Map.entry("김채연", "ChaeYeon"),
            Map.entry("김유연", "YooYeon"),
            Map.entry("김수민", "SooMin"),
            Map.entry("김나경", "NaKyoung"),
            Map.entry("공유빈", "YuBin"),
            Map.entry("카에데", "Kaede"),
            Map.entry("서다현", "DaHyun"),
            Map.entry("코토네", "Kotone"),
            Map.entry("곽연지", "YeonJi"),
            Map.entry("니엔", "Nien"),
            Map.entry("박소현", "SoHyun"),
            Map.entry("신위", "XinYu"),
            Map.entry("마유", "Mayu"),
            Map.entry("린", "Lynn"),
            Map.entry("주빈", "JooBin"),
            Map.entry("정하연", "HaYeon"),
            Map.entry("박시온", "ShiOn"),
            Map.entry("김채원", "ChaeWon"),
            Map.entry("설린", "Sullin"),
            Map.entry("서아", "SeoAh"),
            Map.entry("지연", "JiYeon")
    );

    public static final List<String> COLORS = List.of(
            "#22aeff", "#9200ff", "#fff800", "#98f21d", "#db0c74", "#fc83a4", "#6799a0", "#ffe3e2",
            "#ffc935", "#ff9ad6", "#ffde00", "#5974ff", "#ff953f", "#1222b5", "#d51313", "#fe8e76",
            "#ac62b7", "#b7f54c", "#52d9bb", "#ff428a", "#c7a3e0", "#7bba8d", "#cff3ff", "#ffab62"
    );

    private TripleSMembers() {}

    public static int indexOf(String member) {
        int idx = NAMES_KO.indexOf(member);
        if (idx < 0) {
            throw new IllegalArgumentException("알 수 없는 멤버: " + member);
        }
        return idx;
    }
}
