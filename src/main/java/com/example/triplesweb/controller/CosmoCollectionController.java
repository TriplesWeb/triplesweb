package com.example.triplesweb.controller;

import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.CosmoCollectionService;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.example.triplesweb.util.ObjektFilterOrder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class CosmoCollectionController {
    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) out.add(item.toString());
        }
        return out;
    }

    private final CosmoCollectionService collections;
    private final ArtistRepository artists;

    public CosmoCollectionController(CosmoCollectionService collections, ArtistRepository artists) {
        this.collections = collections;
        this.artists = artists;
    }

    @GetMapping("/cosmo/collection")
    @SuppressWarnings("unchecked")
    public String collection(@RequestParam(required = false) String nickname,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "") String member,
                             @RequestParam(required = false) List<String> season,
                             @RequestParam(name = "class", required = false) List<String> objektClass,
                             @RequestParam(name = "q", defaultValue = "") String query,
                             @RequestParam(defaultValue = "all") String group,
                             @RequestParam(defaultValue = "all") String kind,
                             @RequestParam(defaultValue = "latest") String sort,
                             Model model) {
        model.addAttribute("query", nickname == null ? "" : nickname.trim());
        if (nickname == null) return "cosmo/collection";
        if (nickname.isBlank()) {
            model.addAttribute("collectionError", "없는 닉네임 입니다.");
            return "cosmo/collection";
        }
        try {
            Map<String, Object> view = collections.search(nickname, page, member,
                    season == null ? List.of() : season,
                    objektClass == null ? List.of() : objektClass,
                    query, group, kind, sort);
            model.addAllAttributes(view);
            if (view.get("selectedKind") == null) model.addAttribute("selectedKind", "all");
            if (view.get("selectedSort") == null) model.addAttribute("selectedSort", "latest");
            if (view.get("kindOptions") == null) model.addAttribute("kindOptions", List.of());
            model.addAttribute("hasCollection", true);
            List<Map<String, Object>> allCards = (List<Map<String, Object>>) view.getOrDefault("allCards", List.of());
            Set<String> rawMembers = new LinkedHashSet<>();
            Set<String> seasons = new LinkedHashSet<>();
            Set<String> classes = new LinkedHashSet<>();
            for (Map<String, Object> card : allCards) {
                Object m = card.get("member");
                Object s = card.get("season");
                Object c = card.get("objektClass");
                if (m != null && !m.toString().isBlank()) rawMembers.add(m.toString());
                if (s != null && !s.toString().isBlank()) seasons.add(s.toString());
                if (c != null && !c.toString().isBlank()) classes.add(c.toString());
            }
            List<Map<String, Object>> picker = MemberPortraitOptions.forObjektFilter(
                    MemberPortraitOptions.soloMemberNames(rawMembers), artists.findAll());
            String selectedMember = String.valueOf(view.getOrDefault("selectedMember", ""));
            for (Map<String, Object> option : picker) {
                option.put("active", MemberPortraitOptions.sameMember(selectedMember, String.valueOf(option.get("value"))));
            }
            model.addAttribute("memberPickerOptions", picker);
            List<String> selectedSeasons = stringList(view.get("selectedSeasons"));
            List<String> selectedClasses = stringList(view.get("selectedClasses"));
            List<Map<String, Object>> seasonChoices = new ArrayList<>();
            for (String value : ObjektFilterOrder.sortedSeasons(seasons)) {
                seasonChoices.add(Map.of("value", value, "selected", selectedSeasons.stream().anyMatch(value::equalsIgnoreCase)));
            }
            List<Map<String, Object>> classChoices = new ArrayList<>();
            for (String value : classes.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
                classChoices.add(Map.of("value", value, "selected", selectedClasses.stream().anyMatch(value::equalsIgnoreCase)));
            }
            model.addAttribute("seasonChoices", seasonChoices);
            model.addAttribute("classChoices", classChoices);
            model.addAttribute("hasSeasonFilter", seasonChoices.stream().anyMatch(choice -> Boolean.TRUE.equals(choice.get("selected"))));
            model.addAttribute("hasClassFilter", classChoices.stream().anyMatch(choice -> Boolean.TRUE.equals(choice.get("selected"))));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("collectionError", ex.getMessage());
        } catch (RuntimeException ex) {
            model.addAttribute("collectionError", "소유 오브젝트를 바로 불러오지 못했습니다. 잠시 후 다시 검색해 주세요.");
        }
        return "cosmo/collection";
    }

    @GetMapping("/cosmo/collection/more")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> more(@RequestParam String nickname,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "") String member,
                                                    @RequestParam(required = false) List<String> season,
                                                    @RequestParam(name = "class", required = false) List<String> objektClass,
                                                    @RequestParam(name = "q", defaultValue = "") String query,
                                                    @RequestParam(defaultValue = "all") String group,
                                                    @RequestParam(defaultValue = "all") String kind,
                                                    @RequestParam(defaultValue = "latest") String sort) {
        try {
            Map<String, Object> view = collections.search(nickname, Math.max(0, page), member,
                    season == null ? List.of() : season,
                    objektClass == null ? List.of() : objektClass,
                    query, group, kind, sort);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("items", view.getOrDefault("cards", List.of()));
            body.put("page", view.get("page"));
            body.put("pageSize", view.get("pageSize"));
            body.put("ownedCount", view.get("ownedCount"));
            body.put("filteredCount", view.get("filteredCount"));
            body.put("hasMore", Boolean.TRUE.equals(view.get("hasMore")));
            body.put("nextPage", view.get("nextPage"));
            body.put("loading", Boolean.TRUE.equals(view.get("loading")));
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", ex.getMessage(), "items", List.of(), "hasMore", false));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "소유 오브젝트를 바로 불러오지 못했습니다.", "items", List.of(), "hasMore", false));
        }
    }
}
