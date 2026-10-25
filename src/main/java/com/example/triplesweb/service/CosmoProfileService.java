package com.example.triplesweb.service;

import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.example.triplesweb.util.ObjektFilterOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CosmoProfileService {
    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("\\d+");
    private static final String OTHER_KEY = "__other__";
    private final CosmoCollectionService collections;
    private final ObjektService catalog;
    private final ArtistRepository artists;

    @SuppressWarnings("unchecked")
    public Map<String, Object> profile(String nickname, String member) {
        return profile(nickname, member, "all", "progress");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> profile(String nickname, String member, String group) {
        return profile(nickname, member, group, "progress");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> profile(String nickname, String member, String group, String tab) {
        Map<String, Object> view = new LinkedHashMap<>(collections.search(nickname, 0));
        List<Map<String, Object>> all = (List<Map<String, Object>>) view.remove("allCards");
        String base = String.valueOf(view.get("profileUrl"));
        view.put("nicknameEncoded", URLEncoder.encode(String.valueOf(view.get("nickname")), StandardCharsets.UTF_8));
        view.put("uniqueCount", all.stream().map(card -> text(card, "slug")).filter(s -> !s.isBlank()).distinct().count());
        view.put("memberCount", all.stream()
                .flatMap(card -> MemberPortraitOptions.objektMembers(text(card, "member")).stream())
                .distinct().count());
        List<Map<String, Object>> catalogSnapshot = catalog.findCollections("triples");
        ProgressBuckets buckets = buckets(all, catalogSnapshot);
        view.put("seasonStats", catalogBreakdown(catalogSnapshot, all, "season", "season"));
        view.put("classStats", catalogBreakdown(catalogSnapshot, all, "class", "objektClass"));
        view.put("hasStats", !catalogSnapshot.isEmpty() || !all.isEmpty());
        List<Map<String, Object>> memberRows = memberProgressRows(buckets, base);
        Map<String, Object> otherRow = otherProgressRow(buckets, base);
        view.put("memberProgress", memberRows);
        view.put("hasMemberProgress", !memberRows.isEmpty());
        view.put("otherProgress", otherRow == null ? List.of() : List.of(otherRow));
        view.put("hasOtherProgress", otherRow != null);
        String selected = MemberPortraitOptions.memberName(member);
        boolean membersTab = isMembersTab(tab, selected, group);
        String selectedGroup = membersTab ? normalizeGroup(group, member) : "all";
        view.put("hasProgressMember", !selected.isBlank());
        view.put("selectedMember", selected);
        view.put("selectedGroup", selectedGroup);
        view.put("isOtherGroup", "other".equals(selectedGroup));
        view.put("isMembersGroup", "members".equals(selectedGroup));
        view.put("isAllGroup", "all".equals(selectedGroup));
        view.put("isOverviewTab", !membersTab);
        view.put("isMembersTab", membersTab);
        view.put("overviewUrl", base);
        view.put("membersTabUrl", base + "?tab=members");
        if (membersTab) {
            if ("other".equals(selectedGroup)) {
                view.putAll(memberChecklist(all, catalogSnapshot, "", true));
                view.put("hasProgressMember", false);
                view.put("selectedMember", "기타 옵젝");
            } else if (!selected.isBlank()) {
                view.putAll(memberChecklist(all, catalogSnapshot, selected, false));
            } else {
                view.put("checklistSeasons", List.of());
                view.put("hasChecklist", false);
            }
            Set<String> rawMembers = new LinkedHashSet<>();
            for (Map<String, Object> item : catalogSnapshot) {
                String name = text(item, "member");
                if (!name.isBlank()) rawMembers.add(name);
            }
            List<Map<String, Object>> picker = MemberPortraitOptions.forObjektFilter(
                    MemberPortraitOptions.soloMemberNames(rawMembers), artists.findAll());
            for (Map<String, Object> option : picker) {
                option.put("active", MemberPortraitOptions.sameMember(selected, String.valueOf(option.get("value"))));
            }
            view.put("memberPickerOptions", picker);
        } else {
            view.put("checklistSeasons", List.of());
            view.put("hasChecklist", false);
            view.put("memberPickerOptions", List.of());
        }
        return view;
    }

    private static boolean isMembersTab(String tab, String selectedMember, String group) {
        if (!selectedMember.isBlank()) return true;
        if ("other".equalsIgnoreCase(group)) return true;
        if (tab == null || tab.isBlank()) return false;
        String normalized = tab.trim().toLowerCase(Locale.ROOT);
        return "members".equals(normalized) || "member".equals(normalized);
    }

    private static String normalizeGroup(String group, String member) {
        if ("other".equalsIgnoreCase(group)) return "other";
        if ("members".equalsIgnoreCase(group) || (member != null && !member.isBlank()
                && !MemberPortraitOptions.isOtherMember(member))) return "members";
        if (member != null && !member.isBlank() && MemberPortraitOptions.isOtherMember(member)) return "other";
        return "all";
    }

    private ProgressBuckets buckets(List<Map<String, Object>> held, List<Map<String, Object>> catalogSnapshot) {
        Map<String, Set<String>> totals = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Set<String>> owned = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Integer> copies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map<String, Object> item : catalogSnapshot) {
            if (excluded(text(item, "class"))) continue;
            String slug = text(item, "slug");
            if (slug.isBlank()) continue;
            for (String key : progressKeys(text(item, "member"))) {
                totals.computeIfAbsent(key, k -> new HashSet<>()).add(slug);
            }
        }
        for (Map<String, Object> card : held) {
            if (!"triples".equalsIgnoreCase(text(card, "artist")) || excluded(text(card, "objektClass"))) continue;
            String slug = text(card, "slug");
            if (slug.isBlank()) continue;
            for (String key : progressKeys(text(card, "member"))) {
                owned.computeIfAbsent(key, k -> new HashSet<>()).add(slug);
                copies.merge(key, 1, Integer::sum);
            }
        }
        return new ProgressBuckets(totals, owned, copies);
    }

    /** 유닛은 구성 솔로에, zenith 등은 기타 키에 귀속. 유닛 자체 키는 만들지 않는다. */
    private static Set<String> progressKeys(String memberField) {
        Set<String> solos = MemberPortraitOptions.objektMembers(memberField);
        if (solos.isEmpty()) return Set.of(OTHER_KEY);
        return solos;
    }

    private List<Map<String, Object>> memberProgressRows(ProgressBuckets buckets, String profileUrl) {
        Set<String> members = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String key : buckets.totals.keySet()) if (!OTHER_KEY.equalsIgnoreCase(key)) members.add(key);
        for (String key : buckets.owned.keySet()) if (!OTHER_KEY.equalsIgnoreCase(key)) members.add(key);
        Map<String, Map<String, Object>> portraits = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map<String, Object> option : MemberPortraitOptions.forObjektFilter(new ArrayList<>(members), artists.findAll())) {
            portraits.put(text(option, "value"), option);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String member : members) {
            rows.add(progressRow(member, buckets.forKey(member), portraits.getOrDefault(member, Map.of()),
                    profileUrl + "?tab=members&member=" + encode(member), false));
        }
        rows.sort(Comparator.<Map<String, Object>>comparingInt(row -> (Integer) row.get("percent")).reversed()
                .thenComparing(Comparator.comparingInt((Map<String, Object> row) -> (Integer) row.get("copies")).reversed())
                .thenComparingInt(row -> MemberPortraitOptions.memberIndex(text(row, "member")))
                .thenComparing(row -> text(row, "member")));
        return rows;
    }

    private Map<String, Object> otherProgressRow(ProgressBuckets buckets, String profileUrl) {
        if (!buckets.totals.containsKey(OTHER_KEY) && !buckets.owned.containsKey(OTHER_KEY)) return null;
        return progressRow("기타 옵젝", buckets.forKey(OTHER_KEY), Map.of("color", "#9aa4b2", "textOnly", true),
                profileUrl + "?tab=members&group=other", true);
    }

    private static Map<String, Object> progressRow(String label, ProgressBuckets.Slice slice,
                                                   Map<String, Object> portrait, String progressUrl, boolean other) {
        Set<String> available = slice.totals();
        Set<String> collected = slice.owned();
        long matched = collected.stream().filter(available::contains).count();
        if (available.isEmpty()) matched = collected.size();
        int percent = available.isEmpty() ? 0 : (int) (matched * 100 / available.size());
        String color = text(portrait, "color");
        if (!color.matches("#[0-9a-fA-F]{6}")) color = other ? "#9aa4b2" : "#66e6ce";
        String image = Boolean.TRUE.equals(portrait.get("textOnly")) ? "" : text(portrait, "image");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("member", label);
        row.put("owned", matched);
        row.put("total", available.size());
        row.put("copies", slice.copies());
        row.put("percent", percent);
        row.put("hasTotal", !available.isEmpty());
        row.put("color", color);
        row.put("image", image);
        row.put("hasImage", !image.isBlank());
        row.put("progressUrl", progressUrl);
        row.put("isEmpty", matched == 0);
        row.put("isOther", other);
        return row;
    }

    private static List<Map<String, Object>> catalogBreakdown(
            List<Map<String, Object>> catalog, List<Map<String, Object>> held,
            String catalogField, String heldField) {
        Map<String, Set<String>> totals = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Set<String>> owned = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> ownedSlugs = new HashSet<>();
        for (Map<String, Object> card : held) {
            if (!"triples".equalsIgnoreCase(text(card, "artist")) || excluded(text(card, "objektClass"))) continue;
            String slug = text(card, "slug");
            if (!slug.isBlank()) ownedSlugs.add(slug);
        }
        for (Map<String, Object> item : catalog) {
            if (excluded(text(item, "class"))) continue;
            String slug = text(item, "slug");
            if (slug.isBlank()) continue;
            String label = text(item, catalogField);
            if (label.isBlank()) label = "미분류";
            totals.computeIfAbsent(label, k -> new HashSet<>()).add(slug);
            if (ownedSlugs.contains(slug)) owned.computeIfAbsent(label, k -> new HashSet<>()).add(slug);
        }
        if (totals.isEmpty()) {
            for (Map<String, Object> card : held) {
                String label = text(card, heldField);
                if (label.isBlank()) label = "미분류";
                String slug = text(card, "slug");
                if (slug.isBlank()) continue;
                owned.computeIfAbsent(label, k -> new HashSet<>()).add(slug);
            }
            return owned.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Set<String>>>comparingInt(e -> e.getValue().size()).reversed()
                            .thenComparing(Map.Entry::getKey))
                    .map(entry -> rowBreakdown(entry.getKey(), entry.getValue().size(), entry.getValue().size()))
                    .toList();
        }
        Comparator<String> order = "season".equals(catalogField) ? ObjektFilterOrder.seasons() : String.CASE_INSENSITIVE_ORDER;
        return totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(order))
                .map(entry -> {
                    int total = entry.getValue().size();
                    int have = owned.getOrDefault(entry.getKey(), Set.of()).size();
                    return rowBreakdown(entry.getKey(), have, total);
                })
                .toList();
    }

    private static Map<String, Object> rowBreakdown(String label, int owned, int total) {
        int percent = total == 0 ? 0 : owned * 100 / total;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("owned", owned);
        row.put("total", total);
        row.put("count", owned);
        row.put("percent", percent);
        row.put("isEmpty", owned == 0);
        return row;
    }

    private static Map<String, Object> memberChecklist(List<Map<String, Object>> held,
            List<Map<String, Object>> catalog, String member, boolean otherOnly) {
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> item : catalog) {
            if (excluded(text(item, "class"))) continue;
            String raw = text(item, "member");
            String slug = text(item, "slug");
            if (slug.isBlank()) continue;
            Set<String> solos = MemberPortraitOptions.objektMembers(raw);
            boolean match = otherOnly ? solos.isEmpty() : solos.contains(member);
            if (match) unique.putIfAbsent(slug, item);
        }
        Map<String, Long> copies = new HashMap<>();
        for (Map<String, Object> card : held) copies.merge(text(card, "slug"), 1L, Long::sum);
        Comparator<String> natural = Comparator.comparingLong(CosmoProfileService::numericPart)
                .thenComparing(String.CASE_INSENSITIVE_ORDER);
        Map<String, List<Map<String, Object>>> seasons = new TreeMap<>(ObjektFilterOrder.seasons());
        for (Map<String, Object> item : unique.values())
            seasons.computeIfAbsent(text(item, "season"), k -> new ArrayList<>()).add(item);
        List<String> classOrder = List.of("first", "special", "double", "premier", "motion", "lenticular", "unit");
        Comparator<String> classes = Comparator.comparingInt((String value) -> {
            int i = classOrder.indexOf(value.toLowerCase(Locale.ROOT));
            return i < 0 ? classOrder.size() : i;
        }).thenComparing(String.CASE_INSENSITIVE_ORDER);
        List<Map<String, Object>> seasonRows = new ArrayList<>();
        int collected = 0;
        for (var season : seasons.entrySet()) {
            Map<String, List<Map<String, Object>>> groups = new TreeMap<>(classes);
            for (Map<String, Object> item : season.getValue())
                groups.computeIfAbsent(text(item, "class"), k -> new ArrayList<>()).add(item);
            List<Map<String, Object>> groupRows = new ArrayList<>();
            int seasonOwned = 0;
            for (var group : groups.entrySet()) {
                group.getValue().sort(Comparator.comparing((Map<String, Object> item) -> text(item, "collectionNo"), natural)
                        .thenComparing(item -> text(item, "slug")));
                List<Map<String, Object>> cards = new ArrayList<>();
                int owned = 0;
                for (Map<String, Object> item : group.getValue()) {
                    String slug = text(item, "slug");
                    long count = copies.getOrDefault(slug, 0L);
                    if (count > 0) owned++;
                    String image = GravityService.safeImage(text(item, "thumbnailImage"));
                    if (image.isBlank()) image = GravityService.safeImage(text(item, "frontImage"));
                    cards.add(Map.of("number", text(item, "collectionNo"), "name", text(item, "collectionId"),
                            "image", image, "hasImage", !image.isBlank(), "owned", count > 0,
                            "copies", count, "detailUrl", "/cosmo/objekt/detail/" + encode(slug)));
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("klass", group.getKey().isBlank() ? "미분류" : group.getKey());
                row.put("cards", cards); row.put("owned", owned); row.put("total", cards.size());
                row.put("missing", cards.size() - owned); row.put("complete", owned == cards.size());
                row.put("percent", owned * 100 / cards.size());
                row.put("open", groupRows.isEmpty());
                groupRows.add(row); seasonOwned += owned;
            }
            seasonRows.add(Map.of("season", season.getKey().isBlank() ? "미분류" : season.getKey(),
                    "groups", groupRows, "owned", seasonOwned, "total", season.getValue().size(),
                    "complete", seasonOwned == season.getValue().size(), "percent", seasonOwned * 100 / season.getValue().size()));
            collected += seasonOwned;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checklistSeasons", seasonRows); result.put("hasChecklist", !seasonRows.isEmpty());
        result.put("checklistOwned", collected); result.put("checklistTotal", unique.size());
        result.put("checklistMissing", unique.size() - collected);
        return result;
    }

    private static long numericPart(String value) {
        var match = NUMBER.matcher(value);
        if (!match.find()) return Long.MAX_VALUE;
        try { return Long.parseLong(match.group()); }
        catch (NumberFormatException ignored) { return Long.MAX_VALUE; }
    }

    private static boolean excluded(String klass) {
        return "Welcome".equalsIgnoreCase(klass) || "Zero".equalsIgnoreCase(klass);
    }
    private static String text(Map<String, Object> row, String key) {
        return Objects.toString(row.get(key), "");
    }
    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record ProgressBuckets(Map<String, Set<String>> totals, Map<String, Set<String>> owned,
                                   Map<String, Integer> copies) {
        Slice forKey(String key) {
            return new Slice(totals.getOrDefault(key, Set.of()), owned.getOrDefault(key, Set.of()),
                    copies.getOrDefault(key, 0));
        }
        private record Slice(Set<String> totals, Set<String> owned, int copies) {}
    }
}
