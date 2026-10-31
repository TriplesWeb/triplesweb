package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.ObjektService;
import com.example.triplesweb.service.ObjektMarketDataService;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.example.triplesweb.util.ObjektFilterOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/cosmo")
@RequiredArgsConstructor
public class CosmoController {

    private static final int PAGE_SIZE = 20;
    private final ObjektService objektService;
    private final ArtistRepository artistRepository;
    private final ObjektMarketDataService marketData;

    @GetMapping("/objekt")
    public String indexCosmoObjekt(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) List<String> member,
            @RequestParam(required = false) List<String> season,
            @RequestParam(name = "class", required = false) List<String> classFilters,
            @RequestParam(defaultValue = "all") String kind,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "all") String group,
            Model model
    ) {
        // 무한 스크롤: 최초 HTML은 항상 첫 페이지(20개)만 렌더한다.
        ObjektPageResult page = loadObjektPage(0, search, member, season, classFilters, kind, sort, group);

        List<String> memberOptions = ObjektFilterOrder.sortedMembers(
                uniqueValues(page.allObjekts(), item -> firstValue(item, "member", "memberName")).stream()
                        .flatMap(value -> MemberPortraitOptions.objektMembers(value).stream()).toList());
        List<Artist> artists = artistRepository.findAll(Sort.by(Sort.Direction.ASC, "artistId"));
        model.addAttribute("memberOptions", memberOptions);
        List<Map<String, Object>> pickerOptions = MemberPortraitOptions.forObjektFilter(memberOptions, artists);
        String selectedGroup = memberGroup(group, cleanSelections(member));
        List<String> selectedMembers = "other".equals(selectedGroup) ? List.of() : cleanSelections(member).stream()
                .flatMap(value -> MemberPortraitOptions.objektMembers(value).stream()).distinct().toList();
        for (Map<String, Object> option : pickerOptions) option.put("active", selectedMembers.stream()
                .anyMatch(selected -> MemberPortraitOptions.sameMember(selected, option.get("value").toString())));
        model.addAttribute("memberPickerOptions", pickerOptions);
        model.addAttribute("selectedGroup", selectedGroup);
        model.addAttribute("isOtherGroup", "other".equals(selectedGroup));
        model.addAttribute("seasonOptions", ObjektFilterOrder.sortedSeasons(uniqueValues(page.allObjekts(), objekt -> firstValue(objekt, "seasonKey", "season"))));
        model.addAttribute("classOptions", uniqueValues(page.allObjekts(), objekt -> firstValue(objekt, "classKey", "class")));
        model.addAttribute("objektNumbers", uniqueValues(page.allObjekts(), objekt -> firstValue(objekt, "collectionNo", "collectionNumber")));

        model.addAttribute("objekts", page.items());
        model.addAttribute("objektCount", page.resultCount());
        model.addAttribute("objektPageCount", page.items().size());
        model.addAttribute("objektAllCount", page.allCount());
        model.addAttribute("searchQuery", search == null ? "" : search);
        model.addAttribute("selectedKind", kind);
        model.addAttribute("selectedSort", sort);
        model.addAttribute("objektPageDisplay", 1);
        model.addAttribute("objektTotalPages", page.totalPages());
        model.addAttribute("hasNextPage", page.hasMore());
        model.addAttribute("nextPage", 1);
        model.addAttribute("objektError", page.error().isBlank() ? null : page.error());

        return "cosmo/objekt";
    }

    /** 무한 스크롤용: 필터를 유지한 채 다음 20개만 JSON으로 반환한다. */
    @GetMapping("/objekt/more")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> moreCosmoObjekt(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) List<String> member,
            @RequestParam(required = false) List<String> season,
            @RequestParam(name = "class", required = false) List<String> classFilters,
            @RequestParam(defaultValue = "all") String kind,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "all") String group
    ) {
        int safeRequestPage = Math.max(0, page);
        ObjektPageResult result = loadObjektPage(safeRequestPage, search, member, season, classFilters, kind, sort, group);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", result.page());
        body.put("pageSize", PAGE_SIZE);
        body.put("loaded", result.items().size());
        body.put("resultCount", result.resultCount());
        body.put("allCount", result.allCount());
        body.put("totalPages", result.totalPages());
        body.put("hasMore", result.hasMore());
        body.put("nextPage", result.hasMore() ? result.page() + 1 : null);
        body.put("error", result.error().isBlank() ? null : result.error());
        body.put("items", result.items());
        return ResponseEntity.ok(body);
    }

    private ObjektPageResult loadObjektPage(
            int page,
            String search,
            List<String> member,
            List<String> season,
            List<String> classFilters,
            String kind,
            String sort,
            String group
    ) {
        List<Map<String, Object>> allObjekts;
        String error = "";

        try {
            allObjekts = objektService.findCollections("triples");
            if (allObjekts == null) allObjekts = Collections.emptyList();
            // 정렬용 벌크 통계. 화면의 발행량은 아래 시리얼 집합 결과로 교체한다.
            allObjekts = marketData.withMintCounts(allObjekts);

            if (allObjekts.isEmpty()) error = "API 응답에 표시할 컬렉션이 없습니다.";
        } catch (RestClientException ex) {
            allObjekts = Collections.emptyList();
            error = "Objekt API에 연결하지 못했습니다. 잠시 후 새로고침해 주세요.";
            ex.printStackTrace();
        }

        String query = normalize(search);
        List<String> selectedMembers = cleanSelections(member);
        String selectedGroup = memberGroup(group, selectedMembers);
        java.util.Set<String> selectedNames = selectedMembers.stream()
                .flatMap(value -> MemberPortraitOptions.objektMembers(value).stream()).collect(Collectors.toSet());
        Map<String, java.util.Set<String>> membershipCache = new java.util.HashMap<>();
        List<String> selectedSeasons = cleanSelections(season);
        List<String> selectedClasses = cleanSelections(classFilters);
        String selectedKind = normalize(kind);
        String selectedSort = normalize(sort);

        // 전체 캐시에서 검색어와 모든 필터를 먼저 적용하고, 그 결과에만 정렬과 페이지네이션을 적용한다.
        List<Map<String, Object>> filtered = allObjekts.stream()
                .filter(objekt -> query.isBlank() || searchableText(objekt).contains(query))
                .filter(objekt -> "all".equals(selectedGroup) || matchesMember(selectedGroup, selectedNames,
                        membershipCache.computeIfAbsent(firstValue(objekt, "member", "memberName"), MemberPortraitOptions::objektMembers)))
                .filter(objekt -> matchesSelection(selectedSeasons, firstValue(objekt, "seasonKey", "season")))
                .filter(objekt -> matchesSelection(selectedClasses, firstValue(objekt, "classKey", "class")))
                .filter(objekt -> selectedKind.isBlank() || "all".equals(selectedKind)
                        || normalize(firstValue(objekt, "collectionNo", "collectionNumber")).equals(selectedKind))
                .sorted(sortComparator(selectedSort, new java.util.HashMap<>()))
                .collect(Collectors.toList());

        int resultCount = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil(resultCount / (double) PAGE_SIZE));
        int safePage = Math.max(0, page);
        if (resultCount == 0 || safePage >= totalPages) {
            return new ObjektPageResult(
                    List.of(), allObjekts, safePage, resultCount, allObjekts.size(), totalPages, false, error);
        }
        int fromIndex = safePage * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, resultCount);
        List<Map<String, Object>> pageObjekts = marketData.withIssuedSupply(filtered.subList(fromIndex, toIndex));
        if (pageObjekts == null) pageObjekts = filtered.subList(fromIndex, toIndex);
        boolean hasMore = toIndex < resultCount;

        return new ObjektPageResult(
                pageObjekts,
                allObjekts,
                safePage,
                resultCount,
                allObjekts.size(),
                totalPages,
                hasMore,
                error
        );
    }

    private record ObjektPageResult(
            List<Map<String, Object>> items,
            List<Map<String, Object>> allObjekts,
            int page,
            int resultCount,
            int allCount,
            int totalPages,
            boolean hasMore,
            String error
    ) {}

    private static List<String> cleanSelections(List<String> values) {
        if (values == null) return Collections.emptyList();
        return values.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank() && !"all".equalsIgnoreCase(value))
                .distinct().collect(Collectors.toList());
    }

    private static String memberGroup(String group, List<String> selected) {
        if ("other".equalsIgnoreCase(group)) return "other";
        if ("members".equalsIgnoreCase(group) || selected.stream().anyMatch(value -> !MemberPortraitOptions.objektMembers(value).isEmpty())) return "members";
        return selected.isEmpty() ? "all" : "other";
    }

    private static boolean matchesMember(String group, java.util.Set<String> selected, java.util.Set<String> actual) {
        if ("all".equals(group)) return true;
        if ("other".equals(group)) return actual.isEmpty();
        return !actual.isEmpty() && (selected.isEmpty() || actual.stream().anyMatch(selected::contains));
    }

    private static boolean matchesSelection(List<String> selected, String actual) {
        if (selected == null || selected.isEmpty()) return true;
        String normalizedActual = normalize(actual);
        return selected.stream().map(CosmoController::normalize).anyMatch(normalizedActual::equals);
    }

    private static List<String> uniqueValues(List<Map<String, Object>> source, Function<Map<String, Object>, String> extractor) {
        return source.stream().map(extractor).filter(value -> value != null && !value.isBlank())
                .map(String::trim).distinct().sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
                .collect(Collectors.toList());
    }

    private static Comparator<Map<String, Object>> sortComparator(String sort, Map<String, Integer> memberOrderCache) {
        Comparator<Map<String, Object>> byDate = Comparator.comparing(
                objekt -> firstValue(objekt, "createdAt", "created_at", "date"),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        Comparator<Map<String, Object>> byNumber = Comparator.comparingDouble(objekt -> numberValue(firstValue(objekt, "numberValue", "number", "collectionNo")));
        Comparator<Map<String, Object>> byMember = Comparator.comparing(
                objekt -> firstValue(objekt, "member", "memberName"),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

        return switch (sort) {
            case "oldest" -> byDate;
            case "number_low" -> byNumber;
            case "number_high" -> byNumber.reversed();
            case "mint_low" -> mintComparator(false);
            case "mint_high" -> mintComparator(true);
            case "first_member" -> Comparator.<Map<String, Object>>comparingInt(item -> memberOrder(item, memberOrderCache)).thenComparing(byMember);
            case "last_member" -> Comparator.<Map<String, Object>>comparingInt(item -> { int order = memberOrder(item, memberOrderCache); return order == 24 ? 24 : 23 - order; }).thenComparing(byMember);
            case "latest" -> byDate.reversed();
            default -> byDate.reversed();
        };
    }

    private static int memberOrder(Map<String, Object> item, Map<String, Integer> cache) {
        return cache.computeIfAbsent(firstValue(item, "member", "memberName"), member ->
                MemberPortraitOptions.objektMembers(member).stream().mapToInt(MemberPortraitOptions::memberIndex).min().orElse(24));
    }

    private static Comparator<Map<String, Object>> mintComparator(boolean descending) {
        Comparator<Long> counts = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.<Map<String, Object>, Long>comparing(item ->
                        item.get("mintCount") instanceof Number n ? n.longValue() : null,
                        Comparator.nullsLast(counts))
                .thenComparing(item -> firstValue(item, "slug", "id"));
    }

    private static double numberValue(String value) {
        try { return Double.parseDouble(value); }
        catch (Exception ignored) { return 0; }
    }

    private static String searchableText(Map<String, Object> objekt) {
        return String.join(" ",
                value(objekt, "searchText"), value(objekt, "collectionId"), value(objekt, "slug"),
                value(objekt, "season"), value(objekt, "seasonKey"), value(objekt, "member"),
                value(objekt, "memberName"), value(objekt, "artist"), value(objekt, "collectionNo"),
                value(objekt, "class"), value(objekt, "classKey"), value(objekt, "numberValue"))
                .toLowerCase(Locale.ROOT);
    }

    private static String firstValue(Map<String, Object> objekt, String... keys) {
        for (String key : keys) {
            Object value = objekt.get(key);
            if (value != null && !value.toString().isBlank() && !"null".equalsIgnoreCase(value.toString())) {
                return value.toString();
            }
        }
        return "";
    }

    private static String value(Map<String, Object> objekt, String key) {
        Object value = objekt.get(key);
        return value == null ? "" : value.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    @GetMapping("/event")
    public String indexCosmoEvent() { return "cosmo/event"; }

}
