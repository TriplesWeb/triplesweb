package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.entity.Event;
import com.example.triplesweb.service.ArtistSnapshotService;
import com.example.triplesweb.service.EventService;
import com.example.triplesweb.service.ObjektService;
import com.example.triplesweb.service.ObjektMarketDataService;
import com.example.triplesweb.service.ObjektLiveSettings;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.example.triplesweb.util.ObjektCatalogSort;
import com.example.triplesweb.util.ObjektLinks;
import com.example.triplesweb.util.ObjektSeasonCodes;
import com.example.triplesweb.util.ObjektSeasonColors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/cosmo")
@RequiredArgsConstructor
public class CosmoController {

    private static final int PAGE_SIZE = 20;
    private final ObjektService objektService;
    private final ArtistSnapshotService artistSnapshot;
    private final ObjektMarketDataService marketData;
    private final EventService eventService;
    private final ObjektLiveSettings live;

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

        ObjektService.FilterFacets facets = objektService.filterFacets();
        if (facets == null) facets = ObjektService.FilterFacets.empty();
        List<String> memberOptions = facets.members();
        List<Artist> artists = artistSnapshot.findAll();
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
        model.addAttribute("seasonOptions", facets.seasons());
        model.addAttribute("classOptions", facets.classes());
        model.addAttribute("objektNumbers", facets.collectionNumbers());
        model.addAttribute("kindByClassJson", ObjektCatalogSort.kindByClassJson(facets.numbersByClass()));

