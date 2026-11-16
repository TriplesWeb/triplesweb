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
        List<String> wanted = memberTokens(member);
        if (wanted.isEmpty()) return false;
        List<ArticleObjekt> scoped = new ArrayList<>();
        String sideKey = normalizeSide(side);
        for (ArticleObjekt link : links) {
            if (link == null) continue;
            if (sideKey != null && !sideKey.equalsIgnoreCase(blankToHave(link.getSide()))) continue;
            scoped.add(link);
        }
        for (ArticleObjekt link : scoped) {
            if (matchesAnyName(link.getMemberName(), wanted)) return true;
        }
        for (String name : uniqueMembers(scoped)) {
            if (matchesAnyName(name, wanted)) return true;
        }
        return false;
    }

    public static boolean hasSide(Collection<ArticleObjekt> links, String side) {
        if (links == null || links.isEmpty()) return false;
        String sideKey = normalizeSide(side);
        for (ArticleObjekt link : links) {
            if (link == null) continue;
            if (sideKey == null || sideKey.equalsIgnoreCase(blankToHave(link.getSide()))) return true;
        }
        return false;
    }

    /**
     * 목록 필터: 선택 side의 한 오브젝트가 member·season·class 조건을 모두 만족하면 true.
     * 조건이 없으면 true. sell 보드에서 side만 있는 경우는 {@link #hasSide}로 검사한다.
     */
    public static boolean matchesListing(Collection<ArticleObjekt> links,
                                         String member,
                                         String side,
                                         String season,
                                         String objektClass) {
        List<String> wantedMembers = memberTokens(member);
        boolean needMember = !wantedMembers.isEmpty();
        boolean needSeason = season != null && !season.isBlank();
        boolean needClass = objektClass != null && !objektClass.isBlank();
        if (!needMember && !needSeason && !needClass) return true;
        if (links == null || links.isEmpty()) return false;
        String sideKey = normalizeSide(side);
        String seasonKey = needSeason ? season.trim() : "";
        String classKey = needClass ? objektClass.trim() : "";
        for (ArticleObjekt link : links) {
            if (link == null) continue;
            if (sideKey != null && !sideKey.equalsIgnoreCase(blankToHave(link.getSide()))) continue;
            if (needMember && !matchesAnyName(link.getMemberName(), wantedMembers)) continue;
            if (needSeason && !seasonKey.equalsIgnoreCase(blank(link.getSeason()))) continue;
            if (needClass && !classKey.equalsIgnoreCase(blank(link.getObjektClass()))) continue;
            return true;
        }
        return false;
    }

    /** 목록용 썸네일 프리뷰 (앞에서 limit개 + 초과 개수). limit &lt; 0 이면 전부. */
    public static List<Map<String, Object>> previewThumbs(List<ArticleObjekt> links, int limit) {
        if (links == null || links.isEmpty()) return List.of();
        int max = limit < 0 ? Integer.MAX_VALUE : limit;
        if (max == 0) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        int shown = 0;
        for (ArticleObjekt link : links) {
            if (link == null) continue;
            if (shown >= max) break;
            Map<String, Object> row = new LinkedHashMap<>();
            String thumb = blank(link.getThumbnailImage());
            if (thumb.isEmpty()) thumb = blank(link.getFrontImage());
            String season = blank(link.getSeason());
            String member = blank(link.getMemberName());
            String collectionNo = blank(link.getCollectionNo());
            row.put("objektKey", blank(link.getObjektKey()));
            row.put("thumbnailImage", thumb);
            row.put("season", season);
            row.put("memberName", member);
            row.put("collectionNo", collectionNo);
            row.put("previewTip", previewTip(season, member, collectionNo));
            row.put("hasImage", !thumb.isEmpty());
            out.add(row);
            shown++;
        }
        return out;
    }

    public static int previewMoreCount(List<ArticleObjekt> links, int limit) {
        if (links == null || links.isEmpty() || limit <= 0) return 0;
        int total = 0;
        for (ArticleObjekt link : links) {
            if (link != null) total++;
        }
        return Math.max(0, total - limit);
    }

    /** 목록 썸네일 호버용: 시즌 · 멤버 · 번호 (비어 있는 값은 생략). 시리얼은 글 링크에 없음. */
    static String previewTip(String season, String member, String collectionNo) {
        StringBuilder tip = new StringBuilder();
        appendTipPart(tip, blank(season));
        appendTipPart(tip, blank(member));
        appendTipPart(tip, blank(collectionNo));
        return tip.toString();
    }

    private static void appendTipPart(StringBuilder tip, String part) {
        if (part.isEmpty()) return;
        if (!tip.isEmpty()) tip.append(" · ");
        tip.append(part);
    }

    /** 콤마로 구분된 멤버 필터를 파싱한다. */
    public static List<String> memberTokens(String member) {
        if (member == null || member.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : member.split(",")) {
            String token = part == null ? "" : part.trim();
            if (token.isEmpty()) continue;
            if (MemberPortraitOptions.memberName(token).isBlank()
                    && MemberPortraitOptions.memberAliases(token).isEmpty()) continue;
            out.add(token);
        }
        return out;
    }

    private static boolean matchesAnyName(String stored, List<String> selected) {
        if (selected == null || selected.isEmpty()) return false;
        for (String token : selected) {
            if (matchesName(stored, token)) return true;
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
        List<String> wantMembers = uniqueMembers(want);
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

    public static void copyPreviewsOnto(Object target,
                                        List<ArticleObjekt> have,
                                        List<ArticleObjekt> want,
                                        int limit) {
        if (!(target instanceof com.example.triplesweb.dto.ArticleViewDto dto)) return;
        int[] caps = previewCaps(have, want, limit);
        List<Map<String, Object>> havePreview = previewThumbs(have, caps[0]);
        List<Map<String, Object>> wantPreview = previewThumbs(want, caps[1]);
        int haveMore = previewMoreCount(have, caps[0]);
        int wantMore = previewMoreCount(want, caps[1]);
        dto.setHavePreview(havePreview);
        dto.setWantPreview(wantPreview);
        dto.setHaveAll(previewThumbs(have, -1));
        dto.setWantAll(previewThumbs(want, -1));
        dto.setHasHavePreview(!havePreview.isEmpty());
        dto.setHasWantPreview(!wantPreview.isEmpty());
        dto.setHasObjektPreview(!havePreview.isEmpty() || !wantPreview.isEmpty());
        dto.setHasHavePreviewMore(haveMore > 0);
        dto.setHavePreviewMoreCount(haveMore);
        dto.setHasWantPreviewMore(wantMore > 0);
        dto.setWantPreviewMoreCount(wantMore);
    }

    /**
     * 쪽마다 기본 limit칸을 두고, 한쪽이 덜 쓰면 남는 칸을 넘치는 쪽에 넘긴다.
     * @return [haveCap, wantCap]
     */
    static int[] previewCaps(List<ArticleObjekt> have, List<ArticleObjekt> want, int limit) {
        int base = Math.max(0, limit);
        int haveSize = countLinks(have);
        int wantSize = countLinks(want);
        int haveCap = Math.min(haveSize, base);
        int wantCap = Math.min(wantSize, base);
        int spare = (base - haveCap) + (base - wantCap);
        int haveNeed = Math.max(0, haveSize - haveCap);
        int wantNeed = Math.max(0, wantSize - wantCap);
        if (spare > 0 && haveNeed > 0) {
            int give = Math.min(spare, haveNeed);
            haveCap += give;
            spare -= give;
            haveNeed -= give;
        }
        if (spare > 0 && wantNeed > 0) {
            wantCap += Math.min(spare, wantNeed);
        }
        return new int[]{haveCap, wantCap};
    }

    private static int countLinks(List<ArticleObjekt> links) {
        if (links == null || links.isEmpty()) return 0;
        int total = 0;
        for (ArticleObjekt link : links) {
            if (link != null) total++;
        }
        return total;
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

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }
}
