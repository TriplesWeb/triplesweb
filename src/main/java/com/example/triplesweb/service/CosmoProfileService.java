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
    private final CosmoCollectionService collections;
    private final ObjektService catalog;
    private final ArtistRepository artists;

    @SuppressWarnings("unchecked")
    public Map<String, Object> profile(String nickname, String tab, String member, int page) {
        Map<String, Object> view = new LinkedHashMap<>(collections.search(nickname, 0));
        List<Map<String, Object>> all = (List<Map<String, Object>>) view.remove("allCards");
        String base = String.valueOf(view.get("profileUrl"));
        view.put("isCollection", "collection".equals(tab));
        view.put("isProgress", !"collection".equals(tab));
        view.put("uniqueCount", all.stream().map(card -> text(card, "slug")).filter(s -> !s.isBlank()).distinct().count());
        view.put("memberCount", all.stream().map(card -> text(card, "member")).filter(s -> !s.isBlank()).distinct().count());
        view.put("seasonStats", breakdown(all, "season"));
        view.put("classStats", breakdown(all, "objektClass"));
        view.put("hasStats", !all.isEmpty());
        List<Map<String, Object>> catalogSnapshot = catalog.findCollections("triples");
        List<Map<String, Object>> progress = progress(all, base, catalogSnapshot);
        view.put("memberProgress", progress);
        view.put("hasMemberProgress", !progress.isEmpty());
        String selected = member == null ? "" : member.trim();
        view.put("hasProgressMember", !selected.isBlank());
        if (!"collection".equals(tab)) view.putAll(memberChecklist(all, catalogSnapshot, selected, base));
        List<Map<String, Object>> filtered = all.stream().filter(card -> selected.isBlank()
                || selected.equalsIgnoreCase(text(card, "member"))).toList();
        int pages = Math.max(1, (filtered.size() + 23) / 24);
        int safePage = Math.max(0, Math.min(page, pages - 1));
        view.put("cards", filtered.subList(safePage * 24, Math.min(filtered.size(), safePage * 24 + 24)));
        view.put("hasCards", !filtered.isEmpty());
        view.put("selectedMember", selected);
        view.put("filteredCount", filtered.size());
        view.put("pageDisplay", safePage + 1);
        view.put("totalPages", pages);
        view.put("hasPrev", safePage > 0);
        view.put("hasNext", safePage + 1 < pages);
        String query = "?tab=collection&member=" + encode(selected) + "&page=";
        view.put("previousUrl", base + query + Math.max(0, safePage - 1));
        view.put("nextUrl", base + query + (safePage + 1));
        return view;
    }

    private List<Map<String, Object>> progress(List<Map<String, Object>> held, String profileUrl,
                                               List<Map<String, Object>> catalogSnapshot) {
        Map<String, Set<String>> totals = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map<String, Object> item : catalogSnapshot) {
            if (excluded(text(item, "class"))) continue;
            String member = text(item, "member"), slug = text(item, "slug");
            if (!member.isBlank() && !slug.isBlank()) totals.computeIfAbsent(member, k -> new HashSet<>()).add(slug);
        }
        Map<String, Set<String>> owned = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Integer> copies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map<String, Object> card : held) {
            if (!"triples".equalsIgnoreCase(text(card, "artist")) || excluded(text(card, "objektClass"))) continue;
            String member = text(card, "member"), slug = text(card, "slug");
            if (member.isBlank() || slug.isBlank()) continue;
            owned.computeIfAbsent(member, k -> new HashSet<>()).add(slug);
            copies.merge(member, 1, Integer::sum);
        }
        Set<String> members = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        members.addAll(totals.keySet()); members.addAll(owned.keySet());
        Map<String, Map<String, Object>> portraits = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map<String, Object> option : MemberPortraitOptions.forObjektFilter(new ArrayList<>(members), artists.findAll()))
            portraits.put(text(option, "value"), option);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String member : members) {
            if (copies.getOrDefault(member, 0) == 0) continue;
            Set<String> available = totals.getOrDefault(member, Set.of());
            Set<String> collected = owned.getOrDefault(member, Set.of());
            long matched = collected.stream().filter(available::contains).count();
            int percent = available.isEmpty() ? 0 : (int) (matched * 100 / available.size());
            Map<String, Object> portrait = portraits.getOrDefault(member, Map.of());
            String color = text(portrait, "color");
            if (!color.matches("#[0-9a-fA-F]{6}")) color = "#66e6ce";
            String image = Boolean.TRUE.equals(portrait.get("textOnly")) ? "" : text(portrait, "image");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("member", member); row.put("owned", matched); row.put("total", available.size());
            row.put("copies", copies.getOrDefault(member, 0)); row.put("percent", percent);
            row.put("hasTotal", !available.isEmpty()); row.put("color", color);
            row.put("image", image); row.put("hasImage", !image.isBlank());
            row.put("collectionUrl", profileUrl + "?tab=collection&member=" + encode(member));
            row.put("progressUrl", profileUrl + "?tab=progress&member=" + encode(member));
            rows.add(row);
        }
        rows.sort(Comparator.<Map<String, Object>>comparingInt(row -> (Integer) row.get("percent")).reversed()
                .thenComparing(Comparator.comparingInt((Map<String, Object> row) -> (Integer) row.get("copies")).reversed())
                .thenComparing(row -> text(row, "member")));
        return rows;
    }

    private static Map<String, Object> memberChecklist(List<Map<String, Object>> held,
            List<Map<String, Object>> catalog, String member, String base) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> item : catalog) {
            if (excluded(text(item, "class"))) continue;
            String name = text(item, "member"), slug = text(item, "slug");
            if (!name.isBlank()) names.add(name);
            if (name.equalsIgnoreCase(member) && !slug.isBlank()) unique.putIfAbsent(slug, item);
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
        result.put("progressMemberOptions", names.stream().sorted(ObjektFilterOrder.members()).map(name -> Map.<String, Object>of("name", name,
                "selected", name.equalsIgnoreCase(member), "url", base + "?tab=progress&member=" + encode(name))).toList());
        return result;
    }

    private static long numericPart(String value) {
        var match = NUMBER.matcher(value);
        if (!match.find()) return Long.MAX_VALUE;
        try { return Long.parseLong(match.group()); }
        catch (NumberFormatException ignored) { return Long.MAX_VALUE; }
    }

    private static List<Map<String, Object>> breakdown(List<Map<String, Object>> cards, String field) {
        Map<String, Long> counts = new TreeMap<>();
        for (Map<String, Object> card : cards) {
            String value = text(card, field);
            counts.merge(value.isBlank() ? "미분류" : value, 1L, Long::sum);
        }
        return counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> Map.<String, Object>of("label", entry.getKey(), "count", entry.getValue(),
                        "percent", entry.getValue() * 100 / cards.size())).toList();
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
}