        model.addAttribute("objekts", withMemberLinks(page.items()));
        model.addAttribute("objektCount", page.resultCount());
        model.addAttribute("objektPageCount", page.items().size());
        model.addAttribute("objektAllCount", page.allCount());
        model.addAttribute("searchQuery", search == null ? "" : search);
        model.addAttribute("selectedKind", kind);
        model.addAttribute("selectedKindLabel", kindLabel(kind));
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
        body.put("items", withMemberLinks(result.items()));
        return ResponseEntity.ok(body);
    }

    /**
     * 브라우저 라이브 폴링용. 메모리 스냅샷만 돌려 외부 API와 페이지 렌더를 다시 하지 않는다.
     */
    @GetMapping("/objekt/pulse")
    @ResponseBody
    public Map<String, Object> objektPulse(
            @RequestParam(name = "key", required = false) List<String> keys
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("catalogSig", objektService.catalogSignature());
        body.put("catalogCount", objektService.catalogCount());
        body.put("catalogAt", objektService.getLastSuccessfulRefreshMillis());
        body.put("mintsSig", marketData.mintsSignature());
        body.put("mintsAt", marketData.mintsRefreshedAt());
        Map<String, Object> supplies = new LinkedHashMap<>();
        if (keys != null) {
            for (String key : keys.stream().filter(Objects::nonNull).map(String::trim)
                    .filter(value -> !value.isBlank()).distinct().limit(24).toList()) {
                Map<String, Object> item = objektService.findByKey(key);
                if (item == null) continue;
                Long count = marketData.mintCount(firstValue(item, "slug", "id"));
                if (count == null) continue;
                supplies.put(key, Map.of("status", "available", "count", count));
            }
        }
        body.put("supplies", supplies);
        body.put("dropWindow", live.dropWindow());
        body.put("pollMs", live.pollMs());
        body.put("forceRefreshMs", live.forceRefreshMs());
        body.put("historyTtlMs", live.historyTtlMs());
        return body;
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
        String selectedKind = "전체".equals(normalize(kind)) ? "all" : normalize(kind);
        String selectedSort = normalize(sort);

        // 전체 캐시에서 검색어와 모든 필터를 먼저 적용하고, 그 결과에만 정렬과 페이지네이션을 적용한다.
        List<Map<String, Object>> filtered = allObjekts.stream()
                .filter(objekt -> matchesSearch(query, objekt))
                .filter(objekt -> "all".equals(selectedGroup) || matchesMember(selectedGroup, selectedNames,
                        membershipCache.computeIfAbsent(firstValue(objekt, "member", "memberName"), MemberPortraitOptions::objektMembers)))
                .filter(objekt -> matchesSelection(selectedSeasons, firstValue(objekt, "seasonKey", "season")))
                .filter(objekt -> matchesSelection(selectedClasses, firstValue(objekt, "classKey", "class")))
                .filter(objekt -> selectedKind.isBlank() || "all".equals(selectedKind)
                        || normalize(firstValue(objekt, "collectionNo", "collectionNumber")).equals(selectedKind))
                .sorted(sortComparator(selectedSort))
                .toList();

        int resultCount = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil(resultCount / (double) PAGE_SIZE));
        int safePage = Math.max(0, page);
        if (resultCount == 0 || safePage >= totalPages) {
            return new ObjektPageResult(
                    List.of(), allObjekts, safePage, resultCount, allObjekts.size(), totalPages, false, error);
        }
        int fromIndex = safePage * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, resultCount);
        List<Map<String, Object>> pageSlice = filtered.subList(fromIndex, toIndex);
        List<Map<String, Object>> pageObjekts = marketData.withMintCounts(pageSlice);
        if (pageObjekts == null) pageObjekts = pageSlice;
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
        if ("members".equalsIgnoreCase(group) || (selected != null && !selected.isEmpty())) return "members";
        return "all";
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

    private Comparator<Map<String, Object>> sortComparator(String sort) {
        if ("mint_low".equals(sort)) return mintComparator(false);
        if ("mint_high".equals(sort)) return mintComparator(true);
        return ObjektCatalogSort.comparator(sort);
    }

    private Comparator<Map<String, Object>> mintComparator(boolean descending) {
        Comparator<Long> counts = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.<Map<String, Object>, Long>comparing(this::mintOf, Comparator.nullsLast(counts))
                .thenComparing(item -> firstValue(item, "slug", "id"));
    }

    private Long mintOf(Map<String, Object> item) {
        Long fromStore = marketData.mintCount(firstValue(item, "slug", "id"));
        if (fromStore != null) return fromStore;
        Object existing = item.get("mintCount");
        return existing instanceof Number n ? n.longValue() : null;
    }

    private static boolean matchesSearch(String query, Map<String, Object> objekt) {
        if (query == null || query.isBlank()) return true;
        String season = firstValue(objekt, "season", "seasonKey");
        String collectionNo = firstValue(objekt, "collectionNo");
        String base = String.join(" ",
                value(objekt, "searchText"), value(objekt, "collectionId"), value(objekt, "slug"),
                value(objekt, "season"), value(objekt, "seasonKey"), value(objekt, "member"),
                value(objekt, "memberName"), value(objekt, "artist"), value(objekt, "collectionNo"),
                value(objekt, "class"), value(objekt, "classKey"), value(objekt, "numberValue"))
                .toLowerCase(Locale.ROOT);
        return ObjektSeasonCodes.matches(query, base, season, collectionNo);
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

    private static String kindLabel(String kind) {
        String value = kind == null ? "" : kind.trim();
        if (value.isEmpty() || "all".equalsIgnoreCase(value) || "전체".equals(value)) return "전체";
        return value;
    }

    private List<Map<String, Object>> withMemberLinks(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return items == null ? List.of() : items;
        List<Artist> artists = artistSnapshot.findAll();
        List<Map<String, Object>> out = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            Map<String, Object> copy = new LinkedHashMap<>(item);
            String member = firstValue(item, "member", "memberName");
            copy.put("memberHref", "/cosmo/objekt/member?name="
                    + URLEncoder.encode(member, StandardCharsets.UTF_8).replace("+", "%20"));
            copy.put("memberColor", MemberPortraitOptions.artistColor(member, artists));
            String memberLogo = MemberPortraitOptions.objektMemberLogo(member);
            copy.put("memberLogo", memberLogo);
            copy.put("hasMemberLogo", !memberLogo.isBlank());
            copy.put("publicKey", ObjektLinks.key(item));
            copy.put("seasonColor", ObjektSeasonColors.colorFor(firstValue(item, "season", "seasonKey")));
            copy.put("cardTitle", ObjektSeasonCodes.cardTitle(
                    firstValue(item, "collectionId"), firstValue(item, "season", "seasonKey")));
            out.add(copy);
        }
        return out;
    }

    @GetMapping("/event")
    public String indexCosmoEvent(Model model) {
        model.addAttribute("eventSections", eventService.listGroupedBySection());
        return "cosmo/event";
    }

    @GetMapping("/event/{id:[0-9]+}")
    public String cosmoEventDetail(@PathVariable("id") long eventId, Model model) {
        try {
            Event event = eventService.getDetail(eventId);
            model.addAttribute("event", event);
            return "cosmo/event_detail";
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

}
