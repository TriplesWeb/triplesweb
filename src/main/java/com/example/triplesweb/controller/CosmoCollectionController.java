package com.example.triplesweb.controller;

import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.service.CosmoCollectionService;
import com.example.triplesweb.util.MemberPortraitOptions;
import com.example.triplesweb.util.ObjektFilterOrder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class CosmoCollectionController {
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
                             @RequestParam(defaultValue = "") String season,
                             @RequestParam(name = "class", defaultValue = "") String objektClass,
                             @RequestParam(name = "q", defaultValue = "") String query,
                             @RequestParam(defaultValue = "all") String group,
                             Model model) {
        model.addAttribute("query", nickname == null ? "" : nickname.trim());
        if (nickname == null) return "cosmo/collection";
        if (nickname.isBlank()) {
            model.addAttribute("collectionError", "없는 닉네임 입니다.");
            return "cosmo/collection";
        }
        try {
            Map<String, Object> view = collections.search(nickname, page, member, season, objektClass, query, group);
            model.addAllAttributes(view);
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
            List<Map<String, Object>> seasonChoices = new ArrayList<>();
            for (String value : ObjektFilterOrder.sortedSeasons(seasons)) {
                seasonChoices.add(Map.of("value", value, "selected", value.equalsIgnoreCase(String.valueOf(view.get("selectedSeason")))));
            }
            List<Map<String, Object>> classChoices = new ArrayList<>();
            for (String value : classes.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
                classChoices.add(Map.of("value", value, "selected", value.equalsIgnoreCase(String.valueOf(view.get("selectedClass")))));
            }
            model.addAttribute("seasonChoices", seasonChoices);
            model.addAttribute("classChoices", classChoices);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("collectionError", ex.getMessage());
        } catch (RuntimeException ex) {
            model.addAttribute("collectionError", "소유 오브젝트를 바로 불러오지 못했습니다. 잠시 후 다시 검색해 주세요.");
        }
        return "cosmo/collection";
    }
}
