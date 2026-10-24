package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.ObjektService;
import com.example.triplesweb.util.MemberPortraitOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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

    @GetMapping("/objekt")
    public String indexCosmoObjekt(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) List<String> member,
            @RequestParam(required = false) List<String> season,
            @RequestParam(name = "class", required = false) List<String> classFilters,
            @RequestParam(defaultValue = "all") String kind,
            @RequestParam(defaultValue = "latest") String sort,
            Model model
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
        List<String> selectedSeasons = cleanSelections(season);
        List<String> selectedClasses = cleanSelections(classFilters);
        String selectedKind = normalize(kind);
        String selectedSort = normalize(sort);

        // 전체 캐시에서 검색어와 모든 필터를 먼저 적용하고, 그 결과에만 정렬과 페이지네이션을 적용한다.
        List<Map<String, Object>> filtered = allObjekts.stream()
                .filter(objekt -> query.isBlank() || searchableText(objekt).contains(query))
                .filter(objekt -> matchesMember(selectedMembers, objekt))
                .filter(objekt -> matchesSelection(selectedSeasons, firstValue(objekt, "seasonKey", "season")))
                .filter(objekt -> matchesSelection(selectedClasses, firstValue(objekt, "classKey", "class")))
                .filter(objekt -> selectedKind.isBlank() || "all".equals(selectedKind)
                        || normalize(firstValue(objekt, "collectionNo", "collectionNumber")).equals(selectedKind))
                .sorted(sortComparator(selectedSort))
                .collect(Collectors.toList());

        int resultCount = filtered.size();
        // 0개인 경우에도 페이지 표시는 1페이지로 유지한다.
        int totalPages = Math.max(1, (int) Math.ceil(resultCount / (double) PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int fromIndex = Math.min(safePage * PAGE_SIZE, resultCount);
        int toIndex = Math.min(fromIndex + PAGE_SIZE, resultCount);
        List<Map<String, Object>> pageObjekts = filtered.subList(fromIndex, toIndex);

        // 드롭다운 선택지는 현재 페이지 50개가 아니라 전체 캐시에서 생성한다.
        List<String> memberOptions = orderedMemberOptions(
                uniqueValues(allObjekts, objekt -> firstValue(objekt, "member", "memberName")));
        List<Artist> artists = artistRepository.findAll(Sort.by(Sort.Direction.ASC, "artistId"));
        model.addAttribute("memberOptions", memberOptions);
        model.addAttribute("memberPickerOptions", MemberPortraitOptions.forObjektFilter(memberOptions, artists));
        model.addAttribute("seasonOptions", uniqueValues(allObjekts, objekt -> firstValue(objekt, "seasonKey", "season")));
        model.addAttribute("classOptions", uniqueValues(allObjekts, objekt -> firstValue(objekt, "classKey", "class")));
        model.addAttribute("objektNumbers", uniqueValues(allObjekts, objekt -> firstValue(objekt, "collectionNo", "collectionNumber")));

        model.addAttribute("objekts", pageObjekts);
        model.addAttribute("objektCount", resultCount);
        model.addAttribute("objektPageCount", pageObjekts.size());
        model.addAttribute("objektAllCount", allObjekts.size());
        model.addAttribute("searchQuery", search == null ? "" : search);
        model.addAttribute("selectedKind", kind);
        model.addAttribute("selectedSort", sort);
        model.addAttribute("objektPageDisplay", safePage + 1);
        model.addAttribute("objektTotalPages", totalPages);
        model.addAttribute("hasPreviousPage", safePage > 0);
        model.addAttribute("hasNextPage", safePage + 1 < totalPages);
        model.addAttribute("previousPage", Math.max(0, safePage - 1));
        model.addAttribute("nextPage", Math.min(totalPages - 1, safePage + 1));
        model.addAttribute("previousPageUrl", buildPageUrl(Math.max(0, safePage - 1), search, selectedMembers, selectedSeasons, selectedClasses, kind, sort));
        model.addAttribute("nextPageUrl", buildPageUrl(Math.min(totalPages - 1, safePage + 1), search, selectedMembers, selectedSeasons, selectedClasses, kind, sort));
        model.addAttribute("objektError", error);

        return "cosmo/objekt";
    }

    /**
     * 멤버 체크박스는 사용자가 지정한 트리플에스 멤버 순서로 표시한다.
     * 이 목록에 없는 API 멤버 값은 아래쪽에 이름순으로 표시한다.
     */
    private static List<String> orderedMemberOptions(List<String> values) {
        List<String> englishOrder = List.of(
                "seoyeon", "hyerin", "jiwoo", "chaeyeon", "yooyeon", "soomin",
                "nakyoung", "yubin", "kaede", "dahyun", "kotone", "yeonji",
                "nien", "sohyun", "xinyu", "mayu", "lynn", "joobin",
                "hayeon", "shion", "chaewon", "sullin", "seoah", "jiyeon"
        );
        List<String> koreanOrder = List.of(
                "윤서연", "정혜린", "이지우", "김채연", "김유연", "김수민",
                "김나경", "공유빈", "카에데", "서다현", "코토네", "곽연지",
                "니엔", "박소현", "신위", "마유", "린", "주빈",
                "정하연", "박시온", "김채원", "설린", "서아", "지연"
        );

        return values.stream()
                .sorted(Comparator
                        .comparingInt((String value) -> preferredMemberIndex(value, englishOrder, koreanOrder))
                        .thenComparing(String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Comparator.naturalOrder()))
                .collect(Collectors.toList());
    }

    private static int preferredMemberIndex(String value, List<String> englishOrder, List<String> koreanOrder) {
        String normalized = normalizeMemberName(value);
        for (int i = 0; i < englishOrder.size(); i++) {
            String english = normalizeMemberName(englishOrder.get(i));
            String korean = normalizeMemberName(koreanOrder.get(i));
            if (normalized.equals(english) || normalized.equals(korean)
                    || normalized.contains(english) || normalized.contains(korean)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static String normalizeMemberName(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]", "");
    }

    private static List<String> cleanSelections(List<String> values) {
        if (values == null) return Collections.emptyList();
        return values.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank() && !"all".equalsIgnoreCase(value))
                .distinct().collect(Collectors.toList());
    }

    private static boolean matchesMember(List<String> selected, Map<String, Object> objekt) {
        if (selected == null || selected.isEmpty()) return true;
        String text = String.join(" ",
                firstValue(objekt, "member", "memberName"),
                firstValue(objekt, "collectionId"),
                firstValue(objekt, "slug"),
                firstValue(objekt, "artist"),
                value(objekt, "searchText"));
        return selected.stream().anyMatch(member -> MemberPortraitOptions.objektIncludesMember(text, member));
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

    private static Comparator<Map<String, Object>> sortComparator(String sort) {
        Comparator<Map<String, Object>> byDate = Comparator.comparing(
                objekt -> firstValue(objekt, "createdAt", "created_at", "date"),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        Comparator<Map<String, Object>> byNumber = Comparator.comparingDouble(objekt -> numberValue(firstValue(objekt, "numberValue", "number", "collectionNo")));
        Comparator<Map<String, Object>> byMember = Comparator.comparing(
                objekt -> firstValue(objekt, "member", "memberName"),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

        return switch (sort) {
            case "oldest" -> byDate;
            case "number_low", "mint_low" -> byNumber;
            case "number_high", "mint_high" -> byNumber.reversed();
            case "first_member" -> byMember;
            case "last_member" -> byMember.reversed();
            case "latest" -> byDate.reversed();
            default -> byDate.reversed();
        };
    }

    private static double numberValue(String value) {
        try { return Double.parseDouble(value); }
        catch (Exception ignored) { return 0; }
    }

    private static String buildPageUrl(int page, String search, List<String> members, List<String> seasons,
                                       List<String> classes, String kind, String sort) {
        StringBuilder url = new StringBuilder("/cosmo/objekt?page=").append(page);
        addParam(url, "search", search);
        addParams(url, "member", members);
        addParams(url, "season", seasons);
        addParams(url, "class", classes);
        if (kind != null && !kind.isBlank() && !"all".equalsIgnoreCase(kind)) addParam(url, "kind", kind);
        if (sort != null && !sort.isBlank() && !"latest".equalsIgnoreCase(sort)) addParam(url, "sort", sort);
        return url.toString();
    }

    private static void addParams(StringBuilder url, String name, Collection<String> values) {
        if (values == null) return;
        values.forEach(value -> addParam(url, name, value));
    }

    private static void addParam(StringBuilder url, String name, String value) {
        if (value == null || value.isBlank()) return;
        url.append('&').append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                .append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
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

    @GetMapping("/update")
    public String indexCosmoUpdate() { return "cosmo/update"; }
}
