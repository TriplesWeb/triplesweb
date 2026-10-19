package com.example.triplesweb.controller;

import com.example.triplesweb.service.ObjektService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/cosmo")
@RequiredArgsConstructor
public class CosmoController {

    private final ObjektService objektService;

    @GetMapping("/objekt")
    public String indexCosmoObjekt(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String search,
            Model model
    ) {
        List<Map<String, Object>> allObjekts;
        String error = "";

        try {
            allObjekts = objektService.findCollections("triples");
            if (allObjekts == null) {
                allObjekts = Collections.emptyList();
            }
            if (allObjekts.isEmpty()) {
                error = "API 응답에 표시할 컬렉션이 없습니다.";
            }
        } catch (RestClientException ex) {
            allObjekts = Collections.emptyList();
            error = "Objekt API에 연결하지 못했습니다. 잠시 후 새로고침해 주세요.";
            ex.printStackTrace();
        }

        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        // 페이지네이션보다 먼저 전체 캐시에서 검색한다.
        List<Map<String, Object>> filteredObjekts = query.isBlank()
                ? allObjekts
                : allObjekts.stream()
                    .filter(objekt -> searchableText(objekt).contains(query))
                    .toList();

        int pageSize = 50;
        int totalCount = filteredObjekts.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) pageSize));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int fromIndex = Math.min(safePage * pageSize, totalCount);
        int toIndex = Math.min(fromIndex + pageSize, totalCount);
        List<Map<String, Object>> pageObjekts = filteredObjekts.subList(fromIndex, toIndex);

        // 종류 필터 선택지는 검색 결과가 아닌 전체 오브젝트 기준으로 만든다.
        List<String> objektNumbers = allObjekts.stream()
                .map(objekt -> String.valueOf(objekt.getOrDefault("collectionNo", "")))
                .filter(number -> !number.isBlank() && !"null".equals(number))
                .distinct()
                .sorted()
                .toList();

        model.addAttribute("objekts", pageObjekts);
        model.addAttribute("objektCount", totalCount);
        model.addAttribute("objektAllCount", allObjekts.size());
        model.addAttribute("objektPageCount", pageObjekts.size());
        model.addAttribute("searchQuery", search == null ? "" : search);
        model.addAttribute("objektPageDisplay", safePage + 1);
        model.addAttribute("objektTotalPages", totalPages);
        model.addAttribute("hasPreviousPage", safePage > 0);
        model.addAttribute("hasNextPage", safePage + 1 < totalPages);
        model.addAttribute("previousPage", Math.max(0, safePage - 1));
        model.addAttribute("nextPage", Math.min(totalPages - 1, safePage + 1));
        model.addAttribute("objektError", error);
        model.addAttribute("objektNumbers", objektNumbers);

        return "cosmo/objekt";
    }

    private static String searchableText(Map<String, Object> objekt) {
        return String.join(" ",
                value(objekt, "searchText"),
                value(objekt, "collectionId"),
                value(objekt, "slug"),
                value(objekt, "season"),
                value(objekt, "member"),
                value(objekt, "artist"),
                value(objekt, "collectionNo"),
                value(objekt, "class")
        ).toLowerCase(Locale.ROOT);
    }

    private static String value(Map<String, Object> objekt, String key) {
        Object value = objekt.get(key);
        return value == null ? "" : value.toString();
    }

    @GetMapping("/gravity")
    public String indexGravity() {
        return "cosmo/gravity";
    }

    @GetMapping("/event")
    public String indexCosmoEvent() {
        return "cosmo/event";
    }

    @GetMapping("/update")
    public String indexCosmoUpdate() {
        return "cosmo/update";
    }
}
