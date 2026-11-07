package com.example.triplesweb.util;

import com.example.triplesweb.entity.ArticleObjekt;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.service.ArticleObjektService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 교환/거래 목록·상세용 Have/Want 멤버 요약. */
public final class ArticleMemberSwap {
    private ArticleMemberSwap() {}

    public static boolean matchesMember(Collection<ArticleObjekt> links, String member, String side) {
        if (links == null || links.isEmpty() || member == null || member.isBlank()) return false;
        if (MemberPortraitOptions.memberName(member).isBlank()
                && MemberPortraitOptions.memberAliases(member).isEmpty()) return false;
        List<ArticleObjekt> scoped = new ArrayList<>();
        String sideKey = normalizeSide(side);
        for (ArticleObjekt link : links) {
            if (link == null) continue;
            if (sideKey != null && !sideKey.equalsIgnoreCase(blankToHave(link.getSide()))) continue;
            scoped.add(link);
        }
        for (ArticleObjekt link : scoped) {
            if (matchesName(link.getMemberName(), member)) return true;
        }
        for (String name : uniqueMembers(scoped)) {
            if (matchesName(name, member)) return true;
        }
        return false;
    }

    private static boolean matchesName(String stored, String selected) {
        if (stored == null || stored.isBlank() || selected == null || selected.isBlank()) return false;
        if (MemberPortraitOptions.sameMember(stored, selected)) return true;
        if (MemberPortraitOptions.objektIncludesMember(stored, selected)) return true;
        for (String name : MemberPortraitOptions.objektMembers(stored)) {
            if (MemberPortraitOptions.sameMember(name, selected)) return true;
            if (MemberPortraitOptions.objektIncludesMember(name, selected)) return true;
        }
        return false;
    }

    public static Map<String, Object> summarize(List<ArticleObjekt> have,
                                                List<ArticleObjekt> want,
                                                List<Artist> artists,
                                                boolean trade) {
        List<String> haveMembers = uniqueMembers(have);
        List<String> wantMembers = trade ? uniqueMembers(want) : List.of();
        Map<String, Object> row = new LinkedHashMap<>();
        applyLead(row, "have", haveMembers, artists);
        applyLead(row, "want", wantMembers, artists);
        row.put("hasMemberSwap", Boolean.TRUE.equals(row.get("hasHaveLead"))
                || Boolean.TRUE.equals(row.get("hasWantLead")));
        boolean tradeSwap = trade && Boolean.TRUE.equals(row.get("hasHaveLead"))
                && Boolean.TRUE.equals(row.get("hasWantLead"));
        row.put("isTradeSwap", tradeSwap);
        row.put("showTradeSwap", tradeSwap);
        return row;
    }

    public static void copyOnto(Map<String, Object> summary, Object target) {
        if (summary == null || !(target instanceof com.example.triplesweb.dto.ArticleViewDto dto)) return;
        dto.setHasMemberSwap(truth(summary.get("hasMemberSwap")));
        dto.setShowTradeSwap(truth(summary.get("isTradeSwap")));
        dto.setHasHaveLead(truth(summary.get("hasHaveLead")));
        dto.setHaveLeadName(text(summary.get("haveLeadName")));
        dto.setHaveLeadColor(text(summary.get("haveLeadColor")));
        dto.setHasHaveMore(truth(summary.get("hasHaveMore")));
        dto.setHaveMoreCount(number(summary.get("haveMoreCount")));
        dto.setHasWantLead(truth(summary.get("hasWantLead")));
        dto.setWantLeadName(text(summary.get("wantLeadName")));
        dto.setWantLeadColor(text(summary.get("wantLeadColor")));
        dto.setHasWantMore(truth(summary.get("hasWantMore")));
        dto.setWantMoreCount(number(summary.get("wantMoreCount")));
    }

    private static void applyLead(Map<String, Object> row, String prefix, List<String> members, List<Artist> artists) {
        boolean hasLead = !members.isEmpty();
        row.put("has" + capitalize(prefix) + "Lead", hasLead);
        row.put(prefix + "LeadName", hasLead ? members.get(0) : "");
        row.put(prefix + "LeadColor", hasLead ? MemberPortraitOptions.artistColor(members.get(0), artists) : "#00f5d4");
        int extra = Math.max(0, members.size() - 1);
        row.put("has" + capitalize(prefix) + "More", extra > 0);
        row.put(prefix + "MoreCount", extra);
    }

    private static List<String> uniqueMembers(List<ArticleObjekt> links) {
        Set<String> names = new LinkedHashSet<>();
        if (links == null) return List.of();
        for (ArticleObjekt link : links) {
            if (link == null) continue;
            names.addAll(MemberPortraitOptions.objektMembers(link.getMemberName()));
        }
        return new ArrayList<>(names);
    }

    private static String normalizeSide(String side) {
        if (side == null || side.isBlank() || "all".equalsIgnoreCase(side)) return null;
        return "want".equalsIgnoreCase(side) ? ArticleObjektService.SIDE_WANT : ArticleObjektService.SIDE_HAVE;
    }

    private static String blankToHave(String side) {
        return side == null || side.isBlank() ? ArticleObjektService.SIDE_HAVE : side;
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private static boolean truth(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
